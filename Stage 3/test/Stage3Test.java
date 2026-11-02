import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 不需要網路與 API 金鑰的測試：在本機開一個小網站，用 seeds 模式跑完整流程。
 * 執行：./run.sh test
 */
public class Stage3Test {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testQueryProcessor();
        testTermMatcher();
        testPipeline();
        System.out.println(failures == 0 ? "\n全部測試通過" : "\n失敗 " + failures + " 項");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void testQueryProcessor() {
        QueryProcessor qp = new QueryProcessor(new KeywordDictionary());
        check("有狗的動畫電影", names(qp.parse("有狗的動畫電影")), List.of("狗", "動畫", "電影"));
        check("全形與引號", names(qp.parse("「玩具總動員」　ＩＭＡＸ")), List.of("玩具總動員", "IMAX"));
        check("狗和貓", names(qp.parse("狗和貓")), List.of("狗", "貓"));
        check("郁方", names(qp.parse("郁方")), List.of("郁方"));
        List<Keyword> kws = qp.parse("狗");
        check("狗 的類別", kws.get(0).getCategory(), KeywordCategory.QUERY);
        check("沒有核心詞時補「電影」", qp.buildSearchQuery("狗", kws), "狗 電影");
        check("有核心詞時不補", qp.buildSearchQuery("狗 電影", qp.parse("狗 電影")), "狗 電影");
    }

    static void testTermMatcher() {
        TermMatcher m = new TermMatcher(List.of("電影", "電影院", "劇情", "劇情反轉", "3D"));
        Map<String, Integer> c = m.count("去電影院看電影，劇情反轉很好看，3D 版本，不是 3DS");
        check("最長匹配", c, Map.of("電影院", 1, "電影", 1, "劇情反轉", 1, "3D", 1));
    }

    static void testPipeline() throws Exception {
        Map<String, String> pages = new HashMap<>();
        pages.put("/movie", page("忠犬小八 電影介紹",
                "這是一部關於狗的電影，導演拍攝了很多狗的鏡頭。<a href=\"/movie/review\">影評</a>"
                        + "<a href=\"/movie/cast\">演員</a><a href=\"https://other.example/x\">外站</a>"
                        + "<a href=\"/movie\">回首頁</a>"));
        pages.put("/movie/review", page("影評", "影評：狗狗演技很好，推薦。"));
        pages.put("/movie/cast", page("演員", "主演名單"));
        pages.put("/pet", page("養狗須知", "狗 狗 狗 飼料與疫苗"));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = pages.get(ex.getRequestURI().getPath());
            int code = body == null ? 404 : 200;
            byte[] bytes = (body == null ? "not found" : body).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        Path seeds = Files.createTempFile("seeds", ".txt");
        Files.writeString(seeds, base + "/pet | 養狗須知 | 寵物\n" + base + "/movie | 忠犬小八 | 狗 電影\n"
                + base + "/movie#dup | 重複 | 重複\n");
        Path cfg = Files.createTempFile("cfg", ".properties");
        Files.writeString(cfg, "SEARCH_PROVIDER=SEEDS\nSEEDS_FILE=" + seeds.toString().replace("\\", "/") + "\n");

        try {
            Facade.getInstance().setSearchApiClient(SearchApiClient.fromConfig(new AppConfig(cfg)));
            SearchOptions opt = new SearchOptions();
            SearchResult r = ViewControl.getInstance().onSearch("狗", opt, null);

            check("重複網址只留一個", r.candidateCount(), 2);
            Tree first = r.ranked().get(0);
            check("電影網頁排第一", first.getRoot().getUrl(), base + "/movie");
            check("原排名是第 2", first.getRoot().getOriginalRank(), 2);
            check("只展開同網站、不重複的子頁", first.getRoot().getChildren().size(), 2);

            double expected = first.getRoot().getSum();
            for (Node c : first.getRoot().getChildren()) expected += c.getSum() * 0.5;
            check("網站總分 = 根頁 + Σ子頁×0.5", round(first.getScore()), round(expected));

            // 手算根頁分數：標題+摘要+正文中 狗×3(+5)、電影×3(+5)、導演×1(+5)、拍攝×1(+3)、影評×1(+1)
            check("根頁分數", round(first.getRoot().getSum()), 3 * 5 + 3 * 5 + 5 + 3 + 1.0);

            SearchOptions filtered = new SearchOptions();
            filtered.filter = "導演";
            check("篩選條件", ViewControl.getInstance().onSearch("狗", filtered, null).ranked().size(), 1);
        } finally {
            server.stop(0);
        }
    }

    static String page(String title, String body) {
        return "<html><head><meta charset=\"utf-8\"><title>" + title + "</title>"
                + "<script>var 電影 = 1;</script></head><body>" + body + "</body></html>";
    }

    static List<String> names(List<Keyword> ks) {
        List<String> out = new ArrayList<>();
        for (Keyword k : ks) out.add(k.getName());
        return out;
    }

    static double round(double d) { return Math.round(d * 100) / 100.0; }

    static void check(String name, Object actual, Object expected) {
        boolean ok = Objects.equals(actual, expected);
        if (!ok) failures++;
        System.out.println((ok ? "  ✓ " : "  ✗ ") + name + (ok ? "" : "  期望 " + expected + "，實際 " + actual));
    }
}
