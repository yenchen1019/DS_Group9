package smartranking;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import smartranking.facade.Facade;
import smartranking.model.Keyword;
import smartranking.model.Node;
import smartranking.model.Tree;

public class Main {
    public static void main(String[] args) {
        // 1. 定義自訂關鍵字與其權重矩陣 (參考專題 Proposal 定義)
        List<Keyword> keywordRules = Arrays.asList(
            new Keyword("電影", 5.0),
            new Keyword("導演", 5.0),
            new Keyword("影評", 3.0),
            new Keyword("IMAX", 4.0),
            new Keyword("票房", 3.0)
        );

        // 2. 候選目標網頁集合 (可抽換為 Google 爬取回傳的候選清單)
        Map<String, String> candidateSites = new LinkedHashMap<>();
        candidateSites.put("開眼電影網", "http://www.atmovies.com.tw");
        candidateSites.put("Yahoo電影戲劇", "https://movies.yahoo.com.tw");

        System.out.println("====== [Smart Ranking Engine] 開始分析候選網頁 ======\n");

        // 3. 透過 Facade 單一接口啟動分析
        Facade facade = Facade.getInstance();
        List<Tree> rankedResults = facade.runSearchRanking(candidateSites, keywordRules);

        // 4. 印出排序結果與各節點關鍵字頻率報表
        System.out.println("\n====== [Smart Ranking Engine] 最終排名結果 ======");
        int rank = 1;
        for (Tree tree : rankedResults) {
            System.out.printf("#%d | 站點: %-15s | 綜合加權總分: %.2f%n",
                              rank++, tree.getName(), tree.getTotalScore());

            for (Node node : tree.toList()) {
                System.out.printf("   └── [Page] %-20s (URL: %s)%n", node.getName(), node.getUrl());
                System.out.printf("       單頁分數: %.2f | 關鍵字分佈: %s%n",
                                  node.getSum(), node.getKeywords());
            }
            System.out.println();
        }
    }
}