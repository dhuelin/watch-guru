package dev.dhuelin.watchguru.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dhuelin.watchguru.R
import dev.dhuelin.watchguru.api.models.Bucket
import dev.dhuelin.watchguru.api.models.WatchStats
import dev.dhuelin.watchguru.ui.components.ErrorView
import dev.dhuelin.watchguru.ui.components.FullScreenMessage
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.components.contentOrNull

/**
 * What a person has watched, as figures rather than a list.
 *
 * Everything here is computed server-side over the append-only event log, so
 * the screen is a rendering of a single answer rather than an arithmetic of
 * its own -- which is what keeps the hero figure and the breakdowns from
 * disagreeing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.stats.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_stats)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            is UiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> ErrorView(
                failure = current.failure,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> {
                val stats = current.contentOrNull()
                if (stats == null) {
                    FullScreenMessage(
                        icon = Icons.Outlined.BarChart,
                        message = stringResource(R.string.empty_stats),
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    StatsContent(
                        stats = stats,
                        period = period,
                        onPeriod = viewModel::setPeriod,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: WatchStats,
    period: StatsPeriod,
    onPeriod: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        PeriodSelector(period = period, onPeriod = onPeriod)

        // Nothing watched in this period is a real answer, not an error, and
        // it must not look like a broken screen full of zeroes.
        if (stats.totalMinutes == 0L) {
            Text(
                text = stringResource(R.string.empty_stats_period),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            Streaks(stats, modifier = Modifier.padding(top = 24.dp))
            return@Column
        }

        HeroTime(stats, modifier = Modifier.padding(top = 24.dp))
        Streaks(stats, modifier = Modifier.padding(top = 24.dp))

        // One month of data is not a shape, and drawing a single bar would be
        // a chart pretending to be one.
        if (stats.byMonth.size > 1) {
            SectionHeading(stringResource(R.string.stats_by_month), Modifier.padding(top = 32.dp))
            MonthlyChart(stats.byMonth, modifier = Modifier.padding(top = 8.dp))
        }

        Breakdown(stringResource(R.string.stats_top_titles), stats.topTitles)
        Breakdown(stringResource(R.string.stats_by_genre), stats.byGenre)
        Breakdown(stringResource(R.string.stats_by_service), stats.byService)
    }
}

@Composable
private fun PeriodSelector(period: StatsPeriod, onPeriod: (StatsPeriod) -> Unit) {
    // One filter row above everything it scopes, rather than a control per
    // section: every figure below moves together, and the row says so.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsPeriod.entries.forEach { entry ->
            FilterChip(
                selected = entry == period,
                onClick = { onPeriod(entry) },
                label = { Text(stringResource(entry.label())) },
            )
        }
    }
}

/**
 * The headline: how much of their life this is.
 *
 * Hours, not minutes, past a day's worth -- "12,480 minutes" is a number
 * nobody can feel. Proportional figures rather than tabular: equal-width
 * digits make a large standalone number look loose.
 */
@Composable
private fun HeroTime(stats: WatchStats, modifier: Modifier = Modifier) {
    val hours = stats.totalMinutes / 60
    val minutes = stats.totalMinutes % 60

    Column(modifier = modifier) {
        Text(
            text = if (hours > 0) "$hours h" else "$minutes min",
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = stringResource(
                R.string.stats_hero_detail,
                stats.distinctTitles,
                stats.totalMovieViewings,
                stats.totalEpisodeViewings,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Streaks(stats: WatchStats, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            value = stats.currentStreakDays.toString(),
            label = stringResource(R.string.stats_current_streak),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = stats.longestStreakDays.toString(),
            label = stringResource(R.string.stats_longest_streak),
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = stringResource(R.string.stats_streak_rule),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A number is the chart, where there is only one number. */
@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A ranked list with a proportion bar, not another palette.
 *
 * Genres and services are identities, and giving each a colour would spend
 * eight hues saying what the order already says.
 */
@Composable
private fun Breakdown(heading: String, buckets: List<Bucket>) {
    if (buckets.isEmpty()) return
    val peak = buckets.maxOf { it.minutes }.coerceAtLeast(1)

    SectionHeading(heading, Modifier.padding(top = 32.dp))
    buckets.forEach { bucket ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.stats_hours, bucket.minutes / 60),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { bucket.minutes.toFloat() / peak },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    // The bar is a picture of the figure beside it, so it says
                    // nothing extra to a screen reader.
                    .semantics { contentDescription = "" },
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

private fun StatsPeriod.label(): Int = when (this) {
    StatsPeriod.MONTH -> R.string.stats_period_month
    StatsPeriod.YEAR -> R.string.stats_period_year
    StatsPeriod.ALL_TIME -> R.string.stats_period_all
}
