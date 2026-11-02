import java.util.List;
import java.util.function.Consumer;

/**
 * 前端控制（Singleton）：抓到使用者輸入，交給 QueryProcessor 拆字與設定權重，再傳給 Facade。
 * Stage 5 的 Swing 介面也會透過這裡呼叫後端。
 */
public class ViewControl {
    private static ViewControl instance;

    private ViewControl() { }

    public static synchronized ViewControl getInstance() {
        if (instance == null) instance = new ViewControl();
        return instance;
    }

    public SearchResult onSearch(String input, SearchOptions options, Consumer<String> log) throws Exception {
        Facade facade = Facade.getInstance();
        List<Keyword> keywords = facade.getQueryProcessor().parse(input);
        if (keywords.isEmpty()) throw new IllegalArgumentException("請輸入關鍵字");
        return facade.distribute("S", input, keywords, options, log);
    }
}
