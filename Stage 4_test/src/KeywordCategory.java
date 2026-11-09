/**
 * 關鍵字類別與權重（對應期中報告 2.1）。
 * QUERY：使用者輸入、但不在字典中的詞（例如「狗」）。
 */
public enum KeywordCategory { 


    // 在 enum 中新增 EXTENDED 類別
    EXTENDED("延伸詞", 2), // 分數可自訂
    // ... 原本的其他類別


    CORE("核心詞", 10),
    GENRE("類型", 5),
    QUERY("查詢詞", 5),
    TECH("電影技術", 4),
    RELEASE("上映資訊", 3),
    BEHIND("幕後資訊", 3),
    AWARD("獎項與影展", 3),
    STUDIO("製片公司", 2.5),
    CINEMA("影城名稱", 2),
    REVIEW("評價與口碑", 1),
    PLOT("劇情細節", 1),
    FOOD("飲食", 1);

    private final String label;
    private final double weight;

    KeywordCategory(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() { return label; }

    public double getWeight() { return weight; }
}
