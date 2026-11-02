package smartranking.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 網頁抓取與內容處理工具
 */
public class HtmlHandler {

    /**
     * 抓取指定 URL 的 HTML 原始碼
     */
    public static String getHtml(String targetUrl) {
        StringBuilder content = new StringBuilder();
        try {
            URL url = URI.create(targetUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            // 爬取失敗時記錄日誌並安全略過
            System.err.println("[WARN] 無法存取網址: " + targetUrl + " (" + e.getMessage() + ")");
        }
        return content.toString();
    }

    /**
     * 去除 HTML 標籤、腳本與樣式，只保留純內文
     */
    public static String extractBodyText(String html) {
        if (html == null || html.isEmpty()) return "";
        // 移除 <script> 與 <style>
        String text = html.replaceAll("(?s)<script.*?</script>", " ")
                          .replaceAll("(?s)<style.*?</style>", " ");
        // 移除所有 HTML 標籤
        text = text.replaceAll("<[^>]*>", " ");
        // 壓縮空白字元
        return text.replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 計算特定關鍵字在字串中出現的次數
     */
    public static int countKeyword(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        String lowerKeyword = keyword.toLowerCase();
        while ((index = text.indexOf(lowerKeyword, index)) != -1) {
            count++;
            index += lowerKeyword.length();
        }
        return count;
    }

    /**
     * 提取頁面內的相對或絕對超連結（最多取得 maxLinks 筆）
     */
    public static List<String> extractSubLinks(String html, String baseDomain, int maxLinks) {
        List<String> links = new ArrayList<>();
        if (html == null || html.isEmpty()) return links;

        Pattern pattern = Pattern.compile("href=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find() && links.size() < maxLinks) {
            String link = matcher.group(1);
            // 僅保留同網域連結以避免跳出目標站點
            if (link.contains(baseDomain) && !links.contains(link)) {
                links.add(link);
            }
        }
        return links;
    }
}