import java.util.*;

/** 關鍵字 → 類別 的對照表（對應期中報告 2.1 的 11 類加分詞）。 */
public class KeywordDictionary {
    private final Map<String, KeywordCategory> dict = new LinkedHashMap<>();
    /** 小寫 → 原字串，讓英文查詢不分大小寫 */
    private final Map<String, String> lowerIndex = new HashMap<>();

    public KeywordDictionary() {
        loadDefaults();
    }

    public void loadDefaults() {
        put(KeywordCategory.CORE, "電影", "上映", "導演", "主演", "片長", "預告", "電影院");
        put(KeywordCategory.GENRE, "動作", "愛情", "喜劇", "恐怖", "科幻", "懸疑", "動畫", "紀錄片");
        put(KeywordCategory.TECH, "IMAX", "3D", "4D", "VR 演唱會", "Dolby Cinema");
        put(KeywordCategory.RELEASE, "場次", "戲院", "票房", "分級", "首映");
        put(KeywordCategory.BEHIND, "幕後", "花絮", "拍攝", "取景", "原著");
        put(KeywordCategory.AWARD, "奧斯卡", "金馬獎", "坎城影展", "威尼斯影展", "柏林影展", "日舞影展",
                "最佳影片", "最佳導演");
        put(KeywordCategory.STUDIO, "Disney", "迪士尼", "Walt Disney", "Universal", "環球影業",
                "Warner Bros.", "Warner Brothers", "華納兄弟", "Paramount", "派拉蒙", "中影股份有限公司");
        put(KeywordCategory.CINEMA, "威秀", "秀泰", "美麗華", "國賓", "樂聲影城", "哈拉影城", "總督數位影城",
                "喜樂時代影城", "光點華山電影館", "光點台北", "誠品電影院", "真善美劇院");
        put(KeywordCategory.REVIEW, "影評", "心得", "評價", "推薦", "無雷", "評分", "口碑", "專業評價", "爛番茄");
        put(KeywordCategory.PLOT, "劇透", "暴雷", "彩蛋", "片尾片段", "致敬", "劇情反轉", "開放式結局", "劇情");
        put(KeywordCategory.FOOD, "爆米花", "碳酸飲料", "熱狗", "吉拿棒", "電影套餐");
    }

    private void put(KeywordCategory category, String... terms) {
        for (String t : terms) {
            dict.put(t, category);
            lowerIndex.put(t.toLowerCase(Locale.ROOT), t);
        }
    }

    /** 查詢某個詞屬於哪一類；不在表中回傳 QUERY */
    public KeywordCategory lookup(String term) {
        String key = lowerIndex.get(term.toLowerCase(Locale.ROOT));
        return key == null ? KeywordCategory.QUERY : dict.get(key);
    }

    public boolean contains(String term) {
        return lowerIndex.containsKey(term.toLowerCase(Locale.ROOT));
    }

    /** 回傳字典裡的標準寫法（例如 imax → IMAX） */
    public String canonical(String term) {
        String key = lowerIndex.get(term.toLowerCase(Locale.ROOT));
        return key == null ? term : key;
    }

    public double getWeight(String term) {
        return lookup(term).getWeight();
    }

    public Map<String, KeywordCategory> entries() {
        return Collections.unmodifiableMap(dict);
    }

    public int size() { return dict.size(); }
}
