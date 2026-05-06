package org.sase.mobile.ui.helpers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.sase.mobile.R
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.dto.MobileBeadDetailWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadShowResponseWire
import org.sase.mobile.data.api.dto.MobileBeadSummaryWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagEntryWire
import org.sase.mobile.data.api.dto.MobileHelperProjectContextWire
import org.sase.mobile.data.api.dto.MobileHelperProjectScopeWire
import org.sase.mobile.data.api.dto.MobileHelperResultWire
import org.sase.mobile.data.api.dto.MobileHelperSkippedWire
import org.sase.mobile.data.api.dto.MobileHelperStatusWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogEntryWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogStatsWire
import org.sase.mobile.data.helpers.HelperLoadResult
import org.sase.mobile.data.helpers.HelperRepository
import org.sase.mobile.ui.theme.SaseMobileTheme

@Composable
fun HelpersScreen(
    modifier: Modifier = Modifier,
    repository: HelperRepository? = null,
    initialState: HelpersScreenState? = null,
    helperEventVersion: Int = 0,
    onInsertHelper: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(HelperTab.Changespecs) }
    var projectFilter by remember { mutableStateOf("") }
    var xpromptQuery by remember { mutableStateOf("") }
    var beadStatus by remember { mutableStateOf("") }
    var beadType by remember { mutableStateOf("") }
    var beadTier by remember { mutableStateOf("") }
    var state by remember(initialState) {
        mutableStateOf(initialState ?: HelpersScreenState())
    }

    suspend fun refresh() {
        val helperRepository = repository ?: return
        state = state.copy(
            changespecTags = HelperPaneState.Loading,
            xpromptCatalog = HelperPaneState.Loading,
            beadList = HelperPaneState.Loading,
        )
        state = state.copy(
            changespecTags = helperRepository.changespecTags(
                project = projectFilter,
                limit = HelperRequestLimit,
            ).toPaneState(),
            xpromptCatalog = helperRepository.xpromptCatalog(
                project = projectFilter,
                query = xpromptQuery,
                includePdf = true,
                limit = HelperRequestLimit,
            ).toPaneState(),
            beadList = helperRepository.beads(
                project = projectFilter,
                status = beadStatus,
                beadType = beadType,
                tier = beadTier,
                includeClosed = beadStatus.isBlank(),
                limit = HelperRequestLimit,
            ).toPaneState(),
        )
    }

    LaunchedEffect(repository, helperEventVersion) {
        if (repository != null) {
            refresh()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("helpers_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Helpers",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch { refresh() }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24),
                    contentDescription = "Refresh helpers",
                )
            }
        }

        OutlinedTextField(
            value = projectFilter,
            onValueChange = { projectFilter = it },
            label = { Text("Project") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            HelperTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }

        when (selectedTab) {
            HelperTab.Changespecs -> ChangespecPane(
                state = state.changespecTags,
                query = xpromptQuery,
                onQueryChange = { xpromptQuery = it },
                onInsert = onInsertHelper,
            )

            HelperTab.Xprompts -> XpromptPane(
                state = state.xpromptCatalog,
                query = xpromptQuery,
                onQueryChange = { xpromptQuery = it },
                onInsert = onInsertHelper,
            )

            HelperTab.Beads -> BeadPane(
                state = state.beadList,
                detailState = state.beadDetail,
                status = beadStatus,
                beadType = beadType,
                tier = beadTier,
                onStatusChange = { beadStatus = it },
                onTypeChange = { beadType = it },
                onTierChange = { beadTier = it },
                onOpenBead = { beadId ->
                    val helperRepository = repository ?: return@BeadPane
                    scope.launch {
                        state = state.copy(beadDetail = HelperPaneState.Loading)
                        state = state.copy(
                            beadDetail = helperRepository.beadDetail(
                                beadId = beadId,
                                project = projectFilter,
                            ).toBeadDetailPaneState(),
                        )
                    }
                },
                onInsert = onInsertHelper,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangespecPane(
    state: HelperPaneState<MobileChangeSpecTagListResponseWire>,
    query: String,
    onQueryChange: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    HelperStateContent(state) { response ->
        val entries = response.tags.filterChangespecs(query)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HelperResultBanner(response.result, response.context)
            SearchBox(query, onQueryChange)
            if (entries.isEmpty()) {
                EmptyText("No ChangeSpec tags match the current filters.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.tag }) { entry ->
                        ChangespecRow(entry = entry, onInsert = onInsert)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XpromptPane(
    state: HelperPaneState<MobileXpromptCatalogResponseWire>,
    query: String,
    onQueryChange: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    HelperStateContent(state) { response ->
        val entries = response.entries.filterXprompts(query)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HelperResultBanner(response.result, response.context)
            SearchBox(query, onQueryChange)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(onClick = {}, label = { Text("${response.stats.totalCount} total") })
                AssistChip(onClick = {}, label = { Text("${response.stats.projectCount} project") })
                AssistChip(onClick = {}, label = { Text("${response.stats.skillCount} skills") })
                response.catalogAttachment?.let {
                    AssistChip(onClick = {}, label = { Text(it.displayName) })
                }
            }
            if (entries.isEmpty()) {
                EmptyText("No xprompts match the current filters.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries, key = { it.name }) { entry ->
                        XpromptRow(entry = entry, onInsert = onInsert)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeadPane(
    state: HelperPaneState<MobileBeadListResponseWire>,
    detailState: HelperPaneState<MobileBeadDetailWire>,
    status: String,
    beadType: String,
    tier: String,
    onStatusChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onTierChange: (String) -> Unit,
    onOpenBead: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    HelperStateContent(state) { response ->
        val beads = response.beads
            .filterBy(status) { it.status }
            .filterBy(beadType) { it.beadType }
            .filterBy(tier) { it.tier.orEmpty() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HelperResultBanner(response.result, response.context)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChoice("All status", "", status, onStatusChange)
                FilterChoice("Open", "open", status, onStatusChange)
                FilterChoice("In progress", "in_progress", status, onStatusChange)
                FilterChoice("Closed", "closed", status, onStatusChange)
                FilterChoice("Plan", "plan", beadType, onTypeChange)
                FilterChoice("Phase", "phase", beadType, onTypeChange)
                FilterChoice("Epic", "epic", tier, onTierChange)
            }
            if (beads.isEmpty()) {
                EmptyText("No beads match the current filters.")
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(beads, key = { it.id }) { bead ->
                        BeadRow(bead = bead, onOpenBead = onOpenBead, onInsert = onInsert)
                        HorizontalDivider()
                    }
                }
            }
            BeadDetailPanel(state = detailState, onInsert = onInsert)
        }
    }
}

@Composable
private fun ChangespecRow(
    entry: MobileChangeSpecTagEntryWire,
    onInsert: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ListItem(
        overlineContent = { Text("${entry.project.orEmpty()} ${entry.status}".trim()) },
        headlineContent = {
            Text(entry.changespec, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entry.title?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(entry.tag, fontFamily = FontFamily.Monospace)
                entry.sourcePathDisplay?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        trailingContent = {
            HelperActions(
                copyLabel = "Copy",
                insertLabel = "Insert",
                onCopy = { clipboard.setText(AnnotatedString(entry.tag)) },
                onInsert = { onInsert(entry.tag) },
            )
        },
    )
}

@Composable
private fun XpromptRow(
    entry: MobileXpromptCatalogEntryWire,
    onInsert: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val reference = "#${entry.name}"
    ListItem(
        overlineContent = { Text("${entry.sourceBucket} ${entry.project.orEmpty()}".trim()) },
        headlineContent = {
            Text(entry.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entry.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (entry.tags.isNotEmpty()) {
                    Text(entry.tags.joinToString(", "))
                }
                entry.contentPreview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        },
        trailingContent = {
            HelperActions(
                copyLabel = "Copy",
                insertLabel = "Insert",
                onCopy = { clipboard.setText(AnnotatedString(reference)) },
                onInsert = { onInsert(reference) },
            )
        },
    )
}

@Composable
private fun BeadRow(
    bead: MobileBeadSummaryWire,
    onOpenBead: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ListItem(
        modifier = Modifier.clickable { onOpenBead(bead.id) },
        overlineContent = { Text("${bead.status} ${bead.beadType} ${bead.tier.orEmpty()}".trim()) },
        headlineContent = { Text(bead.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(bead.id, fontFamily = FontFamily.Monospace)
                bead.project?.let { Text(it) }
                bead.planPathDisplay?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        trailingContent = {
            HelperActions(
                copyLabel = "Copy ID",
                insertLabel = "Insert",
                onCopy = { clipboard.setText(AnnotatedString(bead.id)) },
                onInsert = { onInsert(bead.id) },
            )
        },
    )
}

@Composable
private fun BeadDetailPanel(
    state: HelperPaneState<MobileBeadDetailWire>,
    onInsert: (String) -> Unit,
) {
    when (state) {
        HelperPaneState.Loading -> Text("Loading bead detail.")
        HelperPaneState.LoggedOut -> Text("Pair a gateway before opening bead detail.")
        is HelperPaneState.InvalidRequest -> Text(state.message)
        is HelperPaneState.Failed -> Text(state.error.userMessage())
        is HelperPaneState.Ready -> {
            val detail = state.value
            val summary = detail.summary
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(summary.id, style = MaterialTheme.typography.titleMedium)
                    Text(summary.title)
                    detail.description?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    detail.designPathDisplay?.let { Text("Design: $it") }
                    Text("Depends ${detail.dependencies.size} · blocks ${detail.blocks.size} · children ${detail.children.size}")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(onClick = { onInsert(summary.id) }) {
                            Text("Insert ID")
                        }
                        Button(onClick = { onInsert(summary.contextReference()) }) {
                            Text("Context")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelperActions(
    copyLabel: String,
    insertLabel: String,
    onCopy: () -> Unit,
    onInsert: () -> Unit,
) {
    Column {
        TextButton(onClick = onCopy) {
            Text(copyLabel)
        }
        TextButton(onClick = onInsert) {
            Text(insertLabel)
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("helper_search_input"),
    )
}

@Composable
private fun FilterChoice(
    label: String,
    value: String,
    selectedValue: String,
    onChange: (String) -> Unit,
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onChange(value) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelperResultBanner(
    result: MobileHelperResultWire,
    context: MobileHelperProjectContextWire,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(result.status.label) })
                AssistChip(onClick = {}, label = { Text(context.scope.label) })
                context.project?.let { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            result.message?.let { Text(it) }
            result.warnings.forEach { Text("Warning: $it") }
            result.skipped.forEach { skipped ->
                Text("Skipped ${skipped.target ?: "entry"}: ${skipped.reason}")
            }
            result.partialFailureCount?.takeIf { it > 0 }?.let {
                Text("$it partial failure(s)")
            }
        }
    }
}

@Composable
private fun <T> HelperStateContent(
    state: HelperPaneState<T>,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        HelperPaneState.Loading -> EmptyText("Loading helpers.")
        HelperPaneState.LoggedOut -> EmptyText("Pair a gateway in settings before using helpers.")
        is HelperPaneState.InvalidRequest -> EmptyText(state.message)
        is HelperPaneState.Failed -> EmptyText(state.error.userMessage())
        is HelperPaneState.Ready -> content(state.value)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

data class HelpersScreenState(
    val changespecTags: HelperPaneState<MobileChangeSpecTagListResponseWire> = HelperPaneState.Loading,
    val xpromptCatalog: HelperPaneState<MobileXpromptCatalogResponseWire> = HelperPaneState.Loading,
    val beadList: HelperPaneState<MobileBeadListResponseWire> = HelperPaneState.Loading,
    val beadDetail: HelperPaneState<MobileBeadDetailWire> = HelperPaneState.InvalidRequest(
        "Choose a bead to inspect its context.",
    ),
)

sealed interface HelperPaneState<out T> {
    data object Loading : HelperPaneState<Nothing>
    data object LoggedOut : HelperPaneState<Nothing>
    data class InvalidRequest(val message: String) : HelperPaneState<Nothing>
    data class Failed(val error: GatewayApiError) : HelperPaneState<Nothing>
    data class Ready<T>(val value: T) : HelperPaneState<T>
}

private enum class HelperTab(val label: String) {
    Changespecs("ChangeSpecs"),
    Xprompts("Xprompts"),
    Beads("Beads"),
}

private fun <T> HelperLoadResult<T>.toPaneState(): HelperPaneState<T> {
    return when (this) {
        HelperLoadResult.LoggedOut -> HelperPaneState.LoggedOut
        is HelperLoadResult.InvalidRequest -> HelperPaneState.InvalidRequest(message)
        is HelperLoadResult.Success -> HelperPaneState.Ready(value)
        is HelperLoadResult.Failure -> HelperPaneState.Failed(error)
    }
}

private fun HelperLoadResult<MobileBeadShowResponseWire>.toBeadDetailPaneState(): HelperPaneState<MobileBeadDetailWire> {
    return when (this) {
        HelperLoadResult.LoggedOut -> HelperPaneState.LoggedOut
        is HelperLoadResult.InvalidRequest -> HelperPaneState.InvalidRequest(message)
        is HelperLoadResult.Success -> HelperPaneState.Ready(value.bead)
        is HelperLoadResult.Failure -> HelperPaneState.Failed(error)
    }
}

private fun List<MobileChangeSpecTagEntryWire>.filterChangespecs(
    query: String,
): List<MobileChangeSpecTagEntryWire> {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return this
    return filter {
        it.tag.contains(cleanQuery, ignoreCase = true) ||
            it.changespec.contains(cleanQuery, ignoreCase = true) ||
            it.title.orEmpty().contains(cleanQuery, ignoreCase = true)
    }
}

private fun List<MobileXpromptCatalogEntryWire>.filterXprompts(
    query: String,
): List<MobileXpromptCatalogEntryWire> {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return this
    return filter {
        it.name.contains(cleanQuery, ignoreCase = true) ||
            it.displayLabel.contains(cleanQuery, ignoreCase = true) ||
            it.description.orEmpty().contains(cleanQuery, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(cleanQuery, ignoreCase = true) }
    }
}

private fun List<MobileBeadSummaryWire>.filterBy(
    value: String,
    selector: (MobileBeadSummaryWire) -> String,
): List<MobileBeadSummaryWire> {
    val cleanValue = value.trim()
    if (cleanValue.isEmpty()) return this
    return filter { selector(it).equals(cleanValue, ignoreCase = true) }
}

private fun MobileBeadSummaryWire.contextReference(): String {
    return if (beadType == "phase") {
        "#bd/work_phase_bead:$id"
    } else {
        id
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

private val MobileHelperStatusWire.label: String
    get() = when (this) {
        MobileHelperStatusWire.Success -> "Success"
        MobileHelperStatusWire.PartialSuccess -> "Partial success"
        MobileHelperStatusWire.Skipped -> "Skipped"
        MobileHelperStatusWire.Failed -> "Failed"
    }

private val MobileHelperProjectScopeWire.label: String
    get() = when (this) {
        MobileHelperProjectScopeWire.Explicit -> "Explicit project"
        MobileHelperProjectScopeWire.DeviceDefault -> "Device project"
        MobileHelperProjectScopeWire.AllKnown -> "All projects"
        MobileHelperProjectScopeWire.Unspecified -> "Unspecified"
    }

private const val HelperRequestLimit = 100

@Preview(showBackground = true)
@Composable
private fun HelpersScreenPreview() {
    SaseMobileTheme {
        HelpersScreen(initialState = previewState())
    }
}

private fun previewState(): HelpersScreenState {
    val result = MobileHelperResultWire(
        status = MobileHelperStatusWire.PartialSuccess,
        message = "loaded helpers",
        warnings = listOf("one project was skipped"),
        skipped = listOf(MobileHelperSkippedWire(target = "sase/skipped", reason = "missing metadata")),
        partialFailureCount = 1,
    )
    val context = MobileHelperProjectContextWire("sase", MobileHelperProjectScopeWire.Explicit)
    val bead = MobileBeadSummaryWire(
        id = "sase-26.6.7",
        title = "Workflow Helper Screens And Pickers",
        status = "in_progress",
        beadType = "phase",
        project = "sase",
        parentId = "sase-26.6",
        assignee = "sase-26.6.7",
        updatedAt = null,
        dependencyCount = 1,
        blockCount = 1,
        childCount = 0,
    )
    return HelpersScreenState(
        changespecTags = HelperPaneState.Ready(
            MobileChangeSpecTagListResponseWire(
                schemaVersion = 1,
                result = result,
                context = context,
                tags = listOf(
                    MobileChangeSpecTagEntryWire(
                        tag = "#gh:mobile-helper",
                        project = "sase",
                        changespec = "mobile-helper",
                        title = "Mobile helper UI",
                        status = "WIP",
                        workflow = "gh",
                    ),
                ),
                totalCount = 1,
            ),
        ),
        xpromptCatalog = HelperPaneState.Ready(
            MobileXpromptCatalogResponseWire(
                schemaVersion = 1,
                result = result,
                context = context,
                entries = listOf(
                    MobileXpromptCatalogEntryWire(
                        name = "bd/work_phase_bead",
                        displayLabel = "bd/work_phase_bead",
                        description = "Work a claimed phase bead",
                        sourceBucket = "project",
                        project = "sase",
                        tags = listOf("bead", "mobile"),
                        inputSignature = "bead_id",
                        isSkill = false,
                        contentPreview = "Read the bead and complete the phase.",
                    ),
                ),
                stats = MobileXpromptCatalogStatsWire(1, 1, 0, false),
            ),
        ),
        beadList = HelperPaneState.Ready(
            MobileBeadListResponseWire(
                schemaVersion = 1,
                result = result,
                context = context,
                beads = listOf(bead),
                totalCount = 1,
            ),
        ),
        beadDetail = HelperPaneState.Ready(
            MobileBeadDetailWire(
                summary = bead,
                description = "Expose native helper flows.",
                notes = null,
                designPathDisplay = "sdd/epics/202605/mobile_gateway_epic_6.md",
                dependencies = listOf("sase-26.6.1"),
                blocks = listOf("sase-26.6.9"),
                children = emptyList(),
                workspaceDisplay = "sase-android",
            ),
        ),
    )
}
