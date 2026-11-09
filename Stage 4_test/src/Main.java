import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

/**
 * Stage 3 命令列程式。
 *   java -cp out Main 狗
 *   java -cp out Main --limit 20 --children 5 --depth 1 --top 10 --tree "有狗的動畫電影"
 * 不帶關鍵字就進入互動模式。
 */
public class Main {
    private static boolean showTree = false;
    private static boolean verbose = true;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.setOut(out);
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        SearchOptions options = new SearchOptions();
        String configPath = "config.properties";
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": options.limit = Integer.parseInt(args[++i]); break;
                case "--children": options.maxChildren = Integer.parseInt(args[++i]); break;
                case "--depth": options.depth = Integer.parseInt(args[++i]); break;
                case "--top": options.top = Integer.parseInt(args[++i]); break;
                case "--timeout": options.timeoutSeconds = Integer.parseInt(args[++i]); break;
                case "--filter": options.filter = args[++i]; break;
                case "--config": configPath = args[++i]; break;
                case "--tree": showTree = true; break;
                case "--quiet": verbose = false; break;
                case "-h": case "--help": printHelp(); return;
                default:
                    if (query.length() > 0) query.append(' ');
                    query.append(args[i]);
            }
        }

        AppConfig cfg = new AppConfig(Paths.get(configPath));
        Facade.getInstance().setSearchApiClient(SearchApiClient.fromConfig(cfg));

        if (query.length() > 0) {
            runOnce(query.toString(), options);
            System.exit(0);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.println("郁見電影，漫遊四方 — Stage 4測試版（輸入關鍵字後按 Enter，輸入 q 離開）");
        while (true) {
            System.out.print("\n搜尋 > ");
            String line = in.readLine();
            if (line == null || line.trim().equalsIgnoreCase("q")) break;
            if (!line.isBlank()) runOnce(line.trim(), options);
        }
        System.exit(0);
    }

    private static void runOnce(String input, SearchOptions options) {
        try {
            SearchResult r = ViewControl.getInstance().onSearch(input, options,
                    msg -> { if (verbose) System.out.println("  · " + msg); });
            print(r);
        } catch (Exception e) {
            System.out.println("搜尋失敗：" + e.getMessage());
        }
    }

    private static void print(SearchResult r) {
        String line = "─".repeat(78);
        System.out.println(line);
        System.out.println("查詢：" + r.rawQuery() + "    實際搜尋：" + r.searchQuery());
        StringBuilder kw = new StringBuilder();
        for (Keyword k : r.keywords()) {
            if (kw.length() > 0) kw.append("、");
            kw.append(k.getName()).append("(").append(k.getCategory().getLabel())
                    .append(" +").append(trim(k.getWeight())).append(")");
        }
        System.out.println("查詢關鍵字：" + kw);
        System.out.printf("候選網站 %d 個，顯示前 %d 名，耗時 %.1f 秒%n",
                r.candidateCount(), r.ranked().size(), r.elapsedMillis() / 1000.0);
        System.out.println(line);

        int rank = 1;
        for (Tree t : r.ranked()) {
            Node root = t.getRoot();
            int orig = root.getOriginalRank();
            String move = orig == 0 ? "" : (orig > rank ? "↑" + (orig - rank) : orig < rank ? "↓" + (rank - orig) : "—");
            System.out.printf("#%-2d 總分 %7.1f   原排名 #%-2d %s   %d 個網頁%n",
                                       rank, Ranker.adjustedScore(t), orig, move, t.size());
            System.out.println("    " + Tree.shorten(root.getTitle().isEmpty() ? "(無標題)" : root.getTitle(), 70));
            System.out.println("    " + root.getUrl());
            if (!root.getSnippet().isEmpty()) System.out.println("    摘要：" + Tree.shorten(root.getSnippet(), 90));
            System.out.println("    命中：" + Tree.keywordSummary(root, 8)
                    + (root.isAccessible() ? "" : "  (僅以標題與摘要計分：" + root.getFetchError() + ")"));
            if (showTree) {
                System.out.println("    網站樹（[單頁分數 × 衰減係數]）：");
                for (String l : t.toTreeString(6).split("\n")) System.out.println("      " + l);
            }
            System.out.println();
            rank++;
        }
        if (r.ranked().isEmpty()) System.out.println("沒有符合的結果。");
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void printHelp() {
        System.out.println("用法：java -cp out Main [選項] [關鍵字]\n"
                + "  --limit N      從搜尋 API 取 N 個候選網站（預設 10）\n"
                + "  --children N   每頁最多展開 N 個子連結（預設 4）\n"
                + "  --depth N      子頁展開層數（預設 1）\n"
                + "  --top N        顯示前 N 名（預設 10）\n"
                + "  --filter 字詞  只保留包含該字詞的網站（例如 動畫、2024）\n"
                + "  --tree         顯示每個網站的子網頁樹與各節點關鍵字次數\n"
                + "  --timeout N    單一網頁下載逾時秒數（預設 8）\n"
                + "  --config 路徑  設定檔（預設 config.properties）\n"
                + "  --quiet        不顯示處理過程");
    }
}
