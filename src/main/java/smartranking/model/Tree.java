package smartranking.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表一個完整的網站（包含根頁面及其子層級網頁）
 */
public class Tree implements Comparable<Tree> {
    private String name;
    private Node root;
    private double totalScore;

    public Tree(String name, Node root) {
        this.name = name;
        this.root = root;
        this.totalScore = 0.0;
    }

    /**
     * 執行後序走訪聚合全站分數
     */
    public double calculateTotalScore() {
        if (root == null) return 0.0;
        // 子頁面給予 0.5 的權重衰減
        this.totalScore = root.calculateSubTreeSum(0.5);
        return this.totalScore;
    }

    /**
     * 展平樹狀結構為清單（便於報表輸出）
     */
    public List<Node> toList() {
        List<Node> list = new ArrayList<>();
        preOrderCollect(root, list);
        return list;
    }

    private void preOrderCollect(Node current, List<Node> list) {
        if (current == null) return;
        list.add(current);
        for (Node child : current.getChildren()) {
            preOrderCollect(child, list);
        }
    }

    public Node getRoot() { return root; }
    public double getTotalScore() { return totalScore; }
    public String getName() { return name; }

    @Override
    public int compareTo(Tree other) {
        // 分數高者排在前面（降序）
        return Double.compare(other.totalScore, this.totalScore);
    }
}