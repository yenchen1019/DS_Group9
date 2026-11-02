package smartranking.facade;

import java.util.List;
import java.util.Map;

import smartranking.command.SearchKeywordCommand;
import smartranking.manager.BoManager;
import smartranking.model.Keyword;
import smartranking.model.Tree;

/**
 * Facade 整合所有業務邏輯與子系統
 */
public class Facade {
    private static volatile Facade instance;
    private BoManager boManager;

    private Facade() {
        this.boManager = new BoManager();
    }

    public static Facade getInstance() {
        if (instance == null) {
            synchronized (Facade.class) {
                if (instance == null) {
                    instance = new Facade();
                }
            }
        }
        return instance;
    }

    /**
     * 接收信號並執行搜尋重新排名
     */
    public List<Tree> runSearchRanking(Map<String, String> candidateSites, List<Keyword> keywords) {
        SearchKeywordCommand command = new SearchKeywordCommand(candidateSites, keywords, boManager);
        command.execute();
        return boManager.getBoPool();
    }
}