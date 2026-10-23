package com.smartledger.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DiningDetailsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, 23, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `meal period boundaries`() {
        assertEquals(MealType.BREAKFAST, MealType.forTime(at(5, 0), zone))
        assertEquals(MealType.BREAKFAST, MealType.forTime(at(10, 29), zone))
        assertEquals(MealType.LUNCH, MealType.forTime(at(10, 30), zone))
        assertEquals(MealType.LUNCH, MealType.forTime(at(14, 29), zone))
        assertEquals(MealType.TEATIME, MealType.forTime(at(14, 30), zone))
        assertEquals(MealType.TEATIME, MealType.forTime(at(16, 59), zone))
        assertEquals(MealType.DINNER, MealType.forTime(at(17, 0), zone))
        assertEquals(MealType.DINNER, MealType.forTime(at(20, 59), zone))
        assertEquals(MealType.LATE_NIGHT, MealType.forTime(at(21, 0), zone))
        assertEquals(MealType.LATE_NIGHT, MealType.forTime(at(4, 59), zone))
    }

    @Test
    fun `meal labels round trip`() {
        assertEquals(MealType.LUNCH, MealType.fromKey("lunch"))
        assertEquals(MealType.LUNCH, MealType.fromLabel("午餐"))
        assertNull(MealType.fromLabel("午饭"))
    }

    @Test
    fun `food category guesses from merchant`() {
        assertEquals(FoodCategory.COFFEE, DiningDetails.guessFoodCategory("瑞幸咖啡", null))
        assertEquals(FoodCategory.HOTPOT, DiningDetails.guessFoodCategory("海底捞火锅", null))
        assertEquals(FoodCategory.NOODLE, DiningDetails.guessFoodCategory("兰州拉面", null))
        assertEquals(FoodCategory.FAST_FOOD, DiningDetails.guessFoodCategory("麦当劳", null))
        assertEquals(FoodCategory.MILK_TEA, DiningDetails.guessFoodCategory("面包新语", null))
        assertEquals(FoodCategory.WESTERN, DiningDetails.guessFoodCategory("必胜客披萨", null))
    }

    @Test
    fun `food category falls back to note and unknown is null`() {
        assertEquals(FoodCategory.MALATANG, DiningDetails.guessFoodCategory("张亮", "麻辣烫"))
        assertNull(DiningDetails.guessFoodCategory("美团外卖", null))
        assertNull(DiningDetails.guessFoodCategory(null, null))
    }

    @Test
    fun `only expense dining gets auto details`() {
        val lunchTime = at(12, 0)
        val auto = DiningDetails.autoFor("麦当劳", null, lunchTime, isDining = true)
        assertEquals("lunch", auto?.mealType)
        assertEquals("fast_food", auto?.foodCategory)
        assertNull(auto?.foodName)

        assertNull(DiningDetails.autoFor("麦当劳", null, lunchTime, isDining = false))
    }

    @Test
    fun `dining check requires expense`() {
        assertEquals(true, DiningDetails.isDining("餐饮", "expense"))
        assertEquals(false, DiningDetails.isDining("餐饮", "income"))
        assertEquals(false, DiningDetails.isDining("交通", "expense"))
        assertEquals(false, DiningDetails.isDining(null, "expense"))
    }
}
