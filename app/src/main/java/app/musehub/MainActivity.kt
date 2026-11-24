@file:OptIn(ExperimentalMaterial3Api::class)

package app.musehub

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

// V2 palette (doc §03)
private val Bg = Color(0xFF101827)
private val CardBg = Color(0xFF17243A)
private val CardBg2 = Color(0xFF202C40)
private val Accent = Color(0xFF16C7A0)
private val Blue = Color(0xFF299FF5)
private val Purple = Color(0xFF8067F5)
private val TextMain = Color(0xFFF4F7FC)
private val TextSub = Color(0xFF9AAAC2)
private val Divider = Color(0xFF334158)
private val Warn = Color(0xFFF4A340)
private val Err = Color(0xFFF05B67)

class MainActivity : ComponentActivity() {
    private val vm: HubViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, secondary = Blue, tertiary = Purple,
                    background = Bg, surface = CardBg, surfaceVariant = CardBg2,
                    onBackground = TextMain, onSurface = TextMain,
                    onSurfaceVariant = TextSub, error = Err
                )
            ) { HubApp(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubApp(vm: HubViewModel) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0 chat, 1 tasks, 2 files, 3 manage
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var lastRoom by remember { mutableStateOf("general") }
    var selectedTask by remember { mutableStateOf<HubTask?>(null) }
    var feedbackTask by remember { mutableStateOf<HubTask?>(null) }
    var mode by remember { mutableStateOf(vm.store.inputMode) } // keyboard | voice
    val online by vm.online.collectAsState()
    val error by vm.error.collectAsState()
    val toastMsg by vm.toast.collectAsState()
    val pollSeconds = vm.store.pollSeconds
    val recorder = remember { VoiceRecorder(ctx) }

    LaunchedEffect(Unit) { vm.refreshAll() }
    LaunchedEffect(tab, selectedRoom, pollSeconds) {
        while (true) {
            delay(pollSeconds * 1000L)
            if (vm.store.token.isNotBlank()) vm.refreshAll(selectedRoom)
        }
    }
    LaunchedEffect(toastMsg) {
        if (toastMsg.isNotBlank()) {
            Toast.makeText(ctx, toastMsg, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }
    LaunchedEffect(selectedRoom) { if (selectedRoom != null) lastRoom = selectedRoom!! }

    // 系统返回键：跟 MetaBatch 同样的修法。子页面先回上级，只有在聊天首页按才退出。
    // 优先级：完成反馈 > 任务详情 > 聊天会话 > 非首页 Tab 回聊天首页；首页不拦截，交给系统退出。
    BackHandler(enabled = feedbackTask != null || selectedTask != null || selectedRoom != null || tab != 0) {
        when {
            feedbackTask != null -> feedbackTask = null
            selectedTask != null -> selectedTask = null
            selectedRoom != null -> selectedRoom = null
            tab != 0 -> tab = 0
        }
    }

    fun onCentralClick() {
        if (tab != 0) {
            tab = 0
            selectedRoom = lastRoom
            selectedTask = null
            feedbackTask = null
        }
        mode = if (mode == "voice") "keyboard" else "voice"
        vm.store.inputMode = mode
    }

    val titles = listOf("聊天", "任务", "文件", "管理")
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedTask != null) "任务详情" else if (feedbackTask != null) "完成反馈" else if (selectedRoom != null) "聊天" else "Muse Hub", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(when (online) { true -> Accent; false -> Err; else -> TextSub }))
                        Spacer(Modifier.width(4.dp))
                        Text(if (online == true) "Hub 已连接" else if (online == false) "Hub 已断开" else "未连接", fontSize = 11.sp, color = TextSub)
                    }
                },
                navigationIcon = {
                    if (selectedTask != null || selectedRoom != null || feedbackTask != null) {
                        IconButton(onClick = { selectedTask = null; selectedRoom = null; feedbackTask = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshAll(selectedRoom) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = CardBg) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icons = listOf(Icons.Default.Chat, Icons.Default.Assignment, Icons.Default.Folder, Icons.Default.Settings)
                    // left: chat, tasks
                    for (i in 0..1) {
                        BottomItem(titles[i], icons[i], tab == i && selectedTask == null && selectedRoom == null && feedbackTask == null, Modifier.weight(1f)) {
                            tab = i; selectedTask = null; selectedRoom = null; feedbackTask = null
                        }
                    }
                    // central mode button: NOT a page
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { onCentralClick() },
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Accent)
                        ) {
                            Icon(if (mode == "voice") Icons.Default.Keyboard else Icons.Default.Mic, contentDescription = if (mode == "voice") "切换到键盘" else "切换到语音", tint = Color(0xFF06281F), modifier = Modifier.size(26.dp))
                        }
                    }
                    // right: files, manage
                    for (i in 2..3) {
                        BottomItem(titles[i], icons[i], tab == i && selectedTask == null && selectedRoom == null && feedbackTask == null, Modifier.weight(1f)) {
                            tab = i; selectedTask = null; selectedRoom = null; feedbackTask = null
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            if (error.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF7F1D1D), RoundedCornerShape(8.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { vm.clearError() }) { Text("关闭", color = Color.White, fontSize = 12.sp) }
                }
            }
            when {
                feedbackTask != null -> FeedbackView(vm, feedbackTask!!, onTasks = { feedbackTask = null; tab = 1 }, onChat = { feedbackTask = null; tab = 0; selectedRoom = lastRoom })
                selectedTask != null -> TaskDetail(vm, selectedTask!!, onBack = { selectedTask = null }, onDone = { feedbackTask = it; selectedTask = null })
                selectedRoom != null -> ChatView(vm, selectedRoom!!, mode, recorder)
                tab == 0 -> RoomList(vm, onOpen = { selectedRoom = it })
                tab == 1 -> TaskList(vm, onOpen = { selectedTask = it })
                tab == 2 -> FileList(vm)
                else -> ManageView(vm)
            }
        }
    }
}

@Composable
fun BottomItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = if (selected) Accent else TextSub, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = if (selected) Accent else TextSub)
    }
}

@Composable
fun RoomList(vm: HubViewModel, onOpen: (String) -> Unit) {
    val rooms by vm.rooms.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("group") }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("会话", fontSize = 16.sp)
        IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = "建房") }
    }
    if (rooms.isEmpty()) {
        Text("暂无会话，点右上角 + 创建或加入会话。", fontSize = 14.sp, color = TextSub, modifier = Modifier.padding(top = 16.dp))
    }
    LazyColumn {
        items(rooms) { r ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(CardBg2),
                    contentAlignment = Alignment.Center
                ) { Text(r.name.take(1), fontSize = 16.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${typeLabel(r.type)} · ${r.members.size} 人", fontSize = 11.sp, color = TextSub)
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建房间", fontSize = 16.sp) },
            text = {
                Column {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("房间名", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row { listOf("group" to "群聊", "dm" to "1对1", "broadcast" to "广播").forEach { (v, l) ->
                        FilterChip(selected = type == v, onClick = { type = v }, label = { Text(l, fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp))
                    } }
                }
            },
            confirmButton = { Button(onClick = {
                if (name.isBlank()) { vm.setError("房间名不能为空"); return@Button }
                vm.createRoom(name.trim(), type) { ok -> if (ok) { showCreate = false; name = "" } }
            }) { Text("创建", fontSize = 13.sp) } },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消", fontSize = 13.sp) } }
        )
    }
}

private fun typeLabel(t: String) = when (t) { "dm" -> "1对1"; "broadcast" -> "广播"; else -> "群聊" }

@Composable
fun ChatView(vm: HubViewModel, roomId: String, mode: String, recorder: VoiceRecorder) {
    val messages by vm.messages.collectAsState()
    val myId = vm.store.agentId
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<HubMessage?>(null) }
    LaunchedEffect(roomId) { vm.refreshMessages(roomId) }
    val roomMessages = messages.filter { it.roomId == roomId }
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(roomMessages) { m ->
            val mine = m.from == myId
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Text("${m.from} · ${shortTime(m.createdAt)}", fontSize = 11.sp, color = TextSub)
                Box(
                    Modifier.background(if (mine) Blue else CardBg2, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Column {
                        if (m.replyFrom != null) {
                            Text("回复 ${m.replyFrom}：${quoteSnippet(m.replyText ?: "")}", fontSize = 11.sp, color = if (mine) Color.White.copy(alpha = 0.85f) else TextSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(m.text, fontSize = 14.sp, color = TextMain)
                    }
                }
                if (m.taskId != null) Text("任务 ${m.taskId}", fontSize = 11.sp, color = TextSub)
                Text("回复", fontSize = 11.sp, color = TextSub, modifier = Modifier.clickable { replyTo = m }.padding(top = 2.dp, bottom = 2.dp))
            }
        }
    }
    if (replyTo != null) {
        Row(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(8.dp)).padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("回复 ${replyTo!!.from}：${quoteSnippet(replyTo!!.text)}", fontSize = 12.sp, color = TextSub, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { replyTo = null }) { Text("取消", fontSize = 12.sp) }
        }
    }
    if (mode == "voice") {
        VoicePanel(vm, recorder)
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text, onValueChange = { text = it }, placeholder = { Text("发消息…", fontSize = 14.sp) },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = {
                if (text.isBlank()) return@IconButton
                if (myId.isBlank()) { vm.setError("先在「管理」里选当前身份"); return@IconButton }
                val t = text; text = ""
                val rt = replyTo; replyTo = null
                vm.sendMessage(roomId, t, replyTo = rt?.id) { ok -> if (!ok) { text = t; replyTo = rt } }
            }) { Icon(Icons.Default.Send, contentDescription = "发送") }
        }
    }
    }
}

private enum class VoiceState { IDLE, RECORDING, PROCESSING, READY, SENT }

@Composable
fun VoicePanel(vm: HubViewModel, recorder: VoiceRecorder) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(VoiceState.IDLE) }
    var seconds by remember { mutableStateOf(0) }
    var amps by remember { mutableStateOf(listOf<Int>()) }
    var voiceFile by remember { mutableStateOf<File?>(null) }
    var permDenied by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permDenied = false
            if (recorder.start()) { state = VoiceState.RECORDING; seconds = 0; amps = emptyList() }
            else vm.setError("录音启动失败")
        } else {
            permDenied = true
        }
    }
    // waveform sampling while recording (timer is the 1s effect below)
    LaunchedEffect(state) {
        while (state == VoiceState.RECORDING) {
            delay(200)
            amps = (amps + recorder.amplitude()).takeLast(24)
        }
    }
    LaunchedEffect(state) {
        if (state == VoiceState.RECORDING) {
            while (state == VoiceState.RECORDING) { delay(1000); seconds += 1 }
        }
    }
    LaunchedEffect(state) {
        if (state == VoiceState.PROCESSING) {
            delay(600)
            state = if (voiceFile != null && voiceFile!!.exists()) VoiceState.READY else VoiceState.IDLE
        }
        if (state == VoiceState.SENT) {
            delay(1200)
            state = VoiceState.IDLE
            voiceFile = null
        }
    }
    Column(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(
            when (state) {
                VoiceState.IDLE -> "语音模式：点击开始录音（录音将作为音频文件发到文件库，Hub 没有语音转写，不会显示识别文本）"
                VoiceState.RECORDING -> "录音中 ${seconds / 60}:${"%02d".format(seconds % 60)}"
                VoiceState.PROCESSING -> "正在处理语音…"
                VoiceState.READY -> "录音完成 ${seconds / 60}:${"%02d".format(seconds % 60)}，可发送"
                VoiceState.SENT -> "已发送"
            }, fontSize = 12.sp, color = TextSub
        )
        Spacer(Modifier.height(8.dp))
        // waveform bars (amplitude only, no transcription)
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val bars = if (amps.isEmpty()) List(24) { 4 } else amps
            bars.forEach { a ->
                val h = (4 + (a.coerceAtMost(32767) / 32767f * 24)).dp
                Box(Modifier.width(4.dp).height(h).background(Accent, RoundedCornerShape(2.dp)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                VoiceState.IDLE -> {
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            if (recorder.start()) { state = VoiceState.RECORDING; seconds = 0; amps = emptyList() }
                            else vm.setError("录音启动失败")
                        } else {
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) { Icon(Icons.Default.Mic, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("点击开始录音", fontSize = 14.sp) }
                }
                VoiceState.RECORDING -> {
                    Button(onClick = {
                        voiceFile = recorder.stop()
                        state = VoiceState.PROCESSING
                    }) { Text("点击结束", fontSize = 14.sp) }
                    OutlinedButton(onClick = { recorder.cancel(); state = VoiceState.IDLE; voiceFile = null }) { Text("取消", fontSize = 14.sp) }
                }
                VoiceState.PROCESSING -> { Text("正在处理语音…", fontSize = 14.sp, color = TextSub) }
                VoiceState.READY -> {
                    Button(onClick = {
                        val f = voiceFile ?: return@Button
                        val bytes = try { f.readBytes() } catch (e: Exception) { vm.setError("读录音失败：${e.message}"); return@Button }
                        if (bytes.size > 50 * 1024 * 1024) { vm.setError("超 50MB，Hub 不收"); return@Button }
                        vm.uploadFile(f.name, bytes) { ok -> if (ok) { state = VoiceState.SENT; vm.setToast("录音已作为音频文件发到文件库") } }
                    }) { Text("发送录音", fontSize = 14.sp) }
                    OutlinedButton(onClick = { voiceFile?.delete(); voiceFile = null; state = VoiceState.IDLE }) { Text("取消", fontSize = 14.sp) }
                }
                VoiceState.SENT -> { Text("已发送", fontSize = 14.sp, color = Accent) }
            }
        }
        if (permDenied) {
            Text("未授予麦克风权限，无法录音。请在系统设置里允许 Muse Hub 使用麦克风。", fontSize = 12.sp, color = Warn, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            }, modifier = Modifier.padding(top = 4.dp)) { Text("去设置", fontSize = 13.sp) }
        }
    }
}

@Composable
fun TaskList(vm: HubViewModel, onOpen: (HubTask) -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val agents by vm.agents.collectAsState()
    var catFilter by remember { mutableStateOf("全部") }
    var assigneeFilter by remember { mutableStateOf("全部") }
    var showCreate by remember { mutableStateOf(false) }
    val filtered = tasks.filter {
        (catFilter == "全部" || taskCategory(it.status) == catFilter) &&
        (assigneeFilter == "全部" || it.assignee == assigneeFilter)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("全部", "进行中", "待处理", "已完成", "失败").forEach { s ->
            FilterChip(selected = catFilter == s, onClick = { catFilter = s }, label = { Text(s, fontSize = 11.sp) })
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("负责人：", fontSize = 12.sp)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(assigneeFilter, fontSize = 12.sp) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("全部", fontSize = 13.sp) }, onClick = { assigneeFilter = "全部"; expanded = false })
                agents.forEach { a ->
                    DropdownMenuItem(text = { Text(a.id, fontSize = 13.sp) }, onClick = { assigneeFilter = a.id; expanded = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = "新建任务") }
    }
    if (filtered.isEmpty()) {
        Text("暂无任务", fontSize = 14.sp, color = TextSub, modifier = Modifier.padding(top = 16.dp))
        TextButton(onClick = { catFilter = "全部"; assigneeFilter = "全部" }) { Text("清除筛选", fontSize = 13.sp) }
    }
    LazyColumn {
        items(filtered) { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(t) }, colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(taskCategory(t.status), fontSize = 11.sp, color = statusColor(t.status))
                    }
                    Text("${t.assignee}${if (!t.deadline.isNullOrBlank()) " · 截止 ${t.deadline}" else ""}", fontSize = 11.sp, color = TextSub)
                    if (t.description.isNotBlank()) Text(t.description, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (showCreate) CreateTaskDialog(vm, agents, onClose = { showCreate = false })
}

private fun statusColor(s: String) = when (taskCategory(s)) {
    "待处理" -> TextSub
    "进行中" -> Blue
    "已完成" -> Accent
    "失败" -> Err
    else -> TextSub
}

@Composable
fun CreateTaskDialog(vm: HubViewModel, agents: List<Agent>, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf(agents.firstOrNull()?.id ?: "") }
    var deadline by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("新建任务", fontSize = 16.sp) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("标题（必填）", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextField(value = desc, onValueChange = { desc = it }, label = { Text("说明", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                TextField(value = deadline, onValueChange = { deadline = it }, label = { Text("截止（可选）", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Text("派给：$assignee", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row { agents.forEach { a -> FilterChip(selected = assignee == a.id, onClick = { assignee = a.id }, label = { Text(a.id, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp)) } }
            }
        },
        confirmButton = { Button(onClick = {
            if (title.isBlank()) { vm.setError("标题不能为空"); return@Button }
            if (assignee.isBlank()) { vm.setError("先选负责人"); return@Button }
            vm.createTask(title.trim(), desc, assignee, deadline.ifBlank { null }) { ok -> if (ok) onClose() }
        }) { Text("创建", fontSize = 13.sp) } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消", fontSize = 13.sp) } }
    )
}

@Composable
fun TaskDetail(vm: HubViewModel, task: HubTask, onBack: () -> Unit, onDone: (HubTask) -> Unit) {
    var showReject by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val tasks by vm.tasks.collectAsState()
    val fresh = tasks.firstOrNull { it.id == task.id } ?: task
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(fresh.title, fontSize = 20.sp)
            Text("${taskCategory(fresh.status)} · ${fresh.assignee} · 创建人 ${fresh.creator}", fontSize = 12.sp, color = TextSub, modifier = Modifier.padding(top = 4.dp))
            if (!fresh.deadline.isNullOrBlank()) Text("截止：${fresh.deadline}", fontSize = 12.sp)
            if (fresh.reason.isNotBlank()) Text("驳回原因：${fresh.reason}", fontSize = 12.sp, color = Err)
            if (fresh.description.isNotBlank()) Text(fresh.description, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Text("执行记录", fontSize = 12.sp, color = TextSub, modifier = Modifier.padding(top = 12.dp))
            fresh.history.forEach { h ->
                Text("${shortTime(h.at)} ${h.by} → ${taskCategory(h.status)}${if (h.reason.isNotBlank()) "（${h.reason}）" else ""}", fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allowedNextStatuses(fresh.status).forEach { next ->
                    if (next == "rejected") {
                        OutlinedButton(onClick = { showReject = true }) { Text("驳回", fontSize = 13.sp) }
                    } else {
                        Button(onClick = {
                            vm.setTaskStatus(fresh, next) { ok -> if (ok && next == "done") onDone(fresh) else if (ok) vm.setToast("已更新") }
                        }) {
                            Text(when (next) { "in_progress" -> if (fresh.status == "rejected") "重新开始" else "认领"; "review" -> "提交验收"; "done" -> "验收通过"; else -> next }, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) { Text("返回列表", fontSize = 13.sp) }
        }
    }
    if (showReject) {
        AlertDialog(
            onDismissRequest = { showReject = false },
            title = { Text("驳回任务", fontSize = 16.sp) },
            text = { TextField(value = reason, onValueChange = { reason = it }, label = { Text("驳回原因（必填）", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = {
                if (reason.isBlank()) { vm.setError("驳回原因必填"); return@Button }
                vm.setTaskStatus(fresh, "rejected", reason) { ok -> if (ok) showReject = false }
            }) { Text("提交驳回", fontSize = 13.sp) } },
            dismissButton = { TextButton(onClick = { showReject = false }) { Text("取消", fontSize = 13.sp) } }
        )
    }
}

@Composable
fun FeedbackView(vm: HubViewModel, task: HubTask, onTasks: () -> Unit, onChat: () -> Unit) {
    val tasks by vm.tasks.collectAsState()
    val doneCount = tasks.count { it.status == "done" }
    val failCount = tasks.count { it.status == "rejected" }
    val partial = failCount > 0
    Column(Modifier.fillMaxSize().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (partial) "处理完成，部分任务需检查" else "任务执行完成", fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text("本次完成：${task.title}", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("全部任务中已完成 $doneCount 项，失败/需关注 $failCount 项", fontSize = 12.sp, color = TextSub, modifier = Modifier.padding(top = 4.dp))
        if (partial) Text("有失败任务，请在任务列表里查看原因。", fontSize = 12.sp, color = Warn, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onTasks, modifier = Modifier.fillMaxWidth()) { Text("查看结果", fontSize = 14.sp) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) { Text("返回聊天", fontSize = 14.sp) }
    }
}

@Composable
fun FileList(vm: HubViewModel) {
    val ctx = LocalContext.current
    val files by vm.files.collectAsState()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("全部") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, size) = queryFileInfo(ctx, uri)
        if (name.isBlank()) { vm.setError("读不到文件名"); return@rememberLauncherForActivityResult }
        if (isSensitiveFilename(name)) { vm.setError("这个文件名像密码/密钥类，Hub 不收"); return@rememberLauncherForActivityResult }
        if (size > 50L * 1024 * 1024) { vm.setError("超 50MB，Hub 不收"); return@rememberLauncherForActivityResult }
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run { vm.setError("读文件失败"); return@rememberLauncherForActivityResult }
            if (bytes.size > 50 * 1024 * 1024) { vm.setError("超 50MB，Hub 不收"); return@rememberLauncherForActivityResult }
            vm.uploadFile(name, bytes) { ok -> if (ok) vm.setToast("已上传 $name") }
        } catch (e: Exception) { vm.setError("读文件失败：${e.message}") }
    }
    TextField(value = query, onValueChange = { query = it }, placeholder = { Text("搜索文件…", fontSize = 14.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("全部", "文档", "图片", "其他").forEach { c ->
            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c, fontSize = 11.sp) })
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { launcher.launch("*/*") }) { Icon(Icons.Default.Upload, contentDescription = "上传") }
    }
    val filtered = files.filter {
        (cat == "全部" || fileCategory(it.filename) == cat) &&
        (query.isBlank() || it.filename.contains(query, ignoreCase = true))
    }
    if (filtered.isEmpty()) {
        Text(if (files.isEmpty()) "文件尚未上传 / 接收。" else "没有符合条件的文件。", fontSize = 14.sp, color = TextSub, modifier = Modifier.padding(top = 16.dp))
    }
    LazyColumn {
        items(filtered) { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Purple), contentAlignment = Alignment.Center) {
                    Text(fileCategory(f.filename).take(1), fontSize = 14.sp, color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.filename, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatSize(f.size)} · ${f.uploader} · ${shortTime(f.createdAt)} · ${f.sha256.take(8)}", fontSize = 11.sp, color = TextSub)
                }
                IconButton(onClick = { downloadFile(ctx, vm, f) }) { Icon(Icons.Default.Download, contentDescription = "下载") }
            }
        }
    }
}

private fun queryFileInfo(ctx: Context, uri: Uri): Pair<String, Long> {
    var name = ""
    var size = 0L
    val c: Cursor? = ctx.contentResolver.query(uri, null, null, null, null)
    c?.use {
        val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
        if (it.moveToFirst()) {
            if (nameIdx >= 0) name = it.getString(nameIdx) ?: ""
            if (sizeIdx >= 0) size = it.getLong(sizeIdx)
        }
    }
    return name to size
}

private fun downloadFile(ctx: Context, vm: HubViewModel, f: HubFile) {
    Thread {
        try {
            val result = kotlinx.coroutines.runBlocking { vm.client.downloadFile(f.id) }
            if (result.isFailure) {
                vm.setError(result.exceptionOrNull()?.message ?: "下载失败")
                return@Thread
            }
            val bytes = result.getOrThrow()
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, f.filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) { vm.setError("保存失败"); return@Thread }
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            vm.setToast("已下载到 Download/${f.filename}")
        } catch (e: Exception) { vm.setError("下载失败：${e.message}") }
    }.start()
}

@Composable
fun ManageView(vm: HubViewModel) {
    val ctx = LocalContext.current
    val agents by vm.agents.collectAsState()
    val audit by vm.audit.collectAsState()
    val online by vm.online.collectAsState()
    var baseUrl by remember { mutableStateOf(vm.store.baseUrl) }
    var token by remember { mutableStateOf(vm.store.token) }
    var testResult by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var newToken by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle("Hub 连接")
            Text("状态：${if (online == true) "已连接" else if (online == false) "已断开" else "未连接"}", fontSize = 14.sp)
            Text("当前身份：${vm.store.agentId.ifBlank { "未选" }}", fontSize = 12.sp, color = TextSub)
            if (online == false) Text("连接异常，请检查 Hub 地址与 Token，点下方测试连接重试。", fontSize = 12.sp, color = Warn)
            SectionTitle("成员")
            agents.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.id, fontSize = 14.sp)
                        Text(a.role, fontSize = 11.sp, color = TextSub)
                    }
                    if (a.id == vm.store.agentId) Text("当前身份", fontSize = 11.sp, color = Accent)
                }
            }
            OutlinedButton(onClick = { showRegister = true }, modifier = Modifier.padding(vertical = 4.dp)) { Text("注册新 Agent（仅 owner）", fontSize = 13.sp) }
            if (newToken != null) {
                Text("新 Token（只显示一次，请私下转交给对方，别发群里）：", fontSize = 12.sp, color = Err, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(newToken!!, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MuseHub token", newToken))
                        vm.setToast("已复制")
                    }) { Text("复制", fontSize = 12.sp) }
                }
            }
            SectionTitle("通用设置")
            TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Hub 地址", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            TextField(value = token, onValueChange = { token = it }, label = { Text("Token", fontSize = 12.sp) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text("当前身份", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row { agents.forEach { a -> FilterChip(selected = vm.store.agentId == a.id, onClick = { vm.store.agentId = a.id; vm.setToast("当前身份：${a.id}") }, label = { Text(a.id, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp)) } }
            Text("轮询间隔", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row { listOf(30, 45, 60).forEach { s -> FilterChip(selected = vm.store.pollSeconds == s, onClick = { vm.store.pollSeconds = s }, label = { Text("${s}秒", fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp)) } }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.store.baseUrl = baseUrl; vm.store.token = token; vm.applySettings()
                    vm.testConnection { ok, msg -> testResult = msg; if (ok) vm.setToast(msg) }
                }) { Text("保存并测试连接", fontSize = 13.sp) }
                OutlinedButton(onClick = { vm.refreshAll() }) { Text("刷新", fontSize = 13.sp) }
            }
            if (testResult.isNotBlank()) Text(testResult, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            SectionTitle("审计")
            audit.takeLast(30).reversed().forEach { a ->
                Text("${shortTime(a.time)} ${a.agent} ${a.action} ${a.objectId}", fontSize = 12.sp)
            }
            SectionTitle("关于 Muse Hub")
            Text("Muse Hub App 0.1.0 · Hub：${vm.store.baseUrl}", fontSize = 12.sp, color = TextSub)
            SectionTitle("帮助与反馈")
            Text("Token 在 Dedirock 服务器 /root/muse-hub/data/tokens.json 上取，一对一私下交给对应 Muse。连不上时先点「测试连接」，看是地址错、Token 无效还是 Hub 不通。", fontSize = 12.sp, color = TextSub)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showRegister) RegisterDialog(vm, onClose = { showRegister = false }, onToken = { newToken = it; showRegister = false })
}

@Composable
fun SectionTitle(t: String) {
    Text(t, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
fun RegisterDialog(vm: HubViewModel, onClose: () -> Unit, onToken: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("注册新 Agent", fontSize = 16.sp) },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("名字（即 Agent id）", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextField(value = role, onValueChange = { role = it }, label = { Text("角色", fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Text("只有用 owner Token 登录才注册得了，否则 Hub 会拒。", fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { Button(onClick = {
            if (name.isBlank()) { vm.setError("名字不能为空"); return@Button }
            Thread {
                try {
                    val r = kotlinx.coroutines.runBlocking { vm.client.registerAgent(name.trim(), role) }
                    if (r.isSuccess) { onToken(r.getOrDefault("")); vm.refreshAll() }
                    else vm.setError(r.exceptionOrNull()?.message ?: "注册失败")
                } catch (e: Exception) { vm.setError("注册失败：${e.message}") }
            }.start()
        }) { Text("注册", fontSize = 13.sp) } },
        dismissButton = { TextButton(onClick = onClose) { Text("取消", fontSize = 13.sp) } }
    )
}
