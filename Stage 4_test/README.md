# Stage 4：語意分析與擴展搜尋 (Semantic Analysis & Query Expansion)

本專案是一個基於純 Java 開發的客製化電影搜尋引擎。包含 **Stage 3：改善 Google 搜尋排名** 與 **Stage 4：語意分析與擴展搜尋**。
系統不只動態取得候選網頁，還具備「跨網域驗證」、「高容錯擦邊機制」，無論輸入任何關鍵字都能透過自動拆字與語意擴展，精準找出相關的電影作品。

## 🚀 執行方式

需要 Java 17 以上，不需要任何外部套件。

```bash
./run.sh 狗                            # 搜尋一次 (會自動觸發語意分析與擴展搜尋)
./run.sh --tree 桌墊                   # 展示擦邊關聯能力，並顯示子網頁樹與節點計分
./run.sh --limit 20 --filter 動畫 狗     # 取 20 個候選網站，只保留含「動畫」的
./run.sh                               # 互動模式，輸入 q 離開
./run.sh test                          # 離線測試（不需網路與金鑰）
./run.sh --help                        # 所有選項


⚙️ 設定搜尋 API複製 config.properties.example 為 config.properties，填入下列其中一組金鑰（或設成環境變數）：來源需要的設定Google Custom Search JSON APIGOOGLE_API_KEY、GOOGLE_CSE_IDBrave Search APIBRAVE_API_KEYSerpAPI（Google 結果）SERPAPI_KEY

Main → ViewControl.onSearch()
     → QueryProcessor.parse()           拆字＋查字典給權重（包含單字擦邊：桌墊 → 桌/墊）
     → Facade.distribute("S", …)
     → SearchKeywordCommand.execute()

==== 第 1 階段：初步搜尋與嚴格過濾 ====
1. QueryProcessor.buildSearchQuery()   沒有核心詞就補上「電影」再送出
2. performSearchPass()                 呼叫 API，平行下載並建立初步 Tree
3. 嚴格過濾：淘汰完全未命中「原查詢詞」的無效網頁（保留擦邊命中的網頁）。

==== 第 2 階段：語意分析 (Semantic Analysis) ====
4. SemanticAnalyzer.extractExtendedKeywords()
   - 擷取前 5 名網頁的標題與摘要。
   - 排除英數字與停用詞 (Stopwords)。
   - 盲切 2~4 字的 N-gram，計算 TF-IDF 詞頻。
   - 【跨網域驗證】：強制延伸詞必須出現在 2 個以上的「不同網域」，消滅單一網站的自嗨殘字。

==== 第 3 階段：擴展搜尋與重新計分 ====
5. 將發現的延伸關鍵字（類別為 EXTENDED，+3）加入字典。
6. performSearchPass()                 使用包含延伸詞的新查詢進行第二次 API 搜尋。
   - 透過 seenUrls 略過已下載的網址，避免重複。
7. 舊網頁重新計分：讓第 1 階段保留的舊網頁，利用包含新延伸詞的字典重新走訪並計算分數。

==== 第 4 階段：最終統整與排序 ====
8. 合併第 1 階段與第 3 階段的合格網站。
9. Ranker.rank() → filter() → topN()   根據最新總分數進行排序並輸出。
   - 分數計算：根頁分數 + Σ(子樹分數 × 0.5 衰減)
   - 網域加權：電影網站 (x2 +30)、新聞八卦農場 (x0.5)


📂 核心檔案說明檔案說明SemanticAnalyzer(Stage 4 新增) 語意分析器。負責 N-gram 萃取、TF-IDF 評估與跨網域 DF 驗證。SearchKeywordCommand(Stage 4 重構) 流程控制核心。加入新舊階段搜尋隔離、延伸詞擴充、舊樹重新計分以及擦邊過濾機制。KeywordCategory / Keyword關鍵字類別與權重。大幅調高使用者查詢詞 (QUERY) 的權重，並新增延伸詞 (EXTENDED) 類別。TermMatcher負責最長匹配計數與斷詞（「電影院」不會被重複算作「電影」）。SearchApiClient / Json呼叫搜尋 API，並透過自製的輕量化 Parser 解析 JSON。HtmlHandler / UrlFilter下載與解析網頁、網址過濾去重複、擋黑名單，遵守 robots.txt 規範。Ranker排序、篩選、取前 N 名。test/Stage3Test.java離線測試（本機假網站測試）。