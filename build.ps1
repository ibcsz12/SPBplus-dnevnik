# Paths setup
$ANDROID_HOME = "C:\android-sdk"
$BT_VER = "34.0.0"
$API = "34"

$AAPT2 = "$ANDROID_HOME\build-tools\$BT_VER\aapt2.exe"
$D8 = "$ANDROID_HOME\build-tools\$BT_VER\d8.bat"
$ZIPALIGN = "$ANDROID_HOME\build-tools\$BT_VER\zipalign.exe"
$APKSIGNER = "$ANDROID_HOME\build-tools\$BT_VER\apksigner.bat"
$ANDROID_JAR = "$ANDROID_HOME\platforms\android-$API\android.jar"

# Auto-detect 7-Zip path
$7ZIP_EXE = "7z.exe"
if (-not (Get-Command $7ZIP_EXE -ErrorAction SilentlyContinue)) {
    $standardPaths = @(
        "${env:ProgramFiles}\7-Zip\7z.exe",
        "${env:ProgramFiles(x86)}\7-Zip\7z.exe"
    )
    foreach ($path in $standardPaths) {
        if (Test-Path $path) {
            $7ZIP_EXE = $path
            break
        }
    }
}

# Clean folders
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path build\obj -Force | Out-Null
New-Item -ItemType Directory -Path build\gen -Force | Out-Null

# -------------------------------------------------------------
# 1. Компиляция и линковка ресурсов (aapt2)
# -------------------------------------------------------------
Write-Host "1. Compiling & Linking resources (aapt2)..." -ForegroundColor Cyan

# 1.1 Сначала компилируем папку res в архив res.zip
if (Test-Path "res") {
    & $AAPT2 compile --dir res -o build\res.zip
    if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: aapt2 compile failed!" -ForegroundColor Red; Exit }
    $resInput = "build\res.zip"
} else {
    $resInput = $null
}

# 1.2 Линкуем ресурсы, генерируя R.java в папку build\gen (--java build\gen)
& $AAPT2 link `
    -o build\DnevnikClient.unaligned.apk `
    -I $ANDROID_JAR `
    --manifest AndroidManifest.xml `
    --min-sdk-version 24 `
    --target-sdk-version $API `
    --java build\gen `
    --auto-add-overlay `
    $resInput

if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: aapt2 link failed!" -ForegroundColor Red; Exit }

# -------------------------------------------------------------
# 2. Компиляция Java (javac)
# -------------------------------------------------------------
Write-Host "2. Compiling Java (javac)..." -ForegroundColor Cyan

# Берем файлы И из src, И сгенерированный R.java из build\gen
$javaFiles = Get-ChildItem -Path src, build\gen -Filter *.java -Recurse | Select-Object -ExpandProperty FullName

javac -encoding UTF-8 `
    -source 17 -target 17 `
    -classpath "$ANDROID_JAR" `
    -d build\obj `
    $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilation failed at step 2." -ForegroundColor Red
    Exit
}

# -------------------------------------------------------------
# 3. Конвертация в DEX (d8)
# -------------------------------------------------------------
Write-Host "3. Converting to DEX (d8)..." -ForegroundColor Cyan
$classFiles = Get-ChildItem -Path build\obj -Filter *.class -Recurse | Select-Object -ExpandProperty FullName
if (-not $classFiles) {
    Write-Host "ERROR: No .class files found!" -ForegroundColor Red
    Exit
}
& $D8 --output build\ $classFiles --lib $ANDROID_JAR
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: d8 failed!" -ForegroundColor Red; Exit }

# -------------------------------------------------------------
# 4. Добавление classes.dex в APK
# -------------------------------------------------------------
Write-Host "4. Adding classes.dex to APK..." -ForegroundColor Cyan
if (-not (Get-Command $7ZIP_EXE -ErrorAction SilentlyContinue) -and -not (Test-Path $7ZIP_EXE)) {
    Write-Host "ERROR: 7-Zip not found! Please install 7-Zip or fix path." -ForegroundColor Red
    Exit
}
& $7ZIP_EXE u -tzip build\DnevnikClient.unaligned.apk .\build\classes.dex | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: 7-Zip failed!" -ForegroundColor Red; Exit }

# -------------------------------------------------------------
# 5. Выравнивание APK (zipalign)
# -------------------------------------------------------------
Write-Host "5. Aligning APK (zipalign)..." -ForegroundColor Cyan
& $ZIPALIGN -f 4 build\DnevnikClient.unaligned.apk build\DnevnikClient.aligned.apk
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: zipalign failed!" -ForegroundColor Red; Exit }

# Keystore generation
$keystore = "$HOME\dnevnik-debug.keystore"
if (-not (Test-Path $keystore)) {
    Write-Host "Generating debug keystore..." -ForegroundColor Yellow
    keytool -genkeypair -v -keystore $keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
}

# -------------------------------------------------------------
# 6. Подпись APK (apksigner)
# -------------------------------------------------------------
Write-Host "6. Signing APK (apksigner)..." -ForegroundColor Cyan
& $APKSIGNER sign --ks $keystore --ks-pass pass:android --out build\DnevnikClient.apk build\DnevnikClient.aligned.apk
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: apksigner failed!" -ForegroundColor Red; Exit }

# Cleanup промежуточных файлов
Remove-Item build\DnevnikClient.unaligned.apk -ErrorAction SilentlyContinue
Remove-Item build\DnevnikClient.aligned.apk -ErrorAction SilentlyContinue
Remove-Item build\res.zip -ErrorAction SilentlyContinue

Write-Host "Done! Output file: build\DnevnikClient.apk" -ForegroundColor Green