package smartranking.command;

import java.util.List;
import java.util.Map;

import smartranking.manager.BoManager;
import smartranking.model.Keyword;
import smartranking.model.Node;
import smartranking.model.Tree;
import smartranking.util.HtmlHandler;

/**
 * 具體搜尋與打分命令
 */
public class SearchKeywordCommand implements ICommand {
    private Map<String, String> candidateSites; // Name -> URL
    private List<Keyword> scoringKeywords;
    private BoManager boManager;

    public SearchKeywordCommand(Map<String, String> candidateSites,
                                List<Keyword> scoringKeywords,
                                BoManager boManager) {
        this.candidateSites = candidateSites;
        this.scoringKeywords = scoringKeywords;
        this.boManager = boManager;
    }

    @Override
    public void execute() {
        boManager.clearPool();

        for (Map.Entry<String, String> entry : candidateSites.entrySet()) {
            String siteName = entry.getKey();
            String rootUrl = entry.getValue();

            // 1. 建立根節點
            Node rootNode = new Node(siteName, rootUrl);
            rootNode.setKeywords(scoringKeywords);

            // 2. 爬取根節點內容並計數
            String rootHtml = HtmlHandler.getHtml(rootUrl);
            String rootText = HtmlHandler.extractBodyText(rootHtml);
            for (Keyword kw : rootNode.getKeywords()) {
                kw.setCount(HtmlHandler.countKeyword(rootText, kw.getName()));
            }

            // 3. 爬取子連結建立子節點 (取前 2 個有效子連結)
            List<String> subLinks = HtmlHandler.extractSubLinks(rootHtml, getDomain(rootUrl), 2);
            for (int i = 0; i < subLinks.size(); i++) {
                String subUrl = subLinks.get(i);
                Node childNode = new Node(siteName + " - Sub " + (i + 1), subUrl);
                childNode.setKeywords(scoringKeywords);

                String childHtml = HtmlHandler.getHtml(subUrl);
                String childText = HtmlHandler.extractBodyText(childHtml);
                for (Keyword kw : childNode.getKeywords()) {
                    kw.setCount(HtmlHandler.countKeyword(childText, kw.getName()));
                }
                rootNode.addChild(childNode);
            }

            // 4. 將構建完成的網站樹加入 Manager
            Tree tree = new Tree(siteName, rootNode);
            boManager.addTree(tree);
        }

        // 5. 排序結算
        boManager.rankSites();
    }

    private String getDomain(String urlString) {
        return urlString.replace("https://", "").replace("http://", "").split("/")[0];
    }
}