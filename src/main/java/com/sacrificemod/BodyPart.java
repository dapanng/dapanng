package com.sacrificemod;

/**
 * 身体部位枚举
 *
 * 定义了献祭玩法中可以献祭/赎回的5个身体部位。
 * 每个部位有唯一的字符串ID和中文显示名称。
 *
 * 献祭不同部位会触发不同的效果（由SacrificeGameplay处理），
 * 赎回部位需要消耗钻石。
 */
public enum BodyPart {
    /** 头部 - 献祭后影响视野等 */
    HEAD("head"),
    /** 左手 - 献祭后影响左手的交互能力 */
    LEFT_HAND("left_hand"),
    /** 右手 - 献祭后影响右手的交互能力 */
    RIGHT_HAND("right_hand"),
    /** 左腿 - 献祭后影响移动速度 */
    LEFT_LEG("left_leg"),
    /** 右腿 - 献祭后影响移动速度 */
    RIGHT_LEG("right_leg");

    /** 身体部位的字符串标识符，用于序列化和网络传输 */
    private final String id;

    /**
     * 枚举构造函数
     * @param id 身体部位的字符串标识符
     */
    BodyPart(String id) {
        this.id = id;
    }

    /**
     * 获取身体部位的字符串标识符
     * @return 标识符字符串，如"head"、"left_hand"等
     */
    public String getId() {
        return id;
    }

    /**
     * 根据字符串ID查找对应的BodyPart枚举值
     *
     * @param id 要查找的字符串ID
     * @return 对应的BodyPart枚举值，未找到时返回null
     */
    public static BodyPart fromId(String id) {
        for (BodyPart part : values()) {
            if (part.id.equals(id)) return part;
        }
        return null;
    }

    /**
     * 获取身体部位的中文显示名称
     *
     * 用于在UI界面和聊天消息中显示。
     *
     * @return 中文显示名称，如"头部"、"左手"等
     */
    public String getDisplayName() {
        return switch (this) {
            case HEAD -> "头部";
            case LEFT_HAND -> "左手";
            case RIGHT_HAND -> "右手";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }
}
