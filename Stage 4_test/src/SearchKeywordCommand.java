import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Stage 3 & 4 主流程 (高容錯、擦邊關聯版)：
 * 支援任意關鍵字探索，自動拆解字元進行擦邊計分。
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
            boManager.clear();

            // 實際送給 API 的搜尋字串 (例如："小熊 電影作品 主演")
            String searchQuery = queryProcessor.buildSearchQuery(rawQuery, keywords);
            log.accept("【初次搜尋】送出搜尋：「" + searchQuery + "」 (來源：" + api.describe() + ")");
            
            // ================== 建立計分字典 (支援擦邊拆字) ==================
            Map<String, KeywordCategory> initialCategories = new LinkedHashMap<>();
            dictionary.entries().forEach(initialCategories::put);
            for (Keyword k : keywords) initialCategories.putIfAbsent(k.getName(), k.getCategory());
            
            // 【關鍵修改 1：自動拆字擦邊加分】將使用者的中文查詢拆成單字，加入計分字典
            String cleanQuery = rawQuery.replaceAll("[^\\p{L}\\p{Nd}]", ""); 
            for (int i = 0; i < cleanQuery.length(); i++) {
                String singleChar = String.valueOf(cleanQuery.charAt(i));
                // 只針對中文字拆解 (避免英文字母 a, e 等瘋狂洗分)
                if (singleChar.matches("[\\u4e00-\\u9fa5]")) {
                    initialCategories.putIfAbsent(singleChar, KeywordCategory.EXTENDED);
                    // 加入 keywords 列表，讓終端機能印出命中次數
                    if (keywords.stream().noneMatch(k -> k.getName().equals(singleChar))) {
                        keywords.add(new Keyword(singleChar, KeywordCategory.EXTENDED, 0));
                    }
                }
            }

            TermMatcher initialMatcher = new TermMatcher(initialCategories.keySet());

            // 執行第一次搜尋並取得候選網頁
            List<Tree> firstPassTrees = performSearchPass(searchQuery, initialMatcher, initialCategories, new HashSet<>());
            
            // 【關鍵修改 2：寬鬆的擦邊過濾】只要命中任何一個字元就保留
            for (Tree t : firstPassTrees) {
                if (isBorderlineRelated(t, rawQuery)) {
                    boManager.add(t);
                } else {
                    log.accept("淘汰完全無關網頁：連擦邊字都沒有 (" + t.getRoot().getUrl() + ")");
                }
            }
            
            List<Tree> firstPassRanked = ranker.rank(boManager.getBo());

            // ================== 第 2 階段：語意分析與擴展搜尋 ==================
            List<Tree> secondPassTrees = new ArrayList<>();
            
            if (!firstPassRanked.isEmpty()) {
                log.accept("【語意分析】開始從初步結果擷取延伸關鍵字...");
                List<String> extendedTerms = SemanticAnalyzer.extractExtendedKeywords(firstPassRanked, keywords, 2);

                if (!extendedTerms.isEmpty()) {
                    log.accept("【語意分析】發現延伸關鍵字：" + String.join(", ", extendedTerms) + "，啟動擴展搜尋...");
                    
                    List<Keyword> combinedKeywords = new ArrayList<>(keywords);
                    Map<String, KeywordCategory> extendedCategories = new LinkedHashMap<>(initialCategories);
                    
                    for (String term : extendedTerms) {
                        combinedKeywords.add(new Keyword(term, KeywordCategory.EXTENDED, 0));
                        extendedCategories.putIfAbsent(term, KeywordCategory.EXTENDED);
                    }
                    
                    TermMatcher extendedMatcher = new TermMatcher(extendedCategories.keySet());
                    String extendedQuery = searchQuery + " " + String.join(" ", extendedTerms);
                    log.accept("【擴展搜尋】送出搜尋：「" + extendedQuery + "」");
                    
                    Set<String> seenUrls = firstPassTrees.stream()
                            .map(t -> t.getRoot().getUrl()).collect(Collectors.toSet());
                            
                    secondPassTrees = performSearchPass(extendedQuery, extendedMatcher, extendedCategories, seenUrls);
                    
                    // 更新第一次網頁的分數
                    for(Tree t : firstPassTrees) {
                         for (Node n : t.toList()) {
                             n.buildKeywords(extendedMatcher, extendedCategories);
                         }
                         t.computeScore();
                    }
                    
                    keywords.clear();
                    keywords.addAll(combinedKeywords);
                } else {
                    log.accept("【語意分析】未發現足夠的延伸關鍵字，保持原結果。");
                }
            }
            
            // ================== 最終統整與排序 ==================
            boManager.clear();
            
            for (Tree t : firstPassTrees) {
                if (isBorderlineRelated(t, rawQuery)) boManager.add(t);
            }
            for (Tree t : secondPassTrees) {
                if (isBorderlineRelated(t, rawQuery)) boManager.add(t);
            }
            
            List<Tree> finalRanked = ranker.rank(boManager.getBo());
            List<Tree> filtered = ranker.filter(finalRanked, options.filter);
            
            result = new SearchResult(rawQuery, searchQuery, keywords, api.describe(), 
                    boManager.getBo().size(), ranker.topN(filtered, options.top), 
                    System.currentTimeMillis() - startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = e;
        } catch (Exception e) {
            error = e;
        }
    }

    /** 判斷網頁是否與原查詢有「擦邊」關係 */
    private boolean isBorderlineRelated(Tree tree, String rawQuery) {
        String q = rawQuery.replaceAll("[^\\p{L}\\p{Nd}]", "").toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true; // 如果全符號或表情貼，不強制過濾，交給分數決定

        for (Node n : tree.toList()) {
            String text = (n.getTitle() + n.getSnippet() + n.getBody()).toLowerCase(Locale.ROOT);
            if (text.contains(q)) return true; // 完整命中
            
            // 只要命中任何一個中文字，就算擦邊過關
            for (int i = 0; i < q.length(); i++) {
                char c = q.charAt(i);
                if (String.valueOf(c).matches("[\\u4e00-\\u9fa5]") && text.indexOf(c) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Tree> performSearchPass(String query, TermMatcher matcher, Map<String, KeywordCategory> categories, Set<String> seenUrls) throws Exception {
        List<Node> candidates = dedupe(api.search(query, options.limit));
        List<Tree> parsedTrees = new ArrayList<>();
        
        if(candidates.isEmpty()) return parsedTrees;
        
        List<Node> newCandidates = candidates.stream()
                .filter(n -> !seenUrls.contains(n.getUrl()))
                .collect(Collectors.toList());
                
        if(newCandidates.isEmpty()) return parsedTrees;
                
        log.accept("取得 " + newCandidates.size() + " 個新候選網頁，開始下載與計分...");
        
        ExecutorService sitePool = Executors.newFixedThreadPool(Math.min(8, Math.max(1, newCandidates.size())));
        ExecutorService pagePool = Executors.newFixedThreadPool(16);
        
        try {
            List<Future<Tree>> futures = new ArrayList<>();
            for (Node root : newCandidates) {
                futures.add(sitePool.submit(() -> buildTree(root, matcher, categories, pagePool)));
            }
            
            for (int i = 0; i < futures.size(); i++) {
                try {
                    Tree tree = futures.get(i).get(options.siteTimeoutSeconds, TimeUnit.SECONDS);
                    parsedTrees.add(tree);
                } catch (TimeoutException | ExecutionException e) {
                    futures.get(i).cancel(true);
                    Tree fallback = fallbackTree(newCandidates.get(i), matcher, categories);
                    parsedTrees.add(fallback);
                }
            }
        } finally {
            sitePool.shutdownNow();
            pagePool.shutdownNow();
        }
        return parsedTrees;
    }

    private Tree buildTree(Node root, TermMatcher matcher, Map<String, KeywordCategory> categories, ExecutorService pagePool) {
        Tree tree = new Tree(root, urlFilter);
        root.fetch(handler, urlFilter);
        
        List<Node> level = List.of(root);
        for (int d = 1; d <= options.depth && !level.isEmpty(); d++) {
            int maxKids = d == 1 ? options.maxChildren : Math.max(1, options.maxChildren / 2);
            List<Node> next = new ArrayList<>();
            
            for (Node parent : level) {
                next.addAll(parent.buildChildren(tree, urlFilter, maxKids, matcher));
            }
            
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

    private Tree fallbackTree(Node original, TermMatcher matcher, Map<String, KeywordCategory> categories) {
        Node root = new Node(original.getTitle(), original.getUrl(), original.getSnippet());
        root.setOriginalRank(original.getOriginalRank());
        Tree tree = new Tree(root, urlFilter);
        root.buildKeywords(matcher, categories);
        tree.computeScore();
        return tree;
    }

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