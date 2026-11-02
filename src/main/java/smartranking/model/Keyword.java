package smartranking.model;

/**
 * 封裝單一關鍵字名稱、權重與出現次數
 */
public class Keyword {
    private String name;
    private double weight;
    private int count;

    public Keyword(String name, double weight) {
        this.name = name;
        this.weight = weight;
        this.count = 0;
    }

    public double getScore() {
        return this.count * this.weight;
    }

    public String getName() {
        return name;
    }

    public double getWeight() {
        return weight;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return String.format("[%s: count=%d, weight=%.1f, score=%.2f]", name, count, weight, getScore());
    }
}