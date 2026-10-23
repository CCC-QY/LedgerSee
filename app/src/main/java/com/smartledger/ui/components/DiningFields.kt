package com.smartledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.data.model.FoodCategory
import com.smartledger.data.model.MealType
import com.smartledger.ui.theme.SmartLedgerColors

/**
 * 餐饮明细输入：餐次 + 吃了什么 + 品类。
 *
 * 只在分类为「餐饮」且是支出时展示，避免给其他账单增加噪声。
 * 菜名是选填的，不强迫用户每顿都写。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiningFields(
    mealType: String?,
    onMealTypeChange: (String) -> Unit,
    foodName: String,
    onFoodNameChange: (String) -> Unit,
    foodCategory: String?,
    onFoodCategoryChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "餐饮明细",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = SmartLedgerColors.fgSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "选填",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.fgSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MealType.entries.forEach { meal ->
                val selected = mealType == meal.key
                FilterChip(
                    selected = selected,
                    onClick = { onMealTypeChange(meal.key) },
                    label = { Text(meal.label, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SmartLedgerColors.accent,
                        selectedLabelColor = Color.White,
                        containerColor = SmartLedgerColors.surfaceHover,
                        labelColor = SmartLedgerColors.fgSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = foodName,
            onValueChange = { onFoodNameChange(it.take(40)) },
            label = { Text("吃了什么（选填）") },
            placeholder = { Text("如：牛肉面、麻辣烫") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SmartLedgerColors.accent,
                focusedLabelColor = SmartLedgerColors.accent,
                cursorColor = SmartLedgerColors.accent,
                focusedTextColor = SmartLedgerColors.fg,
                unfocusedTextColor = SmartLedgerColors.fg,
                unfocusedBorderColor = SmartLedgerColors.border
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "品类（用于统计）",
            style = MaterialTheme.typography.labelSmall,
            color = SmartLedgerColors.fgSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FoodCategory.selectable.forEach { category ->
                val selected = foodCategory == category.key
                FilterChip(
                    selected = selected,
                    onClick = {
                        onFoodCategoryChange(if (selected) null else category.key)
                    },
                    label = { Text(category.label, fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SmartLedgerColors.accentDim,
                        selectedLabelColor = SmartLedgerColors.accent,
                        containerColor = SmartLedgerColors.surfaceHover,
                        labelColor = SmartLedgerColors.fgSecondary
                    )
                )
            }
        }
    }
}
