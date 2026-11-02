import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** 連上網頁讀取 HTML，並解析標題、正文與子連結。 */
public class HtmlHandler {
    public static final String USER_AGENT =
            "Mozilla/5.0 (compatible; DSGroup9-MovieSearch/1.0; NCCU course project)";
    private static final int MAX_BYTES = 2_000_000;

    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern HREF = Pattern.compile("(?is)<a\\s[^>]*?href\\s*=\\s*[\"']([^\"'#][^\"']*)[\"'][^>]*>(.*?)</a>");
    private static final Pattern META_CHARSET =
            Pattern.compile("(?i)<meta[^>]+charset\\s*=\\s*[\"']?([\\w-]+)");
    private static final Pattern REMOVE_BLOCKS = Pattern.compile(
            "(?is)<!--.*?-->|<script[^>]*>.*?</script>|<style[^>]*>.*?</style>|<noscript[^>]*>.*?</noscript>|<svg[^>]*>.*?</svg>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]+>");
    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    /** 子連結：網址與連結文字 */
    public record Link(String url, String text) {}

    /** 下載結果：重新導向後的最終網址與 HTML */
    public record Page(String finalUrl, String html) {}

    private final HttpClient client;
    private final Duration timeout;

    public HtmlHandler(int timeoutSeconds) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Math.min(5, timeoutSeconds)))
                .build();
    }

    /** 從 URL 連上網路讀取該頁面的 HTML */
    public String getHTML(String url) throws IOException, InterruptedException {
        return fetch(url).html();
    }

    public Page fetch(String url) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = send(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5");
        String type = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.contains("html") && !type.contains("xml")) {
            res.body().close();
            throw new IOException("不是 HTML 網頁 (" + type + ")");
        }
        byte[] bytes = readLimited(res.body());
        return new Page(res.uri().toString(), decode(bytes, type));
    }

    /** 讀純文字（例如 robots.txt）；失敗或非 2xx 回傳 null */
    public String fetchText(String url) {
        try {
            HttpResponse<InputStream> res = send(url, "text/plain,*/*;q=0.5");
            return new String(readLimited(res.body()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private HttpResponse<InputStream> send(String url, String accept) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.6")
                .GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            res.body().close();
            throw new IOException("HTTP " + res.statusCode());
        }
        return res;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1 && out.size() < MAX_BYTES) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** 編碼判斷：Content-Type → <meta charset> → UTF-8（台灣有些網站仍用 Big5） */
    private static String decode(byte[] bytes, String contentType) {
        String charset = null;
        Matcher h = Pattern.compile("charset=([\\w-]+)").matcher(contentType);
        if (h.find()) charset = h.group(1);
        if (charset == null) {
            String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            if (m.find()) charset = m.group(1);
        }
        try {
            if (charset != null) return new String(bytes, Charset.forName(charset));
        } catch (Exception ignored) {
            // 不認得的編碼就用 UTF-8
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 解析頁面標題 */
    public String getPageName(String html) {
        Matcher m = TITLE.matcher(html);
        return m.find() ? clean(m.group(1)) : "";
    }

    /** 解析正文：去掉 script/style 與所有標籤，只留純文字 */
    public String getBody(String html) {
        String noBlocks = REMOVE_BLOCKS.matcher(html).replaceAll(" ");
        return clean(TAGS.matcher(noBlocks).replaceAll(" "));
    }

    /** 解析 <a href> 找出子連結（轉成絕對網址） */
    public List<String> getSubLink(String html, String baseUrl) {
        List<String> out = new ArrayList<>();
        for (Link l : getSubLinkWithText(html, baseUrl)) out.add(l.url());
        return out;
    }

    /** 同上，但保留連結文字，讓 Node 可以優先展開和關鍵字有關的子頁 */
    public List<Link> getSubLinkWithText(String html, String baseUrl) {
        Map<String, Link> links = new LinkedHashMap<>();
        URI base;
        try {
            base = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
        Matcher m = HREF.matcher(html);
        while (m.find()) {
            String href = decodeEntities(m.group(1).trim());
            if (href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("tel:")) continue;
            try {
                String s = base.resolve(href.replace(" ", "%20")).toString();
                if (s.startsWith("http://") || s.startsWith("https://")) links.putIfAbsent(s, new Link(s, clean(m.group(2))));
            } catch (IllegalArgumentException ignored) {
                // 格式錯誤的連結直接略過
            }
        }
        return new ArrayList<>(links.values());
    }

    /** 計算關鍵字在正文出現的次數（不分大小寫、不重疊） */
    public int countKeyword(String body, String keyword) {
        if (body == null || keyword == null || keyword.isEmpty()) return 0;
        String b = body.toLowerCase(Locale.ROOT), k = keyword.toLowerCase(Locale.ROOT);
        int count = 0, idx = 0;
        while ((idx = b.indexOf(k, idx)) != -1) {
            count++;
            idx += k.length();
        }
        return count;
    }

    /** 去掉殘留標籤、解碼 HTML entity、合併空白 */
    public static String clean(String s) {
        if (s == null) return "";
        return decodeEntities(TAGS.matcher(s).replaceAll(" ")).replaceAll("\\s+", " ").trim();
    }

    public static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        Matcher m = NUM_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            String rep;
            try {
                int cp = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
                rep = new String(Character.toChars(cp));
            } catch (IllegalArgumentException e) {
                rep = m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
