import java.util.*;
import java.util.regex.*;

/**
 * 查詢前處理：全形轉半形、雙引號片語、關鍵字拆分、停用詞過濾，再依字典設定權重。
 * 例：「有狗的動畫電影」→ 狗(查詢詞) / 動畫(類型) / 電影(核心詞)
 */
public class QueryProcessor {
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"|“([^”]+)”|「([^」]+)」|『([^』]+)』");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,，、。.;；:：!！?？/|()（）\\[\\]【】]+");

    /** 出現在片段中間就切開的連接詞 */
    private static final String[] SPLIT_WORDS = {
            "有沒有", "我想看", "關於", "有關", "相關", "一部", "一些", "什麼", "哪些", "哪部",
            "的", "和", "與", "跟", "及", "或"
    };
    /** 只在片段開頭或結尾才去掉的字（避免切壞人名，例如「有村架純」） */
    private static final String EDGE_CHARS = "有我想看找要請嗎呢吧了";
    private static final Set<String> ENGLISH_STOPWORDS = Set.of(
            "a", "an", "the", "of", "and", "or", "in", "on", "with", "about", "for", "to", "movie", "movies", "film");

    private final KeywordDictionary dictionary;
    private final TermMatcher matcher;

    public QueryProcessor(KeywordDictionary dictionary) {
        this.dictionary = dictionary;
        this.matcher = new TermMatcher(dictionary.entries().keySet());
    }

    /** 拆字後查字典設定權重，產生 Keyword 物件（不重複） */
    public List<Keyword> parse(String input) {
        Map<String, Keyword> result = new LinkedHashMap<>();
        for (String t : tokenize(normalize(input))) {
            String key = t.toLowerCase(Locale.ROOT);
            if (!result.containsKey(key)) {
                result.put(key, new Keyword(dictionary.canonical(t), dictionary.lookup(t)));
            }
        }
        if (result.isEmpty() && !normalize(input).isEmpty()) {
            String raw = normalize(input);
            result.put(raw, new Keyword(raw, KeywordCategory.QUERY));
        }
        return new ArrayList<>(result.values());
    }

    /**
     * 實際送給搜尋 API 的字串。若使用者沒有輸入核心詞，自動補上「電影」，
     * 這樣輸入「狗」或「郁方」時，候選網頁才會偏向電影相關。
     */
    public String buildSearchQuery(String input, List<Keyword> keywords) {
        String q = normalize(input);
        boolean hasCore = keywords.stream().anyMatch(k -> k.getCategory() == KeywordCategory.CORE);
        return hasCore ? q : q + " 電影";
    }

    /** 基本文字處理：全形轉半形、合併空白 */
    public String normalize(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c == '　') sb.append(' ');
            else if (c >= '！' && c <= '～') sb.append((char) (c - 0xFEE0));
            else sb.append(c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    /** 關鍵字拆分 */
    public List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        // 1. 引號內視為同一個關鍵字
        Matcher m = QUOTED.matcher(s);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            rest.append(s, last, m.start()).append(' ');
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null && !m.group(g).isBlank()) tokens.add(m.group(g).trim());
            }
            last = m.end();
        }
        rest.append(s.substring(last));

        // 2. 其餘依標點與空白切開，再用字典做最長匹配
        for (String chunk : SEPARATORS.split(rest.toString())) {
            if (chunk.isBlank()) continue;
            for (TermMatcher.Segment seg : matcher.segment(chunk)) {
                if (seg.matched()) tokens.add(seg.text());
                else tokens.addAll(cleanUnmatched(seg.text()));
            }
        }
        return tokens;
    }

    /** 沒有命中字典的片段：去掉停用詞後當成查詢詞 */
    private List<String> cleanUnmatched(String text) {
        List<String> out = new ArrayList<>();
        if (text.matches("[\\x00-\\x7F]+")) {
            String w = text.trim();
            if (!w.isEmpty() && !ENGLISH_STOPWORDS.contains(w.toLowerCase(Locale.ROOT))) out.add(w);
            return out;
        }
        List<String> parts = new ArrayList<>(List.of(text));
        for (String sw : SPLIT_WORDS) {
            List<String> next = new ArrayList<>();
            for (String p : parts) next.addAll(Arrays.asList(p.split(Pattern.quote(sw))));
            parts = next;
        }
        for (String p : parts) {
            String w = stripEdges(p.trim());
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    private static String stripEdges(String w) {
        int start = 0, end = w.length();
        while (start < end && EDGE_CHARS.indexOf(w.charAt(start)) >= 0) start++;
        while (end > start && EDGE_CHARS.indexOf(w.charAt(end - 1)) >= 0) end--;
        return w.substring(start, end);
    }
}
