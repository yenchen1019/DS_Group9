/** 一次搜尋的參數。 */
public class SearchOptions {
    public int limit = 10;          // 從搜尋 API 取幾筆候選網站
    public int maxChildren = 4;     // 每個網頁最多展開幾個子連結
    public int depth = 1;           // 子頁展開幾層（Root 為第 0 層）
    public int top = 10;            // 最後顯示前幾名
    public int timeoutSeconds = 8;  // 單一網頁下載逾時
    public int siteTimeoutSeconds = 30; // 單一網站（整棵樹）處理逾時
    public String filter;           // 篩選條件，例如「動畫」或「2024」
}
