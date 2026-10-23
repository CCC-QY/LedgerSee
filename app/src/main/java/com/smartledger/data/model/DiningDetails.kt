package com.smartledger.data.model

import java.time.Instant
import java.time.ZoneId

/**
 * 一顿饭发生在什么时候。
 *
 * 这是时间事实，不是分类：同一家店中午去和晚上去，餐次不同。
 * 数据库存稳定的 key，界面展示中文 label，方便以后调整文案或加语言。
 */
enum class MealType(val key: String, val label: String) {
    BREAKFAST("breakfast", "早餐"),
    LUNCH("lunch", "午餐"),
    TEATIME("teatime", "下午茶"),
    DINNER("dinner", "晚餐"),
    LATE_NIGHT("late_night", "夜宵");

    companion object {
        fun fromKey(key: String?): MealType? = entries.firstOrNull { it.key == key }

        /** 兼容 CSV 里写 key 或中文 label 两种情况 */
        fun fromLabel(label: String?): MealType? =
            entries.firstOrNull { it.key == label || it.label == label }

        /**
         * 按本地时间推断餐次。边界：
         * 05:00–10:29 早餐｜10:30–14:29 午餐｜14:30–16:59 下午茶
         * 17:00–20:59 晚餐｜其余（21:00–04:59）夜宵
         */
        fun forTime(time: Long, zone: ZoneId = ZoneId.systemDefault()): MealType {
            val local = Instant.ofEpochMilli(time).atZone(zone)
            val minutes = local.hour * 60 + local.minute
            return when (minutes) {
                in (5 * 60)..(10 * 60 + 29) -> BREAKFAST
                in (10 * 60 + 30)..(14 * 60 + 29) -> LUNCH
                in (14 * 60 + 30)..(16 * 60 + 59) -> TEATIME
                in (17 * 60)..(20 * 60 + 59) -> DINNER
                else -> LATE_NIGHT
            }
        }
    }
}

/**
 * 食物的结构化品类，用于统计聚合。
 *
 * “吃了什么”是开放文本，不适合做分类；这里只保留可聚合的粗粒度。
 */
enum class FoodCategory(val key: String, val label: String) {
    FAST_FOOD("fast_food", "快餐"),
    NOODLE("noodle", "面食"),
    RICE("rice", "米饭简餐"),
    HOTPOT("hotpot", "火锅"),
    BBQ("bbq", "烧烤"),
    MALATANG("malatang", "麻辣烫"),
    JAPANESE("japanese", "日料"),
    WESTERN("western", "西餐"),
    CHINESE("chinese", "家常菜"),
    COFFEE("coffee", "咖啡"),
    MILK_TEA("milk_tea", "奶茶甜品"),
    OTHER("other", "其他");

    companion object {
        fun fromKey(key: String?): FoodCategory? = entries.firstOrNull { it.key == key }

        /** 兼容 CSV 里写 key 或中文 label 两种情况 */
        fun fromLabel(label: String?): FoodCategory? =
            entries.firstOrNull { it.key == label || it.label == label }

        /** 给 UI 的选择顺序：常用在前 */
        val selectable: List<FoodCategory> = listOf(
            FAST_FOOD, NOODLE, RICE, HOTPOT, BBQ, MALATANG,
            JAPANESE, WESTERN, CHINESE, COFFEE, MILK_TEA, OTHER
        )
    }
}

/**
 * 餐饮明细的领域逻辑：只做纯计算，不依赖 Android，便于单测。
 *
 * 证据分级：
 * - 餐次：时间事实，自动填，可信度高；
 * - 品类：按商户/备注关键词推断，中等可信，允许改；
 * - 菜名：只接受用户输入/OCR 原文，绝不从品牌名硬猜“巨无霸”。
 */
object DiningDetails {

    const val DINING_CATEGORY_NAME = "餐饮"

    /** 自动记账时一次性算好的三个字段；非餐饮返回 null。 */
    data class Auto(
        val mealType: String?,
        val foodCategory: String?,
        val foodName: String? = null
    )

    fun isDining(categoryName: String?, type: String = "expense"): Boolean =
        type == "expense" && categoryName == DINING_CATEGORY_NAME

    fun autoFor(
        merchant: String?,
        note: String?,
        time: Long,
        isDining: Boolean
    ): Auto? {
        if (!isDining) return null
        return Auto(
            mealType = MealType.forTime(time).key,
            foodCategory = guessFoodCategory(merchant, note)?.key,
            foodName = null
        )
    }

    fun mealLabel(key: String?): String? = MealType.fromKey(key)?.label

    fun foodCategoryLabel(key: String?): String? = FoodCategory.fromKey(key)?.label

    /**
     * 按商户/备注里的关键词猜一个粗粒度品类。
     *
     * 规则刻意保持简单、可解释；匹配不到就返回 null，不硬凑。
     * 注意顺序：更具体的规则放前面（例如“面包”要先于面食类规则）。
     */
    fun guessFoodCategory(merchant: String?, note: String?): FoodCategory? {
        val text = listOfNotNull(merchant, note)
            .joinToString(" ")
            .trim()
            .lowercase()
        if (text.isBlank()) return null

        for ((category, keywords) in foodRules) {
            if (keywords.any { text.contains(it) }) return category
        }
        return null
    }

    private val foodRules: List<Pair<FoodCategory, List<String>>> = listOf(
        FoodCategory.COFFEE to listOf(
            "咖啡", "星巴克", "瑞幸", "库迪", "manner", "costa", "tims"
        ),
        FoodCategory.MILK_TEA to listOf(
            "奶茶", "喜茶", "奈雪", "蜜雪", "茶百道", "古茗", "沪上阿姨",
            "霸王茶姬", "甜品", "蛋糕", "面包", "烘焙", "冰淇淋", "糖水"
        ),
        FoodCategory.HOTPOT to listOf(
            "火锅", "海底捞", "呷哺", "涮肉", "涮锅", "打边炉"
        ),
        FoodCategory.BBQ to listOf(
            "烧烤", "烤肉", "烤串", "串吧", "bbq"
        ),
        FoodCategory.MALATANG to listOf(
            "麻辣烫", "冒菜", "串串", "麻辣香锅"
        ),
        FoodCategory.JAPANESE to listOf(
            "寿司", "日料", "居酒屋", "刺身", "鳗鱼", "天妇罗"
        ),
        FoodCategory.WESTERN to listOf(
            "披萨", "比萨", "意面", "意大利面", "牛排", "西餐", "汉堡"
        ),
        FoodCategory.FAST_FOOD to listOf(
            "麦当劳", "肯德基", "kfc", "汉堡王", "德克士", "华莱士", "塔斯汀",
            "炸鸡", "薯条", "快餐", "便当", "盒饭", "沙县", "黄焖鸡"
        ),
        FoodCategory.NOODLE to listOf(
            "拉面", "牛肉面", "面馆", "米线", "米粉", "河粉", "炒面",
            "拌面", "刀削面", "馄饨", "饺子", "包子", "面条"
        ),
        FoodCategory.RICE to listOf(
            "盖饭", "炒饭", "煲仔饭", "米饭", "简餐"
        ),
        FoodCategory.CHINESE to listOf(
            "家常菜", "小炒", "川菜", "湘菜", "粤菜", "东北菜",
            "饭店", "餐厅", "食堂", "小吃"
        )
    )
}
