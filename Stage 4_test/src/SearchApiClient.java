import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * 透過符合使用條款的搜尋 API 動態取得候選網頁，不依賴固定網頁清單。
 * 支援 Google Custom Search JSON API、Brave Search API、SerpAPI；
 * 都沒有設定金鑰時，改讀 seeds.txt（離線測試用）。
 */
public class SearchApiClient {
    public enum Provider { GOOGLE, BRAVE, SERPAPI, SEEDS }

    private final Provider provider;
    private final String apiKey;
    private final String cx;
    private final String endpoint;
    private final Path seedsFile;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SearchApiClient(Provider provider, String apiKey, String cx, String endpoint, Path seedsFile) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.cx = cx;
        this.endpoint = endpoint;
        this.seedsFile = seedsFile;
    }

    /** 依設定自動選擇搜尋來源 */
    public static SearchApiClient fromConfig(AppConfig cfg) {
        String chosen = cfg.get("SEARCH_PROVIDER", "").toUpperCase(Locale.ROOT);
        Path seeds = Paths.get(cfg.get("SEEDS_FILE", "seeds.txt"));
        String gKey = cfg.get("GOOGLE_API_KEY"), gCx = cfg.get("GOOGLE_CSE_ID");
        String braveKey = cfg.get("BRAVE_API_KEY"), serpKey = cfg.get("SERPAPI_KEY");

        if (chosen.isEmpty()) {
            if (gKey != null && gCx != null) chosen = "GOOGLE";
            else if (braveKey != null) chosen = "BRAVE";
            else if (serpKey != null) chosen = "SERPAPI";
            else chosen = "SEEDS";
        }
        switch (chosen) {
            case "GOOGLE":
                require(gKey, "GOOGLE_API_KEY");
                require(gCx, "GOOGLE_CSE_ID");
                return new SearchApiClient(Provider.GOOGLE, gKey, gCx,
                        "https://www.googleapis.com/customsearch/v1", seeds);
            case "BRAVE":
                require(braveKey, "BRAVE_API_KEY");
                return new SearchApiClient(Provider.BRAVE, braveKey, null,
                        "https://api.search.brave.com/res/v1/web/search", seeds);
            case "SERPAPI":
                require(serpKey, "SERPAPI_KEY");
                return new SearchApiClient(Provider.SERPAPI, serpKey, null, "https://serpapi.com/search.json", seeds);
            case "SEEDS":
                return new SearchApiClient(Provider.SEEDS, null, null, null, seeds);
            default:
                throw new IllegalArgumentException("未知的 SEARCH_PROVIDER：" + chosen);
        }
    }

    private static void require(String v, String name) {
        if (v == null) throw new IllegalArgumentException("缺少設定 " + name + "（請設環境變數或寫在 config.properties）");
    }

    public Provider getProvider() { return provider; }

    public String describe() {
        switch (provider) {
            case GOOGLE: return "Google Custom Search API";
            case BRAVE: return "Brave Search API";
            case SERPAPI: return "SerpAPI (Google)";
            default: return "離線清單 " + seedsFile + "（未設定搜尋 API 金鑰）";
        }
    }

    /** 動態取得候選網頁；回傳的 Node 已帶有標題、網址、摘要與原始名次 */
    public List<Node> search(String query, int limit) throws IOException, InterruptedException {
        List<Node> nodes;
        switch (provider) {
            case GOOGLE: nodes = searchGoogle(query, limit); break;
            case BRAVE: nodes = searchBrave(query, limit); break;
            case SERPAPI: nodes = searchSerpApi(query, limit); break;
            default: nodes = readSeeds(limit);
        }
        for (int i = 0; i < nodes.size(); i++) nodes.get(i).setOriginalRank(i + 1);
        return nodes;
    }

    // Google 每次最多 10 筆，用 start 參數翻頁
    private List<Node> searchGoogle(String query, int limit) throws IOException, InterruptedException {
        List<Node> out = new ArrayList<>();
        int start = 1;
        while (out.size() < limit && start <= 91) {
            int num = Math.min(10, limit - out.size());
            String url = endpoint + "?key=" + enc(apiKey) + "&cx=" + enc(cx) + "&q=" + enc(query)
                    + "&num=" + num + "&start=" + start + "&hl=zh-TW&gl=tw";
            Map<String, Object> json = Json.obj(Json.parse(get(url, Map.of())));
            List<Object> items = Json.arr(json.get("items"));
            if (items.isEmpty()) break;
            for (Object o : items) {
                Map<String, Object> it = Json.obj(o);
                out.add(new Node(Json.str(it, "title"), Json.str(it, "link"), Json.str(it, "snippet")));
            }
            start += items.size();
        }
        return out;
    }

    private List<Node> searchBrave(String query, int limit) throws IOException, InterruptedException {
        String url = endpoint + "?q=" + enc(query) + "&count=" + Math.min(20, limit);
        Map<String, Object> json = Json.obj(Json.parse(get(url, Map.of("X-Subscription-Token", apiKey))));
        List<Node> out = new ArrayList<>();
        for (Object o : Json.arr(Json.obj(json.get("web")).get("results"))) {
            Map<String, Object> it = Json.obj(o);
            out.add(new Node(HtmlHandler.clean(Json.str(it, "title")), Json.str(it, "url"),
                    HtmlHandler.clean(Json.str(it, "description"))));
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    private List<Node> searchSerpApi(String query, int limit) throws IOException, InterruptedException {
        String url = endpoint + "?engine=google&q=" + enc(query) + "&num=" + limit
                + "&hl=zh-tw&gl=tw&api_key=" + enc(apiKey);
        Map<String, Object> json = Json.obj(Json.parse(get(url, Map.of())));
        List<Node> out = new ArrayList<>();
        for (Object o : Json.arr(json.get("organic_results"))) {
            Map<String, Object> it = Json.obj(o);
            out.add(new Node(Json.str(it, "title"), Json.str(it, "link"), Json.str(it, "snippet")));
        }
        return out.subList(0, Math.min(limit, out.size()));
    }

    /** seeds.txt 格式：每行「網址」或「網址 | 標題 | 摘要」，# 開頭為註解 */
    private List<Node> readSeeds(int limit) throws IOException {
        if (!Files.exists(seedsFile)) {
            throw new IOException("沒有設定搜尋 API 金鑰，也找不到 " + seedsFile.toAbsolutePath()
                    + "。請參考 README 設定 GOOGLE_API_KEY / BRAVE_API_KEY / SERPAPI_KEY。");
        }
        List<Node> out = new ArrayList<>();
        for (String line : Files.readAllLines(seedsFile, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s*\\|\\s*", 3);
            out.add(new Node(parts.length > 1 ? parts[1] : "", parts[0], parts.length > 2 ? parts[2] : ""));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private String get(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json");
        headers.forEach(b::header);
        HttpResponse<String> res = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            String body = res.body() == null ? "" : res.body();
            throw new IOException("搜尋 API 回傳 HTTP " + res.statusCode() + "："
                    + body.substring(0, Math.min(300, body.length())));
        }
        return res.body();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
