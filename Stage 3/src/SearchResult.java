import java.util.List;

/** 一次搜尋的輸出：排名後的網站樹與相關資訊。 */
public record SearchResult(
        String rawQuery,
        String searchQuery,
        List<Keyword> keywords,
        String source,
        int candidateCount,
        List<Tree> ranked,
        long elapsedMillis) {
}
