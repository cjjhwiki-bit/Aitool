package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.WorkstationRepository
import com.example.data.security.SecureCredentialStore
import com.example.domain.CanonicalModelMapper
import com.example.domain.ChatOrchestrator
import com.example.domain.ExportImportManager
import com.example.domain.ModelProbeQueue
import com.example.network.AiNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PolyChatApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var credentialStore: SecureCredentialStore
        private set
    lateinit var networkClient: AiNetworkClient
        private set
    lateinit var canonicalMapper: CanonicalModelMapper
        private set
    lateinit var repository: WorkstationRepository
        private set
    lateinit var probeQueue: ModelProbeQueue
        private set
    lateinit var chatOrchestrator: ChatOrchestrator
        private set
    lateinit var exportImportManager: ExportImportManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        credentialStore = SecureCredentialStore(this)
        networkClient = AiNetworkClient()
        canonicalMapper = CanonicalModelMapper()
        repository = WorkstationRepository(
            db = database,
            credentialStore = credentialStore,
            networkClient = networkClient,
            canonicalMapper = canonicalMapper,
        )
        probeQueue = ModelProbeQueue(
            scope = appScope,
            modelDao = database.modelDao(),
            providerDao = database.providerDao(),
            credentialStore = credentialStore,
            networkClient = networkClient,
        )
        chatOrchestrator = ChatOrchestrator(
            scope = appScope,
            repo = repository,
            networkClient = networkClient,
            canonicalMapper = canonicalMapper,
        )
        exportImportManager = ExportImportManager(database)

        appScope.launch {
            AppDatabase.seedDefaultsIfNeeded(database)
        }
    }
}
