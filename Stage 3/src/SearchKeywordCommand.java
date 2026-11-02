import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Stage 3 主流程：
 * 1. 呼叫 SearchApiClient 取得候選網頁
 * 2. 為每個候選網頁建立 Tree，下載 Root 與子連結（處理重複網址）
 * 3. 每個 Node 計算 calculateSum()，Tree 以後序走訪匯總（每層 × 0.5）
 * 4. 交給 Ranker 排序後回傳
 */
public class SearchKeywordCommand extends AbstractCommand {
    private final String rawQuery;
    private final List<Keyword> keywords;
    private final SearchOptions options;
    private final KeywordDictionary dictionary;
    private final QueryProcessor queryProcessor;
    private final SearchApiClient api;
    private final HtmlHandler handler;
    private final UrlFilter urlFilter;
    private final BoManager boManager;
    private final Ranker ranker;
    private final Consumer<String> log;

    private SearchResult result;
    private Exception error;

    public SearchKeywordCommand(String rawQuery, List<Keyword> keywords, SearchOptions options,
                                KeywordDictionary dictionary, QueryProcessor queryProcessor,
                                SearchApiClient api, BoManager boManager, Ranker ranker, Consumer<String> log) {
        this.rawQuery = rawQuery;
        this.keywords = keywords;
        this.options = options;
        this.dictionary = dictionary;
        this.queryProcessor = queryProcessor;
        this.api = api;
        this.handler = new HtmlHandler(options.timeoutSeconds);
        this.urlFilter = new UrlFilter(handler);
        this.boManager = boManager;
        this.ranker = ranker;
        this.log = log == null ? s -> { } : log;
    }

    @Override
    public void execute() {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 呼叫搜尋 API 取得候選網頁
            String searchQuery = queryProcessor.buildSearchQuery(rawQuery, keywords);
            log.accept("送出搜尋：「" + searchQuery + "」（來源：" + api.describe() + "）");
            List<Node> candidates = dedupe(api.search(searchQuery, options.limit));
            log.accept("取得 " + candidates.size() + " 個候選網頁，開始下載與計分…");

            // 計分用的詞：字典的所有加分詞 + 使用者輸入的查詢詞
            Map<String, KeywordCategory> categories = new LinkedHashMap<>();
            dictionary.entries().forEach(categories::put);
            for (Keyword k : keywords) categories.putIfAbsent(k.getName(), k.getCategory());
            TermMatcher matcher = new TermMatcher(categories.keySet());

            // 2~3. 平行建立每個網站的 Tree 並計分
            boManager.clear();
            ExecutorService sitePool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, candidates.size())));
            ExecutorService pagePool = Executors.newFixedThreadPool(16);
            try {
                List<Future<Tree>> futures = new ArrayList<>();
                for (Node root : candidates) {
                    futures.add(sitePool.submit(() -> buildTree(root, matcher, categories, pagePool)));
                }
                for (int i = 0; i < futures.size(); i++) {
                    Node root = candidates.get(i);
                    Tree tree;
                    try {
                        tree = futures.get(i).get(options.siteTimeoutSeconds, TimeUnit.SECONDS);
                    } catch (TimeoutException | ExecutionException e) {
                        futures.get(i).cancel(true);
                        tree = fallbackTree(root, matcher, categories);
                    }
                    boManager.add(tree);
                }
            } finally {
                sitePool.shutdownNow();
                pagePool.shutdownNow();
            }

            // 4. 排序、篩選、取前 N 名
            List<Tree> ranked = ranker.rank(boManager.getBo());
            List<Tree> filtered = ranker.filter(ranked, options.filter);
            result = new SearchResult(rawQuery, searchQuery, keywords, api.describe(), candidates.size(),
                    ranker.topN(filtered, options.top), System.currentTimeMillis() - startTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = e;
        } catch (Exception e) {
            error = e;
        }
    }

    private Tree buildTree(Node root, TermMatcher matcher, Map<String, KeywordCategory> categories,
                           ExecutorService pagePool) {
        Tree tree = new Tree(root, urlFilter);
        root.fetch(handler, urlFilter);

        // 逐層展開子連結；同一層的網頁平行下載
        List<Node> level = List.of(root);
        for (int d = 1; d <= options.depth && !level.isEmpty(); d++) {
            int maxKids = d == 1 ? options.maxChildren : Math.max(1, options.maxChildren / 2);
            List<Node> next = new ArrayList<>();
            for (Node parent : level) next.addAll(parent.buildChildren(tree, urlFilter, maxKids, matcher));
            List<CompletableFuture<Void>> jobs = new ArrayList<>();
            for (Node child : next) {
                jobs.add(CompletableFuture.runAsync(() -> child.fetch(handler, urlFilter), pagePool));
            }
            CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).join();
            level = next;
        }

        for (Node n : tree.toList()) n.buildKeywords(matcher, categories);
        tree.computeScore();
        return tree;
    }

    /** 網站處理逾時：只用搜尋 API 給的標題與摘要計分 */
    private Tree fallbackTree(Node original, TermMatcher matcher, Map<String, KeywordCategory> categories) {
        Node root = new Node(original.getTitle(), original.getUrl(), original.getSnippet());
        root.setOriginalRank(original.getOriginalRank());
        Tree tree = new Tree(root, urlFilter);
        root.buildKeywords(matcher, categories);
        tree.computeScore();
        return tree;
    }

    /** 去掉重複網址與不允許存取的網址 */
    private List<Node> dedupe(List<Node> nodes) {
        Set<String> seen = new HashSet<>();
        List<Node> out = new ArrayList<>();
        for (Node n : nodes) {
            String key = urlFilter.normalize(n.getUrl());
            if (key == null || !urlFilter.isAllowed(n.getUrl()) || !seen.add(key)) {
                log.accept("略過：" + n.getUrl());
                continue;
            }
            out.add(n);
        }
        return out;
    }

    public SearchResult getResult() { return result; }
    public Exception getError() { return error; }
}
