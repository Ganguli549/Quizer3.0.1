package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.QuizHistoryEntity
import com.example.data.resolvedSourceTag
import com.example.ui.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: MainViewModel,
    onHome: () -> Unit,
    onBack: () -> Unit = onHome,
    onReview: () -> Unit,
    onPracticeWrong: () -> Unit,
    onNextBatch: () -> Unit = {}
) {
    val history by viewModel.history.collectAsState()
    val lastCompleted by viewModel.lastCompletedQuizResult.collectAsState()
    val practiceMistakesMap by viewModel.practiceMistakesResults.collectAsState()

    // Ensure the main result is never a practice_mistakes_for_* child row
    val lastResult = remember(lastCompleted, history) {
        val candidate = lastCompleted
        if (candidate != null && !candidate.status.startsWith("practice_mistakes_for_")) {
            candidate
        } else {
            history.firstOrNull { !it.status.startsWith("practice_mistakes_for_") } ?: candidate
        }
    }

    val practiceResult = remember(lastResult, practiceMistakesMap, history) {
        val parentId = lastResult?.historyId ?: return@remember null
        practiceMistakesMap[parentId]
            ?: history.firstOrNull { it.status == "practice_mistakes_for_$parentId" }
    }

    val sourceTag = remember(lastResult, viewModel.isLearnMode, viewModel.activeQuizSourceTag) {
        lastResult?.resolvedSourceTag()
            ?: if (viewModel.isLearnMode) "Learn" else viewModel.activeQuizSourceTag
    }
    val isHomeOrLearn = sourceTag == "Quiz" || sourceTag == "Learn"
    var isStartingNextBatch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Quiz Result",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (lastResult != null) {
                            Text(
                                text = lastResult.quizLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("result_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lastResult != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = sourceTag,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (lastResult != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Main Quiz Result Card + Stats
                        ResultCard(
                            viewModel = viewModel,
                            result = lastResult,
                            onReviewClick = {
                                viewModel.selectResultForReview(lastResult.historyId)
                                onReview()
                            }
                        )

                        // 2. Practice Mistakes Section (Below Main Quiz Result, 1 Attempt Only)
                        val mistakePoolCount = lastResult.wrong + lastResult.skipped
                        if (practiceResult != null) {
                            PracticeMistakesResultCard(
                                practiceResult = practiceResult,
                                onReviewPractice = {
                                    viewModel.selectResultForReview(practiceResult.historyId)
                                    onReview()
                                }
                            )
                        } else if (mistakePoolCount > 0) {
                            PracticeMistakesPromptCard(
                                mistakeCount = mistakePoolCount,
                                wrongCount = lastResult.wrong,
                                skippedCount = lastResult.skipped,
                                onStartPractice = {
                                    viewModel.practiceWrongQuiz(lastResult.historyId)
                                    onPracticeWrong()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading results...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Bottom Action Bar
                // For "Start Quiz" (Home) and Learn: Home Icon button on Left + "Next Batch" button on Right
                if (isHomeOrLearn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = {
                                viewModel.playButtonPress()
                                onHome()
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("return_home_icon_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Return Home",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (!isStartingNextBatch) {
                                    isStartingNextBatch = true
                                    viewModel.playButtonPress()
                                    viewModel.startNextBatchQuiz {
                                        isStartingNextBatch = false
                                        onNextBatch()
                                    }
                                }
                            },
                            enabled = !isStartingNextBatch && lastResult != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("next_batch_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isStartingNextBatch) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Preparing Next Batch...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Next Batch",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedIconButton(
                            onClick = {
                                viewModel.playButtonPress()
                                onHome()
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("return_home_icon_button"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Return Home",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.selectResultForReview(lastResult?.historyId)
                                onReview()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("review_questions_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Review Questions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultCard(
    viewModel: MainViewModel,
    result: QuizHistoryEntity,
    onReviewClick: (() -> Unit)? = null
) {
    val total = result.total.coerceAtLeast(1)
    val attempted = (result.correct + result.wrong).coerceAtLeast(0)
    val attemptRate = (attempted.toFloat() / total.toFloat()) * 100f
    val accuracyFraction = (result.accuracy / 100f).coerceIn(0f, 1f)
    val attemptFraction = (attempted.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val isLearnResult = result.status.endsWith("|Learn") || result.quizLabel.contains("Learn", ignoreCase = true)
    val perQuestionCorrectMark = if (isLearnResult) {
        1f
    } else {
        viewModel.currentExamSettings?.customCorrectMarks ?: viewModel.settingsManager.getCorrectMark(result.bookId)
    }
    val perQuestionWrongMark = if (isLearnResult) {
        0f
    } else {
        viewModel.currentExamSettings?.customWrongMarks ?: viewModel.settingsManager.getWrongMark(result.bookId)
    }
    val correctMarks = result.correct * perQuestionCorrectMark
    val wrongMarks = result.wrong * perQuestionWrongMark
    val maxPossibleScore = result.total * perQuestionCorrectMark

    val totalSeconds = (result.timeUsed / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)
    val avgPaceSec = if (attempted > 0) {
        (result.timeUsed.toFloat() / 1000f) / attempted.toFloat()
    } else if (result.total > 0) {
        (result.timeUsed.toFloat() / 1000f) / result.total.toFloat()
    } else 0f

    val correctColor = Color(0xFF16A34A)
    val wrongColor = MaterialTheme.colorScheme.error
    val skippedColor = MaterialTheme.colorScheme.outline

    val animatedAccuracy by animateFloatAsState(
        targetValue = accuracyFraction,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "accuracyAnim"
    )
    val animatedAttempt by animateFloatAsState(
        targetValue = attemptFraction,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "attemptAnim"
    )

    val performanceBadge = when {
        result.accuracy >= 85f -> "Excellent Mastery" to correctColor
        result.accuracy >= 65f -> "Strong Progress" to MaterialTheme.colorScheme.primary
        result.accuracy >= 45f -> "Keep Practicing" to Color(0xFFD97706)
        else -> "Needs Review" to wrongColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Hero Row: Dual Ring Gauge + Score & Performance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val primaryRingColor = performanceBadge.second
                    val secondaryRingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val outerStroke = 10.dp.toPx()
                        val innerStroke = 6.dp.toPx()
                        val outerDiameter = size.minDimension - outerStroke
                        val outerTopLeft = Offset(outerStroke / 2f, outerStroke / 2f)

                        // Outer track (Accuracy)
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = Size(outerDiameter, outerDiameter),
                            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = primaryRingColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedAccuracy,
                            useCenter = false,
                            topLeft = outerTopLeft,
                            size = Size(outerDiameter, outerDiameter),
                            style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                        )

                        // Inner track (Attempt rate)
                        val gap = outerStroke + 5.dp.toPx()
                        val innerDiameter = size.minDimension - gap * 2f
                        val innerTopLeft = Offset(gap, gap)
                        drawArc(
                            color = trackColor.copy(alpha = 0.6f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = Size(innerDiameter, innerDiameter),
                            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = secondaryRingColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedAttempt,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = Size(innerDiameter, innerDiameter),
                            style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${String.format(Locale.US, "%.0f", result.accuracy)}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Accuracy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = performanceBadge.second.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = performanceBadge.second,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = performanceBadge.first,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = performanceBadge.second
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatMarkValue(result.finalScore),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/ ${formatMarkValue(maxPossibleScore)} pts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Text(
                        text = "${result.correct} Correct  •  ${result.wrong} Wrong  •  ${result.skipped} Skipped",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stacked Visual Distribution Bar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (result.correct > 0) {
                        Box(
                            modifier = Modifier
                                .weight(result.correct.toFloat())
                                .fillMaxHeight()
                                .background(correctColor)
                        )
                    }
                    if (result.wrong > 0) {
                        Box(
                            modifier = Modifier
                                .weight(result.wrong.toFloat())
                                .fillMaxHeight()
                                .background(wrongColor)
                        )
                    }
                    if (result.skipped > 0) {
                        Box(
                            modifier = Modifier
                                .weight(result.skipped.toFloat())
                                .fillMaxHeight()
                                .background(skippedColor.copy(alpha = 0.45f))
                        )
                    }
                }

                // 3-Pillar Breakdown Row (Correct / Wrong / Skipped with marks)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutcomePillarChip(
                        modifier = Modifier.weight(1f),
                        label = "Correct",
                        count = result.correct,
                        marksDelta = "+${formatMarkValue(correctMarks)}",
                        color = correctColor,
                        icon = Icons.Default.CheckCircle
                    )
                    OutcomePillarChip(
                        modifier = Modifier.weight(1f),
                        label = "Wrong",
                        count = result.wrong,
                        marksDelta = if (wrongMarks > 0f) "-${formatMarkValue(wrongMarks)}" else "0.00",
                        color = wrongColor,
                        icon = Icons.Default.Close
                    )
                    OutcomePillarChip(
                        modifier = Modifier.weight(1f),
                        label = "Skipped",
                        count = result.skipped,
                        marksDelta = "0.00",
                        color = skippedColor,
                        icon = Icons.Default.RemoveCircleOutline
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 2x2 Telemetry Stats Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultStatMetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.TrendingUp,
                        label = "Attempt Rate",
                        value = "${String.format(Locale.US, "%.0f", attemptRate)}%",
                        subValue = "$attempted of ${result.total} Qs",
                        accent = MaterialTheme.colorScheme.primary
                    )
                    ResultStatMetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        label = "Total Time",
                        value = formattedTime,
                        subValue = "${totalSeconds}s elapsed",
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultStatMetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "Avg Pace",
                        value = "${String.format(Locale.US, "%.1f", avgPaceSec)}s / Q",
                        subValue = if (avgPaceSec <= 25f) "Fast pace" else if (avgPaceSec <= 50f) "Steady pace" else "Deep focus",
                        accent = MaterialTheme.colorScheme.tertiary
                    )
                    ResultStatMetricTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        label = "Marking Scheme",
                        value = "+${formatMarkValue(perQuestionCorrectMark)} / -${formatMarkValue(perQuestionWrongMark)}",
                        subValue = if (isLearnResult) "Learn Mode" else "Exam Scoring",
                        accent = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Embedded Review Questions button inside the Main Quiz Result Card
            if (onReviewClick != null) {
                FilledTonalButton(
                    onClick = onReviewClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("review_main_quiz_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Review Questions (${result.total})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeMistakesPromptCard(
    mistakeCount: Int,
    wrongCount: Int,
    skippedCount: Int,
    onStartPractice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Practice Mistakes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$wrongCount Wrong  •  $skippedCount Skipped",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "1 Attempt Only",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Button(
                onClick = onStartPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("practice_mistakes_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Practice Mistakes ($mistakeCount)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PracticeMistakesResultCard(
    practiceResult: QuizHistoryEntity,
    onReviewPractice: () -> Unit
) {
    val correctColor = Color(0xFF16A34A)
    val wrongColor = MaterialTheme.colorScheme.error
    val skippedColor = MaterialTheme.colorScheme.outline

    val totalSeconds = (practiceResult.timeUsed / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)
    val attempted = (practiceResult.correct + practiceResult.wrong).coerceAtLeast(1)
    val avgPaceSec = (practiceResult.timeUsed.toFloat() / 1000f) / attempted.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("practice_mistakes_result_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Practice Mistakes Result",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Recovered ${practiceResult.correct} of ${practiceResult.total} missed questions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Attempted (1/1)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Stacked Progress Bar for Practice Mistakes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (practiceResult.correct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(practiceResult.correct.toFloat())
                            .fillMaxHeight()
                            .background(correctColor)
                    )
                }
                if (practiceResult.wrong > 0) {
                    Box(
                        modifier = Modifier
                            .weight(practiceResult.wrong.toFloat())
                            .fillMaxHeight()
                            .background(wrongColor)
                    )
                }
                if (practiceResult.skipped > 0) {
                    Box(
                        modifier = Modifier
                            .weight(practiceResult.skipped.toFloat())
                            .fillMaxHeight()
                            .background(skippedColor.copy(alpha = 0.45f))
                    )
                }
            }

            // Key Stats Row for Practice Mistakes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutcomePillarChip(
                    modifier = Modifier.weight(1f),
                    label = "Recovered",
                    count = practiceResult.correct,
                    marksDelta = "${String.format(Locale.US, "%.0f", practiceResult.accuracy)}%",
                    color = correctColor,
                    icon = Icons.Default.CheckCircle
                )
                OutcomePillarChip(
                    modifier = Modifier.weight(1f),
                    label = "Still Wrong",
                    count = practiceResult.wrong,
                    marksDelta = formattedTime,
                    color = wrongColor,
                    icon = Icons.Default.Close
                )
                OutcomePillarChip(
                    modifier = Modifier.weight(1f),
                    label = "Skipped",
                    count = practiceResult.skipped,
                    marksDelta = "${String.format(Locale.US, "%.1f", avgPaceSec)}s/Q",
                    color = skippedColor,
                    icon = Icons.Default.RemoveCircleOutline
                )
            }

            OutlinedButton(
                onClick = onReviewPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("review_practice_mistakes_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Review Practice Mistakes (${practiceResult.total})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OutcomePillarChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    marksDelta: String,
    color: Color,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = marksDelta,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultStatMetricTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMarkValue(value: Float): String {
    val roundedInt = value.toInt()
    return if (kotlin.math.abs(value - roundedInt.toFloat()) < 0.005f) {
        roundedInt.toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}
