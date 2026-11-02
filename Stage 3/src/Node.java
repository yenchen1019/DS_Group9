import java.util.*;

/** 一個 Node 就是一個網頁。 */
public class Node {
    // 祖先與小孩
    private Node ancestor;
    private final List<Node> children = new ArrayList<>();

    // 網頁基本資訊（由搜尋 API 提供）
    private String title;
    private final String url;
    private final String snippet;
    private int originalRank;   // 在搜尋 API 結果中的原始名次（子頁為 0）

    // 正文與子連結（由 HtmlHandler 讀取）
    private String body = "";
    private List<HtmlHandler.Link> subLinks = new ArrayList<>();
    private String fetchError;

    // 分數；無法讀取的網頁只用 title + snippet 計分
    private List<Keyword> keywords = new ArrayList<>();
    private double sum;
    private boolean accessible;

    public Node(String title, String url, String snippet) {
        this.title = title == null ? "" : title;
        this.url = url;
        this.snippet = snippet == null ? "" : snippet;
    }

    /** 下載並解析本頁；失敗時記錄原因，之後只用 title + snippet 計分 */
    public void fetch(HtmlHandler handler, UrlFilter filter) {
        if (!filter.canFetch(url)) {
            fetchError = "robots.txt 不允許";
            return;
        }
        try {
            HtmlHandler.Page page = handler.fetch(url);
            String pageTitle = handler.getPageName(page.html());
            if (title.isEmpty()) title = pageTitle;
            body = handler.getBody(page.html());
            subLinks = handler.getSubLinkWithText(page.html(), page.finalUrl());
            accessible = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fetchError = "中斷";
        } catch (Exception e) {
            fetchError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /**
     * 用 HtmlHandler 找到的 href 建立子節點（只取同網站的連結）。
     * 連結文字含有關鍵字的優先，其次照網頁中出現的順序；這樣比較不會只抓到選單連結。
     * 子節點先不下載，由 SearchKeywordCommand 平行下載。
     */
    public List<Node> buildChildren(Tree tree, UrlFilter filter, int maxChildren, TermMatcher matcher) {
        List<Node> added = new ArrayList<>();
        if (!accessible || maxChildren <= 0) return added;
        String host = hostOf(url);
        List<HtmlHandler.Link> relevant = new ArrayList<>(), others = new ArrayList<>();
        for (HtmlHandler.Link link : subLinks) {
            if (!filter.isAllowed(link.url()) || !sameSite(host, hostOf(link.url())) || !filter.canFetch(link.url())) continue;
            (matcher.count(link.text()).isEmpty() ? others : relevant).add(link);
        }
        relevant.addAll(others);
        for (HtmlHandler.Link link : relevant) {
            if (added.size() >= maxChildren) break;
            Node child = new Node("", link.url(), "");
            if (tree.append(this, child)) added.add(child);
        }
        return added;
    }

    /** 計算每個關鍵字在網頁中出現的次數 */
    public void buildKeywords(TermMatcher matcher, Map<String, KeywordCategory> categories) {
        String text = title + " " + snippet + " " + body;
        keywords = new ArrayList<>();
        for (Map.Entry<String, Integer> e : matcher.count(text).entrySet()) {
            KeywordCategory c = categories.getOrDefault(e.getKey(), KeywordCategory.QUERY);
            keywords.add(new Keyword(e.getKey(), c, e.getValue()));
        }
        keywords.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
    }

    /** 計算網頁裡各個關鍵字的加權總合 */
    public double calculateSum() {
        sum = 0;
        for (Keyword k : keywords) sum += k.getScore();
        return sum;
    }

    /** 給篩選條件用：本頁是否包含某個字 */
    public boolean containsText(String term) {
        String t = term.toLowerCase(Locale.ROOT);
        return (title + " " + snippet + " " + body).toLowerCase(Locale.ROOT).contains(t);
    }

    static String hostOf(String url) {
        try {
            String h = java.net.URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static boolean sameSite(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return stripWww(a).equals(stripWww(b));
    }

    private static String stripWww(String h) {
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    public int getLevel() {
        int level = 0;
        for (Node n = ancestor; n != null; n = n.ancestor) level++;
        return level;
    }

    void addChild(Node child) {
        child.ancestor = this;
        children.add(child);
    }

    public boolean isRoot() { return ancestor == null; }
    public Node getAncestor() { return ancestor; }
    public List<Node> getChildren() { return children; }
    public List<Keyword> getKeywords() { return keywords; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSnippet() { return snippet; }
    public String getBody() { return body; }
    public double getSum() { return sum; }
    public boolean isAccessible() { return accessible; }
    public String getFetchError() { return fetchError; }
    public int getOriginalRank() { return originalRank; }
    public void setOriginalRank(int r) { originalRank = r; }

    @Override
    public String toString() {
        return String.format("%s (%s) 單頁分數 %.1f", title.isEmpty() ? "(無標題)" : title, url, sum);
    }
}
