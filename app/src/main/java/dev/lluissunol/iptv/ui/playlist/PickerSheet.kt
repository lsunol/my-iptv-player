package dev.lluissunol.iptv.ui.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.lluissunol.iptv.R
import dev.lluissunol.iptv.data.PickerEntry
import dev.lluissunol.iptv.data.filterPicker
import dev.lluissunol.iptv.ui.formatNumber

/**
 * "Grupo: Todos ▾" / "Categoría ▾" chip, 32 dp visible inside a 48 dp touch
 * target. Its selected state uses the secondary (violet) language of the old pills.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerChip(
    label: String,
    selected: Boolean,
    description: String,
    minWidth: Dp,
    maxWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            // The chip's own description replaces this text for TalkBack; the
            // click action and role stay on the chip node.
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { }
            )
        },
        trailingIcon = {
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.secondary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.secondary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
    )
}

/** A [PickerChip] followed, when something is selected, by its 48 dp clear button. */
@Composable
internal fun PickerChipWithClear(
    label: String,
    selected: Boolean,
    chipDescription: String,
    clearDescription: String?,
    minWidth: Dp,
    maxWidth: Dp,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PickerChip(
            label = label,
            selected = selected,
            description = chipDescription,
            minWidth = minWidth,
            maxWidth = maxWidth,
            onClick = onOpen,
            modifier = Modifier.weight(1f, fill = false),
            focusRequester = focusRequester
        )
        if (selected && clearDescription != null) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = clearDescription,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private const val PICKER_ALL_KEY = "\u0000all"

/**
 * Bottom sheet listing EVERY group or category with a filter field. Rows are
 * plain composables (no per-row state); entries and their normalized keys are
 * built by the caller once per source list. The "all" row is hidden while a
 * filter text is typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSheet(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    @StringRes allRes: Int,
    @StringRes noMatchRes: Int,
    entries: List<PickerEntry>,
    allCount: Int?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = filterPicker(entries, query)
    val filtering = shown !== entries
    val selectedIndex = entries.indexOfFirst { it.id == selectedId }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (selectedIndex < 0) 0 else maxOf(0, selectedIndex + 1 - 2)
    )
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // Animate the sheet away before telling the caller, instead of vanishing.
    val closeAnimated: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = stringResource(titleRes, formatNumber(entries.size)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .semantics { heading() }
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(stringResource(hintRes), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        if (filtering && shown.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(noMatchRes, query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { query = "" }) {
                    Text(stringResource(R.string.picker_filter_clear))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .imePadding()
                    .selectableGroup()
            ) {
                if (!filtering) {
                    item(key = PICKER_ALL_KEY, contentType = "row") {
                        PickerRow(
                            label = stringResource(allRes),
                            count = allCount,
                            selected = selectedId == null,
                            onClick = {
                                onSelect(null)
                                closeAnimated()
                            }
                        )
                    }
                }
                items(items = shown, key = { it.id }, contentType = { "row" }) { entry ->
                    PickerRow(
                        label = entry.label,
                        count = entry.count,
                        selected = entry.id == selectedId,
                        onClick = {
                            onSelect(entry.id)
                            closeAnimated()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatNumber(count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
