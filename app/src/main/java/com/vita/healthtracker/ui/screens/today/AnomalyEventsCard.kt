package com.vita.healthtracker.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.AnomalyEvent
import com.vita.healthtracker.domain.AnomalySeverity
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaError
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import com.vita.healthtracker.ui.theme.VitaPrimary

/** 只在连续变化比较明显时出现；没有内容时不占位。 */
@Composable
fun AnomalyEventsCard(
    events: List<AnomalyEvent>,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return
    val strongest = events.first().severity
    val accent = severityColor(strongest)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.17f), Color(0xFF0E1320))),
                    MaterialTheme.shapes.large,
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "最近几天",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
                Text(
                    text = "这些变化来自连续几天的记录，仅供日常参考",
                    style = MaterialTheme.typography.bodySmall,
                    color = VitaOnSurfaceMuted,
                )
            }
            events.forEach { event -> EventBlock(event) }
        }
    }
}

@Composable
private fun EventBlock(event: AnomalyEvent) {
    val accent = severityColor(event.severity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = event.severity.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = event.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        event.evidence.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("·", color = accent, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = event.guidance,
            style = MaterialTheme.typography.bodySmall,
            color = accent.copy(alpha = 0.92f),
        )
    }
}

private fun severityColor(severity: AnomalySeverity): Color = when (severity) {
    AnomalySeverity.ALERT -> VitaError
    AnomalySeverity.WATCH -> VitaActive
    AnomalySeverity.NOTICE -> VitaPrimary
}
