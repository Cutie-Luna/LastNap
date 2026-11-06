package me.rerere.rikkahub.di

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.tools.local.CustomToolSecretStore
import me.rerere.rikkahub.data.ai.tools.local.CustomToolStorage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.ChatRequestFactory
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.service.ChatFloatingTracker
import me.rerere.rikkahub.service.ChatNotificationManager
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.MediaCreationService
import me.rerere.rikkahub.ui.floating.ChatFloatingWindows
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    // Recover tool-owned files after a process dies during tool deletion. Conversation media
    // is deliberately outside these stores and is never part of this cleanup.
    single(createdAtStart = true) {
        val settingsStore = get<SettingsStore>()
        val secretStore = get<CustomToolSecretStore>()
        val storage = get<CustomToolStorage>()
        val filesManager = get<FilesManager>()
        val conversationRepository = get<ConversationRepository>()
        get<AppScope>().launch {
            val settings = settingsStore.awaitLoaded()
            try {
                withContext(Dispatchers.IO) {
                    val declaredSecretsByTool = settings.customExtensions.associate { extension ->
                        extension.id to extension.secrets.mapTo(mutableSetOf()) { it.name }
                    }
                    try {
                        secretStore.reconcile(declaredSecretsByTool)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("CustomToolCleanup", "Could not reconcile custom-tool Secrets", error)
                    }
                    try {
                        storage.reconcile(declaredSecretsByTool.keys)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("CustomToolCleanup", "Could not reconcile custom-tool storage", error)
                    }
                    try {
                        filesManager.reconcileConversationMedia(conversationRepository::hasFileReference)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("ConversationMediaCleanup", "Could not reconcile conversation media", error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("CustomToolCleanup", "Could not reconcile orphaned custom-tool data", error)
            }
        }
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        Firebase.crashlytics
    }

    single {
        Firebase.analytics
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get(), get(), get())
    }

    // 生成通知与业务解耦：ChatService 只发事件，通知由这里消费；
    // createdAtStart 保证进程启动即订阅，否则后台生成的事件会因无订阅者而丢失
    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    // ChatService 只在后台才用到，延迟创建
    single {
        ChatFloatingTracker(
            appScope = get(),
            lazyChatService = lazy { get() },
            settingsStore = get(),
        )
    }

    // 同上，进程启动即开始监听前后台切换
    single(createdAtStart = true) {
        ChatFloatingWindows(
            context = get(),
            appScope = get(),
            tracker = get(),
            lazyChatService = lazy { get() },
        )
    }

    single {
        ChatToolFactory(
            context = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
            customToolHttpRuntime = get(),
        )
    }

    single {
        ChatRequestFactory(
            context = get(),
            providerManager = get(),
            usageRecorder = get(),
            chatToolFactory = get(),
            memoryRepository = get(),
            templateTransformer = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            requestFactory = get(),
            memoryConsolidationScheduler = get(),
            translationHandler = get(),
            metaGenerator = get(),
            mcpManager = get(),
            filesManager = get(),
            folderRepository = get()
        )
    }

    single {
        MediaCreationService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            repository = get(),
            manager = get(),
            remoteFileStore = get(),
            okHttpClient = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
