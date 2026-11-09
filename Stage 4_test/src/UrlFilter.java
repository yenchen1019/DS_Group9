import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 網址正規化、去除重複，並檢查是否允許存取（網域黑名單、檔案類型、robots.txt）。 */
public class UrlFilter {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "facebook.com", "instagram.com", "twitter.com", "x.com", "threads.net", "tiktok.com",
            "pinterest.com", "linkedin.com", "accounts.google.com",
            "duitang.com", "labs.giloo.ist", "epochtimes.com");//新增
    private static final Pattern BLOCKED_EXT = Pattern.compile(
            "(?i).*\\.(pdf|jpe?g|png|gif|webp|svg|ico|bmp|mp4|mp3|avi|mov|zip|rar|7z|exe|dmg|apk|"
                    + "docx?|xlsx?|pptx?|css|js|json|xml|rss|txt)$");
    private static final Pattern TRACKING_PARAM = Pattern.compile("(?i)^(utm_[a-z]+|fbclid|gclid|ref|ref_src)=.*");

    private final HtmlHandler handler;
    /** host → robots.txt 規則（快取，每個網站只讀一次） */
    private final Map<String, List<Rule>> robotsCache = new ConcurrentHashMap<>();

    private record Rule(Pattern pattern, int length, boolean allow) {}

    public UrlFilter(HtmlHandler handler) {
        this.handler = handler;
    }

    /** 網址正規化：小寫網域、去掉 #片段、追蹤參數與結尾斜線；格式錯誤回傳 null */
    public String normalize(String url) {
        if (url == null) return null;
        try {
            URI u = URI.create(url.trim().replace(" ", "%20"));
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost() == null ? null : u.getHost().toLowerCase(Locale.ROOT);
            if (host == null || !(scheme.equals("http") || scheme.equals("https"))) return null;
            int port = u.getPort();
            boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443);
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

            StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
            if (!defaultPort) sb.append(':').append(port);
            sb.append(path);
            if (u.getRawQuery() != null) {
                List<String> kept = new ArrayList<>();
                for (String p : u.getRawQuery().split("&")) {
                    if (!p.isEmpty() && !TRACKING_PARAM.matcher(p).matches()) kept.add(p);
                }
                if (!kept.isEmpty()) sb.append('?').append(String.join("&", kept));
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 基本檢查：http(s)、不在黑名單、不是圖片/檔案 */
    public boolean isAllowed(String url) {
        String n = normalize(url);
        if (n == null) return false;
        URI u = URI.create(n);
        String host = u.getHost();
        for (String b : BLOCKED_HOSTS) {
            if (host.equals(b) || host.endsWith("." + b)) return false;
        }
        String path = u.getPath() == null ? "" : u.getPath();
        if (BLOCKED_EXT.matcher(path).matches()) return false;
        String low = n.toLowerCase(Locale.ROOT);
        return !(low.contains("/login") || low.contains("/signin") || low.contains("/logout"));
    }

    /** 檢查 robots.txt 是否允許爬取這個網址 */
    public boolean canFetch(String url) {
        String n = normalize(url);
        if (n == null) return false;
        URI u = URI.create(n);
        String origin = u.getScheme() + "://" + u.getRawAuthority();
        List<Rule> rules = robotsCache.computeIfAbsent(origin, o -> parseRobots(handler.fetchText(o + "/robots.txt")));
        String path = (u.getRawPath() == null ? "/" : u.getRawPath())
                + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        Rule best = null;
        for (Rule r : rules) {
            if (r.pattern().matcher(path).lookingAt() && (best == null || r.length() > best.length()
                    || (r.length() == best.length() && r.allow()))) {
                best = r;
            }
        }
        return best == null || best.allow();
    }

    /** 只讀取 User-agent: * 那一組的 Allow / Disallow */
    private static List<Rule> parseRobots(String txt) {
        List<Rule> rules = new ArrayList<>();
        if (txt == null) return rules;
        boolean inStarGroup = false, lastWasAgent = false;
        for (String raw : txt.split("\\r?\\n")) {
            String line = raw.replaceAll("#.*", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                boolean star = value.equals("*");
                inStarGroup = lastWasAgent ? (inStarGroup || star) : star;
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!inStarGroup || value.isEmpty()) continue;
            if (key.equals("disallow") || key.equals("allow")) {
                rules.add(new Rule(toPattern(value), value.length(), key.equals("allow")));
            }
        }
        return rules;
    }

    private static Pattern toPattern(String rule) {
        boolean anchored = rule.endsWith("$");
        String body = anchored ? rule.substring(0, rule.length() - 1) : rule;
        // robots.txt 的 * 代表任意字元，其餘照字面比對
        List<String> parts = new ArrayList<>();
        for (String part : body.split("\\*", -1)) parts.add(Pattern.quote(part));
        StringBuilder regex = new StringBuilder(String.join(".*", parts));
        if (anchored) regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
