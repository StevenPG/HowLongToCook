package com.stevenpg.howlongtocook.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.model.CookingMethod
import com.stevenpg.howlongtocook.data.model.Step
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.ui.components.IconBadge
import com.stevenpg.howlongtocook.ui.components.LocalTemperatureUnit
import com.stevenpg.howlongtocook.ui.components.Pill
import com.stevenpg.howlongtocook.ui.components.icon
import com.stevenpg.howlongtocook.util.displayIn
import com.stevenpg.howlongtocook.util.formatDuration
import com.stevenpg.howlongtocook.util.label
import com.stevenpg.howlongtocook.util.title

/** Card showing one cooking method: settings summary and numbered steps. */
@Composable
fun MethodCard(method: CookingMethod, modifier: Modifier = Modifier) {
    val unit = LocalTemperatureUnit.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(method.type.icon, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(method.type.label(), style = MaterialTheme.typography.titleLarge)
                    if (method.totalSeconds > 0) {
                        Text(
                            stringResource(R.string.method_total, formatDuration(method.totalSeconds)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val pills = buildList<@Composable () -> Unit> {
                method.temperature?.let { t -> add { Pill(t.displayIn(unit), icon = Icons.Outlined.Thermostat) } }
                method.power?.let { p -> add { Pill(stringResource(R.string.method_power, p), icon = Icons.Outlined.Bolt) } }
                if (method.preheat) {
                    add {
                        Pill(
                            stringResource(R.string.method_preheat),
                            icon = Icons.Outlined.LocalFireDepartment,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pills.forEach { it() } }
            }
            if (method.steps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                method.steps.forEachIndexed { i, step -> StepRow(i + 1, step) }
            }
            method.notes?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: Step) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(step.action.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(step.title(), style = MaterialTheme.typography.titleMedium)
            }
            if (!step.note.isNullOrBlank() && step.action != StepAction.OTHER) {
                Text(step.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
