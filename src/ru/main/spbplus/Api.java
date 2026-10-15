package ru.main.spbplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Api {
    public static final String BASE = "https://dnevnik2.petersburgedu.ru/";

    private static final String USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private String token;

    public static class StudentInfo {
        public final String fio;
        public final String school;

        public StudentInfo(String fio, String school) {
            this.fio = fio;
            this.school = school;
        }
    }

    static {
        // 1. Принудительный IPv4: исправляет бесконечные зависания на Wi-Fi
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");

        // 2. Доверие к сертификатам Минцифры
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception ignored) {}
    }

    public Api(String token) {
        this.token = token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEsiaLoginUrl() throws Exception {
        JSONObject d = request("GET", "api/journal/esa/login-url", null);
        if (d.has("url")) return d.getString("url");
        if (d.has("data")) {
            Object data = d.get("data");
            if (data instanceof JSONObject) return ((JSONObject) data).getString("url");
            if (data instanceof String) return (String) data;
        }
        throw new Exception("Не найден URL в ответе: " + d);
    }

    public StudentInfo studentInfo() {
        try {
            JSONObject d = request("GET", "api/journal/claim/list?p_list_mode=owner_only", null);
            JSONArray items = d.optJSONArray("items");
            if (items != null && items.length() > 0) {
                JSONObject first = items.getJSONObject(0);
                String fio = first.optString("child_fio", "").trim();
                String school = first.optString("institution_name", "").trim();
                return new StudentInfo(fio, school);
            }
        } catch (Exception ignored) {}
        return new StudentInfo("", "");
    }

    public JSONObject currentUser() throws Exception {
        return request("GET", "api/journal/user/get-current-user", null);
    }

    public JSONObject childList() throws Exception {
        return request("GET", "api/journal/person/related-child-list?p_page=1", null);
    }

    public List<String> educationIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray items = childList().optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONArray edu = items.getJSONObject(i).optJSONArray("educations");
                    if (edu == null) continue;
                    for (int j = 0; j < edu.length(); j++) {
                        ids.add(String.valueOf(edu.getJSONObject(j).getInt("education_id")));
                    }
                }
            }
        } catch (Exception ignored) {}

        if (ids.isEmpty()) {
            ids.add("504817");
        }
        return ids;
    }

    public JSONObject homework(List<String> eduIds, String from, String to) throws Exception {
        StringBuilder q = new StringBuilder("api/journal/lesson/list-by-education?p_page=1&p_limit=100");
        for (String id : eduIds) {
            q.append("&p_educations[]=").append(enc(id));
        }
        q.append("&p_datetime_from=").append(enc(from));
        q.append("&p_datetime_to=").append(enc(to));
        return request("GET", q.toString(), null);
    }

    public JSONObject schedule(List<String> eduIds, String from, String to) throws Exception {
        StringBuilder q = new StringBuilder("api/journal/schedule/list-by-education?p_page=1");
        for (String id : eduIds) {
            q.append("&p_educations[]=").append(enc(id));
        }
        q.append("&p_datetime_from=").append(enc(from));
        q.append("&p_datetime_to=").append(enc(to));
        return request("GET", q.toString(), null);
    }

    public JSONObject estimatesPage(String eduId, String dateFrom, String dateTo, int page) throws Exception {
        String q = "api/journal/estimate/table?p_educations[]=" + enc(eduId)
                + "&p_date_from=" + enc(dateFrom)
                + "&p_date_to=" + enc(dateTo)
                + "&p_limit=100&p_page=" + page;
        return request("GET", q, null);
    }

    public JSONArray allEstimates(String eduId, String dateFrom, String dateTo) throws Exception {
        JSONArray all = new JSONArray();
        int page = 1;
        while (page <= 10) {
            JSONObject res = estimatesPage(eduId, dateFrom, dateTo, page);
            JSONArray items = res.optJSONArray("items");
            if (items == null || items.length() == 0) break;
            for (int i = 0; i < items.length(); i++) {
                all.put(items.getJSONObject(i));
            }
            if (items.length() < 100) break;
            page++;
        }
        return all;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private JSONObject request(String method, String path, String jsonBody) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000); // 10 сек тайм-аут
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        c.setRequestProperty("Referer", "https://dnevnik2.petersburgedu.ru/schedule");

        if (token != null && !token.isEmpty()) {
            c.setRequestProperty("X-JWT-Token", token);
            c.setRequestProperty("Cookie", "X-JWT-Token=" + token);
        }

        if (jsonBody != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = is == null ? "" : readAll(is);
        c.disconnect();

        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (Exception e) {
            throw new Exception("HTTP " + code + ": " + (text.length() > 200 ? text.substring(0, 200) : text));
        }

        if (code >= 400) {
            throw new Exception("HTTP " + code + ": " + root);
        }

        JSONObject data = root.optJSONObject("data");
        return data != null ? data : root;
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8);
    }
}