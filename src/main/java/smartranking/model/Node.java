package smartranking.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表單一網頁節點，包含其子連結所構成之子節點
 */
public class Node {
    private String name;
    private String url;
    private double sum;
    private String ancestor;
    private List<Node> children;
    private List<Keyword> keywords;

    public Node(String name, String url) {
        this.name = name;
        this.url = url;
        this.sum = 0.0;
        this.children = new ArrayList<>();
        this.keywords = new ArrayList<>();
    }

    public void setKeywords(List<Keyword> templateKeywords) {
        this.keywords = new ArrayList<>();
        // 為每個節點複製獨立的 Keyword 實體以計數
        for (Keyword kw : templateKeywords) {
            this.keywords.add(new Keyword(kw.getName(), kw.getWeight()));
        }
    }

    public void addChild(Node child) {
        child.setAncestor(this.name);
        this.children.add(child);
    }

    /**
     * 計算本頁面的關鍵字加權分數
     */
    public double calculateSum() {
        this.sum = 0.0;
        for (Keyword kw : this.keywords) {
            this.sum += kw.getScore();
        }
        return this.sum;
    }

    /**
     * 後序遞迴計算整棵子樹（含自己與所有子節點）的加權總分
     */
    public double calculateSubTreeSum(double childDecay) {
        double total = calculateSum();
        for (Node child : children) {
            total += child.calculateSubTreeSum(childDecay) * childDecay;
        }
        return total;
    }

    public String getName() { return name; }
    public String getUrl() { return url; }
    public double getSum() { return sum; }
    public List<Node> getChildren() { return children; }
    public List<Keyword> getKeywords() { return keywords; }
    public void setAncestor(String ancestor) { this.ancestor = ancestor; }
    public String getAncestor() { return ancestor; }
}