package com.yamone.cafevisit

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.webkit.*

/** All WebView operations run on the main thread. The visible browser is never touched. */
class RefreshService : Service() {
    companion object {
        const val START = "com.yamone.cafevisit.START"
        const val PAUSE = "com.yamone.cafevisit.PAUSE"
        const val RESUME = "com.yamone.cafevisit.RESUME"
        const val STOP = "com.yamone.cafevisit.STOP"
        const val RESTORE = "com.yamone.cafevisit.RESTORE"
        private const val CHANNEL = "refresh_status"
        private const val NOTICE = 1041
    }
    private val handler = Handler(Looper.getMainLooper())
    private val tickTask = Runnable { tick() }
    private val timeoutTask = Runnable { finishLoad(false, "페이지 로딩 제한시간 60초 초과") }
    private var web: WebView? = null
    private var loading = false
    private var wake: PowerManager.WakeLock? = null
    private val p get() = Store.prefs(this)
    private val active get() = p.getBoolean("running", false) && !p.getBoolean("paused", false)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "자동 새로고침 상태", NotificationManager.IMPORTANCE_LOW).apply {
                description = "다음 예정 시각과 일시정지·중지 제어"
                setShowBadge(false)
            })
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTICE, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTICE, notification())
        when (intent?.action) {
            STOP -> { stopSession(); return START_NOT_STICKY }
            START -> {
                if (Store.config(this) == null) { stopSession(); return START_NOT_STICKY }
                if (!p.getBoolean("running", false)) {
                    p.edit().putBoolean("running", true).putBoolean("paused", false)
                        .putInt("attempts", 0).putInt("completed", 0).putInt("errors", 0)
                        .putLong("next_elapsed", 0).putLong("next_wall", 0)
                        .putString("result", "자동 실행 시작").commit()
                    Store.log(this, "자동 실행 시작 · 첫 페이지 즉시 로딩")
                }
            }
            PAUSE -> { pauseSession("사용자가 일시정지했습니다"); return START_STICKY }
            RESUME -> {
                if (!p.getBoolean("running", false)) { stopSession(); return START_NOT_STICKY }
                if (p.getBoolean("paused", false)) {
                    val remaining = p.getLong("remaining", 0).coerceAtLeast(0)
                    p.edit().putBoolean("paused", false)
                        .putLong("next_elapsed", SystemClock.elapsedRealtime() + remaining)
                        .putLong("next_wall", System.currentTimeMillis() + remaining).commit()
                    Store.log(this, "자동 실행 재개 · 남은 시간 유지")
                }
            }
        }
        if (!p.getBoolean("running", false)) { stopSession(); return START_NOT_STICKY }
        if (active) tick() else updateNotice()
        return START_STICKY
    }
    private fun arm() {
        handler.removeCallbacks(tickTask)
        if (!active) return
        val next = p.getLong("next_elapsed", 0)
        Schedule.arm(this, next)
        handler.postDelayed(tickTask, (next - SystemClock.elapsedRealtime()).coerceAtLeast(100))
        updateNotice()
    }
    private fun tick() {
        if (!active || loading) return
        if (p.getLong("next_elapsed", 0) > SystemClock.elapsedRealtime() + 100) { arm(); return }
        val cfg = Store.config(this) ?: run { pauseSession("카페 설정을 확인해 주세요"); return }
        val now = SystemClock.elapsedRealtime()
        val seconds = cfg.chooseDelaySeconds()
        // Anchor to this automatic attempt's START, not load completion or user browsing.
        p.edit().putLong("next_elapsed", now + seconds * 1000L)
            .putLong("next_wall", System.currentTimeMillis() + seconds * 1000L)
            .putLong("last_at", System.currentTimeMillis()).putInt("attempts", p.getInt("attempts", 0) + 1)
            .putString("result", "별도 브라우저에서 로딩 중").commit()
        arm()
        Store.log(this, "자동 로딩 시도 · 다음 간격 ${Store.duration(seconds.toLong())}")
        loading = true
        val cm = getSystemService(ConnectivityManager::class.java)
        if (cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            finishLoad(false, "인터넷 연결 없음 · 다음 일정에 다시 시도"); return
        }
        try {
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YamoneCafe:PageLoad").apply { acquire(75_000) }
            val w = Browser.create(applicationContext, fresh = true)
            web = w
            w.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val blocked = !VisitConfig.isNaverHttps(request.url.toString())
                    if (blocked && request.isForMainFrame && web === view) finishLoad(false, "네이버 외부 이동을 차단했습니다")
                    return blocked
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (web === view && Uri.parse(url ?: "").host == "nid.naver.com") {
                        finishLoad(false, "로그인이 필요합니다"); pauseSession("브라우저에서 로그인 후 재개해 주세요")
                    }
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (web !== view || !loading || !VisitConfig.isNaverHttps(url)) return
                    val host = Uri.parse(url).host
                    if (host == "cafe.naver.com" || host == "m.cafe.naver.com") {
                        finishLoad(true, "카페 페이지 로딩 완료 신호 수신")
                    } else finishLoad(false, "다른 네이버 화면으로 이동 · 직접 확인 필요")
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (web === view && request.isForMainFrame) finishLoad(false, "페이지 로딩 오류 (${error.errorCode})")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (web !== view || !request.isForMainFrame) return
                    val code = response.statusCode
                    finishLoad(false, "페이지 HTTP $code 오류")
                    if (code == 401 || code == 403 || code == 429) pauseSession("접근 제한 확인 필요 (HTTP $code)")
                }
                override fun onReceivedSslError(view: WebView, sslHandler: SslErrorHandler, error: SslError) {
                    sslHandler.cancel()
                    if (web === view) { finishLoad(false, "보안 인증서 오류"); pauseSession("안전하지 않은 연결을 중단했습니다") }
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (web === view) finishLoad(false, "웹뷰 렌더러 종료 · 다음 일정에 재생성")
                    return true
                }
            }
            w.onResume()
            handler.postDelayed(timeoutTask, 60_000)
            w.loadUrl(cfg.cafeUrl)
        } catch (_: Exception) { finishLoad(false, "브라우저 실행 오류 · 다음 일정에 다시 시도") }
    }
    private fun releaseBrowser() {
        loading = false
        handler.removeCallbacks(timeoutTask)
        val old = web
        web = null // invalidate all callbacks before cancelling the navigation
        if (old != null) {
            runCatching { old.stopLoading() }
            handler.post { runCatching { old.destroy() } }
        }
        wake?.let { if (it.isHeld) it.release() }
        wake = null
    }
    private fun finishLoad(ok: Boolean, message: String) {
        if (!loading) return
        releaseBrowser()
        runCatching { CookieManager.getInstance().flush() }
        val key = if (ok) "completed" else "errors"
        p.edit().putInt(key, p.getInt(key, 0) + 1).putString("result", message).apply()
        Store.log(this, message, if (ok) "success" else "error")
        updateNotice()
    }
    private fun pauseSession(reason: String) {
        if (!p.getBoolean("running", false)) { stopSession(); return }
        val left = if (p.getBoolean("paused", false)) p.getLong("remaining", 0)
            else (p.getLong("next_elapsed", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        p.edit().putBoolean("paused", true).putLong("remaining", left)
            .putLong("next_wall", 0).putString("result", reason).commit()
        handler.removeCallbacks(tickTask)
        Schedule.cancel(this)
        releaseBrowser()
        Store.log(this, reason)
        updateNotice()
    }
    private fun stopSession() {
        p.edit().putBoolean("running", false).putBoolean("paused", false).putLong("next_elapsed", 0)
            .putLong("next_wall", 0).putLong("remaining", 0).putString("result", "자동 실행 중지").commit()
        handler.removeCallbacks(tickTask)
        Schedule.cancel(this)
        releaseBrowser()
        Store.log(this, "자동 실행 중지")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun notification(): Notification {
        val paused = p.getBoolean("paused", false)
        fun command(code: Int, action: String) = PendingIntent.getForegroundService(this, code,
            Intent(this, RefreshService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = p.getLong("next_wall", 0)
        val text = if (paused) p.getString("result", "일시정지") else "다음 ${Store.clock(next)} · ${p.getString("result", "준비 중")}"
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_cafe)
            .setContentTitle(if (paused) "Yamone Cafe · 일시정지" else "Yamone Cafe · 자동 실행 중")
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, if (paused) "재개" else "일시정지", command(2, if (paused) RESUME else PAUSE)).build())
            .addAction(Notification.Action.Builder(null, "중지", command(3, STOP)).build()).build()
    }
    private fun updateNotice() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(NOTICE, notification())
    }
    override fun onDestroy() {
        handler.removeCallbacks(tickTask)
        releaseBrowser()
        // Keep the pending system alarm for a system-reclaimed active session.
        // Explicit pause/stop already cancels it. A force-stop cannot be bypassed.
        super.onDestroy()
    }
}
