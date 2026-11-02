import java.net.URI;
import java.util.*;

/**
 * 依網站總分由高到低排序。
 * 總分 = Root 匯總後的分數 × 網站係數 + 網站加分：
 *   電影資料網站（開眼、豆瓣、IMDb、維基百科…）加權，八卦新聞網站降權，
 *   讓「郁方」這類人名查詢時，作品列表能排在新聞前面。
 */
public class Ranker {

    /** 電影資料網站：分數 ×2，另外 +30（就算網頁抓不到內容也能往前排） */
    private static final String[] MOVIE_SITES = {
            "atmovies.com.tw", "movie.douban.com", "imdb.com", "wikipedia.org",
            "themoviedb.org", "letterboxd.com", "mtime.com", "1905.com", "catchplay.com"
    };
    private static final double MOVIE_FACTOR = 2.0;
    private static final double MOVIE_BONUS = 30.0;

    /** 新聞 / 娛樂八卦網站：分數 ×0.5 */
    private static final String[] NEWS_SITES = {
            "ettoday.net", "ebc.net.tw", "sohu.com", "udn.com", "chinatimes.com",
            "ltn.com.tw", "setn.com", "tvbs.com.tw", "nownews.com", "mirrormedia.mg",
            "news.yahoo.com", "sina.com", "sina.com.cn", "storm.mg"
    };
    private static final double NEWS_FACTOR = 0.5;

    public List<Tree> rank(List<Tree> trees) {
        PriorityQueue<Tree> pq = new PriorityQueue<>(Math.max(1, trees.size()), (a, b) -> {
            int c = Double.compare(adjustedScore(b), adjustedScore(a));
            // 同分時維持搜尋 API 的原始順序
            return c != 0 ? c : Integer.compare(a.getRoot().getOriginalRank(), b.getRoot().getOriginalRank());
        });
        pq.addAll(trees);
        List<Tree> out = new ArrayList<>(trees.size());
        while (!pq.isEmpty()) out.add(pq.poll());
        return out;
    }

    /** 排序與顯示都用這個分數，兩邊才會一致 */
    public static double adjustedScore(Tree t) {
        double score = t.getScore();
        String host = hostOf(t.getRoot().getUrl());
        if (matches(host, MOVIE_SITES)) return score * MOVIE_FACTOR + MOVIE_BONUS;
        if (matches(host, NEWS_SITES)) return score * NEWS_FACTOR;
        return score;
    }

    /** 取出網址的網域，例如 https://star.ettoday.net/news/1 → star.ettoday.net */
    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    /** 網域本身或其子網域符合清單（例如 zh.wikipedia.org 符合 wikipedia.org） */
    private static boolean matches(String host, String[] sites) {
        for (String s : sites) {
            if (host.equals(s) || host.endsWith("." + s)) return true;
        }
        return false;
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