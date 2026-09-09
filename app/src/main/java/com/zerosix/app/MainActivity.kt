package com.zerosix.app

import android.Manifest
import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.documentfile.provider.DocumentFile
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import org.torproject.jni.TorService
import java.io.File
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var hud: HudView
    private lateinit var terminal: TerminalView
    private lateinit var cpuGraph: GraphView
    private lateinit var netGraph: GraphView
    private lateinit var cpuLabel: TextView
    private lateinit var ramLabel: TextView
    private lateinit var storageLabel: TextView
    private lateinit var batteryLabel: TextView
    private lateinit var netLabel: TextView
    private lateinit var torLabel: TextView
    private lateinit var modeLabel: TextView
    private lateinit var boot: FrameLayout
    private lateinit var session: TerminalSession
    private val sessions = arrayOfNulls<TerminalSession>(3)
    private var activeTab = 0
    private lateinit var tabButtons: Array<TextView>
    private lateinit var monitor: SystemMonitor
    private val handler = Handler(Looper.getMainLooper())
    private val cpuHistory = ArrayDeque<Float>()
    private val sound = SoundEngine(this)
    private var mode = "TERMINAL"
    private var torBound = false
    private var torService: TorService? = null
    private var pendingTree = false
    private var pendingProot = false
    private val REQ_PROOT = 701
    private val REQ_ROOTFS = 702

    private val torReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TorService.ACTION_STATUS) return
            val status = intent.getStringExtra(TorService.EXTRA_STATUS) ?: "?"
            torLabel.text = "TOR  $status"
            torLabel.setTextColor(if (status == TorService.STATUS_ON) 0xFF7CFFB2.toInt() else 0xFFFFC56E.toInt())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        monitor = SystemMonitor(this)
        buildUi()
        startTerminal(false)
        registerTor()
        runBootSequence()
        startMonitorLoop()
    }

    private fun buildUi() {
        root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(2,3,8))
        hud = HudView(this)
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 8) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = TextView(this).apply {
            text = "ZEROSIX"
            typeface = Typeface.MONOSPACE
            textSize = 20f
            setTextColor(0xFF7DF9FF.toInt())
            letterSpacing = .18f
        }
        header.addView(logo, LinearLayout.LayoutParams(0, 46, 1f))
        modeLabel = label("TERMINAL", 12f).also { header.addView(it, LinearLayout.LayoutParams(-2, 46)) }
        content.addView(header)

        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 5f }
        cpuLabel = label("CPU --", 10f); ramLabel = label("RAM --", 10f); storageLabel = label("STO --", 10f); batteryLabel = label("BAT --", 10f); torLabel = label("TOR OFF", 10f)
        listOf(cpuLabel, ramLabel, storageLabel, batteryLabel, torLabel).forEach { status.addView(it, LinearLayout.LayoutParams(0, 34, 1f).apply { setMargins(2,0,2,0) }) }
        content.addView(status)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        tabButtons = arrayOf("TAB 1", "TAB 2", "TAB 3").map { key(it) }.toTypedArray()
        tabButtons.forEachIndexed { index, b ->
            b.setOnClickListener { switchTab(index) }
            tabs.addView(b, LinearLayout.LayoutParams(0, 32, 1f).apply { setMargins(2, 0, 2, 2) })
        }
        content.addView(tabs)

        terminal = TerminalView(this, null).apply {
            setTextSize(13)
            setBackgroundColor(0xCC02040A.toInt())
        }
        content.addView(terminal, LinearLayout.LayoutParams(-1, 0, 1.0f))

        val graphs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cpuGraph = GraphView(this); netGraph = GraphView(this)
        graphs.addView(cpuGraph, LinearLayout.LayoutParams(0, 86, 1f).apply { setMargins(2,4,2,4) })
        graphs.addView(netGraph, LinearLayout.LayoutParams(0, 86, 1f).apply { setMargins(2,4,2,4) })
        content.addView(graphs)

        netLabel = label("NET --", 10f)
        content.addView(netLabel, LinearLayout.LayoutParams(-1, 28))

        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val special = listOf("ESC" to "\u001b", "TAB" to "\t", "CTRL-C" to "\u0003", "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C")
        special.forEach { (name, code) ->
            val b = key(name)
            b.setOnClickListener { session.write(code.toByteArray()); sound.key() }
            keys.addView(b, LinearLayout.LayoutParams(0, 38, 1f).apply { setMargins(2,0,2,0) })
        }
        content.addView(keys)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("TERMINAL", "LINUX", "PROCESS", "NETWORK", "FILES", "TOR").forEach { name ->
            val b = key(name)
            b.setOnClickListener { selectMode(name) }
            nav.addView(b, LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(2,2,2,2) })
        }
        content.addView(nav)
        setContentView(root)
    }

    private fun label(text: String, size: Float) = TextView(this).apply { this.text=text; textSize=size; typeface=Typeface.MONOSPACE; setTextColor(0xFFB7F9FF.toInt()); gravity=Gravity.CENTER_VERTICAL }
    private fun key(text: String) = TextView(this).apply { this.text=text; textSize=9f; typeface=Typeface.MONOSPACE; gravity=Gravity.CENTER; setTextColor(0xFF7DF9FF.toInt()); setBackgroundColor(0x331A5960) }

    private fun terminalClient(): TerminalViewClient = object : TerminalViewClient {
        override fun onScale(scale: Float) = 1f
        override fun onSingleTapUp(e: android.view.MotionEvent) { terminal.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT) }
        override fun shouldBackButtonBeMappedToEscape() = true
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
        override fun onLongPress(event: android.view.MotionEvent) = false
        override fun readControlKey() = false
        override fun readAltKey() = false
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean { sound.key(); return false }
        override fun onEmulatorSet() {}
        override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
        override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
        override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "", e) }
    }

    private fun sessionClient(): TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) { terminal.post { terminal.onScreenUpdated() } }
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) { sound.confirm() }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) { clipboard().setPrimaryClip(android.content.ClipData.newPlainText("terminal", text)) }
        override fun onPasteTextFromClipboard(session: TerminalSession?) { val c=clipboard().primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: return; session?.write(c.toByteArray()) }
        override fun onBell(session: TerminalSession) { sound.tick() }
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) { android.util.Log.e(tag,message) }
        override fun logWarn(tag: String, message: String) { android.util.Log.w(tag,message) }
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag,message,e) }
        override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag,"",e) }
    }

    private fun startTerminal(linux: Boolean) {
        runCatching { if (::session.isInitialized) session.finishIfRunning() }
        val cwd = if (!linux) filesDir.absolutePath else File(filesDir, "linux/rootfs").absolutePath
        val env = arrayOf("TERM=xterm-256color", "HOME=$cwd", "LANG=en_US.UTF-8", "PATH=/system/bin:/system/xbin:/vendor/bin:$cwd/usr/bin:$cwd/bin")
        val shell: String
        val args: Array<String>
        if (!linux) {
            shell = "/system/bin/sh"; args = emptyArray()
        } else {
            val proot = File(filesDir, "linux/proot")
            val rootfs = File(filesDir, "linux/rootfs")
            if (!proot.canExecute() || !File(rootfs, "bin/sh").exists()) {
                terminal.setBackgroundColor(0xDD02040A.toInt())
                modeLabel.text = "LINUX // SETUP REQUIRED"
                shell = "/system/bin/sh"; args = emptyArray()
            } else {
                shell = proot.absolutePath
                args = arrayOf("-0", "-r", rootfs.absolutePath, "-b", filesDir.absolutePath + ":/host", "/bin/sh", "-l")
            }
        }
        session = TerminalSession(shell, cwd, args, env, 2000, sessionClient())
        sessions[activeTab]?.let { oldSession -> runCatching { oldSession.finishIfRunning() } }
        sessions[activeTab] = session
        terminal.setTerminalViewClient(terminalClient())
        terminal.attachSession(session)
        terminal.requestFocus()
        updateTabs()
        modeLabel.text = if (linux) "LINUX" else "TERMINAL"
    }

    private fun switchTab(index: Int) {
        if (index !in 0..2) return
        activeTab = index
        val existing = sessions[index]
        if (existing != null) {
            session = existing
            terminal.setTerminalViewClient(terminalClient())
            terminal.attachSession(existing)
            terminal.requestFocus()
            modeLabel.text = "TERMINAL // TAB ${index + 1}"
            updateTabs()
        } else {
            startTerminal(false)
        }
        sound.confirm()
    }

    private fun updateTabs() {
        if (!::tabButtons.isInitialized) return
        tabButtons.forEachIndexed { i, b ->
            b.text = if (i == activeTab) "TAB ${i + 1}  ●" else "TAB ${i + 1}"
            b.setTextColor(if (i == activeTab) 0xFF7DF9FF.toInt() else 0xFF86AEB3.toInt())
        }
    }

    private fun selectMode(name: String) {
        mode = name
        when (name) {
            "TERMINAL" -> startTerminal(false)
            "LINUX" -> { if (linuxReady()) startTerminal(true) else showLinuxSetup() }
            "PROCESS" -> showProcesses()
            "NETWORK" -> showNetworkInfo()
            "FILES" -> openFiles()
            "TOR" -> toggleTor()
        }
    }

    private fun linuxReady(): Boolean = File(filesDir, "linux/proot").canExecute() && File(filesDir, "linux/rootfs/bin/sh").exists()

    private fun showLinuxSetup() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,24,28,16) }
        val title = label("REAL LINUX ENVIRONMENT", 16f)
        box.addView(title)
        val info = label("ZerosiX does not fake Linux. Import a real proot executable and an extracted Linux rootfs. After both exist, the LINUX button launches the real userland.", 12f)
        info.setPadding(0,16,0,16); box.addView(info)
        val b1 = Button(this).apply { text="IMPORT PROOT BINARY"; setOnClickListener { pendingProot=true; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE) }, REQ_PROOT) } }
        val b2 = Button(this).apply { text="IMPORT EXTRACTED ROOTFS FOLDER"; setOnClickListener { pendingTree=true; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_ROOTFS) } }
        val d = android.app.Dialog(this)
        val cancel = Button(this).apply { text="CANCEL"; setOnClickListener { d.dismiss() } }
        box.addView(b1); box.addView(b2); box.addView(cancel)
 d.setContentView(box); d.window?.setBackgroundDrawableResource(android.R.color.transparent); d.show()
    }

    private fun showNetworkInfo() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(n)
        val sb = StringBuilder()
        sb.append("\u001b[1;36mZEROSIX NETWORK INSPECTOR\u001b[0m\n\n")
        if (caps == null) {
            sb.append("STATUS: NO ACTIVE NETWORK\n")
        } else {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "OTHER"
            }
            sb.append("TRANSPORT: $transport\n")
            sb.append("VALIDATED: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}\n")
            sb.append("INTERNET: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}\n")
            sb.append("NOT_METERED: ${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}\n")
            sb.append("LINK_UPSTREAM: ${caps.linkUpstreamBandwidthKbps} Kbps\n")
            sb.append("LINK_DOWNSTREAM: ${caps.linkDownstreamBandwidthKbps} Kbps\n")
        }
        sb.append("\nACTIVE SOCKETS (/proc/net/tcp):\n")
        sb.append(readProcSockets("/proc/net/tcp"))
        sb.append("\nUDP SOCKETS (/proc/net/udp):\n")
        sb.append(readProcSockets("/proc/net/udp"))
        terminal.sessionWrite(sb.toString())
        modeLabel.text = "NETWORK // LIVE"
    }

    private fun readProcSockets(path: String): String {
        val lines = runCatching { File(path).readLines().drop(1).take(16) }.getOrDefault(emptyList())
        if (lines.isEmpty()) return "none / permission restricted\n"
        return lines.joinToString("\n") { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) "${parts[0]} ${parts[1]} ${parts[2]} state=${parts[3]}" else line
        } + "\n"
    }

    private fun TerminalView.sessionWrite(text: String) { if (::session.isInitialized) session.write((text + "\n").toByteArray()) }

    private fun openFiles() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14) }
        val title = label("ZEROSIX FILE INTERFACE", 15f)
        box.addView(title, LinearLayout.LayoutParams(-1, 42))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val roots = listOf(filesDir, cacheDir, File(filesDir, "linux"))
        roots.forEach { dir ->
            val row = key("OPEN ${dir.absolutePath}")
            row.setOnClickListener {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
            }
            list.addView(row, LinearLayout.LayoutParams(-1, 44).apply { setMargins(0, 3, 0, 3) })
        }
        val info = label("APP SANDBOX\n${filesDir.absolutePath}\n\nThe Android storage model is enforced by the OS. ZerosiX never pretends it has access it does not have.", 11f)
        info.setPadding(0, 12, 0, 12)
        box.addView(list)
        box.addView(info)
        val d = android.app.Dialog(this)
        val close = Button(this).apply { text = "CLOSE"; setOnClickListener { d.dismiss() } }
        box.addView(close)
        d.setContentView(box); d.show()
        modeLabel.text = "FILES // REAL STORAGE"
    }

    private fun showProcesses() {
        val sb = StringBuilder("\u001b[1;36mZEROSIX PROCESS MONITOR\u001b[0m\n\n")
        sb.append("PID\tSTATE\tNAME\n")
        val dirs = runCatching { File("/proc").listFiles().orEmpty().filter { it.name.all(Char::isDigit) }.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }.take(80) }.getOrDefault(emptyList())
        if (dirs.isEmpty()) sb.append("/proc process list unavailable on this Android build\n")
        dirs.forEach { dir ->
            val status = File(dir, "status")
            val map = runCatching { status.readLines().associate { it.substringBefore(":") to it.substringAfter(":").trim() } }.getOrDefault(emptyMap())
            val name = map["Name"] ?: dir.name
            val state = map["State"]?.take(1) ?: "?"
            sb.append("${dir.name}\t$state\t$name\n")
        }
        terminal.sessionWrite(sb.toString())
        modeLabel.text = "PROCESS // /proc"
    }

    private fun toggleTor() {
        try {
            val running = torLabel.text.toString().contains("ON") || torLabel.text.toString().contains("STARTING")
            val action = if (running) TorService.ACTION_STOP else TorService.ACTION_START
            val i = Intent(this, TorService::class.java).apply {
                this.action = action
                putExtra(TorService.EXTRA_PACKAGE_NAME, packageName)
            }
            startService(i)
            if (!torBound && action == TorService.ACTION_START) bindService(Intent(this, TorService::class.java), torConnection, BIND_AUTO_CREATE)
            torLabel.text = if (action == TorService.ACTION_START) "TOR STARTING" else "TOR STOPPING"
            modeLabel.text = "TOR // REAL DAEMON"
            sound.confirm()
        } catch (e: Exception) {
            torLabel.text = "TOR ERROR"
            android.util.Log.e("ZerosiX", "Tor action failed", e)
        }
    }

    private val torConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { torBound = true; torService = (service as? TorService.LocalBinder)?.service }
        override fun onServiceDisconnected(name: ComponentName?) { torBound = false; torService = null }
    }

    private fun registerTor() {
        LocalBroadcastManager.getInstance(this).registerReceiver(torReceiver, IntentFilter(TorService.ACTION_STATUS))
        registerReceiver(torReceiver, IntentFilter(TorService.ACTION_STATUS))
    }

    private fun runBootSequence() {
        boot = FrameLayout(this).apply { setBackgroundColor(0xFF010207.toInt()) }
        root.addView(boot, FrameLayout.LayoutParams(-1,-1))
        val lines = listOf("[BOOT] ZEROSIX INITIALIZING", "[ OK ] terminal engine", "[ OK ] system telemetry", "[ OK ] network telemetry", "[ OK ] file interface", "[ OK ] touch input", "[ OK ] sound engine", "[ OK ] Tor subsystem", "[INIT] visual matrix", "[INIT] terminal session", "[SYS ] integrity 100%", "[SYS ] interface READY")
        val box = TextView(this).apply { typeface=Typeface.MONOSPACE; textSize=11f; setTextColor(0xFF7DF9FF.toInt()); setPadding(22,30,22,20) }
        boot.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val logo = TextView(this).apply { text="ZEROSIX"; typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD); textSize=34f; gravity=Gravity.CENTER; setTextColor(0xFF7DF9FF.toInt()); alpha=0f; letterSpacing=.22f }
        boot.addView(logo, FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=0; alpha=.9f }
        val pp=FrameLayout.LayoutParams(-1,6); pp.gravity=Gravity.BOTTOM; pp.setMargins(28,0,28,32); boot.addView(progress,pp)
        sound.startup()
        var i=0
        fun next(){
            if(i<lines.size){ box.text = box.text.toString() + if(box.text.isEmpty()) "" else "\n" + lines[i]; sound.tick(); progress.progress=((i+1)*100/lines.size); i++; handler.postDelayed(::next,130) }
            else { box.animate().alpha(0f).setDuration(300).start(); logo.animate().alpha(1f).scaleX(1.04f).scaleY(1.04f).setDuration(650).withEndAction { handler.postDelayed({ boot.animate().alpha(0f).setDuration(600).withEndAction { root.removeView(boot) }.start(); sound.confirm() },650) }.start() }
        }
        handler.postDelayed(::next, 300)
    }

    private fun startMonitorLoop() {
        val task = object : Runnable {
            override fun run() {
                val s = monitor.sample()
                cpuLabel.text = "CPU ${s.cpu.toInt()}%"
                ramLabel.text = "RAM ${s.ram.toInt()}%"
                storageLabel.text = "STO ${s.storage.toInt()}%"
                batteryLabel.text = "BAT ${s.battery}%"
                netLabel.text = "NET ↓ ${formatRate(s.rx)}  ↑ ${formatRate(s.tx)}"
                cpuGraph.push(s.cpu, "CPU", "%")
                netGraph.push((s.rx + s.tx).toFloat(), "NET", "B/s")
                hud.tick((System.currentTimeMillis() % 6000L) / 6000f)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(task)
    }

    private fun formatRate(v: Long): String = when {
        v >= 1024*1024 -> String.format(Locale.US, "%.1fMB/s", v/1024.0/1024.0)
        v >= 1024 -> String.format(Locale.US, "%.0fKB/s", v/1024.0)
        else -> "${v}B/s"
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == REQ_PROOT) {
            val out = File(filesDir, "linux/proot")
            out.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.setExecutable(true, false)
            Toast.makeText(this, "PROOT imported. Now import a rootfs folder.", Toast.LENGTH_LONG).show()
        } else if (requestCode == REQ_ROOTFS) {
            val rootDoc = DocumentFile.fromTreeUri(this, uri) ?: return
            val target = File(filesDir, "linux/rootfs")
            target.deleteRecursively(); target.mkdirs()
            copyDocumentTree(rootDoc, target)
            Toast.makeText(this, "Linux rootfs imported.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyDocumentTree(doc: DocumentFile, target: File) {
        doc.listFiles().forEach { child ->
            val safeName = child.name?.replace("/", "_") ?: return@forEach
            val out = File(target, safeName)
            if (child.isDirectory) { out.mkdirs(); copyDocumentTree(child, out) }
            else { contentResolver.openInputStream(child.uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } }
        }
    }

    private fun clipboard() = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager

    override fun onDestroy() {
        runCatching { session.finishIfRunning() }
        runCatching { if (torBound) unbindService(torConnection) }
        runCatching { LocalBroadcastManager.getInstance(this).unregisterReceiver(torReceiver) }
        runCatching { unregisterReceiver(torReceiver) }
        sound.release()
        super.onDestroy()
    }
}
