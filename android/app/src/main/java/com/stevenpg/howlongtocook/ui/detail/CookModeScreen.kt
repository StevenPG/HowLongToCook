package com.stevenpg.howlongtocook.ui.detail

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stevenpg.howlongtocook.R
import com.stevenpg.howlongtocook.data.model.StepAction
import com.stevenpg.howlongtocook.ui.components.LocalTemperatureUnit
import com.stevenpg.howlongtocook.util.displayIn
import com.stevenpg.howlongtocook.util.label
import com.stevenpg.howlongtocook.util.title

/** Large-type, screen-on view of one method's steps; tap a step to tick it off. */
@Composable
fun CookModeScreen(
    methodIndex: Int,
    onClose: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val method = state.product?.methods?.getOrNull(methodIndex)
    val unit = LocalTemperatureUnit.current
    val done = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf<Int>()
    }
    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.product?.name ?: "", style = MaterialTheme.typography.titleLarge)
                        if (method != null) {
                            val subtitle = listOfNotNull(
                                method.type.label(),
                                method.temperature?.displayIn(unit),
                                method.power?.let { stringResource(R.string.method_power, it) },
                                if (method.preheat) stringResource(R.string.method_preheat) else null,
                            ).joinToString(" · ")
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cook_mode_exit)) }
                },
            )
        },
    ) { padding ->
        if (method == null) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            itemsIndexed(method.steps) { i, step ->
                val isDone = i in done
                val container by animateColorAsState(
                    if (isDone) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.primaryContainer,
                    label = "step",
                )
                Card(
                    onClick = { if (isDone) done.remove(i) else done.add(i) },
                    colors = CardDefaults.cardColors(containerColor = container),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isDone) stringResource(R.string.cook_step_done) else stringResource(R.string.cook_step_not_done),
                            modifier = Modifier.size(36.dp),
                            tint = if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(
                                stringResource(R.string.numbered_step, i + 1, step.title()),
                                style = MaterialTheme.typography.displaySmall,
                                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (!step.note.isNullOrBlank() && step.action != StepAction.OTHER) {
                                Text(step.note, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            method.notes?.let { item { Text(it, style = MaterialTheme.typography.titleMedium) } }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
