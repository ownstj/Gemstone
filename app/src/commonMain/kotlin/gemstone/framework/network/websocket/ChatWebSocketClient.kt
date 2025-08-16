package gemstone.framework.network.websocket

import gemstone.framework.network.http.HttpClientFactory
import gemstone.framework.network.http.defaultServerHost
import gemstone.framework.ui.viewmodel.ChatHistory
import gemstone.framework.ui.viewmodel.ChatRole
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement


sealed class ChatState {
    data object Disconnected : ChatState()
    data object Connecting : ChatState()
    data object Connected : ChatState()
    data class Thinking(val elapsedSeconds: Float) : ChatState()
    data object Responding : ChatState()
    data class Error(val message: String) : ChatState()
}


sealed class ChatEvent {
    data class MessageReceived(val content: String, val isThinking: Boolean = false) : ChatEvent()
    data object ThinkingStarted : ChatEvent()
    data object ThinkingEnded : ChatEvent()
    data class ToolCallReceived(val toolCall: JsonElement) : ChatEvent()
    data object MessageComplete : ChatEvent()
    data class ErrorOccurred(val error: String) : ChatEvent()
}


@Serializable
data class SessionResponse(
    val model_id: String,
    val session_id: String,
    val message: String
)


class ChatWebSocketClient(
    private var serverUrl: String = defaultServerHost
) {
    private var httpClient = HttpClientFactory.create()

    init {
        println("INFO: ChatWebSocketClient initialized using server URL - $serverUrl")
    }

    fun updateHost(newHost: String) {
        if (newHost != serverUrl) {
            serverUrl = newHost
            httpClient.close()
            httpClient = HttpClientFactory.create()
            println("INFO: ChatWebSocketClient host updated to $newHost")
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var webSocketSession: DefaultClientWebSocketSession? = null
    var sessionId: String? = null
        set(value) {
            field = value
            if (value != null) {
                println("INFO: Session ID set to $value")
            } else {
                println("INFO: Session ID cleared")
            }
        }

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow<ChatState>(ChatState.Disconnected)
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var thinkingStartTime: Long = 0
    private var isThinking = false

    suspend fun createSession(modelId: String): Result<String> {
        return try {
            val response = httpClient.post("http://$serverUrl/api/models/$modelId/sessions/")
            if (response.status == HttpStatusCode.OK) {
                val sessionResponse = json.decodeFromString<SessionResponse>(response.bodyAsText())
                sessionId = sessionResponse.session_id
                Result.success(sessionResponse.session_id)
            } else {
                Result.failure(Exception("Failed to create session: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun connect(): Result<Unit> {
        return try {
            _state.value = ChatState.Connecting
            webSocketSession = httpClient.webSocketSession(
                method = HttpMethod.Get,
                host = serverUrl.split(":")[0],
                port = serverUrl.split(":")[1].toInt(),
                path = "/api/chat/streaming"
            )
            _state.value = ChatState.Connected
            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = ChatState.Error(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        message: String,
        chatHistory: ChatHistory
    ): Result<Unit> {
        return try {
            // Connect to WebSocket
            val connectResult = connect()
            if (connectResult.isFailure) {
                println("ERROR: Failed to connect WebSocket: ${connectResult.exceptionOrNull()?.message}")
                return connectResult
            }
            println("INFO: WebSocket connected successfully")

            val session = webSocketSession ?: return Result.failure(Exception("Not connected"))
            val currentSessionId = sessionId ?: return Result.failure(Exception("No session ID"))

            // Send session ID
            session.send(json.encodeToString(mapOf("session_id" to currentSessionId)))

            // Send chat history
            session.send(json.encodeToString(chatHistory.toList()))

            // Send user message
            session.send(message)

            // Add user message to history
            chatHistory.add(ChatRole.USER.value, message)

            // Start listening for responses
            listenForMessages()

            // Disconnect after sending the message
            disconnect()
            println("INFO: Message sent and WebSocket disconnected successfully")

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun listenForMessages() {
        val session = webSocketSession ?: return

        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val message = frame.readText()
                        handleMessage(message)
                    }
                    is Frame.Close -> {
                        _state.value = ChatState.Disconnected
                        _events.emit(ChatEvent.MessageComplete)
                        break
                    }
                    else -> { /* Handle other frame types if needed */ }
                }
            }
        } catch (e: Exception) {
            _state.value = ChatState.Error(e.message ?: "Connection error")
            _events.emit(ChatEvent.ErrorOccurred(e.message ?: "Connection error"))
        }
    }

    private suspend fun handleMessage(message: String) {
        println("INFO: Received message: $message")
        when {
            message == "<EOS>" -> {
                disconnect()
                _events.emit(ChatEvent.MessageComplete)
                return
            }

            message == "<think>" -> {
                isThinking = true
                thinkingStartTime = Clock.System.now().toEpochMilliseconds()
                _state.value = ChatState.Thinking(0f)
                _events.emit(ChatEvent.ThinkingStarted)
            }

            message == "</think>" -> {
                isThinking = false
                _state.value = ChatState.Responding
                _events.emit(ChatEvent.ThinkingEnded)
            }

            message.contains("<tool_call>") -> {
                val regex = """<tool_call>([\s\S]*?)</tool_call>""".toRegex()
                regex.findAll(message).forEach { match ->
                    handleToolCall(match.groupValues[1])
                }
            }

            else -> {
                if (isThinking) {
                    _events.emit(ChatEvent.MessageReceived(message, isThinking = true))
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    val elapsedSeconds = (currentTime - thinkingStartTime) / 1000f
                    thinkingStartTime = currentTime
                    _state.value = ChatState.Thinking(elapsedSeconds)
                } else {
                    _state.value = ChatState.Responding
                    _events.emit(ChatEvent.MessageReceived(message, isThinking = false))
                }
            }
        }
    }

    private suspend fun handleToolCall(message: String) {
        try {
            val toolCall = json.decodeFromString<JsonElement>(message.trim())
            _events.emit(ChatEvent.ToolCallReceived(toolCall))
        } catch (e: Exception) {
            _events.emit(ChatEvent.ErrorOccurred("Failed to parse tool call: ${e.message}"))
        }
    }

    private suspend fun disconnect() {
        try {
            webSocketSession?.close()
            webSocketSession = null
            _state.value = ChatState.Disconnected
        } catch (e: Exception) {
            _state.value = ChatState.Error("Disconnect failed: ${e.message}")
        }
    }

    suspend fun deleteSession() {
        val currentSessionId = sessionId ?: return
        try {
            httpClient.delete("http://$serverUrl/api/sessions/$currentSessionId")
            sessionId = null
        } catch (e: Exception) {
            println("Failed to delete session: ${e.message}")
        }
    }

    fun close() {
        httpClient.close()
    }
}
