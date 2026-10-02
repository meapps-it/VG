package it.meapps.gestionale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun AppPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier, shape = RoundedCornerShape(UiSpace.radius), color = color) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun ScreenHeading(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.invoke()
    }
}

internal enum class BadgeTone {
    NEUTRAL,
    BLUE,
    POSITIVE,
    WARM,
    ERROR,
}

@Composable
internal fun StatusBadge(text: String, tone: BadgeTone = BadgeTone.NEUTRAL) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) =
        when (tone) {
            BadgeTone.BLUE -> c.primaryContainer to c.onPrimaryContainer
            BadgeTone.POSITIVE -> c.secondaryContainer to c.onSecondaryContainer
            BadgeTone.WARM -> c.tertiaryContainer to c.onTertiaryContainer
            BadgeTone.ERROR -> c.errorContainer to c.onErrorContainer
            else -> c.surfaceVariant to c.onSurfaceVariant
        }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text,
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

internal fun statusTone(status: String) =
    when (status) {
        "consegnato" -> BadgeTone.POSITIVE
        "spedito" -> BadgeTone.BLUE
        "annullato" -> BadgeTone.NEUTRAL
        "in_lavorazione" -> BadgeTone.WARM
        else -> BadgeTone.NEUTRAL
    }

@Composable
internal fun AppBottomNavigation(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val entries =
        listOf(
            Triple(MainTab.HOME, stringResource(R.string.dashboard), Icons.Default.SpaceDashboard),
            Triple(MainTab.ARTICLES, stringResource(R.string.articles), Icons.Default.Inventory2),
            Triple(
                MainTab.CLIENTS,
                stringResource(R.string.customers),
                Icons.Default.PeopleOutline,
            ),
            Triple(MainTab.ORDERS, stringResource(R.string.orders), Icons.Default.ReceiptLong),
        )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        entries.forEach { (tab, label, icon) ->
            NavigationBarItem(
                modifier = Modifier.testTag("navigation-${tab.name}"),
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

@Composable
internal fun AdaptiveMetrics(content: @Composable FlowRowScope.() -> Unit) {
    BoxWithConstraints {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow =
                if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.25f) 1 else 2,
            content = content,
        )
    }
}

@Composable
internal fun MetricTile(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    positive: Boolean = false,
    onClick: () -> Unit = {},
) {
    AppPanel(
        modifier.clickable(onClick = onClick),
        if (positive) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (positive) Positive else AppNavy,
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FormFeedback(vm: AppViewModel) {
    vm.errorMessage?.let {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                it,
                Modifier.fillMaxWidth().padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
internal fun ResponsiveFormRow(content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.25f) {
            // Row weights remain valid in a vertically wrapping FlowRow.
            FlowRow(maxItemsInEachRow = 1, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        } else
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTopBar(title: String, vm: AppViewModel, onSave: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = { vm.navigateBack() }) {
                Icon(Icons.Default.Close, stringResource(R.string.back))
            }
        },
        actions = {
            Button(
                onClick = onSave,
                enabled = !vm.saving,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    if (vm.saving) stringResource(R.string.saving)
                    else stringResource(R.string.save)
                )
            }
            Spacer(Modifier.width(12.dp))
        },
    )
}

@Composable
internal fun AppTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        content = content,
    )
}

@Composable
internal fun EditorScaffold(
    vm: AppViewModel,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        bottomBar = {
            if (vm.errorMessage != null)
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    FormFeedback(vm)
                }
        },
        content = content,
    )
}
