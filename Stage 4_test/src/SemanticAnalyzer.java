import java.util.*;
import java.util.stream.Collectors;

/** Stage 4: 語意分析 (跨網域 DF 驗證版) */
public class SemanticAnalyzer {
    
    // 擴充停用詞表：加入網頁常見底板字、維基百科廢字
    private static final Set<String> STOPWORDS = Set.of(
        "的", "了", "和", "與", "或", "是", "在", "我", "有", "也", "就", "不", "人", "都", 
        "一", "一個", "上", "這", "為", "影片", "電影", "線上", "觀看", "可以", "我們",
        "他", "她", "它", "中", "及", "等", "被", "把", "從", "到", "向", "著",
        "a", "an", "the", "in", "on", "at", "to", "for", "of", "and", "or", "is", "are",
        "te", "da", "le", "la", "de", "el", "al", "en",
        "百科", "維基", "維基百科", "自由", "首頁", "分類", "編輯", "導覽", "搜尋", "閱讀", "檢視", "作品", "主演"
    );

    public static List<String> extractExtendedKeywords(List<Tree> topTrees, List<Keyword> existingKeywords, int limit) {
        Map<String, Integer> termFrequency = new HashMap<>(); 
        
        // 【關鍵優化】：這裡的 Set 改為儲存 Domain (網域)，而不是單一網址
        Map<String, Set<String>> domainFrequency = new HashMap<>(); 
        
        Set<String> existingNames = existingKeywords.stream()
                .map(k -> k.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int analyzeCount = Math.min(topTrees.size(), 5);
        for (int i = 0; i < analyzeCount; i++) {
            Node root = topTrees.get(i).getRoot();
            
            // 取得該網頁的網域 (例如: zh.wikipedia.org)
            String domain = getHost(root.getUrl()); 
            
            String text = root.getTitle() + " " + root.getSnippet();
            text = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]", " "); 
            
            String[] parts = text.split("\\s+");
            
            for (String part : parts) {
                if (part.length() < 2) continue; 
                
                // 過濾純英文字串與純數字串
                if (part.matches("[a-zA-Z]+") || part.matches("\\d+")) {
                    continue; 
                }

                // 擷取 N-gram (2字到4字詞)
                for (int len = 2; len <= Math.min(part.length(), 4); len++) {
                    for (int j = 0; j <= part.length() - len; j++) {
                        String term = part.substring(j, j + len).toLowerCase(Locale.ROOT);
                        
                        if (!isNumeric(term) 
                            && !term.matches("[a-zA-Z]+") 
                            && !STOPWORDS.contains(term) 
                            && !isExisting(term, existingNames)) {
                            
                            termFrequency.put(term, termFrequency.getOrDefault(term, 0) + 1);
                            
                            // 記錄這個詞出現在哪個網域
                            domainFrequency.putIfAbsent(term, new HashSet<>());
                            domainFrequency.get(term).add(domain);
                        }
                    }
                }
            }
        }

        Map<String, Double> termScores = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            
            // 計算跨網域頻率 (DF)
            int df = domainFrequency.get(term).size();
            
            // 【終極殺手鐧】：如果分析的網頁大於 1 個，但這個詞只出現在「單一網域」(DF < 2)
            // 代表它極度可能是該網站的專有名詞 (如劉偉儀) 或是殘破句 (如本翻閱出)，直接淘汰！
            if (analyzeCount > 1 && df < 2) {
                continue; 
            }
            
            double idfWeight = (double) analyzeCount / Math.max(1, df); 
            double score = tf * idfWeight;
            
            if (term.length() >= 3) {
                score *= 1.2; 
            }
            
            termScores.put(term, score);
        }

        return termScores.entrySet().stream()
                .filter(e -> e.getValue() > 1.0) 
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static boolean isNumeric(String str) {
        return str.matches("\\d+");
    }

    private static boolean isExisting(String term, Set<String> existingNames) {
        for (String ex : existingNames) {
            if (ex.contains(term) || term.contains(ex)) return true;
        }
        return false;
    }
    
    // 解析網址的輔助方法
    private static String getHost(String urlStr) {
        try {
            String host = java.net.URI.create(urlStr).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}