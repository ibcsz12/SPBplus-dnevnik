package ru.main.spbplus;

import ru.main.spbplus.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DiaryActivity extends Activity {
    private static final int TAB_SUMMARY = 0;
    private static final int TAB_SCHEDULE = 1;
    private static final int TAB_HW = 2;
    private static final int TAB_MARKS = 3;
    private static final int TAB_SETTINGS = 4;

    private static final int HW_SUB_ACTUAL = 0;
    private static final int HW_SUB_TOMORROW = 1;

    private Api api;
    private SharedPreferences prefs;
    private List<String> educationIds = new ArrayList<>();
    private final Calendar currentDay = Calendar.getInstance();

    private int currentTab = TAB_SUMMARY;
    private int previousTab = TAB_SUMMARY;
    private int currentHwSub = HW_SUB_ACTUAL;
    private boolean darkMode = false;

    private View rootLayout, headerLayout, tabsContainer, layoutDayNav, layoutSummaryHeader, layoutHwSubTabs;
    private View cardProfile, cardAppearance;
    private TextView tvUserName, tvUserSchool, tvSelectedDate, tvEmptyState, tvSummarySubtitle;
    private TextView tvSettingsTitleProfile, tvSettingsFio, tvSettingsSchool, tvSettingsEduId;
    private TextView tvSettingsTitleTheme, tvSettingsThemeDesc;
    private Button btnRefresh, btnHeaderSettings, btnPrevDay, btnNextDay, btnToggleTheme, btnLogout;
    private Button btnTabSummary, btnTabSchedule, btnTabHw, btnTabMarks;
    private Button btnSubActual, btnSubTomorrow;
    private Button btnBigSchedule, btnBigActual, btnBigMarks, btnBigSettings;
    private ProgressBar progressBar;
    private ListView listView;
    private ScrollView layoutSettings;
    private LessonAdapter adapter;

    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_diary);

        prefs = getSharedPreferences("dnevnik", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        darkMode = prefs.getBoolean("dark_mode", false);

        if (token == null || !token.startsWith("eyJ")) {
            redirectToLogin();
            return;
        }

        api = new Api(token);

        CookieManager.getInstance().flush();

        rootLayout = findViewById(R.id.rootLayout);
        headerLayout = findViewById(R.id.headerLayout);
        tabsContainer = findViewById(R.id.tabsContainer);
        layoutDayNav = findViewById(R.id.layoutDayNav);
        layoutSummaryHeader = findViewById(R.id.layoutSummaryHeader);
        layoutHwSubTabs = findViewById(R.id.layoutHwSubTabs);

        tvUserName = findViewById(R.id.tvUserName);
        tvUserSchool = findViewById(R.id.tvUserSchool);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvSummarySubtitle = findViewById(R.id.tvSummarySubtitle);
        progressBar = findViewById(R.id.progressBar);

        btnRefresh = findViewById(R.id.btnRefresh);
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings);
        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);

        btnBigSchedule = findViewById(R.id.btnBigSchedule);
        btnBigActual = findViewById(R.id.btnBigActual);
        btnBigMarks = findViewById(R.id.btnBigMarks);
        btnBigSettings = findViewById(R.id.btnBigSettings);

        btnTabSummary = findViewById(R.id.btnTabSummary);
        btnTabSchedule = findViewById(R.id.btnTabSchedule);
        btnTabHw = findViewById(R.id.btnTabHw);
        btnTabMarks = findViewById(R.id.btnTabMarks);

        btnSubActual = findViewById(R.id.btnSubActual);
        btnSubTomorrow = findViewById(R.id.btnSubTomorrow);

        listView = findViewById(R.id.lvLessons);
        layoutSettings = findViewById(R.id.layoutSettings);

        cardProfile = findViewById(R.id.cardProfile);
        cardAppearance = findViewById(R.id.cardAppearance);
        tvSettingsTitleProfile = findViewById(R.id.tvSettingsTitleProfile);
        tvSettingsFio = findViewById(R.id.tvSettingsFio);
        tvSettingsSchool = findViewById(R.id.tvSettingsSchool);
        tvSettingsEduId = findViewById(R.id.tvSettingsEduId);
        tvSettingsTitleTheme = findViewById(R.id.tvSettingsTitleTheme);
        tvSettingsThemeDesc = findViewById(R.id.tvSettingsThemeDesc);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnLogout = findViewById(R.id.btnLogout);

        adapter = new LessonAdapter(this);
        listView.setAdapter(adapter);

        btnLogout.setOnClickListener(v -> redirectToLogin());
        btnRefresh.setOnClickListener(v -> reloadCurrentTab());

        btnHeaderSettings.setOnClickListener(v -> {
            if (currentTab == TAB_SETTINGS) {
                switchTab(previousTab != TAB_SETTINGS ? previousTab : TAB_SUMMARY);
            } else {
                previousTab = currentTab;
                switchTab(TAB_SETTINGS);
            }
        });

        btnPrevDay.setOnClickListener(v -> {
            currentDay.add(Calendar.DAY_OF_YEAR, -1);
            loadScheduleDay();
        });

        btnNextDay.setOnClickListener(v -> {
            currentDay.add(Calendar.DAY_OF_YEAR, 1);
            loadScheduleDay();
        });

        btnBigSchedule.setOnClickListener(v -> switchTab(TAB_SCHEDULE));
        btnBigActual.setOnClickListener(v -> switchTab(TAB_HW));
        btnBigMarks.setOnClickListener(v -> switchTab(TAB_MARKS));
        btnBigSettings.setOnClickListener(v -> {
            previousTab = TAB_SUMMARY;
            switchTab(TAB_SETTINGS);
        });

        btnTabSummary.setOnClickListener(v -> switchTab(TAB_SUMMARY));
        btnTabSchedule.setOnClickListener(v -> switchTab(TAB_SCHEDULE));
        btnTabHw.setOnClickListener(v -> switchTab(TAB_HW));
        btnTabMarks.setOnClickListener(v -> switchTab(TAB_MARKS));

        btnSubActual.setOnClickListener(v -> {
            currentHwSub = HW_SUB_ACTUAL;
            updateHwSubTabStyles();
            loadActualHomework();
        });

        btnSubTomorrow.setOnClickListener(v -> {
            currentHwSub = HW_SUB_TOMORROW;
            updateHwSubTabStyles();
            loadTomorrowHomework();
        });

        btnToggleTheme.setOnClickListener(v -> {
            darkMode = !darkMode;
            prefs.edit().putBoolean("dark_mode", darkMode).apply();
            applyTheme();
        });

        applyTheme();
        initProfileAndLoad();
    }

    private void applyTheme() {
        float d = getResources().getDisplayMetrics().density;
        adapter.setDarkMode(darkMode);

        int statusBarColor;
        if (darkMode) {
            statusBarColor = Color.parseColor("#1A1C26");
            rootLayout.setBackgroundColor(Color.parseColor("#12131A"));
            headerLayout.setBackgroundColor(Color.parseColor("#1A1C26"));
            tabsContainer.setBackgroundColor(Color.parseColor("#1A1C26"));
            layoutHwSubTabs.setBackgroundColor(Color.parseColor("#1A1C26"));

            tvUserName.setTextColor(Color.parseColor("#ECEFF4"));
            tvUserSchool.setTextColor(Color.parseColor("#8F95B2"));
            tvSelectedDate.setTextColor(Color.parseColor("#ECEFF4"));
            tvEmptyState.setTextColor(Color.parseColor("#8F95B2"));
            tvSummarySubtitle.setTextColor(Color.parseColor("#B388FF"));

            tvSettingsTitleProfile.setTextColor(Color.parseColor("#B388FF"));
            tvSettingsTitleTheme.setTextColor(Color.parseColor("#B388FF"));
            tvSettingsFio.setTextColor(Color.parseColor("#ECEFF4"));
            tvSettingsSchool.setTextColor(Color.parseColor("#D8DEE9"));
            tvSettingsEduId.setTextColor(Color.parseColor("#8F95B2"));
            tvSettingsThemeDesc.setTextColor(Color.parseColor("#8F95B2"));

            btnToggleTheme.setText("☀️ Включить светлую тему");
        } else {
            statusBarColor = Color.parseColor("#FFFFFF");
            rootLayout.setBackgroundColor(Color.parseColor("#F4F5F7"));
            headerLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
            tabsContainer.setBackgroundColor(Color.parseColor("#FFFFFF"));
            layoutHwSubTabs.setBackgroundColor(Color.parseColor("#FFFFFF"));

            tvUserName.setTextColor(Color.parseColor("#111111"));
            tvUserSchool.setTextColor(Color.parseColor("#757575"));
            tvSelectedDate.setTextColor(Color.parseColor("#212121"));
            tvEmptyState.setTextColor(Color.parseColor("#9E9E9E"));
            tvSummarySubtitle.setTextColor(Color.parseColor("#5E35B1"));

            tvSettingsTitleProfile.setTextColor(Color.parseColor("#5E35B1"));
            tvSettingsTitleTheme.setTextColor(Color.parseColor("#5E35B1"));
            tvSettingsFio.setTextColor(Color.parseColor("#212121"));
            tvSettingsSchool.setTextColor(Color.parseColor("#424242"));
            tvSettingsEduId.setTextColor(Color.parseColor("#757575"));
            tvSettingsThemeDesc.setTextColor(Color.parseColor("#757575"));

            btnToggleTheme.setText("🌙 Включить тёмную тему");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(statusBarColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decor = window.getDecorView();
                if (darkMode) {
                    decor.setSystemUiVisibility(0);
                } else {
                    decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        }

        styleCard(cardProfile, d);
        styleCard(cardAppearance, d);

        styleSecondaryButton(btnRefresh, d);
        styleSecondaryButton(btnHeaderSettings, d);
        styleSecondaryButton(btnPrevDay, d);
        styleSecondaryButton(btnNextDay, d);

        styleBigButton(btnBigSchedule, d);
        styleBigButton(btnBigActual, d);
        styleBigButton(btnBigMarks, d);
        styleBigButton(btnBigSettings, d);

        GradientDrawable themeBtnBg = new GradientDrawable();
        themeBtnBg.setCornerRadius(10 * d);
        themeBtnBg.setColor(darkMode ? Color.parseColor("#7C4DFF") : Color.parseColor("#5E35B1"));
        btnToggleTheme.setBackground(themeBtnBg);
        btnToggleTheme.setTextColor(Color.WHITE);

        GradientDrawable logoutBg = new GradientDrawable();
        logoutBg.setCornerRadius(10 * d);
        logoutBg.setColor(darkMode ? Color.parseColor("#3B1F26") : Color.parseColor("#FFEBEE"));
        logoutBg.setStroke((int) (1 * d), Color.parseColor("#E53935"));
        btnLogout.setBackground(logoutBg);
        btnLogout.setTextColor(Color.parseColor("#EF5350"));

        updateTabStyles();
        updateHwSubTabStyles();
    }

    private void styleBigButton(Button btn, float d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * d);
        if (darkMode) {
            bg.setColor(Color.parseColor("#1E202B"));
            bg.setStroke((int) (1 * d), Color.parseColor("#2E3244"));
            btn.setTextColor(Color.parseColor("#ECEFF4"));
        } else {
            bg.setColor(Color.parseColor("#FFFFFF"));
            bg.setStroke((int) (1 * d), Color.parseColor("#E0E0E0"));
            btn.setTextColor(Color.parseColor("#212121"));
        }
        btn.setBackground(bg);
    }

    private void styleCard(View card, float d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * d);
        if (darkMode) {
            bg.setColor(Color.parseColor("#1E202B"));
            bg.setStroke((int) (1 * d), Color.parseColor("#2E3244"));
        } else {
            bg.setColor(Color.parseColor("#FFFFFF"));
            bg.setStroke((int) (1 * d), Color.parseColor("#E0E0E0"));
        }
        card.setBackground(bg);
    }

    private void styleSecondaryButton(Button btn, float d) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * d);
        if (darkMode) {
            bg.setColor(Color.parseColor("#262938"));
            btn.setTextColor(Color.parseColor("#ECEFF4"));
        } else {
            bg.setColor(Color.parseColor("#ECEFF1"));
            btn.setTextColor(Color.parseColor("#37474F"));
        }
        btn.setBackground(bg);
        btn.setPadding(0, 0, 0, 0);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
    }

    private void updateTabStyles() {
        float d = getResources().getDisplayMetrics().density;
        Button[] tabs = {btnTabSummary, btnTabSchedule, btnTabHw, btnTabMarks};

        for (int i = 0; i < tabs.length; i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8 * d);

            if (i == currentTab) {
                bg.setColor(darkMode ? Color.parseColor("#7C4DFF") : Color.parseColor("#5E35B1"));
                tabs[i].setTextColor(Color.parseColor("#FFFFFF"));
            } else {
                bg.setColor(darkMode ? Color.parseColor("#252836") : Color.parseColor("#EDE7F6"));
                tabs[i].setTextColor(darkMode ? Color.parseColor("#8F95B2") : Color.parseColor("#7E57C2"));
            }
            tabs[i].setBackground(bg);
        }
    }

    private void updateHwSubTabStyles() {
        float d = getResources().getDisplayMetrics().density;

        GradientDrawable bgActual = new GradientDrawable();
        bgActual.setCornerRadius(8 * d);

        GradientDrawable bgTomorrow = new GradientDrawable();
        bgTomorrow.setCornerRadius(8 * d);

        if (currentHwSub == HW_SUB_ACTUAL) {
            bgActual.setColor(darkMode ? Color.parseColor("#7C4DFF") : Color.parseColor("#5E35B1"));
            btnSubActual.setTextColor(Color.WHITE);

            bgTomorrow.setColor(darkMode ? Color.parseColor("#252836") : Color.parseColor("#ECEFF1"));
            btnSubTomorrow.setTextColor(darkMode ? Color.parseColor("#8F95B2") : Color.parseColor("#546E7A"));
        } else {
            bgTomorrow.setColor(darkMode ? Color.parseColor("#7C4DFF") : Color.parseColor("#5E35B1"));
            btnSubTomorrow.setTextColor(Color.WHITE);

            bgActual.setColor(darkMode ? Color.parseColor("#252836") : Color.parseColor("#ECEFF1"));
            btnSubActual.setTextColor(darkMode ? Color.parseColor("#8F95B2") : Color.parseColor("#546E7A"));
        }

        btnSubActual.setBackground(bgActual);
        btnSubTomorrow.setBackground(bgTomorrow);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        updateTabStyles();

        if (tab == TAB_SUMMARY) {
            layoutSummaryHeader.setVisibility(View.VISIBLE);
            tabsContainer.setVisibility(View.GONE);
            layoutHwSubTabs.setVisibility(View.GONE);
            layoutDayNav.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            loadSummary();
        } else {
            layoutSummaryHeader.setVisibility(View.GONE);
            tabsContainer.setVisibility(View.VISIBLE);

            if (tab == TAB_SETTINGS) {
                layoutHwSubTabs.setVisibility(View.GONE);
                layoutDayNav.setVisibility(View.GONE);
                listView.setVisibility(View.GONE);
                tvEmptyState.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                layoutSettings.setVisibility(View.VISIBLE);
            } else {
                layoutSettings.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                layoutDayNav.setVisibility(tab == TAB_SCHEDULE ? View.VISIBLE : View.GONE);
                layoutHwSubTabs.setVisibility(tab == TAB_HW ? View.VISIBLE : View.GONE);
                reloadCurrentTab();
            }
        }
    }

    private void reloadCurrentTab() {
        if (currentTab == TAB_SUMMARY) {
            loadSummary();
        } else if (currentTab == TAB_SCHEDULE) {
            loadScheduleDay();
        } else if (currentTab == TAB_HW) {
            if (currentHwSub == HW_SUB_ACTUAL) {
                loadActualHomework();
            } else {
                loadTomorrowHomework();
            }
        } else if (currentTab == TAB_MARKS) {
            loadAllMarks();
        }
    }

    // Чистый переход на окно логина без наслоения экранов
    private void redirectToLogin() {
        prefs.edit().remove("token").apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("force_relogin", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initProfileAndLoad() {
        setLoading(true);
        new Thread(() -> {
            try {
                Api.StudentInfo info = api.studentInfo();
                String name = info.fio;
                String school = info.school;

                if (name.isEmpty()) {
                    JSONObject me = api.currentUser();
                    name = (me.optString("first_name", "") + " " + me.optString("last_name", "")).trim();
                    if (name.isEmpty()) name = me.optString("name", "Ученик");
                }
                if (school.isEmpty()) {
                    school = "Петербургское образование";
                }

                educationIds = api.educationIds();

                String finalName = name;
                String finalSchool = school;
                String eduIdStr = educationIds.isEmpty() ? "504817" : educationIds.get(0);

                runOnUiThread(() -> {
                    tvUserName.setText(finalName);
                    tvUserSchool.setText(finalSchool);
                    tvSettingsFio.setText("Ученик: " + finalName);
                    tvSettingsSchool.setText("Школа: " + finalSchool);
                    tvSettingsEduId.setText("ID обучения: " + eduIdStr);
                    switchTab(TAB_SUMMARY);
                });
            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    // ВКЛАДКА 0: Сводка
    private void loadSummary() {
        Calendar calFrom = Calendar.getInstance();
        calFrom.add(Calendar.DAY_OF_YEAR, -14);
        String dateFrom = apiDateFormat.format(calFrom.getTime());

        Calendar calTo = Calendar.getInstance();
        calTo.add(Calendar.DAY_OF_YEAR, 2);
        String dateTo = apiDateFormat.format(calTo.getTime());

        String eduId = educationIds.isEmpty() ? "504817" : educationIds.get(0);

        setLoading(true);

        new Thread(() -> {
            List<Lesson> summaryCards = new ArrayList<>();
            try {
                JSONArray items = api.allEstimates(eduId, dateFrom, dateTo);
                List<JSONObject> realMarks = new ArrayList<>();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject m = items.getJSONObject(i);
                    if (isAttendanceMark(m)) continue;

                    String val = cleanStr(m, "estimate_value_name");
                    if (val.isEmpty()) continue;
                    realMarks.add(m);
                }

                SimpleDateFormat parser = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
                Collections.sort(realMarks, new Comparator<JSONObject>() {
                    @Override
                    public int compare(JSONObject a, JSONObject b) {
                        try {
                            Date da = parser.parse(cleanStr(a, "date"));
                            Date db = parser.parse(cleanStr(b, "date"));
                            if (da != null && db != null) {
                                return db.compareTo(da);
                            }
                        } catch (Exception ignored) {}
                        return 0;
                    }
                });

                int counter = 1;
                for (JSONObject m : realMarks) {
                    String subj = cleanStr(m, "subject_name");
                    if (subj.isEmpty()) subj = "Предмет";
                    String val = cleanStr(m, "estimate_value_name");
                    String date = cleanStr(m, "date");
                    String type = cleanStr(m, "estimate_type_name");
                    String comment = cleanStr(m, "estimate_comment");

                    StringBuilder details = new StringBuilder();
                    if (!type.isEmpty()) {
                        details.append("<b>Тип работы:</b> ").append(type);
                    }
                    if (!comment.isEmpty()) {
                        if (details.length() > 0) details.append("<br>");
                        details.append("<i>Примечание: ").append(comment).append("</i>");
                    }
                    if (details.length() == 0) {
                        details.append("Ответ на уроке");
                    }

                    String dateLabel = date.isEmpty() ? "" : "Получена: " + date;

                    summaryCards.add(new Lesson(
                            String.valueOf(counter++),
                            subj,
                            dateLabel,
                            "",
                            details.toString(),
                            val,
                            "ОЦЕНКА ЗА РАБОТУ:"
                    ));
                }

                runOnUiThread(() -> {
                    if (currentTab != TAB_SUMMARY) return;
                    setLoading(false);
                    adapter.setItems(summaryCards);
                    tvEmptyState.setText("За последние 2 недели новых оценок нет");
                    tvEmptyState.setVisibility(summaryCards.isEmpty() ? View.VISIBLE : View.GONE);
                });

            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    // ВКЛАДКА 1: Дневник
    private void loadScheduleDay() {
        String dateTitle = displayDateFormat.format(currentDay.getTime());
        tvSelectedDate.setText(dateTitle.substring(0, 1).toUpperCase() + dateTitle.substring(1));

        String dateStr = apiDateFormat.format(currentDay.getTime());
        String from = dateStr + " 00:00:00";
        String to = dateStr + " 23:59:59";
        String eduId = educationIds.isEmpty() ? "504817" : educationIds.get(0);

        Calendar prevCal = (Calendar) currentDay.clone();
        prevCal.add(Calendar.DAY_OF_YEAR, -7);
        String prevDateStr = apiDateFormat.format(prevCal.getTime());
        String prevFrom = prevDateStr + " 00:00:00";
        String prevTo = prevDateStr + " 23:59:59";

        setLoading(true);

        new Thread(() -> {
            List<Lesson> resultLessons = new ArrayList<>();
            try {
                JSONArray schedItems = null;
                try {
                    schedItems = api.schedule(educationIds, from, to).optJSONArray("items");
                } catch (Exception ignored) {}

                JSONArray lessonItems = null;
                try {
                    lessonItems = api.homework(educationIds, from, to).optJSONArray("items");
                } catch (Exception ignored) {}

                JSONArray prevSchedItems = null;
                try {
                    prevSchedItems = api.schedule(educationIds, prevFrom, prevTo).optJSONArray("items");
                } catch (Exception ignored) {}

                JSONArray prevLessonItems = null;
                try {
                    prevLessonItems = api.homework(educationIds, prevFrom, prevTo).optJSONArray("items");
                } catch (Exception ignored) {}

                Map<String, List<JSONObject>> realMarksBySubject = new HashMap<>();
                Map<String, List<JSONObject>> attendanceBySubject = new HashMap<>();

                try {
                    JSONArray marks = api.allEstimates(eduId, dateStr, dateStr);
                    for (int i = 0; i < marks.length(); i++) {
                        JSONObject m = marks.getJSONObject(i);
                        String subj = cleanStr(m, "subject_name");
                        String val = cleanStr(m, "estimate_value_name");
                        if (subj.isEmpty() || val.isEmpty()) continue;

                        if (isAttendanceMark(m)) {
                            if (!attendanceBySubject.containsKey(subj)) {
                                attendanceBySubject.put(subj, new ArrayList<>());
                            }
                            attendanceBySubject.get(subj).add(m);
                        } else {
                            if (!realMarksBySubject.containsKey(subj)) {
                                realMarksBySubject.put(subj, new ArrayList<>());
                            }
                            realMarksBySubject.get(subj).add(m);
                        }
                    }
                } catch (Exception ignored) {}

                Map<String, JSONObject> conductedMap = new LinkedHashMap<>();
                if (lessonItems != null) {
                    for (int i = 0; i < lessonItems.length(); i++) {
                        JSONObject obj = lessonItems.getJSONObject(i);
                        String num = String.valueOf(obj.optInt("number", i + 1));
                        String subj = cleanStr(obj, "subject_name");
                        if (subj.isEmpty()) subj = "Урок";
                        conductedMap.put(num + "_" + subj, obj);
                    }
                }

                Map<String, JSONObject> currentMerged = new LinkedHashMap<>();
                if (schedItems != null) {
                    for (int i = 0; i < schedItems.length(); i++) {
                        JSONObject sObj = schedItems.getJSONObject(i);
                        String num = String.valueOf(sObj.optInt("number", i + 1));
                        String subj = cleanStr(sObj, "subject_name");
                        if (subj.isEmpty()) subj = "Урок";
                        String key = num + "_" + subj;

                        if (conductedMap.containsKey(key)) {
                            currentMerged.put(key, conductedMap.get(key));
                            conductedMap.remove(key);
                        } else {
                            currentMerged.put(key, sObj);
                        }
                    }
                }
                currentMerged.putAll(conductedMap);

                Map<Integer, JSONObject> referenceByNumber = new HashMap<>();
                if (prevSchedItems != null) {
                    for (int i = 0; i < prevSchedItems.length(); i++) {
                        JSONObject obj = prevSchedItems.getJSONObject(i);
                        int num = obj.optInt("number", i + 1);
                        referenceByNumber.put(num, obj);
                    }
                }
                if (prevLessonItems != null) {
                    for (int i = 0; i < prevLessonItems.length(); i++) {
                        JSONObject obj = prevLessonItems.getJSONObject(i);
                        int num = obj.optInt("number", i + 1);
                        if (!referenceByNumber.containsKey(num)) {
                            referenceByNumber.put(num, obj);
                        }
                    }
                }

                Map<Integer, JSONObject> currentByNumber = new HashMap<>();
                int autoNum = 1;
                for (JSONObject obj : currentMerged.values()) {
                    int num = obj.optInt("number", autoNum++);
                    currentByNumber.put(num, obj);
                }

                for (Map.Entry<Integer, JSONObject> entry : referenceByNumber.entrySet()) {
                    int num = entry.getKey();
                    if (!currentByNumber.containsKey(num)) {
                        JSONObject refObj = entry.getValue();
                        JSONObject restored = new JSONObject();
                        restored.put("number", num);
                        restored.put("subject_name", cleanStr(refObj, "subject_name"));
                        restored.put("time_from", cleanStr(refObj, "time_from"));
                        restored.put("time_to", cleanStr(refObj, "time_to"));
                        restored.put("datetime_from", cleanStr(refObj, "datetime_from"));
                        restored.put("datetime_to", cleanStr(refObj, "datetime_to"));
                        restored.put("room_name", cleanStr(refObj, "room_name"));
                        restored.put("tasks", new JSONArray());
                        currentByNumber.put(num, restored);
                    }
                }

                for (JSONObject obj : currentByNumber.values()) {
                    if (currentTab != TAB_SCHEDULE) return;

                    String num = String.valueOf(obj.optInt("number", 1));
                    String subject = cleanStr(obj, "subject_name");
                    if (subject.isEmpty()) subject = "Урок";
                    String time = extractTimeRange(obj);
                    String room = cleanStr(obj, "room_name");

                    StringBuilder gradeBadge = new StringBuilder();
                    StringBuilder gradeDetails = new StringBuilder();

                    List<JSONObject> subjMarks = realMarksBySubject.get(subject);
                    if (subjMarks != null) {
                        for (JSONObject m : subjMarks) {
                            String val = cleanStr(m, "estimate_value_name");
                            String type = cleanStr(m, "estimate_type_name");
                            String comment = cleanStr(m, "estimate_comment");

                            if (gradeBadge.length() > 0) gradeBadge.append(", ");
                            gradeBadge.append(val);

                            if (gradeDetails.length() > 0) gradeDetails.append("<br>");
                            gradeDetails.append("<b>⭐ Оценка: ").append(val).append("</b>");
                            if (!type.isEmpty()) gradeDetails.append(" (").append(type).append(")");
                            if (!comment.isEmpty()) gradeDetails.append(" — <i>").append(comment).append("</i>");
                        }
                        realMarksBySubject.remove(subject);
                    }

                    StringBuilder attendanceDetails = new StringBuilder();
                    List<JSONObject> subjAtt = attendanceBySubject.get(subject);
                    if (subjAtt != null) {
                        for (JSONObject a : subjAtt) {
                            String val = cleanStr(a, "estimate_value_name");
                            String comment = cleanStr(a, "estimate_comment");
                            if (attendanceDetails.length() > 0) attendanceDetails.append("<br>");
                            attendanceDetails.append("⚠️ <b>Посещаемость:</b> ").append(val);
                            if (!comment.isEmpty()) attendanceDetails.append(" (<i>").append(comment).append("</i>)");
                        }
                        attendanceBySubject.remove(subject);
                    }

                    String hwHtml = formatTasksHtml(obj.optJSONArray("tasks"));

                    StringBuilder combinedBody = new StringBuilder();
                    if (attendanceDetails.length() > 0) {
                        combinedBody.append(attendanceDetails);
                    }
                    if (gradeDetails.length() > 0) {
                        if (combinedBody.length() > 0) combinedBody.append("<br><br>");
                        combinedBody.append(gradeDetails);
                    }
                    if (!hwHtml.isEmpty()) {
                        if (combinedBody.length() > 0) combinedBody.append("<br><br>");
                        combinedBody.append(hwHtml);
                    }

                    String boxTitle = "ДОМАШНЕЕ ЗАДАНИЕ:";
                    if (hwHtml.isEmpty()) {
                        if (gradeDetails.length() > 0) boxTitle = "ОЦЕНКА ЗА УРОК:";
                        else if (attendanceDetails.length() > 0) boxTitle = "ПОСЕЩАЕМОСТЬ:";
                    }

                    resultLessons.add(new Lesson(num, subject, time, room, combinedBody.toString(), gradeBadge.toString(), boxTitle));
                }

                Collections.sort(resultLessons, new Comparator<Lesson>() {
                    @Override
                    public int compare(Lesson a, Lesson b) {
                        try {
                            return Integer.compare(Integer.parseInt(a.number), Integer.parseInt(b.number));
                        } catch (Exception e) {
                            return a.number.compareTo(b.number);
                        }
                    }
                });

                runOnUiThread(() -> {
                    if (currentTab != TAB_SCHEDULE) return;
                    setLoading(false);
                    adapter.setItems(resultLessons);
                    tvEmptyState.setText("В этот день уроков нет");
                    tvEmptyState.setVisibility(resultLessons.isEmpty() ? View.VISIBLE : View.GONE);
                });

            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    // ВКЛАДКА 2А: Д/З -> Актуальное
    private void loadActualHomework() {
        Calendar calFrom = Calendar.getInstance();
        calFrom.add(Calendar.DAY_OF_YEAR, -14);
        String from = apiDateFormat.format(calFrom.getTime()) + " 00:00:00";

        Calendar calTo = Calendar.getInstance();
        calTo.add(Calendar.DAY_OF_YEAR, 14);
        String to = apiDateFormat.format(calTo.getTime()) + " 23:59:59";

        setLoading(true);

        new Thread(() -> {
            List<Lesson> actualList = new ArrayList<>();
            try {
                JSONObject response = api.homework(educationIds, from, to);
                JSONArray items = response.optJSONArray("items");

                if (items != null) {
                    Map<String, JSONObject> latestBySubject = new HashMap<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject lesson = items.getJSONObject(i);
                        JSONArray tasks = lesson.optJSONArray("tasks");
                        if (!hasRealTasks(tasks)) continue;

                        String subject = cleanStr(lesson, "subject_name");
                        if (subject.isEmpty()) subject = "Разное";

                        long currentLessonTime = parseLessonTimestamp(lesson);

                        if (!latestBySubject.containsKey(subject)) {
                            latestBySubject.put(subject, lesson);
                        } else {
                            JSONObject existingLesson = latestBySubject.get(subject);
                            long existingTime = parseLessonTimestamp(existingLesson);
                            if (currentLessonTime > existingTime) {
                                latestBySubject.put(subject, lesson);
                            }
                        }
                    }

                    List<String> sortedSubjects = new ArrayList<>(latestBySubject.keySet());
                    Collections.sort(sortedSubjects);

                    int counter = 1;
                    for (String subject : sortedSubjects) {
                        JSONObject lesson = latestBySubject.get(subject);
                        String dtFrom = cleanStr(lesson, "datetime_from");
                        String dateStr = dtFrom.contains(" ") ? dtFrom.split(" ")[0] : dtFrom;

                        String hwHtml = formatTasksHtml(lesson.optJSONArray("tasks"));
                        String timeText = dateStr.isEmpty() ? "" : "Задано на уроке " + dateStr;

                        actualList.add(new Lesson(
                                String.valueOf(counter++),
                                subject,
                                timeText,
                                "",
                                hwHtml,
                                "",
                                "АКТУАЛЬНОЕ ЗАДАНИЕ:"
                        ));
                    }
                }

                runOnUiThread(() -> {
                    if (currentTab != TAB_HW || currentHwSub != HW_SUB_ACTUAL) return;
                    setLoading(false);
                    adapter.setItems(actualList);
                    tvEmptyState.setText("Актуальных домашних заданий нет");
                    tvEmptyState.setVisibility(actualList.isEmpty() ? View.VISIBLE : View.GONE);
                });

            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    // ВКЛАДКА 2Б: Д/З -> На завтра
    private void loadTomorrowHomework() {
        Calendar calTom = Calendar.getInstance();
        int dayOfWeek = calTom.get(Calendar.DAY_OF_WEEK);

        if (dayOfWeek == Calendar.FRIDAY) {
            calTom.add(Calendar.DAY_OF_YEAR, 3);
        } else if (dayOfWeek == Calendar.SATURDAY) {
            calTom.add(Calendar.DAY_OF_YEAR, 2);
        } else if (dayOfWeek == Calendar.SUNDAY) {
            calTom.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            calTom.add(Calendar.DAY_OF_YEAR, 1);
        }

        String tomDateStr = apiDateFormat.format(calTom.getTime());
        String tomFrom = tomDateStr + " 00:00:00";
        String tomTo = tomDateStr + " 23:59:59";
        String tomDisplay = displayDateFormat.format(calTom.getTime());

        Calendar calHwFrom = Calendar.getInstance();
        calHwFrom.add(Calendar.DAY_OF_YEAR, -14);
        String hwFrom = apiDateFormat.format(calHwFrom.getTime()) + " 00:00:00";

        setLoading(true);

        new Thread(() -> {
            List<Lesson> tomorrowList = new ArrayList<>();
            try {
                JSONArray tomSchedItems = null;
                try {
                    tomSchedItems = api.schedule(educationIds, tomFrom, tomTo).optJSONArray("items");
                } catch (Exception ignored) {}

                JSONObject hwResp = api.homework(educationIds, hwFrom, tomTo);
                JSONArray hwItems = hwResp.optJSONArray("items");

                Map<String, JSONObject> latestHwBySubject = new HashMap<>();
                if (hwItems != null) {
                    for (int i = 0; i < hwItems.length(); i++) {
                        JSONObject lesson = hwItems.getJSONObject(i);
                        JSONArray tasks = lesson.optJSONArray("tasks");
                        if (!hasRealTasks(tasks)) continue;

                        String subject = cleanStr(lesson, "subject_name");
                        if (subject.isEmpty()) continue;

                        long currentLessonTime = parseLessonTimestamp(lesson);

                        if (!latestHwBySubject.containsKey(subject)) {
                            latestHwBySubject.put(subject, lesson);
                        } else {
                            long existingTime = parseLessonTimestamp(latestHwBySubject.get(subject));
                            if (currentLessonTime > existingTime) {
                                latestHwBySubject.put(subject, lesson);
                            }
                        }
                    }
                }

                if (tomSchedItems != null) {
                    for (int i = 0; i < tomSchedItems.length(); i++) {
                        JSONObject sObj = tomSchedItems.getJSONObject(i);
                        String subject = cleanStr(sObj, "subject_name");
                        if (subject.isEmpty()) continue;

                        if (latestHwBySubject.containsKey(subject)) {
                            JSONObject hwLesson = latestHwBySubject.get(subject);
                            String num = String.valueOf(sObj.optInt("number", i + 1));
                            String time = extractTimeRange(sObj);
                            String room = cleanStr(sObj, "room_name");

                            String hwHtml = formatTasksHtml(hwLesson.optJSONArray("tasks"));

                            String boxTitle = (dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
                                    ? "Д/З НА ПОНЕДЕЛЬНИК:"
                                    : "Д/З НА ЗАВТРА:";

                            tomorrowList.add(new Lesson(
                                    num,
                                    subject,
                                    time.isEmpty() ? "Урок" : time,
                                    room,
                                    hwHtml,
                                    "",
                                    boxTitle
                            ));
                        }
                    }
                }

                Collections.sort(tomorrowList, new Comparator<Lesson>() {
                    @Override
                    public int compare(Lesson a, Lesson b) {
                        try {
                            return Integer.compare(Integer.parseInt(a.number), Integer.parseInt(b.number));
                        } catch (Exception e) {
                            return a.number.compareTo(b.number);
                        }
                    }
                });

                runOnUiThread(() -> {
                    if (currentTab != TAB_HW || currentHwSub != HW_SUB_TOMORROW) return;
                    setLoading(false);
                    adapter.setItems(tomorrowList);
                    tvEmptyState.setText("На " + tomDisplay + " заданий нет 🎉");
                    tvEmptyState.setVisibility(tomorrowList.isEmpty() ? View.VISIBLE : View.GONE);
                });

            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    // ВКЛАДКА 3: Оценки
    private void loadAllMarks() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        if (now.get(Calendar.MONTH) < Calendar.AUGUST) {
            year -= 1;
        }
        String dateFrom = "01.09." + year;

        Calendar future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_YEAR, 14);
        String dateTo = apiDateFormat.format(future.getTime());

        String eduId = educationIds.isEmpty() ? "504817" : educationIds.get(0);

        setLoading(true);

        new Thread(() -> {
            List<Lesson> markCards = new ArrayList<>();
            try {
                JSONArray items = api.allEstimates(eduId, dateFrom, dateTo);
                Map<String, List<JSONObject>> bySubject = new HashMap<>();

                for (int i = 0; i < items.length(); i++) {
                    JSONObject m = items.getJSONObject(i);
                    if (isAttendanceMark(m)) continue;

                    String subj = cleanStr(m, "subject_name");
                    String val = cleanStr(m, "estimate_value_name");
                    if (subj.isEmpty() || val.isEmpty()) continue;

                    if (!bySubject.containsKey(subj)) {
                        bySubject.put(subj, new ArrayList<>());
                    }
                    bySubject.get(subj).add(m);
                }

                List<String> subjects = new ArrayList<>(bySubject.keySet());
                Collections.sort(subjects);

                int idx = 1;
                for (String subj : subjects) {
                    List<JSONObject> list = bySubject.get(subj);
                    int sum = 0;
                    int count = 0;
                    StringBuilder gradesLine = new StringBuilder();

                    for (JSONObject m : list) {
                        String val = cleanStr(m, "estimate_value_name");
                        String date = cleanStr(m, "date");
                        if (date.length() >= 5) date = date.substring(0, 5);
                        String type = cleanStr(m, "estimate_type_name");
                        String comment = cleanStr(m, "estimate_comment");

                        try {
                            int numVal = Integer.parseInt(val.trim());
                            if (numVal >= 1 && numVal <= 5) {
                                sum += numVal;
                                count++;
                            }
                        } catch (Exception ignored) {}

                        if (gradesLine.length() > 0) gradesLine.append("<br>");
                        gradesLine.append("• <b>").append(val).append("</b>");
                        if (!date.isEmpty()) gradesLine.append(" (").append(date);
                        if (!type.isEmpty()) gradesLine.append(" — ").append(type);
                        if (!comment.isEmpty()) gradesLine.append(" [<i>").append(comment).append("</i>]");
                        if (!date.isEmpty()) gradesLine.append(")");
                    }

                    String avgBadge = count > 0
                            ? String.format(Locale.US, "★ %.2f", (double) sum / count)
                            : "Зачет";

                    String countInfo = "Оценок: " + count;
                    markCards.add(new Lesson(
                            String.valueOf(idx++),
                            subj,
                            countInfo,
                            "",
                            gradesLine.toString(),
                            avgBadge,
                            "ИСТОРИЯ ОЦЕНОК:"
                    ));
                }

                runOnUiThread(() -> {
                    if (currentTab != TAB_MARKS) return;
                    setLoading(false);
                    adapter.setItems(markCards);
                    tvEmptyState.setText("Оценок за учебный год пока нет");
                    tvEmptyState.setVisibility(markCards.isEmpty() ? View.VISIBLE : View.GONE);
                });

            } catch (Exception e) {
                handleError(e);
            }
        }).start();
    }

    private static boolean hasRealTasks(JSONArray tasks) {
        if (tasks == null || tasks.length() == 0) return false;
        for (int t = 0; t < tasks.length(); t++) {
            JSONObject task = tasks.optJSONObject(t);
            if (task == null) continue;
            String name = cleanStr(task, "task_name");
            if (!name.isEmpty()) return true;
            JSONArray files = task.optJSONArray("files");
            if (files != null && files.length() > 0) return true;
        }
        return false;
    }

    private static long parseLessonTimestamp(JSONObject obj) {
        if (obj == null) return 0;
        String dt = cleanStr(obj, "datetime_from");
        if (dt.isEmpty()) dt = cleanStr(obj, "date");
        if (dt.isEmpty()) dt = cleanStr(obj, "date_from");
        if (dt.isEmpty()) return 0;

        try {
            if (dt.contains(".")) {
                SimpleDateFormat sdf = new SimpleDateFormat(dt.contains(" ") ? "dd.MM.yyyy HH:mm:ss" : "dd.MM.yyyy", Locale.US);
                Date d = sdf.parse(dt);
                return d != null ? d.getTime() : 0;
            } else if (dt.contains("-")) {
                SimpleDateFormat sdf = new SimpleDateFormat(dt.contains(" ") ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd", Locale.US);
                Date d = sdf.parse(dt);
                return d != null ? d.getTime() : 0;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static boolean isAttendanceMark(JSONObject m) {
        String val = cleanStr(m, "estimate_value_name").toLowerCase(Locale.ROOT);
        String type = cleanStr(m, "estimate_type_name").toLowerCase(Locale.ROOT);

        if (type.contains("посещаем") || type.contains("пропуск") || type.contains("отсутств") || type.contains("неявк")) {
            return true;
        }
        if (val.equals("н") || val.equals("б") || val.equals("о") || val.equals("у")) {
            return true;
        }
        if (val.contains("уважительн") || val.contains("болезн") || val.contains("пропуск") || val.contains("неявк") || val.contains("отсутств")) {
            return true;
        }
        return false;
    }

    private static String cleanStr(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) return "";
        String s = obj.optString(key, "").trim();
        if ("null".equalsIgnoreCase(s)) return "";
        return s;
    }

    private String extractTimeRange(JSONObject obj) {
        String from = cleanStr(obj, "time_from");
        String to = cleanStr(obj, "time_to");

        if (from.isEmpty()) {
            String dtFrom = cleanStr(obj, "datetime_from");
            if (dtFrom.contains(" ")) from = dtFrom.split(" ")[1];
        }
        if (to.isEmpty()) {
            String dtTo = cleanStr(obj, "datetime_to");
            if (dtTo.contains(" ")) to = dtTo.split(" ")[1];
        }

        if (from.length() >= 5) from = from.substring(0, 5);
        if (to.length() >= 5) to = to.substring(0, 5);

        if (!from.isEmpty() && !to.isEmpty()) return from + " — " + to;
        return from;
    }

    private String formatTasksHtml(JSONArray tasks) {
        if (tasks == null || tasks.length() == 0) return "";
        StringBuilder sb = new StringBuilder();

        for (int t = 0; t < tasks.length(); t++) {
            JSONObject task = tasks.optJSONObject(t);
            if (task == null) continue;

            String taskName = cleanStr(task, "task_name");
            if (!taskName.isEmpty()) {
                if (sb.length() > 0) sb.append("<br>");
                sb.append("📌 <b>ДЗ:</b> ").append(taskName);
            }

            JSONArray files = task.optJSONArray("files");
            if (files != null) {
                for (int f = 0; f < files.length(); f++) {
                    JSONObject file = files.optJSONObject(f);
                    if (file == null) continue;

                    String fName = cleanStr(file, "file_name");
                    if (fName.isEmpty()) fName = cleanStr(file, "name");
                    if (fName.isEmpty()) fName = "Файл";

                    String uuid = cleanStr(file, "uuid");
                    String fileUrl = "";

                    if (!uuid.isEmpty()) {
                        fileUrl = "https://dnevnik2.petersburgedu.ru/api/filekit/file/download?p_uuid=" + uuid;
                    } else {
                        String raw = cleanStr(file, "url");
                        if (raw.isEmpty()) raw = cleanStr(file, "path");
                        if (raw.isEmpty()) raw = cleanStr(file, "link");
                        if (!raw.isEmpty()) {
                            fileUrl = raw.startsWith("http") ? raw : "https://dnevnik2.petersburgedu.ru" + raw;
                        }
                    }

                    if (sb.length() > 0) sb.append("<br>");
                    if (!fileUrl.isEmpty()) {
                        sb.append("&nbsp;&nbsp;📎 <b>Файл:</b> <a href=\"").append(fileUrl).append("\">").append(fName).append("</a>");
                    } else {
                        sb.append("&nbsp;&nbsp;📎 <b>Файл:</b> ").append(fName);
                    }
                }
            }
        }
        return sb.toString();
    }

    // Обработка 401 с защитой от пинг-понга
    private void handleError(Exception e) {
        runOnUiThread(() -> {
            setLoading(false);
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                // АНТИ-ПЕТЛЯ: если токен получен меньше 15 секунд назад, НЕ прыгаем на логин
                long tokenTime = prefs.getLong("token_time", 0);
                if (System.currentTimeMillis() - tokenTime < 15000) {
                    Toast.makeText(this, "Сервер обновляет данные. Нажмите '↻'", Toast.LENGTH_SHORT).show();
                    return;
                }
                redirectToLogin();
                return;
            }
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) tvEmptyState.setVisibility(View.GONE);
    }
}