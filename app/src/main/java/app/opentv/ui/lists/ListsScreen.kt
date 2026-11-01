package app.opentv.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.model.PlaylistConfig
import app.opentv.data.model.PlaylistKind
import app.opentv.data.model.xtreamBaseUrl
import app.opentv.ui.formatNumber
import app.opentv.ui.playlist.ListFormError
import app.opentv.ui.playlist.ListFormState
import app.opentv.ui.playlist.UiState
import app.opentv.ui.shortDate

/**
 * Home screen: the configured lists, or the add-list form when there is none.
 * The form is also reused to edit an existing list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    state: UiState,
    onOpenList: (String) -> Unit,
    onAddList: () -> Unit,
    onEditList: (String) -> Unit,
    onDeleteList: (String) -> Unit,
    onCancelForm: () -> Unit,
    onFormNameChange: (String) -> Unit,
    onFormKindChange: (PlaylistKind) -> Unit,
    onFormUrlChange: (String) -> Unit,
    onFormServerChange: (String) -> Unit,
    onFormUsernameChange: (String) -> Unit,
    onFormPasswordChange: (String) -> Unit,
    onFormSubmit: () -> Unit
) {
    val form = state.form
    if (form != null) {
        ListForm(
            form = form,
            canCancel = state.lists.isNotEmpty(),
            onCancel = onCancelForm,
            onNameChange = onFormNameChange,
            onKindChange = onFormKindChange,
            onUrlChange = onFormUrlChange,
            onServerChange = onFormServerChange,
            onUsernameChange = onFormUsernameChange,
            onPasswordChange = onFormPasswordChange,
            onSubmit = onFormSubmit,
            onDelete = { form.listId?.let(onDeleteList) }
        )
    } else {
        ListsContent(
            state = state,
            onOpenList = onOpenList,
            onAddList = onAddList,
            onEditList = onEditList
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListsContent(
    state: UiState,
    onOpenList: (String) -> Unit,
    onAddList: () -> Unit,
    onEditList: (String) -> Unit
) {
    val count = state.lists.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B2A28), MaterialTheme.colorScheme.background)
                    )
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_mark),
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.lists_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.lists_count,
                                    count,
                                    formatNumber(count)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = onAddList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.add_list))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items = state.lists, key = { it.id }) { list ->
                ListRow(
                    list = list,
                    isActive = list.id == state.activeListId,
                    onClick = { onOpenList(list.id) },
                    onEdit = { onEditList(list.id) }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(
    list: PlaylistConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = list.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listSubtitle(list, isActive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_list)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** Name-safe summary: kind + host only, never the credentials. */
@Composable
private fun listSubtitle(list: PlaylistConfig, isActive: Boolean): String {
    val parts = mutableListOf(
        stringResource(
            if (list.kind == PlaylistKind.XTREAM) R.string.list_kind_xtream else R.string.list_kind_url
        )
    )
    list.displayHost?.let { parts += it }
    list.lastLoadedAt?.let { parts += stringResource(R.string.list_updated_at, shortDate(it)) }
    if (isActive) parts += stringResource(R.string.list_last_used)
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListForm(
    form: ListFormState,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onNameChange: (String) -> Unit,
    onKindChange: (PlaylistKind) -> Unit,
    onUrlChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit
) {
    var revealUrl by rememberSaveable { mutableStateOf(false) }
    var revealPassword by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val base = xtreamBaseUrl(form.server)

    if (canCancel) {
        BackHandler { onCancel() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (form.isNew) R.string.new_list else R.string.edit_list
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.list_form_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.list_name_label)) },
                placeholder = { Text(stringResource(R.string.list_name_hint)) },
                singleLine = true,
                isError = form.error == ListFormError.NAME_REQUIRED,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = fieldShape,
                colors = fieldColors
            )

            Text(
                text = stringResource(R.string.list_method_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MethodPill(
                    label = stringResource(R.string.list_method_url),
                    selected = form.kind == PlaylistKind.URL,
                    onClick = { onKindChange(PlaylistKind.URL) }
                )
                MethodPill(
                    label = stringResource(R.string.list_method_xtream),
                    selected = form.kind == PlaylistKind.XTREAM,
                    onClick = { onKindChange(PlaylistKind.XTREAM) }
                )
            }

            if (form.kind == PlaylistKind.URL) {
                OutlinedTextField(
                    value = form.url,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.url_label)) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.url_placeholder),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                    isError = form.error == ListFormError.URL_INVALID,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    visualTransformation = if (revealUrl) {
                        VisualTransformation.None
                    } else {
                        RedactCredentialsTransformation
                    },
                    trailingIcon = {
                        IconButton(onClick = { revealUrl = !revealUrl }) {
                            Icon(
                                imageVector = if (revealUrl) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (revealUrl) R.string.hide_credentials else R.string.show_credentials
                                )
                            )
                        }
                    },
                    shape = fieldShape,
                    colors = fieldColors
                )
            } else {
                OutlinedTextField(
                    value = form.server,
                    onValueChange = onServerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.xtream_server_label)) },
                    placeholder = { Text(stringResource(R.string.xtream_server_hint)) },
                    singleLine = true,
                    isError = form.error == ListFormError.XTREAM_INCOMPLETE ||
                        form.error == ListFormError.SERVER_IS_FULL_URL,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = form.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.xtream_user_label)) },
                    singleLine = true,
                    isError = form.error == ListFormError.XTREAM_INCOMPLETE,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = fieldShape,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.xtream_password_label)) },
                    singleLine = true,
                    isError = form.error == ListFormError.XTREAM_INCOMPLETE,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    visualTransformation = if (revealPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { revealPassword = !revealPassword }) {
                            Icon(
                                imageVector = if (revealPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(
                                    if (revealPassword) {
                                        R.string.hide_credentials
                                    } else {
                                        R.string.show_credentials
                                    }
                                )
                            )
                        }
                    },
                    shape = fieldShape,
                    colors = fieldColors
                )
                Text(
                    text = stringResource(
                        R.string.xtream_help,
                        base ?: stringResource(R.string.xtream_server_hint)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            form.error?.let { FormErrorCard(it) }

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.save_list))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.list_privacy_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!form.isNew) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_list))
                }
            }

            if (canCancel) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_list_title)) },
            text = { Text(stringResource(R.string.delete_list_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FormErrorCard(error: ListFormError) {
    val message = when (error) {
        ListFormError.NAME_REQUIRED -> stringResource(R.string.error_list_name)
        ListFormError.URL_INVALID -> stringResource(R.string.error_list_url)
        ListFormError.XTREAM_INCOMPLETE -> stringResource(R.string.error_xtream_incomplete)
        ListFormError.SERVER_IS_FULL_URL -> stringResource(R.string.error_xtream_server_full_url)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val fieldShape = RoundedCornerShape(16.dp)

private val fieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        cursorColor = MaterialTheme.colorScheme.primary
    )

/** Masks the credential values of a pasted Xtream URL while typing. */
private object RedactCredentialsTransformation : VisualTransformation {
    private val pattern = Regex("""(?i)(username|password|token|key|auth)=([^&\s]+)""")

    override fun filter(text: AnnotatedString): TransformedText {
        val redacted = pattern.replace(text.text) { match ->
            val value = match.groupValues[2]
            "${match.groupValues[1]}=" + "•".repeat(value.length)
        }
        return TransformedText(AnnotatedString(redacted), OffsetMapping.Identity)
    }
}

