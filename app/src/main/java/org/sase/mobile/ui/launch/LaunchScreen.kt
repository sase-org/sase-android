package org.sase.mobile.ui.launch

import android.net.Uri
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sase.mobile.data.agents.AgentActionState
import org.sase.mobile.data.agents.AgentFailureKind
import org.sase.mobile.data.agents.AgentFailure
import org.sase.mobile.data.agents.AgentsState
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.dto.MobileAgentImageLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchResultWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchSlotResultWire
import org.sase.mobile.data.api.dto.MobileAgentLaunchSlotStatusWire
import org.sase.mobile.data.api.dto.MobileAgentTextLaunchRequestWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadSummaryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagEntryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.helpers.HelperLoadResult
import org.sase.mobile.data.helpers.HelperRepository
import org.sase.mobile.ui.theme.SaseMobileTheme

@Composable
fun LaunchScreen(
    state: AgentsState,
    onLaunch: suspend (MobileAgentTextLaunchRequestWire) -> AgentActionState,
    onLaunchImage: suspend (MobileAgentImageLaunchRequestWire) -> AgentActionState = {
        AgentActionState.Failed("Image launch is unavailable")
    },
    onOpenAgents: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    helperRepository: HelperRepository? = null,
    helperEventVersion: Int = 0,
    prefillPrompt: String? = null,
    onPrefillConsumed: () -> Unit = {},
    initialHelperState: LaunchHelperState? = null,
    initialImageAttachment: SelectedImageAttachment? = null,
    imageAttachmentReader: ImageAttachmentReader? = null,
    requestIdFactory: () -> String = { "android-${UUID.randomUUID()}" },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolvedImageReader = imageAttachmentReader ?: remember(context) {
        AndroidImageAttachmentReader(context.applicationContext)
    }
    var prompt by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var displayName by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("") }
    var runtime by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var project by rememberSaveable { mutableStateOf("") }
    var requestId by rememberSaveable { mutableStateOf(requestIdFactory()) }
    var helperState by remember(initialHelperState) {
        mutableStateOf(initialHelperState ?: LaunchHelperState())
    }
    var selectedImage by remember(initialImageAttachment) {
        mutableStateOf(initialImageAttachment)
    }
    var imageError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun attachImage(uri: Uri, source: ImageAttachmentSource) {
        scope.launch {
            imageError = null
            when (val result = resolvedImageReader.describe(uri)) {
                is ImageAttachmentLoadResult.Success -> {
                    selectedImage = SelectedImageAttachment(uri, result.metadata, source)
                }

                is ImageAttachmentLoadResult.Failure -> {
                    imageError = result.error.userMessage()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            imageError = "No image selected."
        } else {
            attachImage(uri, ImageAttachmentSource.Gallery)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (success && uri != null) {
            attachImage(uri, ImageAttachmentSource.Camera)
        } else {
            imageError = "Camera capture was cancelled or permission was denied."
        }
        pendingCameraUri = null
    }

    suspend fun refreshHelpers() {
        val repository = helperRepository ?: return
        helperState = helperState.copy(loading = true, failureMessage = null)
        val tags = repository.changespecTags(project = project, limit = HelperLimit)
        val xprompts = repository.xpromptCatalog(project = project, limit = HelperLimit)
        val beads = repository.beads(project = project, status = "in_progress", limit = HelperLimit)
        helperState = LaunchHelperState.from(tags, xprompts, beads)
    }

    LaunchedEffect(prefillPrompt) {
        val value = prefillPrompt
        if (value != null) {
            prompt = TextFieldValue(value, selection = TextRange(value.length))
            onPrefillConsumed()
        }
    }

    LaunchedEffect(helperRepository, helperEventVersion) {
        refreshHelpers()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("launch_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "Launch", style = MaterialTheme.typography.titleLarge)

        LaunchActionBanner(
            action = state.action,
            failure = state.failure,
            onOpenSettings = onOpenSettings,
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            minLines = 8,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("launch_prompt_input"),
        )

        LaunchFields(
            displayName = displayName,
            name = name,
            provider = provider,
            runtime = runtime,
            model = model,
            project = project,
            requestId = requestId,
            onDisplayNameChange = { displayName = it },
            onNameChange = { name = it },
            onProviderChange = { provider = it },
            onRuntimeChange = { runtime = it },
            onModelChange = { model = it },
            onProjectChange = { project = it },
            onRequestIdChange = { requestId = it },
            onRegenerateRequestId = { requestId = requestIdFactory() },
        )

        ImageAttachPanel(
            selectedImage = selectedImage,
            imageError = imageError,
            launchEnabled = prompt.text.isNotBlank() &&
                selectedImage != null &&
                state.action !is AgentActionState.Running,
            onPickImage = {
                galleryLauncher.launch(arrayOf("image/*"))
            },
            onCaptureImage = {
                val uri = resolvedImageReader.createCameraCaptureUri()
                if (uri == null) {
                    imageError = "Camera capture is unavailable."
                } else {
                    pendingCameraUri = uri.toString()
                    cameraLauncher.launch(uri)
                }
            },
            onClearImage = {
                selectedImage = null
                imageError = null
            },
            onLaunchImage = {
                val image = selectedImage
                if (image == null) {
                    imageError = "Attach an image before launching."
                } else {
                    scope.launch {
                        imageError = null
                        when (val payloadResult = resolvedImageReader.encodedPayload(image.uri, image.metadata)) {
                            is ImageAttachmentPayloadResult.Success -> {
                                val payload = payloadResult.payload
                                val request = MobileAgentImageLaunchRequestWire(
                                    prompt = prompt.text,
                                    requestId = requestId.cleanLaunchField(),
                                    originalFilename = payload.metadata.displayName,
                                    contentType = payload.metadata.contentType,
                                    byteLength = payload.metadata.byteLength ?: 0,
                                    base64Image = payload.base64Image,
                                    displayName = displayName.cleanLaunchField(),
                                    name = name.cleanLaunchField(),
                                    provider = provider.cleanLaunchField(),
                                    runtime = runtime.cleanLaunchField(),
                                    model = model.cleanLaunchField(),
                                    project = project.cleanLaunchField(),
                                )
                                val result = onLaunchImage(request)
                                if (result is AgentActionState.Succeeded) {
                                    requestId = requestIdFactory()
                                }
                            }

                            is ImageAttachmentPayloadResult.Failure -> {
                                imageError = payloadResult.error.userMessage()
                            }
                        }
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = prompt.text.isNotBlank() && state.action !is AgentActionState.Running,
                onClick = {
                    val request = MobileAgentTextLaunchRequestWire(
                        prompt = prompt.text,
                        requestId = requestId.cleanLaunchField(),
                        displayName = displayName.cleanLaunchField(),
                        name = name.cleanLaunchField(),
                        provider = provider.cleanLaunchField(),
                        runtime = runtime.cleanLaunchField(),
                        model = model.cleanLaunchField(),
                        project = project.cleanLaunchField(),
                    )
                    scope.launch {
                        val result = onLaunch(request)
                        if (result is AgentActionState.Succeeded) {
                            requestId = requestIdFactory()
                        }
                    }
                },
                modifier = Modifier.testTag("launch_submit"),
            ) {
                Text("Launch")
            }
            OutlinedButton(onClick = onOpenAgents) {
                Text("Agents")
            }
            OutlinedButton(
                onClick = {
                    scope.launch { refreshHelpers() }
                },
                enabled = helperRepository != null,
            ) {
                Text("Refresh helpers")
            }
        }

        LaunchHelperInsertPanel(
            state = helperState,
            onInsert = { insertion ->
                prompt = insertPromptSnippet(prompt, insertion)
            },
            onSetProject = { selectedProject -> project = selectedProject },
        )

        RecentLaunchResults(
            results = state.recentLaunchResults,
            onOpenAgents = onOpenAgents,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageAttachPanel(
    selectedImage: SelectedImageAttachment?,
    imageError: String?,
    launchEnabled: Boolean,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onClearImage: () -> Unit,
    onLaunchImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Image", style = MaterialTheme.typography.titleSmall)
        selectedImage?.let { image ->
            Text(
                text = listOfNotNull(
                    image.metadata.displayName,
                    image.metadata.contentType,
                    image.metadata.byteLength?.let { "${it} bytes" },
                    image.source.label,
                ).joinToString(" - "),
                modifier = Modifier.testTag("launch_image_summary"),
            )
        }
        imageError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("launch_image_error"),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.testTag("launch_pick_image"),
            ) {
                Text("Gallery")
            }
            OutlinedButton(
                onClick = onCaptureImage,
                modifier = Modifier.testTag("launch_capture_image"),
            ) {
                Text("Camera")
            }
            OutlinedButton(
                onClick = onClearImage,
                enabled = selectedImage != null,
                modifier = Modifier.testTag("launch_clear_image"),
            ) {
                Text("Clear")
            }
            Button(
                onClick = onLaunchImage,
                enabled = launchEnabled,
                modifier = Modifier.testTag("launch_image_submit"),
            ) {
                Text("Launch image")
            }
        }
    }
}

@Composable
private fun LaunchFields(
    displayName: String,
    name: String,
    provider: String,
    runtime: String,
    model: String,
    project: String,
    requestId: String,
    onDisplayNameChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onRuntimeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onProjectChange: (String) -> Unit,
    onRequestIdChange: (String) -> Unit,
    onRegenerateRequestId: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = "Display",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTextField(
                value = provider,
                onValueChange = onProviderChange,
                label = "Provider",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = runtime,
                onValueChange = onRuntimeChange,
                label = "Runtime",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = model,
                onValueChange = onModelChange,
                label = "Model",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTextField(
                value = project,
                onValueChange = onProjectChange,
                label = "Project",
                modifier = Modifier.weight(1f),
            )
            CompactTextField(
                value = requestId,
                onValueChange = onRequestIdChange,
                label = "Request ID",
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRegenerateRequestId) {
                Text("New ID")
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaunchHelperInsertPanel(
    state: LaunchHelperState,
    onInsert: (String) -> Unit,
    onSetProject: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Helpers", style = MaterialTheme.typography.titleSmall)
        when {
            state.loading -> Text("Loading helpers.")
            state.failureMessage != null -> Text(state.failureMessage)
            state.isEmpty() -> Text("No helpers loaded.")
            else -> {
                state.projects.distinct().forEach { project ->
                    AssistChip(onClick = { onSetProject(project) }, label = { Text("Project $project") })
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.changespecs.take(8).forEach { entry ->
                        AssistChip(
                            onClick = { onInsert(entry.tag) },
                            label = { Text(entry.tag) },
                        )
                    }
                    state.xprompts.take(8).forEach { entry ->
                        AssistChip(
                            onClick = { onInsert("#${entry.name}") },
                            label = { Text("#${entry.displayLabel}") },
                        )
                    }
                    state.beads.take(8).forEach { bead ->
                        AssistChip(
                            onClick = { onInsert(bead.contextReference()) },
                            label = { Text(bead.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchActionBanner(
    action: AgentActionState,
    failure: AgentFailure?,
    onOpenSettings: () -> Unit,
) {
    when (action) {
        AgentActionState.Idle -> Unit
        is AgentActionState.Running -> Banner(text = action.label)
        is AgentActionState.Succeeded -> Banner(text = action.message)
        is AgentActionState.Failed -> {
            Banner(text = action.message)
            if (failure?.kind == AgentFailureKind.AuthExpired) {
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecentLaunchResults(
    results: List<MobileAgentLaunchResultWire>,
    onOpenAgents: () -> Unit,
) {
    if (results.isEmpty()) {
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("launch_result_slots"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recent launches",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenAgents) {
                Text("Open agents")
            }
        }
        results.take(3).forEach { result ->
            result.slots.forEach { slot ->
                LaunchSlotRow(slot)
            }
        }
    }
}

@Composable
private fun LaunchSlotRow(slot: MobileAgentLaunchSlotResultWire) {
    ElevatedAssistChip(
        onClick = {},
        label = {
            Text(
                listOfNotNull(
                    slot.slotId,
                    slot.name,
                    slot.status.label,
                    slot.message,
                ).joinToString(" - "),
            )
        },
    )
}

data class LaunchHelperState(
    val changespecs: List<MobileChangeSpecTagEntryWire> = emptyList(),
    val xprompts: List<MobileXpromptCatalogEntryWire> = emptyList(),
    val beads: List<MobileBeadSummaryWire> = emptyList(),
    val projects: List<String> = emptyList(),
    val loading: Boolean = false,
    val failureMessage: String? = null,
) {
    fun isEmpty(): Boolean = changespecs.isEmpty() && xprompts.isEmpty() && beads.isEmpty()

    companion object {
        fun from(
            changespecs: HelperLoadResult<MobileChangeSpecTagListResponseWire>,
            xprompts: HelperLoadResult<MobileXpromptCatalogResponseWire>,
            beads: HelperLoadResult<MobileBeadListResponseWire>,
        ): LaunchHelperState {
            val failures = listOf(changespecs.failureMessage(), xprompts.failureMessage(), beads.failureMessage())
                .filterNotNull()
            val changespecValue = (changespecs as? HelperLoadResult.Success)?.value
            val xpromptValue = (xprompts as? HelperLoadResult.Success)?.value
            val beadValue = (beads as? HelperLoadResult.Success)?.value
            return LaunchHelperState(
                changespecs = changespecValue?.tags.orEmpty(),
                xprompts = xpromptValue?.entries.orEmpty(),
                beads = beadValue?.beads.orEmpty(),
                projects = listOfNotNull(
                    changespecValue?.context?.project,
                    xpromptValue?.context?.project,
                    beadValue?.context?.project,
                ),
                failureMessage = failures.firstOrNull(),
            )
        }
    }
}

internal fun insertPromptSnippet(
    prompt: TextFieldValue,
    snippet: String,
): TextFieldValue {
    val range = prompt.selection
    val start = minOf(range.start, range.end).coerceIn(0, prompt.text.length)
    val end = maxOf(range.start, range.end).coerceIn(0, prompt.text.length)
    val text = prompt.text.replaceRange(start, end, snippet)
    val cursor = start + snippet.length
    return TextFieldValue(text = text, selection = TextRange(cursor))
}

private fun String.cleanLaunchField(): String? = trim().takeIf { it.isNotEmpty() }

private fun MobileBeadSummaryWire.contextReference(): String {
    return if (beadType == "phase") {
        "#bd/work_phase_bead:$id"
    } else {
        id
    }
}

private fun <T> HelperLoadResult<T>.failureMessage(): String? {
    return when (this) {
        HelperLoadResult.LoggedOut -> "Pair a gateway in settings before loading helpers."
        is HelperLoadResult.InvalidRequest -> message
        is HelperLoadResult.Failure -> error.userMessage()
        is HelperLoadResult.Success -> null
    }
}

private fun GatewayApiError.userMessage(): String {
    return when (this) {
        is GatewayApiError.Http -> apiError?.let { "${it.code.name}: ${it.message}" }
            ?: "HTTP $statusCode"

        is GatewayApiError.InvalidJson -> "Invalid helper response"
        is GatewayApiError.Transport -> "Gateway unavailable: ${kind.name}"
    }
}

private fun ImageAttachmentError.userMessage(): String {
    return when (this) {
        ImageAttachmentError.PermissionDenied -> "Image permission was denied."
        ImageAttachmentError.MissingContent -> "Selected image is no longer available."
        ImageAttachmentError.UnsupportedType -> "Use a PNG, JPEG, WEBP, or GIF image."
        ImageAttachmentError.Oversize -> "Image is larger than the 10 MB mobile upload limit."
        ImageAttachmentError.ReadFailed -> "Could not read the selected image."
    }
}

private val ImageAttachmentSource.label: String
    get() = when (this) {
        ImageAttachmentSource.Camera -> "camera"
        ImageAttachmentSource.Gallery -> "gallery"
    }

private val MobileAgentLaunchSlotStatusWire.label: String
    get() = when (this) {
        MobileAgentLaunchSlotStatusWire.Launched -> "launched"
        MobileAgentLaunchSlotStatusWire.DryRun -> "dry run"
        MobileAgentLaunchSlotStatusWire.Failed -> "failed"
    }

private const val HelperLimit = 20

@Preview(showBackground = true)
@Composable
private fun LaunchScreenPreview() {
    SaseMobileTheme {
        LaunchScreen(
            state = AgentsState(
                recentLaunchResults = listOf(previewLaunchResult()),
                action = AgentActionState.Succeeded("Launched: mobile-demo"),
            ),
            onLaunch = { AgentActionState.Succeeded("Launched: mobile-demo") },
            onOpenAgents = {},
            onOpenSettings = {},
            initialHelperState = previewHelperState(),
            requestIdFactory = { "android-preview" },
        )
    }
}

private fun previewLaunchResult() = MobileAgentLaunchResultWire(
    schemaVersion = 1,
    primary = MobileAgentLaunchSlotResultWire(
        slotId = "primary",
        name = "mobile-demo",
        status = MobileAgentLaunchSlotStatusWire.Launched,
    ),
    slots = listOf(
        MobileAgentLaunchSlotResultWire(
            slotId = "primary",
            name = "mobile-demo",
            status = MobileAgentLaunchSlotStatusWire.Launched,
        ),
    ),
)

private fun previewHelperState(): LaunchHelperState {
    return LaunchHelperState(
        changespecs = listOf(
            MobileChangeSpecTagEntryWire(
                tag = "#gh:mobile-launch",
                project = "sase",
                changespec = "mobile-launch",
                title = "Mobile launch UI",
                status = "WIP",
            ),
        ),
        xprompts = listOf(
            MobileXpromptCatalogEntryWire(
                name = "bd/work_phase_bead",
                displayLabel = "bd/work_phase_bead",
                sourceBucket = "project",
                project = "sase",
                tags = listOf("bead"),
                isSkill = false,
            ),
        ),
        beads = listOf(
            MobileBeadSummaryWire(
                id = "sase-26.6.5",
                title = "Text Launch Screen",
                status = "in_progress",
                beadType = "phase",
                tier = null,
                project = "sase",
                dependencyCount = 1,
                blockCount = 1,
                childCount = 0,
            ),
        ),
        projects = listOf("sase"),
    )
}
