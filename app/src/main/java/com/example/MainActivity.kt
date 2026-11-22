package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.CanonicalMapperScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.ProviderDetailScreen
import com.example.ui.screens.SearchAndExportScreen
import com.example.ui.screens.SettingsOverviewScreen
import com.example.ui.theme.PolyChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (prefs.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            PolyChatTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs.dynamicColorEnabled,
            ) {
                val screen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val providers by viewModel.providers.collectAsStateWithLifecycle()
                val models by viewModel.models.collectAsStateWithLifecycle()
                val aliases by viewModel.aliases.collectAsStateWithLifecycle()
                val conversations by viewModel.conversations.collectAsStateWithLifecycle()
                val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
                val turns by viewModel.activeTurns.collectAsStateWithLifecycle()
                val versions by viewModel.activeVersions.collectAsStateWithLifecycle()
                val participants by viewModel.activeRoundtableParticipants.collectAsStateWithLifecycle()
                val presets by viewModel.presets.collectAsStateWithLifecycle()
                val templates by viewModel.templates.collectAsStateWithLifecycle()
                val keyStatusMap by viewModel.providerKeyStatus.collectAsStateWithLifecycle()
                val probeQueueState by viewModel.probeQueueState.collectAsStateWithLifecycle()
                val probingKeys by viewModel.probingKeys.collectAsStateWithLifecycle()
                val activeStreamingIds by viewModel.activeStreamingConvIds.collectAsStateWithLifecycle()
                val failoverPrompt by viewModel.failoverPrompt.collectAsStateWithLifecycle()
                val roundtableState by viewModel.roundtableState.collectAsStateWithLifecycle()
                val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(snackbarMessage) {
                    snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackbar()
                    }
                }

                AnimatedContent(
                    targetState = screen,
                    label = "screen_transition",
                ) { current ->
                    when (current) {
                        AppScreen.Chat -> {
                            val isConvStreaming = activeConversation?.id?.let { it in activeStreamingIds } == true
                            ChatWorkspaceScreen(
                                conversations = conversations,
                                activeConversation = activeConversation,
                                turns = turns,
                                versions = versions,
                                roundtableParticipants = participants,
                                providers = providers,
                                models = models,
                                presets = presets,
                                templates = templates,
                                keyStatusMap = keyStatusMap,
                                probingKeys = probingKeys,
                                isStreaming = isConvStreaming,
                                failoverPrompt = failoverPrompt,
                                roundtableState = roundtableState,
                                snackbarHostState = snackbarHostState,
                                onSelectConversation = viewModel::selectConversation,
                                onCreateConversation = viewModel::createNewConversation,
                                onDeleteConversation = viewModel::deleteConversation,
                                onSelectSingleModel = viewModel::selectSingleModel,
                                onApplyParallelModels = viewModel::updateParallelModels,
                                onSetParallelMergeStrategy = viewModel::setParallelMergeStrategy,
                                onAdoptParallelBranch = viewModel::adoptParallelBranch,
                                onApplyRoundtableConfig = viewModel::saveRoundtableConfig,
                                onSendMessage = viewModel::sendUserMessage,
                                onStopGeneration = viewModel::stopActiveGeneration,
                                onRegenerateMessage = viewModel::regenerateMessage,
                                onSelectMessageVersion = viewModel::selectMessageVersion,
                                onEditMessageLocally = viewModel::editMessageContentLocally,
                                onEditUserAndResend = viewModel::editUserMessageAndResend,
                                onDeleteTurn = viewModel::deleteTurn,
                                onDeleteTurnAndFollowing = viewModel::deleteTurnAndFollowing,
                                onForkFromTurn = viewModel::forkFromTurn,
                                onConfirmFailoverSwitch = viewModel::confirmFailoverSwitch,
                                onResumeFailoverWithModel = viewModel::resumeFailoverWithModel,
                                onDismissFailover = viewModel::dismissFailoverPrompt,
                                onStartOrResumeRoundtable = viewModel::startOrResumeRoundtable,
                                onPauseRoundtable = viewModel::pauseRoundtable,
                                onStepNextRoundtableTurn = viewModel::stepNextRoundtableTurn,
                                onInjectRoundtableGuidance = viewModel::injectRoundtableMessage,
                                onProbeSingle = viewModel::probeSingleModel,
                                onApplyPreset = viewModel::applyPresetToActiveConversation,
                                onSaveConversationParams = viewModel::saveConversationParameters,
                                onSaveGlobalParams = viewModel::saveGlobalParameters,
                                onSaveCustomPreset = viewModel::saveCustomPreset,
                                onSavePromptTemplate = viewModel::savePromptTemplate,
                                onSaveProxyCredentials = viewModel::saveProxyCredentials,
                                onOpenSettings = { viewModel.navigateTo(AppScreen.SettingsOverview) },
                                onOpenSearchAndExport = { viewModel.navigateTo(AppScreen.SearchAndExport) },
                                onOpenCanonicalMapping = { viewModel.navigateTo(AppScreen.CanonicalMapping) },
                            )
                        }

                        AppScreen.SettingsOverview -> {
                            SettingsOverviewScreen(
                                providers = providers,
                                models = models,
                                keyStatusMap = keyStatusMap,
                                preferences = prefs,
                                onBack = { viewModel.navigateBack() },
                                onOpenProvider = { id -> viewModel.navigateTo(AppScreen.ProviderDetail(id)) },
                                onToggleProviderEnabled = viewModel::toggleProviderEnabled,
                                onAddCustomProvider = viewModel::addCustomProvider,
                                onToggleSilentFailover = viewModel::setSilentAutoFailover,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetDynamicColor = viewModel::setDynamicColorEnabled,
                                onOpenCanonicalMapping = { viewModel.navigateTo(AppScreen.CanonicalMapping) },
                            )
                        }

                        is AppScreen.ProviderDetail -> {
                            val targetProvider = providers.firstOrNull { it.id == current.providerId }
                            if (targetProvider != null) {
                                val providerModels = remember(models, targetProvider.id) {
                                    models.filter { it.providerId == targetProvider.id }
                                }
                                ProviderDetailScreen(
                                    provider = targetProvider,
                                    models = providerModels,
                                    probeQueueState = probeQueueState,
                                    probingKeys = probingKeys,
                                    loadPlainApiKey = viewModel::getPlainApiKey,
                                    onBack = { viewModel.navigateBack() },
                                    onSave = viewModel::saveProviderDetail,
                                    onDeleteCustomProvider = viewModel::deleteCustomProvider,
                                    onTestConnection = viewModel::testProviderConnection,
                                    onSyncModels = viewModel::syncProviderModels,
                                    onAddManualModel = viewModel::addManualModel,
                                    onToggleModelSelected = viewModel::toggleModelSelected,
                                    onDeleteModel = viewModel::deleteModel,
                                    onProbeSingle = viewModel::probeSingleModel,
                                    onProbeBatch = viewModel::probeBatchModels,
                                    onCancelBatchProbe = viewModel::cancelBatchProbe,
                                )
                            } else {
                                viewModel.navigateBack()
                            }
                        }

                        AppScreen.SearchAndExport -> {
                            SearchAndExportScreen(
                                searchResults = searchResults,
                                hasActiveConversation = activeConversation != null,
                                onBack = { viewModel.navigateBack() },
                                onSearch = viewModel::searchMessages,
                                onOpenConversation = viewModel::selectConversation,
                                onExportJson = viewModel::exportAllJson,
                                onImportJson = viewModel::importJsonBackup,
                                onExportMarkdown = viewModel::exportActiveConversationMarkdown,
                                onShowMessage = viewModel::showMessage,
                            )
                        }

                        AppScreen.CanonicalMapping -> {
                            CanonicalMapperScreen(
                                aliases = aliases,
                                models = models,
                                providers = providers,
                                onBack = { viewModel.navigateBack() },
                                onAddAlias = viewModel::addCanonicalAlias,
                                onDeleteAlias = viewModel::deleteCanonicalAlias,
                                onBindModelCanonicalId = viewModel::bindModelCanonicalId,
                            )
                        }
                    }
                }
            }
        }
    }
}
