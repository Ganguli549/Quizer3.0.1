package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

class SheetHeightBounds {
    var minHeightPx: Int = 0
    var targetHeightPx: Int = 0
    var isFixedHeight: Boolean = false
}

val LocalSheetHeightBounds = staticCompositionLocalOf { SheetHeightBounds() }

private class SheetLayoutMetrics {
    var measuredSheetHeightPx: Int = 0
    var latestChromeAndInsetPx: Float = 0f
    var isImeCurrentlyVisible: Boolean = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    maxHeightFraction: Float = 0.80f,
    onDismissRequest: () -> Unit,
    enableDynamicHeight: Boolean = true,
    fillMaxSheetHeight: Boolean = false,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hostView = LocalView.current

    val rootWindowInsets = androidx.core.view.ViewCompat.getRootWindowInsets(hostView)
    val rootStatusBarPx = rootWindowInsets
        ?.getInsetsIgnoringVisibility(
            androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )?.top ?: 0
    val rootNavBarPx = rootWindowInsets
        ?.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        ?.bottom ?: 0

    val composeStatusBarPx = WindowInsets.statusBars.getTop(density)
    val composeNavBarPx = WindowInsets.navigationBars.getBottom(density)

    val statusBarHeightDp = maxOf(
        with(density) { maxOf(rootStatusBarPx, composeStatusBarPx).toDp() },
        28.dp
    )
    val navBarHeightDp = with(density) { maxOf(rootNavBarPx, composeNavBarPx).toDp() }

    val screenHeightDp = configuration.screenHeightDp.dp
    val fullWindowHeightDp = with(density) {
        hostView.rootView.height.takeIf { it > 0 }?.toDp()
    } ?: (screenHeightDp + statusBarHeightDp + navBarHeightDp)
    val screenHeightPx = with(density) { fullWindowHeightDp.toPx().coerceAtLeast(1f) }

    val defaultExpandedFraction = maxHeightFraction.coerceIn(0.50f, 0.94f)
    val fullExpandedFraction = defaultExpandedFraction
    // Exact 50% middle screen height snap point
    val halfExpandedFraction = 0.50f
    val dismissThresholdFraction = 0.28f

    val sheetFractionAnim = remember(defaultExpandedFraction) {
        Animatable(defaultExpandedFraction)
    }

    var isDraggingHandle by remember { mutableStateOf(false) }
    var dragFraction by remember(defaultExpandedFraction) { mutableFloatStateOf(defaultExpandedFraction) }
    // When null, a dynamic sheet (like Sparkle AI) starts at 50% middle screen height and auto-expands
    // up to defaultExpandedFraction (90%) as content grows. Once the user drags or taps the handle to
    // adjust height (e.g. to 50% middle screen height or 90% expanded), userLockedFraction holds that
    // chosen height so the sheet stays at 50% (even with long chat history) or 90% (even with short chat).
    var userLockedFraction by remember { mutableStateOf<Float?>(null) }

    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val metrics = remember { SheetLayoutMetrics() }
    val heightBounds = remember { SheetHeightBounds() }

    // NestedScrollConnection that swallows any unconsumed vertical scroll/fling inside the sheet content
    // so scrolling upwards or downwards inside LazyColumn / verticalScroll / RichText NEVER drags or closes the sheet.
    val contentScrollGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                return Velocity(0f, available.y)
            }
        }
    }

    val view = LocalView.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun safeHideKeyboard() {
        try {
            if (view.windowToken != null) {
                keyboardController?.hide()
            }
        } catch (_: Exception) {}
        try {
            focusManager.clearFocus()
        } catch (_: Exception) {}
    }

    fun dismissSheetSmoothly() {
        coroutineScope.launch {
            safeHideKeyboard()
            try {
                sheetState.hide()
            } catch (_: Exception) {
            } finally {
                onDismissRequest()
            }
        }
    }

    fun snapSheetTo(fromFraction: Float, targetFraction: Float, lockToTarget: Boolean) {
        if (lockToTarget) {
            userLockedFraction = targetFraction
        }
        dragFraction = targetFraction
        coroutineScope.launch {
            if (abs(sheetFractionAnim.value - fromFraction) > 0.005f) {
                sheetFractionAnim.snapTo(fromFraction)
            }
            sheetFractionAnim.animateTo(
                targetValue = targetFraction,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    fun resolveEffectiveStartFraction(): Float {
        if (metrics.measuredSheetHeightPx <= 0) {
            return userLockedFraction ?: sheetFractionAnim.value
        }
        val totalVisiblePx = metrics.measuredSheetHeightPx.toFloat() + metrics.latestChromeAndInsetPx
        val actualVisibleFraction = (totalVisiblePx / screenHeightPx)
            .coerceIn(0.14f, fullExpandedFraction)
        return if (userLockedFraction != null || fillMaxSheetHeight) {
            sheetFractionAnim.value.coerceIn(0.14f, fullExpandedFraction)
        } else {
            minOf(sheetFractionAnim.value, actualVisibleFraction)
        }
    }

    fun finishHandleDrag(startFraction: Float, endFraction: Float, velocityY: Float) {
        val fastFlingPxPerSec = with(density) { 1050.dp.toPx() }
        val hardDismissFlingPxPerSec = with(density) { 2100.dp.toPx() }
        val draggedDownFraction = startFraction - endFraction

        if (!enableDynamicHeight) {
            if (draggedDownFraction > 0.10f || velocityY > fastFlingPxPerSec) {
                dismissSheetSmoothly()
            } else {
                snapSheetTo(endFraction, defaultExpandedFraction, lockToTarget = false)
            }
            return
        }

        val shouldDismiss =
            endFraction < minOf(dismissThresholdFraction, startFraction - 0.06f) ||
            (startFraction <= halfExpandedFraction + 0.04f && endFraction < halfExpandedFraction - 0.10f) ||
            velocityY > hardDismissFlingPxPerSec ||
            (velocityY > fastFlingPxPerSec && startFraction <= halfExpandedFraction + 0.05f && endFraction < halfExpandedFraction - 0.04f)

        when {
            shouldDismiss -> dismissSheetSmoothly()
            velocityY > fastFlingPxPerSec -> {
                // Fast downward fling from expanded state snaps cleanly to 50% middle screen height
                snapSheetTo(endFraction, halfExpandedFraction, lockToTarget = true)
            }
            velocityY < -fastFlingPxPerSec -> {
                val upwardTarget = if (endFraction > defaultExpandedFraction + 0.03f) {
                    fullExpandedFraction
                } else {
                    defaultExpandedFraction
                }
                snapSheetTo(endFraction, upwardTarget, lockToTarget = true)
            }
            else -> {
                val midPoint = (halfExpandedFraction + defaultExpandedFraction) * 0.5f
                val snapTarget = when {
                    endFraction < midPoint -> halfExpandedFraction
                    endFraction > (defaultExpandedFraction + fullExpandedFraction) * 0.5f -> fullExpandedFraction
                    else -> defaultExpandedFraction
                }
                snapSheetTo(endFraction, snapTarget, lockToTarget = true)
            }
        }
    }

    val pillWidth by animateDpAsState(
        targetValue = if (isDraggingHandle) 46.dp else 34.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillWidth"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (isDraggingHandle) 0.85f else 0.42f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "handlePillAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        // Disable ModalBottomSheet's internal body swipe so scrolling inner content never closes the sheet
        sheetGesturesEnabled = false,
        // Exclude WindowInsetsSides.Top so ModalBottomSheet NEVER adds status-bar height above the drag pill
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .union(WindowInsets.ime)
        },
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, bottom = 3.dp)
                    .onGloballyPositioned { handleCoords = it }
                    .pointerInput(enableDynamicHeight, defaultExpandedFraction, screenHeightPx) {
                        val touchSlopPx = viewConfiguration.touchSlop

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val initialRootY = handleCoords?.localToRoot(down.position)?.y ?: down.position.y
                            val startFraction = resolveEffectiveStartFraction()
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, Offset(0f, initialRootY))

                            var dragStarted = false
                            var lastHapticSnapZone = when {
                                startFraction < dismissThresholdFraction -> 0
                                startFraction < (halfExpandedFraction + defaultExpandedFraction) * 0.5f -> 1
                                else -> 2
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    val currentRootY = handleCoords?.localToRoot(change.position)?.y
                                        ?: (initialRootY + (change.position.y - down.position.y))
                                    val totalDeltaY = currentRootY - initialRootY

                                    if (!dragStarted && abs(totalDeltaY) > touchSlopPx * 0.4f) {
                                        dragStarted = true
                                        dragFraction = startFraction
                                        isDraggingHandle = true
                                        if (metrics.isImeCurrentlyVisible) {
                                            safeHideKeyboard()
                                        }
                                    }

                                    if (dragStarted) {
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, currentRootY))

                                        val deltaFraction = totalDeltaY / screenHeightPx
                                        val minAllowed = 0.12f
                                        val maxAllowed = if (enableDynamicHeight) fullExpandedFraction else defaultExpandedFraction
                                        val targetFraction = (startFraction - deltaFraction).coerceIn(minAllowed, maxAllowed)

                                        val currentZone = when {
                                            targetFraction < dismissThresholdFraction -> 0
                                            targetFraction < (halfExpandedFraction + defaultExpandedFraction) * 0.5f -> 1
                                            else -> 2
                                        }
                                        if (currentZone != lastHapticSnapZone) {
                                            lastHapticSnapZone = currentZone
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }

                                        dragFraction = targetFraction
                                    } else if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                    }
                                }
                            } finally {
                                isDraggingHandle = false
                            }

                            if (!dragStarted) {
                                if (enableDynamicHeight) {
                                    val current = resolveEffectiveStartFraction()
                                    val nextTarget = if (abs(current - halfExpandedFraction) < 0.08f) {
                                        defaultExpandedFraction
                                    } else {
                                        halfExpandedFraction
                                    }
                                    if (metrics.isImeCurrentlyVisible && nextTarget <= halfExpandedFraction + 0.05f) {
                                        safeHideKeyboard()
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    snapSheetTo(current, nextTarget, lockToTarget = true)
                                }
                            } else {
                                val velocityY = velocityTracker.calculateVelocity().y
                                finishHandleDrag(startFraction, dragFraction, velocityY)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(4.dp)
                        .background(
                            color = if (isDraggingHandle) {
                                MaterialTheme.colorScheme.primary.copy(alpha = pillAlpha)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pillAlpha)
                            },
                            shape = CircleShape
                        )
                )
            }
        },
        content = {
            val dialogView = LocalView.current
            LaunchedEffect(dialogView) {
                val window = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                if (window != null) {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }
            }

            val dialogInsets = androidx.core.view.ViewCompat.getRootWindowInsets(dialogView)
            val dialogStatusBarDp = with(density) {
                (dialogInsets?.getInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                        androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                )?.top ?: 0).toDp()
            }
            val dialogNavBarDp = with(density) {
                (dialogInsets?.getInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars()
                )?.bottom ?: 0).toDp()
            }

            val effectiveStatusBarDp = maxOf(statusBarHeightDp, dialogStatusBarDp, 28.dp)
            val baseNavBarDp = maxOf(navBarHeightDp, dialogNavBarDp)
            val headerDragZonePx = with(density) { 44.dp.toPx() }
            val imeInsets = WindowInsets.ime
            val navBarInsets = WindowInsets.navigationBars

            CompositionLocalProvider(
                LocalSheetHeightBounds provides heightBounds
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val effectiveStatusBarPx = effectiveStatusBarDp.roundToPx()
                            val effectiveNavBarPx = maxOf(
                                baseNavBarDp.roundToPx(),
                                navBarInsets.getBottom(this)
                            )
                            // Read animated IME inset directly in the Layout phase so it is 100%
                            // synchronized frame-for-frame with ModalBottomSheet's contentWindowInsets
                            // without triggering Composition-phase recompositions or Frame-0 jumps.
                            val effectiveImePx = imeInsets.getBottom(this)
                            val effectiveBottomInsetPx = maxOf(effectiveNavBarPx, effectiveImePx)
                            val unconsumedNavBarPx = (effectiveNavBarPx - effectiveImePx).coerceAtLeast(0)
                            val dragHandleHeightPx = 14.dp.roundToPx()

                            val activeFraction = if (isDraggingHandle) {
                                dragFraction
                            } else {
                                sheetFractionAnim.value
                            }

                            val maxTopFraction = maxOf(activeFraction, defaultExpandedFraction)
                            val minTopClearancePx = maxOf(
                                effectiveStatusBarPx + 18.dp.roundToPx(),
                                (screenHeightPx * (1f - maxTopFraction)).toInt()
                            )

                            val maxAllowedColumnHeightPx = (
                                screenHeightPx.toInt() - minTopClearancePx - effectiveBottomInsetPx - dragHandleHeightPx
                            ).coerceAtLeast(140.dp.roundToPx())

                            val desiredColumnHeightPx = (
                                (screenHeightPx * activeFraction).toInt() - effectiveNavBarPx - dragHandleHeightPx
                            ).coerceAtLeast(120.dp.roundToPx())

                            val halfColumnHeightPx = minOf(
                                ((screenHeightPx * halfExpandedFraction).toInt() - effectiveNavBarPx - dragHandleHeightPx)
                                    .coerceAtLeast(140.dp.roundToPx()),
                                maxAllowedColumnHeightPx
                            )

                            val targetHeightPx = minOf(desiredColumnHeightPx, maxAllowedColumnHeightPx)
                            val isFixedSheetHeight = fillMaxSheetHeight || isDraggingHandle || (userLockedFraction != null)
                            val minHeightPx = if (isFixedSheetHeight) {
                                targetHeightPx
                            } else {
                                minOf(halfColumnHeightPx, targetHeightPx)
                            }

                            heightBounds.minHeightPx = minHeightPx
                            heightBounds.targetHeightPx = targetHeightPx
                            heightBounds.isFixedHeight = isFixedSheetHeight

                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = minHeightPx.coerceAtMost(constraints.maxHeight),
                                    maxHeight = targetHeightPx.coerceAtMost(constraints.maxHeight)
                                )
                            )

                            metrics.measuredSheetHeightPx = placeable.height
                            metrics.isImeCurrentlyVisible = effectiveImePx > effectiveNavBarPx + 20.dp.roundToPx()
                            metrics.latestChromeAndInsetPx = (unconsumedNavBarPx + dragHandleHeightPx).toFloat()

                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                        .onGloballyPositioned { contentCoords = it }
                        // Allow dragging the sheet from the top 44.dp title/header bar right below the pill
                        // without interfering with taps on header buttons (menu, close, tabs).
                        .pointerInput(enableDynamicHeight, defaultExpandedFraction, screenHeightPx) {
                            val touchSlopPx = viewConfiguration.touchSlop

                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    pass = PointerEventPass.Initial,
                                    requireUnconsumed = false
                                )
                                if (down.position.y > headerDragZonePx) {
                                    return@awaitEachGesture
                                }

                                val initialRootY = contentCoords?.localToRoot(down.position)?.y ?: down.position.y
                                val startFraction = resolveEffectiveStartFraction()
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, Offset(0f, initialRootY))

                                var dragStarted = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                        if (!change.pressed) {
                                            if (dragStarted) change.consume()
                                            break
                                        }

                                        val currentRootY = contentCoords?.localToRoot(change.position)?.y
                                            ?: (initialRootY + (change.position.y - down.position.y))
                                        val totalDeltaY = currentRootY - initialRootY
                                        val totalDeltaX = change.position.x - down.position.x

                                        if (!dragStarted) {
                                            if (abs(totalDeltaY) > touchSlopPx && abs(totalDeltaY) > abs(totalDeltaX) * 1.2f) {
                                                dragStarted = true
                                                dragFraction = startFraction
                                                isDraggingHandle = true
                                                if (metrics.isImeCurrentlyVisible) {
                                                    safeHideKeyboard()
                                                }
                                            } else if (abs(totalDeltaX) > touchSlopPx) {
                                                break
                                            }
                                        }

                                        if (dragStarted) {
                                            change.consume()
                                            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, currentRootY))
                                            val deltaFraction = totalDeltaY / screenHeightPx
                                            val minAllowed = 0.12f
                                            val maxAllowed = if (enableDynamicHeight) fullExpandedFraction else defaultExpandedFraction
                                            dragFraction = (startFraction - deltaFraction).coerceIn(minAllowed, maxAllowed)
                                        }
                                    }
                                } finally {
                                    if (dragStarted) {
                                        isDraggingHandle = false
                                    }
                                }

                                if (dragStarted) {
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    finishHandleDrag(startFraction, dragFraction, velocityY)
                                }
                            }
                        }
                        .nestedScroll(contentScrollGuard)
                ) {
                    content()
                }
            }
        }
    )
}
