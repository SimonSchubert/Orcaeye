package com.inspiredandroid.orcaeye

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.inspiredandroid.orcaeye.data.CrontabRepository
import com.inspiredandroid.orcaeye.data.InventoryRepository
import com.inspiredandroid.orcaeye.ui.BrowserScreen
import com.inspiredandroid.orcaeye.ui.InventoryViewModel
import com.inspiredandroid.orcaeye.ui.LoopsActions
import com.inspiredandroid.orcaeye.ui.LoopsViewModel
import com.inspiredandroid.orcaeye.ui.theme.OrcaeyeTheme

@Composable
fun App(
    repository: InventoryRepository,
    crontabRepository: CrontabRepository,
) {
    val viewModel = remember(repository) { InventoryViewModel(repository) }
    val loopsViewModel = remember(crontabRepository) { LoopsViewModel(crontabRepository) }
    val state by viewModel.state.collectAsState()
    val loopsState by loopsViewModel.state.collectAsState()

    val loopsActions =
        remember(loopsViewModel, viewModel) {
            LoopsActions(
                onRefresh = loopsViewModel::refresh,
                onCreate = { loopsViewModel.startCreate() },
                onEdit = loopsViewModel::startEdit,
                onEditorChange = loopsViewModel::updateEditor,
                onEditorSave = loopsViewModel::saveEditor,
                onEditorCancel = loopsViewModel::cancelEditor,
                onToggleEnabled = loopsViewModel::toggleEnabled,
                onRunNow = loopsViewModel::runNow,
                onViewLog = { job ->
                    // Logs open in the Context editor pane, read-only.
                    job.logPath?.let { path ->
                        viewModel.openFile(
                            path = path,
                            title = "${job.name}.log",
                            deletePath = null,
                            canDelete = false,
                            readOnly = true,
                        )
                    }
                },
                onRequestDelete = loopsViewModel::requestDelete,
                onConfirmDelete = loopsViewModel::confirmDelete,
                onCancelDelete = loopsViewModel::cancelDelete,
                onClearStatus = loopsViewModel::clearStatus,
            )
        }

    OrcaeyeTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BrowserScreen(
                state = state,
                loopsState = loopsState,
                loopsActions = loopsActions,
                onSelectSection = viewModel::selectSection,
                onRefresh = viewModel::refresh,
                onSelectSystem = viewModel::selectSystem,
                onSelectProject = viewModel::selectProject,
                onOpenSkill = viewModel::openSkill,
                onOpenMemory = viewModel::openMemory,
                onOpenFile = { path, title -> viewModel.openFile(path, title) },
                onDraftChange = viewModel::updateDraft,
                onSave = viewModel::saveDraft,
                onClosePreview = viewModel::closePreview,
                onShowCreate = viewModel::showCreate,
                onDismissCreate = viewModel::dismissCreate,
                onCreate = viewModel::create,
                onRequestDeleteFromEditor = viewModel::requestDeleteFromEditor,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onClearStatus = viewModel::clearStatus,
                onOpenTool = viewModel::openTool,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
