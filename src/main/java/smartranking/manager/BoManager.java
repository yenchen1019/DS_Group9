package smartranking.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import smartranking.model.Tree;

/**
 * 管理搜尋結果所有的網站樹與排序結果
 */
public class BoManager {
    private List<Tree> boPool;

    public BoManager() {
        this.boPool = new ArrayList<>();
    }

    public void clearPool() {
        this.boPool.clear();
    }

    public void addTree(Tree tree) {
        this.boPool.add(tree);
    }

    /**
     * 重新計算所有 Tree 的分數並依分數高低排序
     */
    public List<Tree> rankSites() {
        for (Tree tree : boPool) {
            tree.calculateTotalScore();
        }
        Collections.sort(boPool); // 依據 Comparable<Tree> 降序排序
        return boPool;
    }

    public List<Tree> getBoPool() {
        return boPool;
    }
}