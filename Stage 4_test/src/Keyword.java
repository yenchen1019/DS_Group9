/** 一個關鍵字：名稱、類別、權重（由類別決定）、在網頁中出現的次數。 */
public class Keyword {
    private final String name;
    private final KeywordCategory category;
    private final double weight;
    private int count;

    public Keyword(String name, KeywordCategory category) {
        this.name = name;
        this.category = category;
        this.weight = category.getWeight();
    }

    public Keyword(String name, KeywordCategory category, int count) {
        this(name, category);
        this.count = count;
    }

    /** 單一關鍵字分數 = 出現次數 × 權重 */
    public double getScore() { return count * weight; }

    public String getName() { return name; }
    public KeywordCategory getCategory() { return category; }
    public double getWeight() { return weight; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    @Override
    public String toString() {
        return name + "×" + count;
    }
}
