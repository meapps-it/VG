@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.meapps.gestionale

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun ProductPhotoCarousel(
    product: Product,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.15f,
    rounded: RoundedCornerShape = RoundedCornerShape(18.dp),
    showThumbnails: Boolean = false,
) {
    val photos = remember(product.photos) { product.photos.sortedBy { it.order } }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    photos.forEach { photo -> LaunchedEffect(photo.path) { vm.ensureSignedUrl(photo.path) } }

    if (photos.isEmpty()) {
        Surface(
            modifier.fillMaxWidth().height(76.dp),
            shape = rounded,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    null,
                    Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Foto da aggiungere",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val visibleIndex by remember { derivedStateOf { state.firstVisibleItemIndex } }
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
            val itemWidth = maxWidth
            LazyRow(
                state = state,
                flingBehavior =
                    androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(photos, key = { it.id }) { photo ->
                    Surface(
                        modifier = Modifier.width(itemWidth).fillMaxHeight(),
                        shape = rounded,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        val url = vm.signedUrls[photo.path]
                        if (url != null) {
                            SubcomposeAsyncImage(
                                model = url,
                                loading = {
                                    Box(
                                        Modifier.fillMaxSize().semantics {
                                            contentDescription = "Caricamento foto"
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            "Foto non disponibile",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                contentDescription = product.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            if (photos.size > 1) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD90F172A),
                ) {
                    Text(
                        "${(visibleIndex + 1).coerceAtMost(photos.size)}/${photos.size}",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (showThumbnails && photos.size > 1) {
            Spacer(Modifier.height(5.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(photos.size) { index ->
                    val photo = photos[index]
                    val selected = index == visibleIndex
                    Surface(
                        modifier =
                            Modifier.size(52.dp).clickable {
                                scope.launch { state.animateScrollToItem(index) }
                            },
                        shape = RoundedCornerShape(9.dp),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) AppNavy else LegacyBorder,
                            ),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        val url = vm.signedUrls[photo.path]
                        if (url != null) {
                            SubcomposeAsyncImage(
                                model = url,
                                loading = {
                                    Box(
                                        Modifier.fillMaxSize().semantics {
                                            contentDescription = "Caricamento foto"
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                },
                                error = {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            "Foto non disponibile",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                contentDescription = "Foto ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
