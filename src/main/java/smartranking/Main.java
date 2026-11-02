package smartranking;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import smartranking.facade.Facade;
import smartranking.model.Keyword;
import smartranking.model.Node;
import smartranking.model.Tree;

public class Main {
    public static void main(String[] args) {
        // 1. 定義專題各分類關鍵字與權重
        List<Keyword> keywordRules = Arrays.asList(
            // 核心詞 (+5)
            new Keyword("電影", 5.0),
            new Keyword("上映", 5.0),
            new Keyword("導演", 5.0),
            new Keyword("主演", 5.0),
            new Keyword("片長", 5.0),
            new Keyword("預告", 5.0),
            new Keyword("電影院", 5.0),

            // 類型 (+5)
            new Keyword("動作", 5.0),
            new Keyword("愛情", 5.0),
            new Keyword("喜劇", 5.0),
            new Keyword("恐怖", 5.0),
            new Keyword("科幻", 5.0),
            new Keyword("懸疑", 5.0),
            new Keyword("動畫", 5.0),
            new Keyword("紀錄片", 5.0),

            // 評價與口碑 (+1)
            new Keyword("影評", 1.0),
            new Keyword("心得", 1.0),
            new Keyword("評價", 1.0),
            new Keyword("推薦", 1.0),
            new Keyword("無雷", 1.0),
            new Keyword("評分", 1.0),
            new Keyword("口碑", 1.0),
            new Keyword("專業評價", 1.0),
            new Keyword("爛番茄", 1.0),

            // 上映資訊 (+3)
            new Keyword("場次", 3.0),
            new Keyword("戲院", 3.0),
            new Keyword("票房", 3.0),
            new Keyword("分級", 3.0),
            new Keyword("首映", 3.0),

            // 幕後資訊 (+3)
            new Keyword("幕後", 3.0),
            new Keyword("花絮", 3.0),
            new Keyword("拍攝", 3.0),
            new Keyword("取景", 3.0),
            new Keyword("原著", 3.0),

            // 獎項與影展 (+3)
            new Keyword("奧斯卡", 3.0),
            new Keyword("金馬獎", 3.0),
            new Keyword("坎城影展", 3.0),
            new Keyword("威尼斯影展", 3.0),
            new Keyword("柏林影展", 3.0),
            new Keyword("日舞影展", 3.0),
            new Keyword("最佳影片", 3.0),
            new Keyword("最佳導演", 3.0),

            // 劇情細節 (+1)
            new Keyword("劇透", 1.0),
            new Keyword("暴雷", 1.0),
            new Keyword("彩蛋", 1.0),
            new Keyword("片尾片段", 1.0),
            new Keyword("致敬", 1.0),
            new Keyword("劇情反轉", 1.0),
            new Keyword("開放式結局", 1.0),
            new Keyword("劇情", 1.0),

            // 影城名稱 (+2)
            new Keyword("威秀", 2.0),
            new Keyword("秀泰", 2.0),
            new Keyword("美麗華", 2.0),
            new Keyword("國賓", 2.0),
            new Keyword("樂聲影城（西門町24小時）", 2.0),
            new Keyword("哈拉影城（內湖）", 2.0),
            new Keyword("總督數位影城（松山區）", 2.0),
            new Keyword("喜樂時代影城（南港CITYLINK）", 2.0),
            new Keyword("光點華山電影館", 2.0),
            new Keyword("光點台北", 2.0),
            new Keyword("誠品電影院", 2.0),
            new Keyword("真善美劇院", 2.0),

            // 電影技術 (+4)
            new Keyword("IMAX", 4.0),
            new Keyword("3D", 4.0),
            new Keyword("4D", 4.0),
            new Keyword("VR演唱會", 4.0),
            new Keyword("Dolby cinema", 4.0),

            // 製片公司 (+2.5)，相同詞彙只列一次，避免重複計分
            new Keyword("Disney", 2.5),
            new Keyword("迪士尼", 2.5),
            new Keyword("Walt Disney", 2.5),
            new Keyword("Universal", 2.5),
            new Keyword("環球影業", 2.5),
            new Keyword("Warner Bros.", 2.5),
            new Keyword("Warner Brothers", 2.5),
            new Keyword("華納兄弟", 2.5),
            new Keyword("Paramount", 2.5),
            new Keyword("派拉蒙", 2.5),
            new Keyword("中影股份有限公司", 2.5),

            // 飲食 (+1)
            new Keyword("爆米花", 1.0),
            new Keyword("碳酸飲料", 1.0),
            new Keyword("熱狗", 1.0),
            new Keyword("吉拿棒", 1.0),
            new Keyword("電影套餐", 1.0)
        );

        // 2. 由使用者輸入候選網站 URL
        Map<String, String> candidateSites = readCandidateSites();
        if (candidateSites.isEmpty()) {
            System.out.println("\n沒有可分析的網站，程式結束。");
            return;
        }

        System.out.printf("%n已完成網站輸入，共 %d 個。%n", candidateSites.size());
        System.out.println("分析開始，正在連線並整理網站內容，請稍候...");

        // 3. 透過 Facade 單一接口啟動分析
        Facade facade = Facade.getInstance();
        List<Tree> rankedResults = facade.runSearchRanking(candidateSites, keywordRules);

        // 4. 印出排序結果與各節點關鍵字頻率報表
        System.out.println("\n====== 分析完成：網站排名結果 ======");
        int rank = 1;
        for (Tree tree : rankedResults) {
            System.out.printf("#%d | 站點: %-15s | 綜合加權總分: %.2f%n",
                              rank++, tree.getName(), tree.getTotalScore());

            for (Node node : tree.toList()) {
                System.out.printf("   └── [Page] %-20s (URL: %s)%n", node.getName(), node.getUrl());
                System.out.printf("       單頁分數: %.2f | 關鍵字分佈: %s%n",
                                  node.getSum(), node.getKeywords().stream()
                                      .filter(keyword -> keyword.getScore() > 0)
                                      .collect(Collectors.toList()));
            }
            System.out.println();
        }
    }

    private static Map<String, String> readCandidateSites() {
        Map<String, String> candidateSites = new LinkedHashMap<>();
        boolean inputEndedByEof = true;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("====== 網站輸入 ======");
            System.out.println("請逐行輸入要分析的網站網址，每次輸入一個。");
            System.out.println("輸入完成後，按 Enter（空白行）開始分析。");
            System.out.println("至少需要輸入一個有效網址。");
            while (scanner.hasNextLine()) {
                System.out.printf("網站 %d（直接 Enter 結束輸入）> ", candidateSites.size() + 1);
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    if (candidateSites.isEmpty()) {
                        System.out.println("目前還沒有有效網址，請先輸入一個網站。");
                        continue;
                    }
                    System.out.println("網站輸入結束。");
                    inputEndedByEof = false;
                    break;
                }

                String url = input.contains("://") ? input : "https://" + input;
                try {
                    URI uri = new URI(url);
                    String scheme = uri.getScheme();
                    if (uri.getHost() == null
                            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                        System.out.println("網址格式不正確。請輸入有效的 HTTP 或 HTTPS 網址，例如 https://example.com。");
                        continue;
                    }

                    String siteName = uri.getHost();
                    String uniqueName = siteName;
                    int suffix = 2;
                    while (candidateSites.containsKey(uniqueName)) {
                        uniqueName = siteName + " (" + suffix++ + ")";
                    }
                    candidateSites.put(uniqueName, uri.toString());
                    System.out.printf("已加入：%s%n", uri);
                } catch (URISyntaxException e) {
                    System.out.println("無法辨識這個網址，請檢查格式後再試一次。");
                }
            }
            if (inputEndedByEof && !candidateSites.isEmpty()) {
                System.out.println("\n已收到輸入結束訊號，開始分析已輸入的網站。");
            }
        }
        return candidateSites;
    }
}