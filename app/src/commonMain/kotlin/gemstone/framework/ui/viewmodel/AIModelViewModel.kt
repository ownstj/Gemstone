package gemstone.framework.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import gemstone.framework.network.http.defaultServerHost
import gemstone.framework.network.websocket.ChatWebSocketClient


val webSocketClient = ChatWebSocketClient()


object AIModelViewModel {
    var isEditingServerHost by mutableStateOf(false)
    var serverHost by mutableStateOf(defaultServerHost)
        private set

    var recentServerHosts by mutableStateOf(listOf(defaultServerHost))
        private set

    private fun addRecentServerHost(host: String) {
        val sanitized = host.trim()
        if (sanitized.isBlank()) return
        val withoutDup = listOf(sanitized) + recentServerHosts.filter { it != sanitized }
        recentServerHosts = withoutDup.take(8)
    }

    fun changeServerHost(newHost: String) {
        val host = newHost.trim()
        if (host.isBlank()) return
        addRecentServerHost(host)

        // Show the current applied server address under New Chat
        selectedAIModelDescription = host

        val isSame = host == serverHost
        if (!isSame) {
            ChatViewModel.runBlocking {
                webSocketClient.deleteSession()
            }
            serverHost = host
            webSocketClient.updateHost(host)
        }

        // Ensure model state and session are consistent on the new server
        if (selectedAIModel.isNotEmpty()) {
            // Switch to ALL (default) if a specific model was selected
            deselectAIModel() // This will initialize a default session
            // Keep the description as server address for visibility
            selectedAIModelDescription = host
        } else if (!isSame) {
            // Already ALL; create a default session on the new server
            ChatViewModel.runBlocking {
                initializeModel(webSocketClient)
            }
        }
    }

    var defaultAIModel by mutableStateOf("Qwen3")
    var defaultAIModelDescription by mutableStateOf("Qwen3 14B 4bitQ IT")

    var selectedAIModel by mutableStateOf("")
    var selectedAIModelDescription by mutableStateOf(defaultAIModelDescription)
    var availableAIModels by mutableStateOf(listOf<Pair<String, String>>(
        Pair("Qwen3", "Qwen3 14B 4bitQ IT"),
        Pair("Llama3", "Llama3.1 8B 4bitQ Instruct"),
    ))
    val selectedAIModelOrDefault
        get() = selectedAIModel.ifEmpty { defaultAIModel }

    fun addAIModel(model: String, description: String) {
        val new = Pair(model, description)
        if (new !in availableAIModels) {
            availableAIModels += new
            if (selectedAIModel.isEmpty()) {
                selectAIModel(model, description)
            }
        }
    }
    fun removeAIModel(model: String) {
        for (pair in availableAIModels) {
            if (pair.first == model) {
                availableAIModels -= pair
                if (selectedAIModel == model) {
                    selectedAIModel = ""
                    selectedAIModelDescription = defaultAIModelDescription
                }
                break
            }
        }
    }
    fun selectAIModel(model: String, description: String) {
        if (selectedAIModel == model) return
        if (Pair(model, description) in availableAIModels) {
            selectedAIModel = model
            selectedAIModelDescription = description
            ChatViewModel.runBlocking {
                initializeModel(webSocketClient, model.lowercase())
            }
        }
    }
    fun deselectAIModel() {
        if (selectedAIModel.isEmpty()) return
        selectedAIModel = ""
        selectedAIModelDescription = defaultAIModelDescription
        ChatViewModel.runBlocking {
            initializeModel(webSocketClient)
        }
    }
    suspend fun initializeModel(
        client: ChatWebSocketClient,
        model: String = defaultAIModel,
        failureCallback: () -> Unit = {}
    ) {
        client.deleteSession()
        val result = client.createSession(model.lowercase())
        if (!result.isSuccess) {
            println("ERROR: Failed to create WebSocket session: ${result.exceptionOrNull()?.message}")
            failureCallback()
        }
    }

    var chatRoomList by mutableStateOf(mapOf<Int, Pair<Boolean, String>>())
    var selectedChatRoom by mutableStateOf(-1)
}
