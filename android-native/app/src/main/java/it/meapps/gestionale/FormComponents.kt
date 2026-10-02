@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun ValueRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }

@Composable
internal fun QuickArchive(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = AppBlue)
            Text(count.toString(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(label, fontSize = 11.sp, maxLines = 1)
        }
    }
}

internal fun shareProduct(
    context: android.content.Context,
    product: Product,
    targetPackage: String?,
) {
    val text = buildString {
        append(product.name)
        val code = product.code.ifBlank { product.sku }
        if (code.isNotBlank()) append("\nCodice: ").append(code)
        if (product.effectiveSalePrice > 0) {
            append("\nPrezzo: ").append(money(product.effectiveSalePrice))
            if (product.inPromotion && product.promotionalPrice > 0) append(" (promo)")
        }
        if (product.productUrl.isNotBlank()) append("\n").append(product.productUrl)
    }
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!targetPackage.isNullOrBlank()) setPackage(targetPackage)
        }
    val fallback =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent.createChooser(fallback, "Condividi articolo")) }
}

@Composable
internal fun SelectionField(
    label: String,
    selected: String?,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier =
                if (compact) Modifier.heightIn(min = 48.dp).widthIn(min = 112.dp)
                else Modifier.fillMaxWidth(),
            contentPadding =
                if (compact) PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                else ButtonDefaults.ContentPadding,
        ) {
            Text(
                options.firstOrNull { it.first == selected }?.second
                    ?: if (compact) label else "$label: ${stringResource(R.string.none)}",
                maxLines = if (compact) 2 else 4,
                fontSize = if (compact) 11.sp else 14.sp,
            )
            Spacer(Modifier.width(if (compact) 3.dp else 6.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(if (compact) 16.dp else 24.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.none)) },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelect(option.first)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    leading: (@Composable (() -> Unit))? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (minLines > 1) ImeAction.Default else ImeAction.Next,
            ),
        singleLine = minLines == 1,
        minLines = minLines,
        leadingIcon = leading,
        shape = RoundedCornerShape(14.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
    )
}

@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) =
    AppTextField(
        value,
        { next ->
            if (
                next.count { it == ',' || it == '.' } <= 1 &&
                    next.all { it.isDigit() || it == ',' || it == '.' }
            )
                onValueChange(next.replace(',', '.'))
        },
        label,
        modifier,
        KeyboardType.Decimal,
    )

@Composable
internal fun SectionTitle(text: String) =
    Text(
        text,
        Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = AppNavy,
    )

@Composable
internal fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, null, Modifier.size(48.dp), tint = Color(0xFF98A2B3))
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LoadingScreen(label: String) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label)
        }
    }

internal fun money(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    format.currency = java.util.Currency.getInstance("EUR")
    return format.format(value)
}

internal fun tabTitle(tab: MainTab): String =
    when (tab) {
        MainTab.HOME -> "Dashboard"
        MainTab.ARTICLES -> "Articoli"
        MainTab.CLIENTS -> "Clienti"
        MainTab.ORDERS -> "Ordini"
        MainTab.ARCHIVES -> "Anagrafiche"
        MainTab.SETTINGS -> "Impostazioni"
    }
