package dev.lluissunol.iptv.ui.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.CanonicalGenre
import dev.lluissunol.iptv.data.CatalogSort
import dev.lluissunol.iptv.data.GenreLabel
import dev.lluissunol.iptv.data.LanguageOption
import dev.lluissunol.iptv.data.VodLayout
import dev.lluissunol.iptv.ui.formatNumber
import java.text.Collator
import java.util.Locale

/** The callbacks the catalogue controls and notices need, grouped to keep signatures short. */
internal class CatalogActions(
    val onSort: (CatalogSort) -> Unit,
    val onLanguage: (LanguageOption?) -> Unit,
    val onGenre: (String?) -> Unit,
    val onMinRating: (Boolean) -> Unit,
    val onClearFilters: () -> Unit,
    val onClearQuery: () -> Unit,
    val onOpenMovie: (dev.lluissunol.iptv.data.model.VodItem) -> Unit,
    val onDismissHint: () -> Unit,
    val onAcknowledgeReset: () -> Unit
)

/**
 * The catalogue version each lazy layout last scrolled for. Plain fields on
 * purpose (never snapshot state): the grid/list compare them from composition
 * and call `requestScrollToItem(0)` once per new version, and an instance kept
 * above the screen switch means coming back from a detail never rescrolls.
 */
internal class ScrollVersions {
    var grid: Int = -1
    var list: Int = -1
}

@Composable
internal fun sortLabel(sort: CatalogSort): String = stringResource(
    when (sort) {
        CatalogSort.PROVIDER -> R.string.sort_provider
        CatalogSort.RECENT -> R.string.sort_recent
        CatalogSort.RELEASE -> R.string.sort_release
        CatalogSort.RATING -> R.string.sort_rating
        CatalogSort.AZ -> R.string.sort_az
    }
)

@Composable
internal fun languageLabel(option: LanguageOption): String = stringResource(
    when (option) {
        LanguageOption.ES -> R.string.lang_es
        LanguageOption.LATINO -> R.string.lang_latino
        LanguageOption.EN -> R.string.lang_en
        LanguageOption.FR -> R.string.lang_fr
        LanguageOption.DE -> R.string.lang_de
        LanguageOption.IT -> R.string.lang_it
        LanguageOption.PT -> R.string.lang_pt
        LanguageOption.PT_BR -> R.string.lang_pt_br
        LanguageOption.CA -> R.string.lang_ca
        LanguageOption.GL -> R.string.lang_gl
        LanguageOption.EU -> R.string.lang_eu
        LanguageOption.AR -> R.string.lang_ar
        LanguageOption.TR -> R.string.lang_tr
        LanguageOption.NL -> R.string.lang_nl
        LanguageOption.PL -> R.string.lang_pl
        LanguageOption.RU -> R.string.lang_ru
        LanguageOption.RO -> R.string.lang_ro
        LanguageOption.HI -> R.string.lang_hi
        LanguageOption.VO -> R.string.lang_vo
        LanguageOption.OTHER -> R.string.other
    }
)

@Composable
internal fun genreLabel(label: GenreLabel): String = when (label) {
    is GenreLabel.Other -> label.label
    is GenreLabel.Known -> stringResource(
        when (label.genre) {
            CanonicalGenre.ACTION -> R.string.genre_action
            CanonicalGenre.ADVENTURE -> R.string.genre_adventure
            CanonicalGenre.ANIMATION -> R.string.genre_animation
            CanonicalGenre.COMEDY -> R.string.genre_comedy
            CanonicalGenre.CRIME -> R.string.genre_crime
            CanonicalGenre.DOCUMENTARY -> R.string.genre_documentary
            CanonicalGenre.DRAMA -> R.string.genre_drama
            CanonicalGenre.FAMILY -> R.string.genre_family
            CanonicalGenre.FANTASY -> R.string.genre_fantasy
            CanonicalGenre.HISTORY -> R.string.genre_history
            CanonicalGenre.HORROR -> R.string.genre_horror
            CanonicalGenre.MUSIC -> R.string.genre_music
            CanonicalGenre.MUSICAL -> R.string.genre_musical
            CanonicalGenre.MYSTERY -> R.string.genre_mystery
            CanonicalGenre.ROMANCE -> R.string.genre_romance
            CanonicalGenre.SCIFI -> R.string.genre_scifi
            CanonicalGenre.TV_MOVIE -> R.string.genre_tv_movie
            CanonicalGenre.THRILLER -> R.string.genre_thriller
            CanonicalGenre.WAR -> R.string.genre_war
            CanonicalGenre.POLITICS -> R.string.genre_politics
            CanonicalGenre.WESTERN -> R.string.genre_western
            CanonicalGenre.KIDS -> R.string.genre_kids
            CanonicalGenre.NEWS -> R.string.genre_news
            CanonicalGenre.REALITY -> R.string.genre_reality
            CanonicalGenre.SOAP -> R.string.genre_soap
            CanonicalGenre.TALK -> R.string.genre_talk
            CanonicalGenre.BIOGRAPHY -> R.string.genre_biography
            CanonicalGenre.SPORT -> R.string.genre_sport
            CanonicalGenre.GAME_SHOW -> R.string.genre_game_show
            CanonicalGenre.FOOD -> R.string.genre_food
            CanonicalGenre.HOME_GARDEN -> R.string.genre_home_garden
            CanonicalGenre.MINI_SERIES -> R.string.genre_mini_series
            CanonicalGenre.SHORT -> R.string.genre_short
            CanonicalGenre.TRAVEL -> R.string.genre_travel
        }
    )
}

/**
 * The count row of a catalogue tab, turned into the results toolbar: count text,
 * warnings, then Ordenar, Filtros and the list/grid toggle. Sort and filter
 * buttons exist only when this catalogue has data for them.
 */
@Composable
internal fun ResultsToolbar(
    state: UiState,
    warningCount: Int,
    onOpenFilters: () -> Unit,
    onLayoutToggle: () -> Unit,
    actions: CatalogActions
) {
    val tabIndex = state.tabIndex
    val filters = state.activeSheetFilters
    val sort = state.currentSort

    val displayed = formatNumber(state.displayedCount)
    val countText = if (filters > 0) {
        stringResource(R.string.items_count_of, displayed, formatNumber(state.tabCount(state.tab)))
    } else {
        stringResource(R.string.items_count, displayed)
    }
    val text = if (sort != CatalogSort.PROVIDER) {
        stringResource(R.string.items_count_sorted, countText, sortLabel(sort))
    } else {
        countText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (warningCount > 0) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.warning_count, warningCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (tabIndex != null && tabIndex.size >= 2) {
            SortButton(
                current = sort,
                available = tabIndex.sorts,
                showFootnote = tabIndex.sortsHidden,
                onSelect = actions.onSort,
                onOpened = actions.onAcknowledgeReset
            )
        }
        if (tabIndex != null && tabIndex.size >= 2 && tabIndex.hasFilters) {
            IconButton(onClick = {
                actions.onAcknowledgeReset()
                onOpenFilters()
            }) {
                BadgedBox(
                    badge = {
                        if (filters > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.clearAndSetSemantics { }
                            ) {
                                // A 2x font would grow the badge over the icon.
                                val density = LocalDensity.current
                                CompositionLocalProvider(
                                    LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))
                                ) { Text(filters.toString()) }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = if (filters > 0) {
                            pluralStringResource(R.plurals.filters_cd_active, filters, filters)
                        } else {
                            stringResource(R.string.filters)
                        },
                        tint = if (filters > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        IconButton(onClick = onLayoutToggle) {
            val grid = state.vodLayout == VodLayout.GRID
            Icon(
                imageVector = if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = stringResource(
                    if (grid) R.string.layout_list else R.string.layout_grid
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SortButton(
    current: CatalogSort,
    available: List<CatalogSort>,
    showFootnote: Boolean,
    onSelect: (CatalogSort) -> Unit,
    onOpened: () -> Unit
) {
    // One instance of UI chrome state, never per row.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            onOpened()
            expanded = true
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_cd, sortLabel(current)),
                tint = if (current != CatalogSort.PROVIDER) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
        ) {
            Text(
                text = stringResource(R.string.sort_menu_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { heading() }
            )
            val azCd = stringResource(R.string.sort_az_cd)
            for (sort in available) {
                val isSelected = sort == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sortLabel(sort),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Spacer(Modifier.size(20.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                    modifier = Modifier.semantics {
                        selected = isSelected
                        role = Role.RadioButton
                        if (sort == CatalogSort.AZ) contentDescription = azCd
                    }
                )
            }
            if (showFootnote) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sort_unavailable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        modifier = Modifier.widthIn(max = 220.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.secondary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
    )
}

/**
 * The filter sheet. Filters apply live, so the "Ver N títulos" footer is always
 * true and only closes the sheet. A section appears only when this catalogue
 * has data for it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    state: UiState,
    onDismiss: () -> Unit,
    actions: CatalogActions
) {
    val tabIndex = state.tabIndex ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isMovies = state.tab == SourceTypeMovie
    val count = state.displayedCount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = stringResource(
                if (isMovies) R.string.filters_title_movies else R.string.filters_title_series
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            tabIndex.language?.let { facet ->
                SectionHeader(stringResource(R.string.filter_language))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    SheetChip(stringResource(R.string.filter_all), state.effectiveLanguage == null) {
                        actions.onLanguage(null)
                    }
                    for (option in facet.options) {
                        val selected = state.effectiveLanguage == option
                        SheetChip(languageLabel(option), selected) {
                            actions.onLanguage(if (selected) null else option)
                        }
                    }
                }
            }
            tabIndex.genres?.let { facet ->
                SectionHeader(stringResource(R.string.filter_genre))
                val collator = remember { Collator.getInstance(Locale("es")) }
                val labelled = facet.options
                    .map { it to genreLabel(it.label) }
                    .sortedWith { a, b -> collator.compare(a.second, b.second) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    SheetChip(stringResource(R.string.filter_all), state.effectiveGenre == null) {
                        actions.onGenre(null)
                    }
                    for ((option, label) in labelled) {
                        val selected = state.effectiveGenre == option.key
                        SheetChip(label, selected) {
                            actions.onGenre(if (selected) null else option.key)
                        }
                    }
                }
                if (facet.capped) {
                    Text(
                        text = stringResource(R.string.filter_genre_capped, MAX_GENRES_SHOWN),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (tabIndex.ratingFilter) {
                SectionHeader(stringResource(R.string.filter_rating))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = state.effectiveMinRating,
                            role = Role.Switch,
                            onValueChange = actions.onMinRating
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.filter_rating_min),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.effectiveMinRating, onCheckedChange = null)
                }
                Text(
                    text = stringResource(R.string.filter_rating_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tabIndex.filtersHidden) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.filters_unavailable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = actions.onClearFilters,
                enabled = state.activeSheetFilters > 0,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.filters_clear))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDismiss,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(pluralStringResource(R.plurals.filters_show_results, count, formatNumber(count)))
            }
        }
    }
}

private val SourceTypeMovie = dev.lluissunol.iptv.data.model.SourceType.MOVIE
private const val MAX_GENRES_SHOWN = dev.lluissunol.iptv.data.MAX_GENRE_OPTIONS

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** Removable chips for the active sheet filters, shown under the category chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFilterChips(state: UiState, actions: CatalogActions) {
    val language = state.effectiveLanguage
    val genreKey = state.effectiveGenre
    val activeGenre = genreKey?.let { key ->
        state.tabIndex?.genres?.options?.firstOrNull { it.key == key }?.label
    }
    val chips = buildList {
        if (language != null) add(ActiveChip("lang") { actions.onLanguage(null) })
        if (activeGenre != null) add(ActiveChip("genre") { actions.onGenre(null) })
        if (state.effectiveMinRating) add(ActiveChip("rating") { actions.onMinRating(false) })
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = chips, key = { it.key }) { chip ->
            val label = when (chip.key) {
                "lang" -> languageLabel(language!!)
                "genre" -> genreLabel(activeGenre!!)
                else -> stringResource(R.string.filter_rating_chip)
            }
            val removeCd = stringResource(R.string.filter_remove_cd, label)
            InputChip(
                selected = true,
                onClick = chip.onRemove,
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                },
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .semantics { contentDescription = removeCd },
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.secondary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.secondary
                ),
                border = InputChipDefaults.inputChipBorder(
                    enabled = true,
                    selected = true,
                    selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
            )
        }
    }
}

private class ActiveChip(val key: String, val onRemove: () -> Unit)
