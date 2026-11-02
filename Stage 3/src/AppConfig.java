import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** 讀取設定：先讀 config.properties，環境變數優先（API 金鑰不要寫進程式碼）。 */
public class AppConfig {
    private final Properties props = new Properties();

    public AppConfig(Path file) {
        if (file != null && Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                System.err.println("讀取設定檔失敗：" + e.getMessage());
            }
        }
    }

    public String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env.trim();
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public String get(String key, String def) {
        String v = get(key);
        return v == null ? def : v;
    }
}
