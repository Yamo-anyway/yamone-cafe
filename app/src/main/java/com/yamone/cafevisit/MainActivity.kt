package com.yamone.cafevisit

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*

/** Small native UI, no tracking SDKs, no server, no account or password storage. */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable { override fun run() { update(); ui.postDelayed(this, 1000) } }
    private lateinit var browser: WebView
    private lateinit var browserHost: FrameLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var stateBadge: TextView
    private lateinit var count: TextView
    private lateinit var state: TextView
    private lateinit var next: TextView
    private lateinit var result: TextView
    private lateinit var metrics: TextView
    private lateinit var start: Button
    private lateinit var pause: Button
    private lateinit var stop: Button
    private lateinit var cafe: EditText
    private lateinit var minM: EditText
    private lateinit var minS: EditText
    private lateinit var maxM: EditText
    private lateinit var maxS: EditText
    private lateinit var permissions: TextView
    private lateinit var logList: LinearLayout
    private val pages = ArrayList<View>()
    private val tabs = ArrayList<TextView>()
    private var selected = 0
    private var lastLogs = ""
    private var restoreScroll = -1
    private var restoringUrl: String? = null
    private val p get() = Store.prefs(this)
    private val ink get() = getColor(R.color.ink)
    private val muted get() = getColor(R.color.muted)
    private val accent get() = getColor(R.color.accent)
    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.bg)) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_cafe); contentDescription = "Yamone Cafe" }
        header.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
        val title = column().apply { setPadding(dp(10), 0, 0, 0) }
        title.addView(text("YAMONE  CAFE", 17, bold = true))
        title.addView(text("나만의 카페 브라우저", 11, muted))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        stateBadge = text("대기", 12, accent, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8))
            background = shape(getColor(R.color.soft), 20); setOnClickListener { select(1) }
        }
        header.addView(stateBadge)
        root.addView(header)
        val body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        pages.add(buildBrowser(saved)); pages.add(buildAutomation()); pages.add(buildLogs())
        pages.forEach { body.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        val bottom = row().apply { setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(getColor(R.color.surface)) }
        listOf("브라우저", "자동 실행", "기록").forEachIndexed { i, name ->
            val tab = text(name, 14, muted, true).apply {
                gravity = Gravity.CENTER; contentDescription = name; setOnClickListener { select(i) }
            }
            tabs.add(tab)
            bottom.addView(tab, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        root.addView(bottom)
        select(saved?.getInt("tab", 0) ?: 0)
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { back() }
    }

    private fun buildBrowser(saved: Bundle?): View {
        val panel = column()
        val bar = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(12), dp(8)) }
        address = input("네이버 또는 카페 주소").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> navigateInput(); true }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(button("이동") { navigateInput() }, LinearLayout.LayoutParams(dp(68), dp(48)).apply { leftMargin = dp(6) })
        panel.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(accent); visibility = View.GONE
        }
        panel.addView(progress, LinearLayout.LayoutParams(-1, dp(2)))
        browserHost = FrameLayout(this)
        panel.addView(browserHost, LinearLayout.LayoutParams(-1, 0, 1f))
        installBrowser(saved)
        val tools = row().apply { setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(getColor(R.color.surface)) }
        val actions = listOf<Pair<String, () -> Unit>>(
            "‹" to { if (browser.canGoBack()) browser.goBack() },
            "›" to { if (browser.canGoForward()) browser.goForward() },
            "로그인" to { browser.loadUrl(Browser.LOGIN) },
            "카페" to { browser.loadUrl(Store.config(this)?.cafeUrl ?: Browser.HOME) },
            "새로고침" to { browser.reload() }
        )
        actions.forEach { (name, action) -> tools.addView(button(name, false, action), LinearLayout.LayoutParams(0, dp(48), if (name.length > 2) 1.5f else 1f)) }
        panel.addView(tools)
        return panel
    }

    private fun installBrowser(saved: Bundle?) {
        browser = Browser.create(this)
        browserHost.addView(browser, FrameLayout.LayoutParams(-1, -1))
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) {
                progress.progress = value; progress.visibility = if (value >= 100) View.GONE else View.VISIBLE
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (VisitConfig.isNaverHttps(request.url.toString())) return false
                if (request.isForMainFrame) toast("앱 내부에서는 네이버 HTTPS 페이지만 엽니다.")
                return true
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (!address.hasFocus()) address.setText(url ?: "")
                rememberPage()
                if (restoreScroll >= 0 && url == restoringUrl) {
                    val y = restoreScroll; restoreScroll = -1
                    view.postDelayed({ if (view.url == url) view.scrollTo(0, y) }, 400)
                }
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (!address.hasFocus()) address.setText(url ?: "")
                rememberPage()
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); toast("보안 인증서 오류로 연결을 중단했습니다.")
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) toast("페이지를 열지 못했습니다. 연결 상태를 확인하세요.")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                browserHost.removeView(view); view.destroy()
                browserHost.post { installBrowser(null); toast("브라우저를 복구했습니다.") }
                return true
            }
        }
        val restored = saved?.getBundle("web")?.let { browser.restoreState(it) } != null
        if (!restored) {
            val last = p.getString("last_url", Browser.HOME).orEmpty()
            restoringUrl = if (VisitConfig.isNaverHttps(last)) last else Browser.HOME
            restoreScroll = p.getInt("scroll", 0)
            browser.loadUrl(restoringUrl!!)
        }
    }

    private fun buildAutomation(): View {
        val content = column().apply { setPadding(dp(18), dp(10), dp(18), dp(20)) }
        content.addView(text("자동 새로고침", 25, bold = true))
        content.addView(text("읽는 화면은 그대로. 일정은 독립적으로.", 13, muted), space(4, 16))
        val hero = column().apply { background = shape(Color.rgb(18, 61, 51), 22); setPadding(dp(22), dp(20), dp(22), dp(20)) }
        state = text("시작할 준비가 됐어요", 14, Color.rgb(160, 223, 197), true)
        count = text("31:00—35:00", 36, Color.WHITE, true).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        next = text("매번 초 단위로 다른 간격", 13, Color.rgb(195, 220, 209))
        result = text("첫 실행은 바로 접속합니다.", 12, Color.rgb(195, 220, 209))
        hero.addView(state); hero.addView(count, space(12, 8)); hero.addView(next); hero.addView(result, space(12, 0))
        content.addView(hero)
        start = button("자동 실행 시작", true) {
            if (!saveConfig()) return@button
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
            command(RefreshService.START)
        }
        content.addView(start, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        val controls = row()
        pause = button("일시정지") { command(if (p.getBoolean("paused", false)) RefreshService.RESUME else RefreshService.PAUSE) }
        stop = button("중지") { command(RefreshService.STOP) }
        controls.addView(pause, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(5) })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(5) })
        content.addView(controls, space(8, 0))
        metrics = text("시도 0    ·    로드 0    ·    오류 0", 13, muted).apply { gravity = Gravity.CENTER }
        content.addView(metrics, space(12, 20))
        val target = card(content, "새로고침할 카페")
        target.addView(text("자동 실행은 이 주소만 불러옵니다.", 12, muted), space(4, 10))
        cafe = input("https://cafe.naver.com/카페이름").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(p.getString("cafe", ""))
        }
        target.addView(cafe, LinearLayout.LayoutParams(-1, dp(50)))
        target.addView(button("현재 카페 주소 가져오기") {
            if (p.getBoolean("running", false)) toast("자동 실행을 중지한 뒤 설정을 바꿔 주세요.")
            else runCatching { VisitConfig.normalizeCafeUrl(browser.url.orEmpty()) }.onSuccess { cafe.setText(it) }.onFailure { toast("브라우저에서 원하는 카페를 먼저 열어 주세요.") }
        }, space(8, 0))
        val timing = card(content, "새로고침 간격")
        val timeRow = row()
        fun timeBox(title: String, seconds: Int): Triple<LinearLayout, EditText, EditText> {
            val box = column(); box.addView(text(title, 12, muted), space(0, 7))
            val r = row().apply { gravity = Gravity.CENTER_VERTICAL }
            val m = input("분").apply { setText((seconds / 60).toString()); inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(4)); gravity = Gravity.CENTER }
            val s = input("초").apply { setText((seconds % 60).toString().padStart(2, '0')); inputType = InputType.TYPE_CLASS_NUMBER; filters = arrayOf(InputFilter.LengthFilter(2)); gravity = Gravity.CENTER }
            r.addView(m, LinearLayout.LayoutParams(0, dp(50), 1.2f)); r.addView(text("분 ", 12, muted))
            r.addView(s, LinearLayout.LayoutParams(0, dp(50), 1f)); r.addView(text("초", 12, muted))
            box.addView(r); return Triple(box, m, s)
        }
        val lo = timeBox("최소", p.getInt("min", 1860)); val hi = timeBox("최대", p.getInt("max", 2100))
        minM = lo.second; minS = lo.third; maxM = hi.second; maxS = hi.third
        timeRow.addView(lo.first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        timeRow.addView(hi.first, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        timing.addView(timeRow, space(12, 10))
        timing.addView(text("31분~24시간 · 같은 값은 고정 간격\n직접 읽거나 새로고침해도 자동 일정은 바뀌지 않습니다.", 12, muted))
        timing.addView(button("설정 저장") { if (saveConfig()) toast("설정을 저장했습니다.") }, space(12, 0))
        val permissionCard = card(content, "백그라운드 실행 준비")
        permissions = text("", 12, muted)
        permissionCard.addView(permissions, space(8, 10))
        permissionCard.addView(button("알림 허용 설정") { notificationSettings() })
        permissionCard.addView(button("정확한 알람 허용") {
            if (Build.VERSION.SDK_INT >= 31 && !Schedule.exact(this)) openSetting(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            else toast("정확한 알람을 사용할 수 있습니다.")
        }, space(6, 0))
        permissionCard.addView(button("배터리 제한 해제") {
            val power = getSystemService(PowerManager::class.java)
            if (power.isIgnoringBatteryOptimizations(packageName)) toast("배터리 최적화 제외 상태입니다.")
            else openSetting(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }, space(6, 0))
        val reboot = Switch(this).apply {
            text = "휴대폰 재시작 후 재개 시도"; textSize = 13f; setTextColor(ink); minHeight = dp(52)
            isChecked = p.getBoolean("resume_boot", false)
            setOnCheckedChangeListener { _, checked -> p.edit().putBoolean("resume_boot", checked).apply() }
        }
        permissionCard.addView(reboot, space(8, 0))
        content.addView(text("개인용 v0.4.0 · 외부 앱을 열지 않습니다.\n절전·강제 중지·네트워크 상태에 따라 실행이 지연될 수 있습니다. 로그인은 네이버 화면에서 직접 진행하세요.", 12, muted), space(18, 4))
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun buildLogs(): View {
        val content = column().apply { setPadding(dp(18), dp(10), dp(18), dp(20)) }
        content.addView(text("실행 기록", 25, bold = true))
        content.addView(text("최근 100건 · 기기 안에만 저장", 13, muted), space(6, 14))
        val controls = row()
        controls.addView(button("기록 복사") {
            val value = Store.logs(this).joinToString("\n") { "${Store.stamp(it.getLong("time"))}  ${it.getString("text")}" }
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Yamone Cafe 기록", value))
            toast("기록을 복사했습니다.")
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(button("지우기") {
            AlertDialog.Builder(this).setTitle("실행 기록을 지울까요?")
                .setMessage("로그인과 자동 실행 설정은 유지됩니다.")
                .setNegativeButton("취소", null).setPositiveButton("지우기") { _, _ -> p.edit().putString("logs", "[]").apply(); lastLogs = ""; update() }.show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(controls)
        logList = column(); content.addView(logList, space(14, 0))
        return ScrollView(this).apply { addView(content) }
    }

    private fun update() {
        if (!::state.isInitialized) return
        val running = p.getBoolean("running", false); val paused = p.getBoolean("paused", false)
        stateBadge.text = if (!running) "대기" else if (paused) "일시정지" else "실행 중"
        state.text = if (!running) "시작할 준비가 됐어요" else if (paused) "잠시 쉬는 중" else "다음 자동 새로고침까지"
        val left = if (paused) p.getLong("remaining", 0) else p.getLong("next_elapsed", 0) - SystemClock.elapsedRealtime()
        count.text = if (running) Store.duration((left.coerceAtLeast(0) + 999) / 1000) else "31:00—35:00"
        next.text = if (!running) "첫 실행은 바로 접속합니다." else if (paused) "재개하면 남은 시간부터 계속합니다." else "예정 ${Store.clock(p.getLong("next_wall", 0))}"
        result.text = p.getString("result", "읽는 화면과 별도로 동작합니다.")
        metrics.text = "시도 ${p.getInt("attempts", 0)}    ·    로드 ${p.getInt("completed", 0)}    ·    오류 ${p.getInt("errors", 0)}"
        start.visibility = if (running) View.GONE else View.VISIBLE
        pause.isEnabled = running; stop.isEnabled = running
        pause.alpha = if (running) 1f else 0.4f; stop.alpha = pause.alpha
        pause.text = if (paused) "재개" else "일시정지"
        listOf(cafe, minM, minS, maxM, maxS).forEach { it.isEnabled = !running; it.alpha = if (running) 0.65f else 1f }
        val allowed = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        permissions.text = "알림 ${if (allowed) "허용됨" else "허용 필요"}  ·  알람 ${if (Schedule.exact(this)) "정확" else "지연 가능"}\n배터리 ${if (battery) "최적화 제외" else "제한 해제 권장"}"
        val raw = p.getString("logs", "[]").orEmpty()
        if (selected == 2 && raw != lastLogs) {
            lastLogs = raw; logList.removeAllViews()
            val logs = Store.logs(this)
            if (logs.isEmpty()) logList.addView(text("아직 실행 기록이 없어요.\n자동 실행을 시작하면 여기에 표시됩니다.", 15, muted), space(24, 0))
            logs.forEach { entry ->
                val box = column().apply { background = shape(getColor(R.color.surface), 14); setPadding(dp(16), dp(14), dp(16), dp(14)) }
                val color = when (entry.optString("kind")) { "error" -> getColor(R.color.danger); "success" -> accent; else -> muted }
                box.addView(text(Store.stamp(entry.getLong("time")), 12, color, true))
                box.addView(text(entry.getString("text"), 14), space(5, 0))
                logList.addView(box, space(0, 8))
            }
        }
    }
    private fun select(index: Int) {
        selected = index.coerceIn(0, 2)
        pages.forEachIndexed { i, v -> v.visibility = if (i == selected) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, v -> v.setTextColor(if (i == selected) accent else muted); v.background = shape(if (i == selected) getColor(R.color.soft) else Color.TRANSPARENT, 14) }
        update()
    }
    private fun saveConfig(): Boolean {
        if (p.getBoolean("running", false)) { toast("자동 실행을 중지한 뒤 설정을 바꿔 주세요."); return false }
        return runCatching { VisitConfig.fromInputs(cafe.text.toString(), minM.text.toString(), minS.text.toString(), maxM.text.toString(), maxS.text.toString()) }
            .onSuccess { Store.save(this, it); cafe.setText(it.cafeUrl) }
            .onFailure { toast(it.message ?: "설정을 확인하세요.") }.isSuccess
    }
    private fun command(action: String) {
        try { startForegroundService(Intent(this, RefreshService::class.java).setAction(action)) }
        catch (_: RuntimeException) { toast("서비스를 실행하지 못했습니다. 기기 설정을 확인하세요.") }
        ui.postDelayed({ update() }, 200)
    }
    private fun navigateInput() {
        runCatching { VisitConfig.navigationUrl(address.text.toString()) }.onSuccess {
            address.clearFocus(); getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(address.windowToken, 0)
            browser.loadUrl(it)
        }.onFailure { toast(it.message ?: "주소를 확인하세요.") }
    }
    private fun notificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
        else openSetting(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
    private fun openSetting(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast("기기의 앱 설정에서 직접 확인해 주세요.") } }
    private fun rememberPage() {
        val url = browser.url ?: return
        if (VisitConfig.isNaverHttps(url) && Uri.parse(url).host != "nid.naver.com") p.edit().putString("last_url", url).apply()
    }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) browser.onResume(); ui.removeCallbacks(pulse); ui.post(pulse) }
    override fun onPause() {
        ui.removeCallbacks(pulse)
        if (::browser.isInitialized) {
            rememberPage(); p.edit().putInt("scroll", browser.scrollY).apply(); browser.onPause()
            CookieManager.getInstance().flush()
            // Never call pauseTimers(): it would stop the service's other WebView too.
        }
        super.onPause()
    }
    override fun onSaveInstanceState(out: Bundle) {
        val web = Bundle(); browser.saveState(web); out.putBundle("web", web); out.putInt("tab", selected)
        super.onSaveInstanceState(out)
    }
    private fun back() { if (selected != 0) select(0) else if (browser.canGoBack()) browser.goBack() else moveTaskToBack(true) }
    @Deprecated("Legacy Android back callback")
    override fun onBackPressed() { back() }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); if (::browser.isInitialized) { browserHost.removeView(browser); browser.destroy() }; super.onDestroy() }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun text(value: String, sp: Int, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = sp.toFloat(); setTextColor(color); setLineSpacing(dp(2).toFloat(), 1f)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun button(title: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = title; textSize = 13f; isAllCaps = false; minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
        setTextColor(if (primary) Color.WHITE else accent); setPadding(dp(10), dp(8), dp(10), dp(8))
        background = RippleDrawable(ColorStateList.valueOf(0x22087F68), shape(if (primary) Color.rgb(8, 127, 104) else getColor(R.color.soft), 13), null)
        setOnClickListener { action() }
    }
    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText; textSize = 14f; setTextColor(ink); setHintTextColor(muted); setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10)); background = shape(getColor(R.color.bg), 10).apply { setStroke(dp(1), getColor(R.color.line)) }
        selectAllOnFocus = true
    }
    private fun space(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val box = column().apply { background = shape(getColor(R.color.surface), 18); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        box.addView(text(title, 15, bold = true)); parent.addView(box, space(0, 12)); return box
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}
