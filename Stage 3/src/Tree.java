import java.util.*;

/** 一棵 Tree 代表一個候選網站：搜尋結果頁為 Root，頁面裡的子連結為子節點。 */
public class Tree {
    /** 子頁每往下一層，分數乘以這個衰減係數 */
    public static final double DECAY = 0.5;

    private final String name;
    private final Node root;
    private final Set<String> visitedUrls = new HashSet<>();
    private final UrlFilter filter;
    private double score;

    public Tree(Node root, UrlFilter filter) {
        this.root = root;
        this.filter = filter;
        this.name = Node.hostOf(root.getUrl());
        visitedUrls.add(key(root.getUrl()));
    }

    /** 加入子節點；網址重複（包含連回已加入的網頁）就不加，回傳 false */
    public synchronized boolean append(Node parent, Node child) {
        if (!visitedUrls.add(key(child.getUrl()))) return false;
        parent.addChild(child);
        return true;
    }

    /** 網站總分 = 根頁分數 + Σ(子樹分數 × 0.5) */
    public double computeScore() {
        score = postOrderTraverse(root);
        return score;
    }

    /** 後序走訪：先算完所有子樹，再加回父節點 */
    private double postOrderTraverse(Node n) {
        double childTotal = 0;
        for (Node c : n.getChildren()) childTotal += postOrderTraverse(c);
        return n.calculateSum() + childTotal * DECAY;
    }

    /** 前序走訪，回傳所有節點 */
    public List<Node> toList() {
        List<Node> out = new ArrayList<>();
        preOrderTraverse(root, out);
        return out;
    }

    private void preOrderTraverse(Node n, List<Node> out) {
        out.add(n);
        for (Node c : n.getChildren()) preOrderTraverse(c, out);
    }

    /** 每個節點的關鍵字次數，以樹狀圖輸出（Stage 2 要求的格式） */
    public String toTreeString(int maxKeywordsPerNode) {
        StringBuilder sb = new StringBuilder();
        appendTree(sb, root, "", true, maxKeywordsPerNode);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, Node n, String prefix, boolean last, int maxKw) {
        String branch = n.isRoot() ? "" : (last ? "└─ " : "├─ ");
        double factor = Math.pow(DECAY, n.getLevel());
        sb.append(prefix).append(branch)
                .append(String.format("[%.1f × %s] ", n.getSum(), trimZeros(factor)))
                .append(shorten(n.getTitle().isEmpty() ? n.getUrl() : n.getTitle(), 40));
        if (!n.isAccessible()) sb.append("  (未讀取正文：").append(n.getFetchError()).append(")");
        sb.append('\n');

        String childPrefix = prefix + (n.isRoot() ? "" : (last ? "   " : "│  "));
        String detailPrefix = childPrefix + (n.getChildren().isEmpty() ? "   " : "│  ");
        sb.append(detailPrefix).append(n.getUrl()).append('\n');
        sb.append(detailPrefix).append("關鍵字：").append(keywordSummary(n, maxKw)).append('\n');

        List<Node> kids = n.getChildren();
        for (int i = 0; i < kids.size(); i++) {
            appendTree(sb, kids.get(i), childPrefix, i == kids.size() - 1, maxKw);
        }
    }

    static String keywordSummary(Node n, int max) {
        List<Keyword> kws = n.getKeywords();
        if (kws.isEmpty()) return "(無)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, kws.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(kws.get(i));
        }
        if (kws.size() > max) sb.append(" …(共 ").append(kws.size()).append(" 個)");
        return sb.toString();
    }

    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String trimZeros(double d) {
        String s = String.format("%.4f", d);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String key(String url) {
        String n = filter.normalize(url);
        return n == null ? url : n;
    }

    public boolean isEmpty() { return root == null; }
    public String getName() { return name; }
    public Node getRoot() { return root; }
    public double getScore() { return score; }
    public int size() { return toList().size(); }
}
