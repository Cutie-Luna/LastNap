package me.rerere.rikkahub.ui.pages.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.CustomToolHttpRuntime
import me.rerere.rikkahub.data.ai.tools.local.CustomToolSecretStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.removeCustomExtension
import me.rerere.rikkahub.data.model.CustomExtension
import me.rerere.rikkahub.data.model.CustomExtensionLimits
import me.rerere.rikkahub.data.model.CustomExtensionValidationError
import me.rerere.rikkahub.data.model.CustomToolValueDeclaration
import me.rerere.rikkahub.data.model.testFingerprint
import me.rerere.rikkahub.data.model.validationErrors
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private enum class BuilderMode { SIMPLE, ADVANCED }

@Composable
fun ExtensionBuilderPage(
    settingsStore: SettingsStore = koinInject(),
    httpRuntime: CustomToolHttpRuntime = koinInject(),
    secretStore: CustomToolSecretStore = koinInject(),
) {
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var isEditor by rememberSaveable { mutableStateOf(false) }
    var isReviewing by rememberSaveable { mutableStateOf(false) }
    var isExistingDraft by rememberSaveable { mutableStateOf(false) }
    var draftId by rememberSaveable { mutableStateOf(Uuid.random().toString()) }
    var builderModeName by rememberSaveable { mutableStateOf(BuilderMode.SIMPLE.name) }
    val builderMode = BuilderMode.valueOf(builderModeName)
    var showBuildDetails by rememberSaveable { mutableStateOf(false) }

    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var skill by rememberSaveable { mutableStateOf("") }
    var inputSchema by rememberSaveable {
        mutableStateOf("""{"type":"object","properties":{},"additionalProperties":true}""")
    }
    var implementation by rememberSaveable { mutableStateOf("") }
    var testInput by rememberSaveable { mutableStateOf("{}") }
    var testedHash by rememberSaveable { mutableStateOf<String?>(null) }
    var enabled by rememberSaveable { mutableStateOf(false) }
    var needsApproval by rememberSaveable { mutableStateOf(true) }
    var apiAccessEnabled by rememberSaveable { mutableStateOf(false) }
    var allowedApiHostsText by rememberSaveable { mutableStateOf("") }
    var allowLocalNetwork by rememberSaveable { mutableStateOf(false) }
    var secretDeclarations by remember { mutableStateOf<List<CustomToolValueDeclaration>>(emptyList()) }
    var variableDeclarations by remember { mutableStateOf<List<CustomToolValueDeclaration>>(emptyList()) }
    var variableValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val pendingSecretValues = remember { mutableStateMapOf<String, String>() }
    var savedSecretNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var secretNameInput by rememberSaveable { mutableStateOf("") }
    var secretDescriptionInput by rememberSaveable { mutableStateOf("") }
    var secretRequiredInput by rememberSaveable { mutableStateOf(true) }
    var variableNameInput by rememberSaveable { mutableStateOf("") }
    var variableDescriptionInput by rememberSaveable { mutableStateOf("") }
    var variableRequiredInput by rememberSaveable { mutableStateOf(true) }
    val allowedApiHosts = remember(allowedApiHostsText) {
        allowedApiHostsText.lineSequence()
            .flatMap { it.split(',').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    var isTesting by remember { mutableStateOf(false) }
    var testPassed by remember { mutableStateOf<Boolean?>(null) }
    var testResult by remember { mutableStateOf("") }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val draft = CustomExtension(
        id = draftId,
        name = name,
        description = description,
        skill = skill,
        inputSchema = inputSchema,
        implementation = implementation,
        needsApproval = needsApproval,
        enabled = false,
        testedDefinitionHash = testedHash,
        apiAccessEnabled = apiAccessEnabled,
        allowedApiHosts = allowedApiHosts,
        allowLocalNetwork = allowLocalNetwork,
        secrets = secretDeclarations,
        variables = variableDeclarations,
    )
    val validationErrors = draft.validationErrors()
    val schemaIsValid = validationErrors.none {
        it == CustomExtensionValidationError.INPUT_SCHEMA_TOO_LONG ||
            it == CustomExtensionValidationError.INVALID_INPUT_SCHEMA
    }
    val implementationIsWithinLimit = implementation.length <= CustomExtensionLimits.MAX_IMPLEMENTATION_LENGTH
    val apiPolicyIsValid = validationErrors.none {
        it == CustomExtensionValidationError.API_HOSTS_REQUIRED ||
            it == CustomExtensionValidationError.TOO_MANY_API_HOSTS ||
            it == CustomExtensionValidationError.INVALID_API_HOST_PATTERN ||
            it == CustomExtensionValidationError.LOCAL_NETWORK_WITHOUT_API_ACCESS
    }
    val inputError = remember(testInput) {
        runCatching { buildCustomExtensionTestCode(testInput, "0") }.exceptionOrNull()?.message
    }
    val canTest = !isTesting && implementation.isNotBlank() &&
        implementationIsWithinLimit && schemaIsValid && apiPolicyIsValid && inputError == null
    val missingRequiredSecrets = if (apiAccessEnabled) {
        secretDeclarations.filter { it.required && it.name !in savedSecretNames }
    } else emptyList()
    val missingRequiredVariables = variableDeclarations.filter {
        it.required && variableValues[it.name].isNullOrBlank()
    }
    val canEnable = draft.canBeEnabledFromBuilder() &&
        missingRequiredSecrets.isEmpty() && missingRequiredVariables.isEmpty()
    val hasCurrentTest = draft.hasCurrentBuilderTest()

    fun clearDraftState() {
        isEditor = false
        isReviewing = false
        isExistingDraft = false
        testPassed = null
        testResult = ""
        isTesting = false
    }

    fun startNewDraft() {
        draftId = Uuid.random().toString()
        secretDeclarations = emptyList()
        variableDeclarations = emptyList()
        variableValues = emptyMap()
        pendingSecretValues.clear()
        savedSecretNames = emptySet()
        secretNameInput = ""
        secretDescriptionInput = ""
        secretRequiredInput = true
        variableNameInput = ""
        variableDescriptionInput = ""
        variableRequiredInput = true
        isExistingDraft = false
        builderModeName = BuilderMode.SIMPLE.name
        showBuildDetails = false
        name = ""
        description = ""
        skill = ""
        inputSchema = """{"type":"object","properties":{},"additionalProperties":true}"""
        implementation = ""
        testInput = "{}"
        testedHash = null
        enabled = false
        needsApproval = true
        apiAccessEnabled = false
        allowedApiHostsText = ""
        allowLocalNetwork = false
        testPassed = null
        testResult = ""
        isReviewing = false
        isEditor = true
    }

    fun editDraft(extension: CustomExtension) {
        draftId = extension.id
        isExistingDraft = true
        builderModeName = BuilderMode.ADVANCED.name
        showBuildDetails = true
        name = extension.name
        description = extension.description
        skill = extension.skill
        inputSchema = extension.inputSchema
        implementation = extension.implementation
        testInput = "{}"
        testedHash = extension.testedDefinitionHash
        enabled = extension.enabled && extension.copy(enabled = true).validationErrors().isEmpty()
        needsApproval = extension.needsApproval
        apiAccessEnabled = extension.apiAccessEnabled
        allowedApiHostsText = extension.allowedApiHosts.joinToString("\n")
        allowLocalNetwork = extension.allowLocalNetwork
        secretDeclarations = extension.secrets
        variableDeclarations = extension.variables
        variableValues = settings.customToolVariables[extension.id].orEmpty()
        pendingSecretValues.clear()
        savedSecretNames = extension.secrets.filter { declaration ->
            runCatching { secretStore.hasSecret(extension.id, declaration.name) }.getOrDefault(false)
        }.map { it.name }.toSet()
        secretNameInput = ""
        secretDescriptionInput = ""
        secretRequiredInput = true
        variableNameInput = ""
        variableDescriptionInput = ""
        variableRequiredInput = true
        testPassed = null
        testResult = ""
        isReviewing = false
        isEditor = true
    }

    fun leaveEditor() {
        val persistedNames = settings.customExtensions
            .firstOrNull { it.id == draftId }
            ?.secrets
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        scope.launch(Dispatchers.IO) {
            secretStore.retainDeclaredSecrets(draftId, persistedNames)
        }
        clearDraftState()
    }

    fun saveDraft() {
        val saved = draft.copy(
            name = name.trim(),
            description = description.trim(),
            enabled = enabled && canEnable,
            testedDefinitionHash = testedHash.takeIf { hasCurrentTest },
        )
        val valuesToSave = variableValues.filterKeys { key -> saved.variables.any { it.name == key } }
        scope.launch {
            settingsStore.update { current ->
                val next = if (isExistingDraft) {
                    current.customExtensions.map { if (it.id == draftId) saved else it }
                } else {
                    current.customExtensions + saved
                }
                val nextVariables = if (valuesToSave.isEmpty()) {
                    current.customToolVariables - draftId
                } else {
                    current.customToolVariables + (draftId to valuesToSave)
                }
                current.copy(customExtensions = next, customToolVariables = nextVariables)
            }
            withContext(Dispatchers.IO) {
                secretStore.retainDeclaredSecrets(draftId, saved.secrets.map { it.name }.toSet())
            }
            clearDraftState()
        }
    }

    fun runTest() {
        if (!canTest) return
        val schemaUnderTest = inputSchema
        val implementationUnderTest = implementation
        val inputUnderTest = testInput
        val apiAccessUnderTest = apiAccessEnabled
        val allowedHostsUnderTest = allowedApiHosts
        val localNetworkUnderTest = allowLocalNetwork
        val definitionUnderTest = draft.copy(enabled = false)
        val variablesUnderTest = variableValues
        val secretsUnderTest = savedSecretNames
        val declarationsUnderTest = secretDeclarations to variableDeclarations
        val fingerprintUnderTest = definitionUnderTest.testFingerprint()
        isTesting = true
        testedHash = null
        enabled = false
        testPassed = null
        testResult = ""

        scope.launch {
            try {
                val code = buildCustomExtensionTestCode(inputUnderTest, implementationUnderTest)
                val output = httpRuntime.evaluate(
                    definitionUnderTest,
                    code,
                    variables = variablesUnderTest,
                )
                val payload = Json.parseToJsonElement(output) as? JsonObject
                    ?: error("The sandbox returned an unexpected result")
                val error = payload["error"]
                if (inputSchema == schemaUnderTest &&
                    implementation == implementationUnderTest &&
                    apiAccessEnabled == apiAccessUnderTest &&
                    allowedApiHosts == allowedHostsUnderTest &&
                    allowLocalNetwork == localNetworkUnderTest &&
                    variableValues == variablesUnderTest &&
                    savedSecretNames == secretsUnderTest &&
                    declarationsUnderTest == (secretDeclarations to variableDeclarations)
                ) {
                    if (error == null) {
                        testedHash = fingerprintUnderTest
                        testPassed = true
                        testResult = output.take(6_000)
                    } else {
                        testPassed = false
                        testResult = error.toString().take(6_000)
                    }
                } else {
                    testPassed = false
                    testResult = "The schema or implementation changed while the test was running. Run the test again."
                }
            } catch (error: Exception) {
                testPassed = false
                testResult = (error.message ?: "Test failed").take(6_000)
            } finally {
                isTesting = false
            }
        }
    }

    BackHandler(enabled = isEditor) {
        if (isTesting) {
            // Do not leave while the sandbox test is still resolving its result.
        } else if (isReviewing) {
            isReviewing = false
        } else {
            leaveEditor()
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        when {
                            !isEditor -> stringResource(R.string.extension_builder_page_title)
                            isReviewing -> stringResource(R.string.extension_builder_page_review_title)
                            isExistingDraft -> stringResource(R.string.extension_builder_page_edit_title)
                            else -> stringResource(R.string.extension_builder_page_create_title)
                        }
                    )
                },
                navigationIcon = {
                    if (isEditor) {
                        FilledTonalIconButton(
                            onClick = { if (isReviewing) isReviewing = false else leaveEditor() },
                            enabled = !isTesting,
                        ) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = stringResource(R.string.back))
                        }
                    } else {
                        BackButton()
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            if (isEditor && !isReviewing) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            modifier = Modifier.weight(0.8f),
                            onClick = ::leaveEditor,
                            enabled = !isTesting,
                        ) {
                            Text(stringResource(R.string.extension_builder_page_back_to_drafts))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = ::runTest,
                            enabled = canTest,
                        ) {
                            Text(stringResource(if (isTesting) R.string.extension_builder_page_testing else R.string.extension_builder_page_run_test))
                        }
                        Button(
                            modifier = Modifier.weight(0.8f),
                            onClick = { isReviewing = true },
                            enabled = !isTesting,
                        ) {
                            Text(stringResource(R.string.extension_builder_page_review))
                        }
                    }
                }
            } else if (isEditor && isReviewing) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { isReviewing = false },
                        ) {
                            Text(stringResource(R.string.extension_builder_page_edit_draft))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = ::saveDraft,
                            enabled = name.isNotBlank() && !isTesting,
                        ) {
                            Text(stringResource(R.string.extension_builder_page_save))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isEditor) {
                androidx.compose.material3.FloatingActionButton(onClick = ::startNewDraft) {
                    Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.extension_builder_page_create))
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when {
            !isEditor -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (settings.customExtensions.isEmpty()) {
                        item {
                            ExtensionEmptyState(
                                message = stringResource(R.string.extension_builder_page_empty_title),
                                description = stringResource(R.string.extension_builder_page_empty_desc),
                                buttonText = stringResource(R.string.extension_builder_page_create),
                                onAction = ::startNewDraft,
                                icon = HugeIcons.Puzzle,
                            )
                        }
                    } else {
                        item {
                            Text(
                                stringResource(R.string.extension_builder_page_drafts),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(settings.customExtensions.size, key = { settings.customExtensions[it].id }) { index ->
                            val extension = settings.customExtensions[index]
                            val statusText = when {
                                extension.enabled && extension.hasCurrentBuilderTest() ->
                                    stringResource(R.string.extension_builder_page_status_enabled)
                                extension.hasCurrentBuilderTest() ->
                                    stringResource(R.string.extension_builder_page_status_tested)
                                else -> stringResource(R.string.extension_builder_page_status_draft)
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { editDraft(extension) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(14.dp),
                                    ) {
                                        Icon(
                                            HugeIcons.Puzzle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(12.dp).size(24.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(extension.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            extension.description.ifBlank { stringResource(R.string.extension_builder_page_no_description) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    TextButton(onClick = { editDraft(extension) }) {
                                        Text(stringResource(R.string.extension_builder_page_edit))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            isReviewing -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.extension_builder_page_review_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_name)) {
                            Text(name.ifBlank { stringResource(R.string.extension_builder_page_not_set) })
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_description)) {
                            Text(description.ifBlank { stringResource(R.string.extension_builder_page_not_set) })
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_skill)) {
                            Text(skill.ifBlank { stringResource(R.string.extension_builder_page_not_set) })
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_input_schema)) {
                            ReviewCode(inputSchema)
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_implementation)) {
                            ReviewCode(implementation.ifBlank { stringResource(R.string.extension_builder_page_not_set) })
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_test_input)) {
                            ReviewCode(testInput)
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_api_access)) {
                            Text(stringResource(if (apiAccessEnabled) R.string.extension_builder_page_api_access_on else R.string.extension_builder_page_api_access_off))
                            if (apiAccessEnabled) {
                                Text(stringResource(R.string.extension_builder_page_api_access_allowed_hosts))
                                ReviewCode(allowedApiHosts.joinToString("\n"))
                                Text(stringResource(if (allowLocalNetwork) R.string.extension_builder_page_local_network_on else R.string.extension_builder_page_local_network_off))
                            }
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_permission)) {
                            Text(stringResource(if (needsApproval) R.string.extension_builder_page_approval_on else R.string.extension_builder_page_approval_off))
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_test_status)) {
                            Text(
                                when {
                                    hasCurrentTest -> stringResource(R.string.extension_builder_page_status_tested)
                                    testPassed == false -> stringResource(R.string.extension_builder_page_status_test_failed)
                                    else -> stringResource(R.string.extension_builder_page_status_not_tested)
                                },
                                color = if (hasCurrentTest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (testResult.isNotBlank()) {
                                Spacer(Modifier.size(8.dp))
                                ReviewCode(testResult)
                            }
                        }
                    }
                    item {
                        ReviewSection(title = stringResource(R.string.extension_builder_page_enablement)) {
                            Text(
                                if (enabled && canEnable) stringResource(R.string.extension_builder_page_will_be_enabled)
                                else stringResource(R.string.extension_builder_page_will_be_saved_disabled)
                            )
                            if (!canEnable) {
                                Text(
                                    stringResource(R.string.extension_builder_page_enable_requires_test),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.size(8.dp)) }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.extension_builder_page_editor_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = builderMode == BuilderMode.SIMPLE,
                                onClick = { builderModeName = BuilderMode.SIMPLE.name },
                                label = { Text(stringResource(R.string.extension_builder_page_simple_mode)) },
                            )
                            FilterChip(
                                selected = builderMode == BuilderMode.ADVANCED,
                                onClick = { builderModeName = BuilderMode.ADVANCED.name },
                                label = { Text(stringResource(R.string.extension_builder_page_advanced_mode)) },
                            )
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_basics)) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_name)) },
                                singleLine = true,
                                supportingText = { Text("${name.length} / ${CustomExtensionLimits.MAX_NAME_LENGTH}") },
                                isError = name.length > CustomExtensionLimits.MAX_NAME_LENGTH,
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_description)) },
                                minLines = 2,
                                supportingText = { Text("${description.length} / ${CustomExtensionLimits.MAX_DESCRIPTION_LENGTH}") },
                                isError = description.length > CustomExtensionLimits.MAX_DESCRIPTION_LENGTH,
                            )
                            OutlinedTextField(
                                value = skill,
                                onValueChange = { skill = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_skill)) },
                                minLines = 3,
                                supportingText = {
                                    Text(
                                        if (builderMode == BuilderMode.SIMPLE) {
                                            stringResource(R.string.extension_builder_page_skill_simple_help)
                                        } else {
                                            stringResource(R.string.extension_builder_page_skill_help)
                                        }
                                    )
                                },
                                isError = skill.length > CustomExtensionLimits.MAX_SKILL_LENGTH,
                            )
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Text(
                                stringResource(
                                    if (builderMode == BuilderMode.SIMPLE) {
                                        R.string.extension_builder_page_simple_help
                                    } else {
                                        R.string.extension_builder_page_advanced_help
                                    }
                                ),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_api_access)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extension_builder_page_api_access_enable))
                                    Text(
                                        stringResource(R.string.extension_builder_page_api_access_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = apiAccessEnabled,
                                    onCheckedChange = {
                                        apiAccessEnabled = it
                                        testedHash = null
                                        enabled = false
                                        testPassed = null
                                        testResult = ""
                                        if (!it) allowLocalNetwork = false
                                    },
                                    enabled = !isTesting,
                                )
                            }
                            if (apiAccessEnabled) {
                                OutlinedTextField(
                                    value = allowedApiHostsText,
                                    onValueChange = {
                                        allowedApiHostsText = it
                                        testedHash = null
                                        enabled = false
                                        testPassed = null
                                        testResult = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.extension_builder_page_api_allowed_hosts)) },
                                    minLines = 3,
                                    supportingText = {
                                        Text(
                                            stringResource(R.string.extension_builder_page_api_allowed_hosts_help),
                                            color = if (apiPolicyIsValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    isError = !apiPolicyIsValid,
                                    enabled = !isTesting,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.extension_builder_page_local_network))
                                        Text(
                                            stringResource(R.string.extension_builder_page_local_network_help),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = allowLocalNetwork,
                                        onCheckedChange = {
                                            allowLocalNetwork = it
                                            testedHash = null
                                            enabled = false
                                            testPassed = null
                                            testResult = ""
                                        },
                                        enabled = !isTesting,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_secrets)) {
                            Text(
                                stringResource(R.string.extension_builder_page_secrets_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            secretDeclarations.forEach { declaration ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(declaration.name, style = MaterialTheme.typography.titleSmall)
                                            if (declaration.description.isNotBlank()) {
                                                Text(declaration.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                secretDeclarations = secretDeclarations.filterNot { it.name == declaration.name }
                                                pendingSecretValues.remove(declaration.name)
                                                savedSecretNames = savedSecretNames - declaration.name
                                                testedHash = null
                                                enabled = false
                                                testPassed = null
                                            },
                                            enabled = !isTesting,
                                        ) { Text(stringResource(R.string.extension_builder_page_remove)) }
                                    }
                                    OutlinedTextField(
                                        value = pendingSecretValues[declaration.name].orEmpty(),
                                        onValueChange = { pendingSecretValues[declaration.name] = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.extension_builder_page_secret_value)) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        enabled = !isTesting,
                                        supportingText = {
                                            Text(
                                                stringResource(
                                                    if (declaration.name in savedSecretNames) R.string.extension_builder_page_secret_configured
                                                    else R.string.extension_builder_page_secret_not_configured
                                                )
                                            )
                                        },
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val value = pendingSecretValues[declaration.name].orEmpty()
                                            scope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        secretStore.putSecret(draftId, declaration.name, value)
                                                    }
                                                }.onSuccess {
                                                    savedSecretNames = savedSecretNames + declaration.name
                                                    pendingSecretValues.remove(declaration.name)
                                                    testedHash = null
                                                    enabled = false
                                                    testPassed = null
                                                    testResult = ""
                                                }.onFailure {
                                                    testPassed = false
                                                    testResult = it.message ?: "Could not save Secret"
                                                }
                                            }
                                        },
                                        enabled = !isTesting && pendingSecretValues[declaration.name].orEmpty().isNotEmpty(),
                                    ) {
                                        Text(stringResource(R.string.extension_builder_page_save_secret))
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = secretNameInput,
                                onValueChange = { secretNameInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(CustomExtensionLimits.MAX_VALUE_NAME_LENGTH) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_secret_name)) },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = secretDescriptionInput,
                                onValueChange = { secretDescriptionInput = it.take(CustomExtensionLimits.MAX_VALUE_DESCRIPTION_LENGTH) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_secret_description)) },
                                singleLine = true,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = secretRequiredInput, onCheckedChange = { secretRequiredInput = it })
                                Text(stringResource(R.string.extension_builder_page_value_required))
                            }
                            OutlinedButton(
                                onClick = {
                                    val declaration = CustomToolValueDeclaration(
                                        name = secretNameInput.trim(),
                                        description = secretDescriptionInput.trim(),
                                        required = secretRequiredInput,
                                    )
                                    if (declaration.name.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) &&
                                        secretDeclarations.none { it.name == declaration.name } &&
                                        variableDeclarations.none { it.name == declaration.name } &&
                                        secretDeclarations.size < CustomExtensionLimits.MAX_VALUE_DECLARATIONS
                                    ) {
                                        secretDeclarations = secretDeclarations + declaration
                                        secretNameInput = ""
                                        secretDescriptionInput = ""
                                        secretRequiredInput = true
                                        testedHash = null
                                        enabled = false
                                    }
                                },
                                enabled = !isTesting && secretNameInput.isNotBlank(),
                            ) { Text(stringResource(R.string.extension_builder_page_add_secret)) }
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_variables)) {
                            Text(
                                stringResource(R.string.extension_builder_page_variables_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            variableDeclarations.forEach { declaration ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(declaration.name, style = MaterialTheme.typography.titleSmall)
                                            if (declaration.description.isNotBlank()) {
                                                Text(declaration.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                variableDeclarations = variableDeclarations.filterNot { it.name == declaration.name }
                                                variableValues = variableValues - declaration.name
                                                testedHash = null
                                                enabled = false
                                                testPassed = null
                                            },
                                            enabled = !isTesting,
                                        ) { Text(stringResource(R.string.extension_builder_page_remove)) }
                                    }
                                    OutlinedTextField(
                                        value = variableValues[declaration.name].orEmpty(),
                                        onValueChange = {
                                            variableValues = variableValues + (declaration.name to it.take(CustomExtensionLimits.MAX_SECRET_VALUE_LENGTH))
                                            testedHash = null
                                            enabled = false
                                            testPassed = null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(declaration.name) },
                                        singleLine = true,
                                        enabled = !isTesting,
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = variableNameInput,
                                onValueChange = { variableNameInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(CustomExtensionLimits.MAX_VALUE_NAME_LENGTH) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_variable_name)) },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = variableDescriptionInput,
                                onValueChange = { variableDescriptionInput = it.take(CustomExtensionLimits.MAX_VALUE_DESCRIPTION_LENGTH) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.extension_builder_page_variable_description)) },
                                singleLine = true,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = variableRequiredInput, onCheckedChange = { variableRequiredInput = it })
                                Text(stringResource(R.string.extension_builder_page_value_required))
                            }
                            OutlinedButton(
                                onClick = {
                                    val declaration = CustomToolValueDeclaration(
                                        name = variableNameInput.trim(),
                                        description = variableDescriptionInput.trim(),
                                        required = variableRequiredInput,
                                    )
                                    if (declaration.name.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) &&
                                        variableDeclarations.none { it.name == declaration.name } &&
                                        secretDeclarations.none { it.name == declaration.name } &&
                                        variableDeclarations.size < CustomExtensionLimits.MAX_VALUE_DECLARATIONS
                                    ) {
                                        variableDeclarations = variableDeclarations + declaration
                                        variableNameInput = ""
                                        variableDescriptionInput = ""
                                        variableRequiredInput = true
                                        testedHash = null
                                        enabled = false
                                    }
                                },
                                enabled = !isTesting && variableNameInput.isNotBlank(),
                            ) { Text(stringResource(R.string.extension_builder_page_add_variable)) }
                        }
                    }
                    if (builderMode == BuilderMode.SIMPLE) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showBuildDetails = !showBuildDetails }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extension_builder_page_build_details), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.extension_builder_page_build_details_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(if (showBuildDetails) "−" else "+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    if (builderMode == BuilderMode.ADVANCED || showBuildDetails) {
                        item {
                            BuilderSection(title = stringResource(R.string.extension_builder_page_inputs_section)) {
                                OutlinedTextField(
                                    value = inputSchema,
                                    onValueChange = {
                                        if (it != inputSchema) {
                                            inputSchema = it
                                            testedHash = null
                                            enabled = false
                                            testPassed = null
                                            testResult = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(if (builderMode == BuilderMode.SIMPLE) R.string.extension_builder_page_generated_input_schema else R.string.extension_builder_page_input_schema)) },
                                    minLines = 4,
                                    supportingText = {
                                        Text(
                                            when {
                                                !schemaIsValid -> stringResource(R.string.extension_builder_page_input_schema_invalid)
                                                builderMode == BuilderMode.SIMPLE -> stringResource(R.string.extension_builder_page_generated_input_schema_help)
                                                else -> stringResource(R.string.extension_builder_page_input_schema_help)
                                            }
                                        )
                                    },
                                    isError = !schemaIsValid,
                                )
                            }
                        }
                        item {
                            BuilderSection(title = stringResource(R.string.extension_builder_page_implementation_section)) {
                                OutlinedTextField(
                                    value = implementation,
                                    onValueChange = {
                                        if (it != implementation) {
                                            implementation = it
                                            testedHash = null
                                            enabled = false
                                            testPassed = null
                                            testResult = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(if (builderMode == BuilderMode.SIMPLE) R.string.extension_builder_page_generated_implementation else R.string.extension_builder_page_implementation)) },
                                    minLines = 6,
                                    supportingText = {
                                        Text(
                                            stringResource(
                                                if (builderMode == BuilderMode.SIMPLE) {
                                                    R.string.extension_builder_page_generated_implementation_help
                                                } else {
                                                    R.string.extension_builder_page_implementation_help
                                                }
                                            )
                                        )
                                    },
                                    isError = !implementationIsWithinLimit,
                                )
                                Text(
                                    "${implementation.length} / ${CustomExtensionLimits.MAX_IMPLEMENTATION_LENGTH}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (implementationIsWithinLimit) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        item {
                            BuilderSection(title = stringResource(R.string.extension_builder_page_test_section)) {
                                OutlinedTextField(
                                    value = testInput,
                                    onValueChange = {
                                        testInput = it
                                        testResult = ""
                                        testPassed = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(if (builderMode == BuilderMode.SIMPLE) R.string.extension_builder_page_generated_test_input else R.string.extension_builder_page_test_input)) },
                                    minLines = 3,
                                    supportingText = {
                                        Text(
                                            inputError ?: stringResource(
                                                if (builderMode == BuilderMode.SIMPLE) {
                                                    R.string.extension_builder_page_generated_test_input_help
                                                } else {
                                                    R.string.extension_builder_page_test_input_help
                                                }
                                            )
                                        )
                                    },
                                    isError = inputError != null,
                                )
                                if (hasCurrentTest) {
                                    Text(
                                        stringResource(R.string.extension_builder_page_current_test_valid),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.extension_builder_page_status_not_tested),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (testPassed == false && testResult.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.extension_builder_page_status_test_failed),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (testResult.isNotBlank()) {
                                    ReviewCode(testResult)
                                }
                            }
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_enablement)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = enabled,
                                    onCheckedChange = { checked -> enabled = checked && canEnable },
                                    enabled = canEnable,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extension_builder_page_enable))
                                    Text(
                                        stringResource(R.string.extension_builder_page_enable_requires_test),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!canEnable) {
                                Text(
                                    stringResource(R.string.extension_builder_page_enable_blocked),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    item {
                        BuilderSection(title = stringResource(R.string.extension_builder_page_permission)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extension_builder_page_need_approval))
                                    Text(
                                        stringResource(R.string.extension_builder_page_need_approval_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = needsApproval,
                                    onCheckedChange = { needsApproval = it },
                                )
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Text(
                                stringResource(R.string.extension_builder_page_sandbox_note),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isExistingDraft) {
                        item {
                            HorizontalDivider()
                            TextButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !isTesting,
                            ) {
                                Text(
                                    stringResource(R.string.extension_builder_page_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.extension_builder_page_delete_title)) },
            text = { Text(stringResource(R.string.extension_builder_page_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsStore.update { it.removeCustomExtension(draftId) }
                            showDeleteConfirmation = false
                            clearDraftState()
                        }
                    },
                ) {
                    Text(stringResource(R.string.extension_builder_page_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BuilderSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content(this)
        }
    }
}

@Composable
private fun ReviewSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content(this)
        }
    }
}

@Composable
private fun ReviewCode(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = value.ifBlank { "—" },
            modifier = Modifier.padding(12.dp).heightIn(max = 220.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

