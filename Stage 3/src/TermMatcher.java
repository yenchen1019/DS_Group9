import java.util.*;

/**
 * 以「最長匹配、不重疊」的方式在文字中找出關鍵字。
 * 例如「電影院」只會算成「電影院」一次，不會再多算一次「電影」；
 * 「劇情反轉」也不會重複算到「劇情」。英文詞要求前後不是英數字（避免 3D 命中 3DS）。
 */
public class TermMatcher {
    /** 開頭字元 → 以此字元開頭的詞（小寫，長的排前面） */
    private final Map<Character, List<String>> index = new HashMap<>();
    private final Map<String, String> lowerToOriginal = new HashMap<>();

    public record Segment(String text, boolean matched) {}

    public TermMatcher(Collection<String> terms) {
        for (String t : terms) {
            if (t == null || t.isBlank()) continue;
            String low = t.toLowerCase(Locale.ROOT);
            if (lowerToOriginal.putIfAbsent(low, t) != null) continue;
            index.computeIfAbsent(low.charAt(0), k -> new ArrayList<>()).add(low);
        }
        for (List<String> list : index.values()) {
            list.sort((a, b) -> b.length() - a.length());
        }
    }

    /** 計算每個詞出現的次數（key 為原本的寫法） */
    public Map<String, Integer> count(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return counts;
        String low = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < low.length()) {
            String m = matchAt(low, i);
            if (m != null) {
                counts.merge(lowerToOriginal.get(m), 1, Integer::sum);
                i += m.length();
            } else {
                i++;
            }
        }
        return counts;
    }

    /** 把字串切成「命中字典的詞」與「沒命中的片段」 */
    public List<Segment> segment(String text) {
        List<Segment> out = new ArrayList<>();
        String low = text.toLowerCase(Locale.ROOT);
        StringBuilder rest = new StringBuilder();
        int i = 0;
        while (i < low.length()) {
            String m = matchAt(low, i);
            if (m != null) {
                if (rest.length() > 0) {
                    out.add(new Segment(rest.toString(), false));
                    rest.setLength(0);
                }
                out.add(new Segment(lowerToOriginal.get(m), true));
                i += m.length();
            } else {
                rest.append(text.charAt(i));
                i++;
            }
        }
        if (rest.length() > 0) out.add(new Segment(rest.toString(), false));
        return out;
    }

    private String matchAt(String low, int i) {
        List<String> candidates = index.get(low.charAt(i));
        if (candidates == null) return null;
        for (String c : candidates) {
            if (low.startsWith(c, i) && boundaryOk(low, i, c)) return c;
        }
        return null;
    }

    private static boolean boundaryOk(String low, int start, String term) {
        int end = start + term.length();
        if (isAsciiAlnum(term.charAt(0)) && start > 0 && isAsciiAlnum(low.charAt(start - 1))) return false;
        if (isAsciiAlnum(term.charAt(term.length() - 1)) && end < low.length() && isAsciiAlnum(low.charAt(end)))
            return false;
        return true;
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
