import java.util.*;

/** 極簡 JSON 解析器（只用來讀搜尋 API 的回應，不需要額外套件）。 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    /** 回傳 Map / List / String / Double / Boolean / null */
    public static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw new IllegalArgumentException("JSON 結尾不完整");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            expect(':');
            m.put(key, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("JSON 格式錯誤，位置 " + i);
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return list; }
        while (true) {
            list.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return list;
            if (c != ',') throw new IllegalArgumentException("JSON 格式錯誤，位置 " + i);
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: sb.append(e);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalArgumentException("JSON 格式錯誤，位置 " + i);
        return Double.parseDouble(s.substring(start, i));
    }

    private void expect(char c) {
        if (s.charAt(i) != c) throw new IllegalArgumentException("JSON 預期 '" + c + "'，位置 " + i);
        i++;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }
}
