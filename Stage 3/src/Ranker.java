import java.util.*;

/** 依網站總分（Root 匯總後的分數）由高到低排序。 */
public class Ranker {

    public List<Tree> rank(List<Tree> trees) {
        PriorityQueue<Tree> pq = new PriorityQueue<>(Math.max(1, trees.size()), (a, b) -> {
            int c = Double.compare(b.getScore(), a.getScore());
            // 同分時維持搜尋 API 的原始順序
            return c != 0 ? c : Integer.compare(a.getRoot().getOriginalRank(), b.getRoot().getOriginalRank());
        });
        pq.addAll(trees);
        List<Tree> out = new ArrayList<>(trees.size());
        while (!pq.isEmpty()) out.add(pq.poll());
        return out;
    }

    /** 條件篩選：電影類型、上映年份等（網站任一頁包含此字就保留） */
    public List<Tree> filter(List<Tree> trees, String condition) {
        if (condition == null || condition.isBlank()) return trees;
        List<Tree> out = new ArrayList<>();
        for (Tree t : trees) {
            for (Node n : t.toList()) {
                if (n.containsText(condition.trim())) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    /** 只回傳前 N 筆 */
    public List<Tree> topN(List<Tree> trees, int n) {
        return new ArrayList<>(trees.subList(0, Math.min(n, trees.size())));
    }
}
