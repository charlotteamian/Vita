package com.vita.healthtracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vita.healthtracker.R

enum class StatsRange(val labelRes: Int, val days: Int) {
    Day(R.string.stats_range_day, 1),
    Week(R.string.stats_range_week, 7),
    Month(R.string.stats_range_month, 30),
    Year(R.string.stats_range_year, 365),
    All(R.string.stats_range_all, Int.MAX_VALUE),
}

@Composable
fun RangeSelector(
    current: StatsRange,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = current == range,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsRange.entries.size),
            ) { Text(stringResource(range.labelRes)) }
        }
    }
}
