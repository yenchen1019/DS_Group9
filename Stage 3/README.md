# Stage 3：改善 Google 搜尋排名（Refine the Rank of Google）

只輸入搜尋關鍵字，不預先指定 URL：程式透過搜尋 API 動態取得候選網頁，再套用 Stage 2 的網站排名（樹狀結構 + 每層 × 0.5）重新排序。

## 執行

需要 Java 17 以上，不需要任何外部套件。

```bash
./run.sh 狗                            # 搜尋一次
./run.sh --tree 有狗的動畫電影           # 一併顯示每個網站的子網頁樹與各節點關鍵字次數
./run.sh --limit 20 --filter 動畫 狗     # 取 20 個候選網站，只保留含「動畫」的
./run.sh                               # 互動模式，輸入 q 離開
./run.sh test                          # 離線測試（不需網路與金鑰）
./run.sh --help                        # 所有選項
```

Windows 沒有 bash 時：
```
javac -encoding UTF-8 -d out src/*.java
java -cp out Main 狗
```

## 設定搜尋 API

複製 `config.properties.example` 為 `config.properties`，填入下列其中一組金鑰（或設成環境變數）：

| 來源 | 需要的設定 |
|---|---|
| Google Custom Search JSON API | `GOOGLE_API_KEY`、`GOOGLE_CSE_ID` |
| Brave Search API | `BRAVE_API_KEY` |
| SerpAPI（Google 結果） | `SERPAPI_KEY` |

都沒設定時，程式會讀 `seeds.txt` 裡的網址當作「搜尋結果」，方便沒有金鑰時測試。
我們不直接爬 Google 搜尋結果頁，因為違反使用條款，也會被機器人驗證擋下。

## 流程

```
Main → ViewControl.onSearch()
     → QueryProcessor.parse()           拆字＋查字典給權重（「有狗的動畫電影」→ 狗/動畫/電影）
     → Facade.distribute("S", …)
     → SearchKeywordCommand.execute()
         1. QueryProcessor.buildSearchQuery()   沒有核心詞就補上「電影」再送出
         2. SearchApiClient.search()            取得候選網頁（標題、網址、摘要、原始名次）
         3. UrlFilter                           正規化網址、去重複、擋黑名單/檔案、遵守 robots.txt
         4. 每個候選網頁建一棵 Tree（平行處理）
              Root = 搜尋結果頁；子節點 = 同網站的子連結（連結文字含關鍵字者優先）
              Node.buildKeywords() → calculateSum()
              Tree.computeScore()  後序走訪：根頁分數 + Σ(子樹分數 × 0.5)
         5. Ranker.rank() → filter() → topN()
```

- 計分用的詞 = 期中報告 2.1 的 11 類加分詞 + 使用者輸入但不在字典中的詞（QUERY，+5）。
- 計數採「最長匹配、不重疊」：「電影院」不會再多算一次「電影」。
- 無法讀取的網頁（逾時、被擋、robots.txt 不允許）只用標題＋摘要計分。
- 輸出包含新排名、原搜尋排名與名次變化（↑↓），方便之後算 Precision@5 和原始排名比較。

## 檔案

| 檔案 | 說明 |
|---|---|
| `KeywordCategory` / `Keyword` / `KeywordDictionary` | 關鍵字類別、權重、字典 |
| `TermMatcher` | 最長匹配計數與斷詞 |
| `QueryProcessor` | 查詢前處理 |
| `SearchApiClient` / `Json` / `AppConfig` | 呼叫搜尋 API、解析 JSON、讀設定 |
| `HtmlHandler` / `UrlFilter` | 下載與解析網頁、網址過濾 |
| `Node` / `Tree` / `BoManager` | 網頁、網站樹、所有網站樹 |
| `Ranker` | 排序、篩選、取前 N 名 |
| `AbstractCommand` / `SearchKeywordCommand` / `Facade` / `ViewControl` | 流程控制 |
| `Main` | 命令列介面（Stage 5 再換成 Swing） |
| `test/Stage3Test.java` | 離線測試（本機假網站） |
