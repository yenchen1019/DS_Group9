import java.util.*;

/** 維護所有候選網站樹的集合，交給 Ranker 排序。 */
public class BoManager {
    private final List<Tree> boPool = Collections.synchronizedList(new ArrayList<>());

    public void add(Tree tree) { boPool.add(tree); }

    public void clear() { boPool.clear(); }

    /** Command 呼叫 BoManager 取得所有網站樹 */
    public List<Tree> getBo() {
        synchronized (boPool) {
            return new ArrayList<>(boPool);
        }
    }
}
