package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.evaluateJavascript
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.ExtensionPermission
import me.rerere.rikkahub.data.model.extensionSha256
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ExtensionBuilderPage(
    settingsStore: SettingsStore = koinInject(),
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var builderMode by rememberSaveable { mutableStateOf("SIMPLE") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var skill by rememberSaveable { mutableStateOf("") }
    var implementation by rememberSaveable { mutableStateOf("") }
    var testInput by rememberSaveable { mutableStateOf("{}") }
    var testResult by rememberSaveable { mutableStateOf("") }
    var testedHash by rememberSaveable { mutableStateOf<String?>(null) }
    var enabled by rememberSaveable { mutableStateOf(false) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var permissionName by rememberSaveable { mutableStateOf(ExtensionPermission.ASK_EVERY_TIME.name) }
    val permission = ExtensionPermission.valueOf(permissionName)
    val codeIsTested = implementation.isNotBlank() && testedHash == implementation.extensionSha256()

    fun startNewDraft() {
        editingId = null
        builderMode = "SIMPLE"
        name = ""
        description = ""
        skill = ""
        implementation = ""
        testInput = "{}"
        testResult = ""
        testedHash = null
        enabled = false
        permissionName = ExtensionPermission.ASK_EVERY_TIME.name
        showEditor = true
    }

    fun editDraft(extension: CustomExtension) {
        editingId = extension.id
        builderMode = "ADVANCED"
        name = extension.name
        description = extension.description
        skill = extension.skill
        implementation = extension.implementation
        testInput = "{}"
        testResult = ""
        testedHash = extension.testedImplementationHash
        enabled = extension.enabled &&
            extension.testedImplementationHash == extension.implementation.extensionSha256()
        permissionName = extension.permission.name
        showEditor = true
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.extension_builder_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::startNewDraft) {
                Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.extension_builder_page_create))
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.extension_builder_page_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.extension_builder_page_intro_desc),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.extension_builder_page_foundation_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (settings.customExtensions.isEmpty()) {
                item {
                    CardGroup {
                        item(
                            leadingContent = { CardGroupIcon(HugeIcons.Puzzle) },
                            headlineContent = { Text(stringResource(R.string.extension_builder_page_empty_title)) },
                            supportingContent = { Text(stringResource(R.string.extension_builder_page_empty_desc)) },
                        )
                    }
                }
            } else {
                item {
                    CardGroup(title = { Text(stringResource(R.string.extension_builder_page_drafts)) }) {
                        settings.customExtensions.forEach { extension ->
                            item(
                                leadingContent = { CardGroupIcon(HugeIcons.Puzzle) },
                                headlineContent = { Text(extension.name) },
                                supportingContent = {
                                    Text(
                                        extension.description.ifBlank {
                                            stringResource(R.string.extension_builder_page_no_description)
                                        }
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = { editDraft(extension) }) {
                                        Text(stringResource(R.string.extension_builder_page_edit))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = {
                Text(
                    if (editingId == null) stringResource(R.string.extension_builder_page_create_title)
                    else stringResource(R.string.extension_builder_page_edit_title)
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = builderMode == "SIMPLE",
                                onClick = { builderMode = "SIMPLE" },
                                label = { Text(stringResource(R.string.extension_builder_page_simple_mode)) },
                            )
                            FilterChip(
                                selected = builderMode == "ADVANCED",
                                onClick = { builderMode = "ADVANCED" },
                                label = { Text(stringResource(R.string.extension_builder_page_advanced_mode)) },
                            )
                        }
                    }
                    if (builderMode == "SIMPLE") {
                        item {
                            Text(
                                stringResource(R.string.extension_builder_page_simple_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.extension_builder_page_name)) },
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.extension_builder_page_description)) },
                            minLines = 2,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = skill,
                            onValueChange = { skill = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.extension_builder_page_skill)) },
                            minLines = 3,
                            supportingText = { Text(stringResource(R.string.extension_builder_page_skill_help)) },
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = implementation,
                            onValueChange = {
                                if (it != implementation) {
                                    implementation = it
                                    testedHash = null
                                    enabled = false
                                    testResult = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(if (builderMode == "SIMPLE") R.string.extension_builder_page_generated_implementation else R.string.extension_builder_page_implementation))
                            },
                            minLines = 4,
                            supportingText = {
                                Text(stringResource(if (builderMode == "SIMPLE") R.string.extension_builder_page_generated_implementation_help else R.string.extension_builder_page_implementation_help))
                            },
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = testInput,
                            onValueChange = { testInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(if (builderMode == "SIMPLE") R.string.extension_builder_page_generated_test_input else R.string.extension_builder_page_test_input))
                            },
                            minLines = 2,
                            supportingText = {
                                Text(stringResource(if (builderMode == "SIMPLE") R.string.extension_builder_page_generated_test_input_help else R.string.extension_builder_page_test_input_help))
                            },
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = !isTesting && implementation.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        isTesting = true
                                        testedHash = null
                                        enabled = false
                                        testResult = ""
                                        val codeUnderTest = implementation
                                        val inputUnderTest = testInput
                                        try {
                                            val input = Json.parseToJsonElement(inputUnderTest).jsonObject
                                            val output = evaluateJavascript("const input = ${input};\n$codeUnderTest")
                                            val payload = Json.parseToJsonElement(output).jsonObject
                                            val error = payload["error"]
                                            if (error != null) {
                                                testResult = error.toString()
                                            } else if (implementation == codeUnderTest) {
                                                testedHash = codeUnderTest.extensionSha256()
                                                testResult = payload["result"]?.toString() ?: output
                                            } else {
                                                testResult = "The code changed while the test was running. Run the test again."
                                            }
                                        } catch (error: Exception) {
                                            testResult = error.message ?: "Test failed"
                                        } finally {
                                            isTesting = false
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    if (isTesting) stringResource(R.string.extension_builder_page_testing)
                                    else stringResource(R.string.extension_builder_page_run_test)
                                )
                            }
                            if (codeIsTested) {
                                Text(
                                    stringResource(R.string.extension_builder_page_test_passed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (testResult.isNotBlank()) {
                        item {
                            Text(
                                text = stringResource(R.string.extension_builder_page_test_result, testResult),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (codeIsTested) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = enabled,
                                enabled = codeIsTested,
                                onCheckedChange = { enabled = it },
                            )
                            Text(
                                stringResource(R.string.extension_builder_page_enable),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.extension_builder_page_permission),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    item {
                        Column {
                            ExtensionPermission.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = permission == option,
                                        onClick = { permissionName = option.name },
                                    )
                                    Text(
                                        when (option) {
                                            ExtensionPermission.ALWAYS_ALLOW -> stringResource(R.string.extension_builder_page_permission_always)
                                            ExtensionPermission.ASK_EVERY_TIME -> stringResource(R.string.extension_builder_page_permission_ask)
                                            ExtensionPermission.THIS_SESSION -> stringResource(R.string.extension_builder_page_permission_session)
                                            ExtensionPermission.DISABLED -> stringResource(R.string.extension_builder_page_permission_disabled)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.extension_builder_page_not_executable_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !isTesting,
                    onClick = {
                        val updated = CustomExtension(
                            id = editingId ?: CustomExtension(name = name.trim(), description = description.trim()).id,
                            name = name.trim(),
                            description = description.trim(),
                            skill = skill,
                            implementation = implementation,
                            permission = permission,
                            enabled = enabled && codeIsTested,
                            testedImplementationHash = testedHash.takeIf { codeIsTested },
                        )
                        scope.launch {
                            settingsStore.update { current ->
                                val next = if (editingId == null) {
                                    current.customExtensions + updated
                                } else {
                                    current.customExtensions.map { if (it.id == editingId) updated else it }
                                }
                                current.copy(customExtensions = next)
                            }
                        }
                        showEditor = false
                    },
                ) {
                    Text(stringResource(R.string.extension_builder_page_save))
                }
            },
            dismissButton = {
                Row {
                    if (editingId != null) {
                        TextButton(
                            onClick = {
                                val id = editingId
                                scope.launch {
                                    settingsStore.update { current ->
                                        current.copy(customExtensions = current.customExtensions.filterNot { it.id == id })
                                    }
                                }
                                showEditor = false
                            },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}
