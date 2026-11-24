package app.musehub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HubViewModel(app: Application) : AndroidViewModel(app) {
    val store = HubStore(app)
    val client = HubClient(store.baseUrl, store.token)

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents
    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms
    private val _messages = MutableStateFlow<List<HubMessage>>(emptyList())
    val messages: StateFlow<List<HubMessage>> = _messages
    private val _tasks = MutableStateFlow<List<HubTask>>(emptyList())
    val tasks: StateFlow<List<HubTask>> = _tasks
    private val _files = MutableStateFlow<List<HubFile>>(emptyList())
    val files: StateFlow<List<HubFile>> = _files
    private val _audit = MutableStateFlow<List<AuditEntry>>(emptyList())
    val audit: StateFlow<List<AuditEntry>> = _audit

    private val _online = MutableStateFlow<Boolean?>(null)
    val online: StateFlow<Boolean?> = _online
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error
    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast

    fun applySettings() {
        client.baseUrl = store.baseUrl
        client.token = store.token
    }

    fun setError(msg: String) { _error.value = msg }
    fun clearError() { _error.value = "" }
    fun setToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = "" }

    fun testConnection(onDone: (Boolean, String) -> Unit) {
        applySettings()
        viewModelScope.launch {
            val h = client.health()
            if (h.isFailure) {
                _online.value = false
                _error.value = h.exceptionOrNull()?.message ?: "连不上 Hub"
                onDone(false, _error.value)
                return@launch
            }
            val a = client.getAgents()
            if (a.isFailure) {
                _online.value = false
                _error.value = a.exceptionOrNull()?.message ?: "Token 无效"
                onDone(false, _error.value)
                return@launch
            }
            _online.value = true
            _agents.value = a.getOrDefault(emptyList())
            _error.value = ""
            onDone(true, "Hub 已连接，${_agents.value.size} 个 Agent")
        }
    }

    fun refreshAll(roomId: String? = null) {
        applySettings()
        if (store.token.isBlank()) {
            _error.value = "先在「管理」里填 Token"
            return
        }
        viewModelScope.launch {
            client.getAgents().onSuccess { _agents.value = it }.onFailure { _error.value = it.message ?: "拉 Agent 失败" }
            client.getRooms().onSuccess { _rooms.value = it }.onFailure { _error.value = it.message ?: "拉房间失败" }
            client.getTasks().onSuccess { _tasks.value = it }.onFailure { _error.value = it.message ?: "拉任务失败" }
            client.getFiles().onSuccess { _files.value = it }.onFailure { _error.value = it.message ?: "拉文件失败" }
            client.getAudit().onSuccess { _audit.value = it }.onFailure { /* audit is best-effort */ }
            if (roomId != null) refreshMessages(roomId)
            _online.value = _error.value.isBlank()
        }
    }

    fun refreshMessages(roomId: String) {
        viewModelScope.launch {
            client.getMessages(roomId).onSuccess { _messages.value = it }.onFailure { _error.value = it.message ?: "拉消息失败" }
        }
    }

    fun sendMessage(roomId: String, text: String, onDone: (Boolean) -> Unit) {
        val from = store.agentId.ifBlank { return }
        viewModelScope.launch {
            val r = client.sendMessage(from, roomId, text)
            if (r.isSuccess) { refreshMessages(roomId); onDone(true) }
            else { _error.value = r.exceptionOrNull()?.message ?: "发送失败"; onDone(false) }
        }
    }

    fun createRoom(name: String, type: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = client.createRoom(name, type)
            if (r.isSuccess) { refreshAll(); onDone(true) }
            else { _error.value = r.exceptionOrNull()?.message ?: "建房失败"; onDone(false) }
        }
    }

    fun createTask(title: String, desc: String, assignee: String, deadline: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = client.createTask(title, desc, assignee, deadline)
            if (r.isSuccess) { refreshAll(); onDone(true) }
            else { _error.value = r.exceptionOrNull()?.message ?: "新建任务失败"; onDone(false) }
        }
    }

    fun setTaskStatus(task: HubTask, status: String, reason: String = "", onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = client.setTaskStatus(task.id, status, reason)
            if (r.isSuccess) { refreshAll(); onDone(true) }
            else { _error.value = r.exceptionOrNull()?.message ?: "改状态失败"; onDone(false) }
        }
    }

    fun uploadFile(filename: String, bytes: ByteArray, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = client.uploadFile(filename, bytes)
            if (r.isSuccess) { refreshAll(); onDone(true) }
            else { _error.value = r.exceptionOrNull()?.message ?: "上传失败"; onDone(false) }
        }
    }
}
