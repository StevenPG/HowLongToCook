package com.stevenpg.howlongtocook.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Microwave
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.ui.graphics.vector.ImageVector
import com.stevenpg.howlongtocook.data.model.MethodType
import com.stevenpg.howlongtocook.data.model.StepAction

val MethodType.icon: ImageVector
    get() = when (this) {
        MethodType.MICROWAVE -> Icons.Outlined.Microwave
        MethodType.OVEN -> Icons.Outlined.Kitchen
        MethodType.AIR_FRYER -> Icons.Outlined.Air
        MethodType.STOVETOP -> Icons.Outlined.SoupKitchen
    }

val StepAction.icon: ImageVector
    get() = when (this) {
        StepAction.COOK -> Icons.Outlined.Timer
        StepAction.STIR -> Icons.Outlined.Sync
        StepAction.FLIP -> Icons.Outlined.SwapVert
        StepAction.SHAKE -> Icons.Outlined.Vibration
        StepAction.STAND -> Icons.Outlined.HourglassBottom
        StepAction.VENT -> Icons.Outlined.ContentCut
        StepAction.REMOVE -> Icons.Outlined.Output
        StepAction.OTHER -> Icons.AutoMirrored.Outlined.Notes
    }
