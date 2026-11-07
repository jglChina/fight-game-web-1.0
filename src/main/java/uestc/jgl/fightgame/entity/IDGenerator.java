package uestc.jgl.fightgame.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * id 生成器（复制自控制台版，逻辑不变）
 */
public class IDGenerator {

    /** 已占用的 id 集合（内部是线程安全的） */
    private static final Set<String> OCCUPIED_IDS =
            Collections.synchronizedSet(new HashSet<>());

    private static final Random RANDOM = new Random();

    /** 格式前缀 */
    private static final String PREFIX = "heima";

    /** 最大重试次数，防止极端情况下死循环 */
    private static final int MAX_RETRY = 1000;

    private IDGenerator() {
        // 工具类，禁止实例化
    }

    /**
     * 生成一个全局唯一的新 id
     * 格式：heima + 5位数字，例如 heima12345
     */
    public static String next() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String id = PREFIX + String.format("%05d", RANDOM.nextInt(100000));
            if (OCCUPIED_IDS.add(id)) {
                return id;
            }
        }
        throw new IllegalStateException(
                "id 池耗尽！已无法生成唯一 id，请检查系统中是否已有超过 10 万个用户。");
    }

    /** 注册一个已存在的 id（例如从文件/数据库加载用户时调用） */
    public static void register(String id) {
        OCCUPIED_IDS.add(id);
    }

    /** 批量注册已存在的 id */
    public static void registerAll(Set<String> ids) {
        if (ids != null) {
            OCCUPIED_IDS.addAll(ids);
        }
    }

    /** 判断某个 id 是否已被占用 */
    public static boolean isOccupied(String id) {
        return OCCUPIED_IDS.contains(id);
    }

    /** 当前已注册的 id 数量（调试用） */
    public static int size() {
        return OCCUPIED_IDS.size();
    }
}
