import java.util.List;
import java.util.function.Consumer;

/** 後端入口（Singleton）：接收 ViewControl 的 Signal，派送對應的 Command。 */
public class Facade {
    private static Facade instance;

    private final KeywordDictionary dictionary = new KeywordDictionary();
    private final QueryProcessor queryProcessor = new QueryProcessor(dictionary);
    private final BoManager boManager = new BoManager();
    private final Ranker ranker = new Ranker();
    private SearchApiClient api;

    private Facade() { }

    public static synchronized Facade getInstance() {
        if (instance == null) instance = new Facade();
        return instance;
    }

    public void setSearchApiClient(SearchApiClient api) { this.api = api; }

    public KeywordDictionary getDictionary() { return dictionary; }

    public QueryProcessor getQueryProcessor() { return queryProcessor; }

    /** signal 'S' = 搜尋並重新排名 */
    public SearchResult distribute(String signal, String rawQuery, List<Keyword> keywords,
                                   SearchOptions options, Consumer<String> log) throws Exception {
        if (api == null) throw new IllegalStateException("尚未設定 SearchApiClient");
        switch (signal.charAt(0)) {
            case 'S': {
                SearchKeywordCommand cmd = new SearchKeywordCommand(rawQuery, keywords, options, dictionary,
                        queryProcessor, api, boManager, ranker, log);
                cmd.execute();
                if (cmd.getError() != null) throw cmd.getError();
                return cmd.getResult();
            }
            default:
                throw new IllegalArgumentException("未知的 signal：" + signal);
        }
    }
}
