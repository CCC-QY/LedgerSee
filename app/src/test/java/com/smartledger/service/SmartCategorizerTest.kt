package com.smartledger.service

import com.smartledger.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartCategorizerTest {

    private val categories = listOf(
        Category(id = 1, name = "餐饮", icon = "Restaurant", color = 0, type = "expense"),
        Category(id = 2, name = "交通", icon = "Bus", color = 0, type = "expense"),
        Category(id = 3, name = "其他", icon = "More", color = 0, type = "expense"),
        Category(id = 4, name = "其他", icon = "More", color = 0, type = "income")
    )

    @Test
    fun `restaurant names fall back to dining`() {
        // 这些名字不在原有餐饮关键词表里，但能命中 DiningDetails 的品类词库
        assertEquals(
            1L,
            SmartCategorizer.categorize("老王面馆", null, null, categories, "expense")
        )
        assertEquals(
            1L,
            SmartCategorizer.categorize("张亮麻辣烫", null, null, categories, "expense")
        )
        assertEquals(
            1L,
            SmartCategorizer.categorize("一番寿司", null, null, categories, "expense")
        )
        assertEquals(
            1L,
            SmartCategorizer.categorize("正新炸鸡", null, null, categories, "expense")
        )
    }

    @Test
    fun `existing category rules still win`() {
        assertEquals(
            2L,
            SmartCategorizer.categorize("滴滴出行", null, null, categories, "expense")
        )
        assertEquals(
            1L,
            SmartCategorizer.categorize("海底捞", null, null, categories, "expense")
        )
    }

    @Test
    fun `unmatched expense still falls back to other`() {
        assertEquals(
            3L,
            SmartCategorizer.categorize("某某科技有限公司", null, null, categories, "expense")
        )
    }

    @Test
    fun `income does not use the dining fallback`() {
        assertEquals(
            4L,
            SmartCategorizer.categorize("老王面馆", null, null, categories, "income")
        )
    }
}
