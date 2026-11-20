package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.engine.BrowserEngineManager
import com.example.network.KaspaPrivacyEngine

object KaspaWebViewConfigurator {
    private val appliedWebAuthState = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebSettings, Boolean>())
    private val appliedWebAuthLevel = java.util.Collections.synchronizedMap(java.util.WeakHashMap<WebSettings, Int>())

    /**
     * Uses browser-mode WebAuthn only when Android has granted the privileged
     * CREDENTIAL_MANAGER_SET_ORIGIN permission. Otherwise remains in app mode, which
     * is limited to relying parties associated with this app through Digital Asset Links.
     * Returns the runtime-applied support level or -1 if unsupported.
     */
    fun applyWebAuthenticationSupport(settings: WebSettings, enabled: Boolean): Int {
        return try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                -1
            } else {
                val alreadyApplied = synchronized(appliedWebAuthState) {
                    appliedWebAuthState[settings] == enabled
                }
                if (alreadyApplied) {
                    return synchronized(appliedWebAuthLevel) { appliedWebAuthLevel[settings] }
                        ?: WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                }
                val hasPrivilegedOriginPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    settings.context.checkSelfPermission("android.permission.CREDENTIAL_MANAGER_SET_ORIGIN") ==
                    PackageManager.PERMISSION_GRANTED
                val targetLevel = when {
                    !enabled -> WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
                    hasPrivilegedOriginPermission -> WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER
                    else -> WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                }
                WebSettingsCompat.setWebAuthenticationSupport(settings, targetLevel)
                val actualLevel = try {
                    WebSettingsCompat.getWebAuthenticationSupport(settings)
                } catch (_: Throwable) {
                    targetLevel
                }
                synchronized(appliedWebAuthState) {
                    appliedWebAuthState[settings] = enabled
                    appliedWebAuthLevel[settings] = actualLevel
                }
                BrowserEngineManager.recordPasskeySupport(enabled, actualLevel)
                actualLevel
            }
        } catch (t: Throwable) {
            Log.w(TAG, "WebAuthn support config notice: ${t.message}")
            -1
        }
    }

    /**
     * Applies Kazview's native WebKit & WebKitGTK enhancements (Back-Forward Cache for instant
     * back/forward navigation and Privacy Sandbox Attribution Reporting suppression) safely
     * behind WebViewFeature checks.
     */
    @SuppressLint("RestrictedApi")
    fun applyKazviewSettings(settings: WebSettings) {
        var bfCache = false
        var attributionDisabled = false
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
                WebSettingsCompat.setBackForwardCacheEnabled(settings, true)
                bfCache = runCatching { WebSettingsCompat.getBackForwardCacheEnabled(settings) }.getOrDefault(true)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "BFCache setup notice: ${t.message}")
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
                WebSettingsCompat.setAttributionRegistrationBehavior(
                    settings,
                    WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED
                )
                attributionDisabled = true
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Attribution privacy setup notice: ${t.message}")
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Requested-With suppression notice: ${t.message}")
        }
        BrowserEngineManager.recordWebSettingsApplied(
            bfCacheEnabled = bfCache,
            attributionDisabled = attributionDisabled
        )
    }

    /**
     * Dynamically ensures Google Sign-In, OAuth, and 2FA authentication URLs have proper cookies and UA settings.
     */
    fun checkAndConfigureForUrl(webView: WebView, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            if (KaspaPrivacyEngine.isGoogleAuthUrl(url) || KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(url)) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                cookieManager.flush()
            }
        } catch (_: Throwable) {}
    }


    private const val TAG = "KaspaWebViewConfig"

    /**
     * Synchronously enables and configures the Android WebView ServiceWorker subsystem.
     * Ensures ServiceWorker registration, cache API, fetch interception, and offline resources
     * work properly for PWAs and modern web applications.
     */
    fun initServiceWorkerSupport() {
        try {
            val swController = ServiceWorkerController.getInstance()
            val swSettings = swController.serviceWorkerWebSettings
            swSettings.allowContentAccess = false
            swSettings.allowFileAccess = false
            swSettings.blockNetworkLoads = false
            swSettings.cacheMode = WebSettings.LOAD_DEFAULT
            swController.setServiceWorkerClient(object : ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    if (request.isForMainFrame) return null
                    val reqUrl = request.url?.toString()
                    if (!reqUrl.isNullOrBlank()) {
                        if (KaspaPrivacyEngine.isEssentialAuthOrCaptchaUrl(reqUrl)) {
                            return null
                        }
                        // Never block PWA/game offline stylesheets, scripts, WebAssembly, audio, or fonts
                        val path = runCatching { request.url?.path?.lowercase() }.getOrNull() ?: ""
                        val isAsset = path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".mjs") ||
                            path.endsWith(".json") || path.endsWith(".wasm") || path.endsWith(".pck") ||
                            path.endsWith(".data") || path.endsWith(".unityweb") || path.endsWith(".glb") ||
                            path.endsWith(".gltf") || path.endsWith(".woff") || path.endsWith(".woff2") ||
                            path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".png") ||
                            path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") ||
                            path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".mp3") ||
                            path.endsWith(".ogg") || path.endsWith(".wav") || path.endsWith(".mp4") ||
                            path.endsWith(".webm") || path.endsWith(".ktx") || path.endsWith(".ktx2") ||
                            path.endsWith(".basis") || path.endsWith(".dds") || path.endsWith(".hdr") ||
                            path.endsWith(".exr") || path.endsWith(".tga") || path.endsWith(".pvr") ||
                            path.endsWith(".astc") || path.endsWith(".bin")
                        if (isAsset) {
                            return null
                        }

                        if (KaspaPrivacyEngine.isTrackerOrAd(reqUrl)) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                403,
                                "Blocked by Shield",
                                mapOf("Access-Control-Allow-Origin" to "*"),
                                java.io.ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }
                    return super.shouldInterceptRequest(request)
                }
            })
            Log.d("PWA", "[PWA] service worker: active")
        } catch (e: Exception) {
            Log.w(TAG, "ServiceWorkerController initialization notice: ${e.message}")
        }
    }

    /**
     * Configures WebSettings with strict browser/PWA parity and security hardening.
     * Complies with Google Play policy and web standards.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyWebSettings(
        webView: WebView,
        context: Context,
        isDesktop: Boolean = false,
        acceptThirdPartyCookies: Boolean = false,
        webAuthEnabled: Boolean = true
    ) {
        val defaultDeviceUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Throwable) {
            null
        }

        webView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
            offscreenPreRaster = false

            // Web Platform Parity: Compatibility mode allows secure webapps and modern PWAs to render seamlessly
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }

            applyWebAuthenticationSupport(this, webAuthEnabled)
            applyKazviewSettings(this)
            KaspaPrivacyEngine.applyUserAgentAndMetadata(this, isDesktop, defaultDeviceUa)
        }

        // Cookie Configuration. Third-party cookies remain disabled unless the
        // caller explicitly opts in for a compatibility-sensitive flow.
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, acceptThirdPartyCookies)
        } catch (e: Exception) {
            Log.w(TAG, "CookieManager setup notice: ${e.message}")
        }

        // Native Document-Start Script Injection (True Chromium Engine-Level Farbling)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    KaspaPrivacyEngine.JS_PRIVACY_SHIELD_INJECTION,
                    setOf("*")
                )
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    KaspaPrivacyEngine.getWebGlTextureStabilityScript(),
                    setOf("*")
                )
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    BrowserBackgroundPlayback.getBackgroundPlaybackScript(),
                    setOf("*")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "DocumentStartJavaScript setup notice: ${e.message}")
        }
    }

    private fun escapeHtml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeJsString(input: String): String {
        val sb = StringBuilder(input.length + 2).append('"')
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\'' -> sb.append("\\'")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '<' -> sb.append("\\u003C")
                '>' -> sb.append("\\u003E")
                '&' -> sb.append("\\u0026")
                else -> sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    private fun buildOfflineArcadeMarkup(autoOpenGame: Boolean): String {
        val containerDisplay = if (autoOpenGame) "block" else "none"
        return """
            <div id="arcadeWrap" class="arcade-wrap" style="display:$containerDisplay;">
                <div class="arcade-header">
                    <div class="arcade-tabs">
                        <button id="tabRunner" class="arcade-tab active" onclick="selectOfflineGame('runner')">BlockDAG Runner</button>
                        <button id="tab2048" class="arcade-tab" onclick="selectOfflineGame('2048')">Kaspa 2048</button>
                    </div>
                    <div class="arcade-scores">
                        <span>Score <strong id="scoreVal">0</strong></span>
                        <span>Best <strong id="bestVal">0</strong></span>
                    </div>
                </div>
                <div id="paneRunner" class="game-pane">
                    <canvas id="runnerCanvas"></canvas>
                    <div class="game-hint">Tap anywhere or press Space to jump (double-jump enabled)</div>
                </div>
                <div id="pane2048" class="game-pane" style="display:none;">
                    <div id="grid2048" class="grid-2048"></div>
                    <div class="dpad-row">
                        <button class="dpad-btn" onclick="move2048(0,-1)" aria-label="Left">◀</button>
                        <button class="dpad-btn" onclick="move2048(-1,0)" aria-label="Up">▲</button>
                        <button class="dpad-btn" onclick="move2048(1,0)" aria-label="Down">▼</button>
                        <button class="dpad-btn" onclick="move2048(0,1)" aria-label="Right">▶</button>
                        <button class="dpad-btn new-btn" onclick="reset2048()">New</button>
                    </div>
                </div>
            </div>
            <script>
                (function() {
                    var activeGame = 'runner';
                    function readBest(k) {
                        try { return parseInt(localStorage.getItem(k) || '0', 10) || 0; } catch(e) { return 0; }
                    }
                    function writeBest(k, v) {
                        try { localStorage.setItem(k, String(v)); } catch(e) {}
                    }
                    function updateHud(score, best) {
                        var sEl = document.getElementById('scoreVal');
                        var bEl = document.getElementById('bestVal');
                        if (sEl) sEl.textContent = String(score);
                        if (bEl) bEl.textContent = String(best);
                    }

                    // --- Game 1: BlockDAG Runner ---
                    var canvas = document.getElementById('runnerCanvas');
                    var ctx = canvas ? canvas.getContext('2d') : null;
                    var rRunning = false, rDead = false, rScore = 0, rBest = readBest('kaspa_runner_best');
                    var rSpeed = 4.2, rGroundY = 0, rW = 320, rH = 180;
                    var player = { x: 44, y: 0, vy: 0, r: 13, jumps: 0 };
                    var obstacles = [], coins = [], stars = [];
                    var spawnTimer = 0, rafId = 0, lastTs = 0;

                    function sizeRunnerCanvas() {
                        if (!canvas) return;
                        var rect = canvas.parentElement.getBoundingClientRect();
                        var w = Math.max(240, Math.floor(rect.width));
                        var h = Math.min(220, Math.max(165, Math.floor(w * 0.52)));
                        var dpr = Math.min(window.devicePixelRatio || 1, 2);
                        rW = w; rH = h;
                        rGroundY = h - 30;
                        canvas.style.width = w + 'px';
                        canvas.style.height = h + 'px';
                        canvas.width = Math.floor(w * dpr);
                        canvas.height = Math.floor(h * dpr);
                        if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
                        if (player.y > rGroundY - player.r || player.y === 0) {
                            player.y = rGroundY - player.r;
                        }
                    }

                    function initRunnerStars() {
                        stars = [];
                        for (var i = 0; i < 18; i++) {
                            stars.push({ x: Math.random() * rW, y: 12 + Math.random() * Math.max(40, rGroundY - 50), s: 0.6 + Math.random() * 1.4 });
                        }
                    }

                    function resetRunner() {
                        sizeRunnerCanvas();
                        rScore = 0; rSpeed = 4.2; rDead = false; rRunning = true;
                        obstacles = []; coins = []; spawnTimer = 30;
                        player.y = rGroundY - player.r; player.vy = 0; player.jumps = 0;
                        updateHud(0, rBest);
                    }

                    function runnerJump() {
                        if (activeGame !== 'runner') return;
                        if (rDead || !rRunning) {
                            resetRunner();
                            return;
                        }
                        if (player.jumps < 2) {
                            player.vy = -8.8;
                            player.jumps++;
                        }
                    }

                    if (canvas) {
                        sizeRunnerCanvas();
                        initRunnerStars();
                        canvas.addEventListener('pointerdown', function(e) {
                            e.preventDefault();
                            runnerJump();
                        }, { passive: false });
                        window.addEventListener('resize', sizeRunnerCanvas);
                    }

                    function stepRunner(ts) {
                        rafId = requestAnimationFrame(stepRunner);
                        if (!ctx || activeGame !== 'runner') return;
                        var wrap = document.getElementById('arcadeWrap');
                        if (!wrap || wrap.style.display === 'none') return;
                        var dt = lastTs ? Math.min(2.2, (ts - lastTs) / 16.67) : 1;
                        lastTs = ts;

                        if (rRunning && !rDead) {
                            rScore += dt * 0.16;
                            rSpeed = 4.2 + Math.min(5.5, rScore / 140);
                            player.vy += 0.44 * dt;
                            player.y += player.vy * dt;
                            if (player.y >= rGroundY - player.r) {
                                player.y = rGroundY - player.r;
                                player.vy = 0;
                                player.jumps = 0;
                            }
                            spawnTimer -= dt;
                            if (spawnTimer <= 0) {
                                var h = 20 + Math.random() * 22;
                                var w = 14 + Math.random() * 10;
                                obstacles.push({ x: rW + 16, w: w, h: h });
                                if (Math.random() > 0.35) {
                                    coins.push({ x: rW + 65 + Math.random() * 45, y: rGroundY - 38 - Math.random() * 45, r: 7 });
                                }
                                spawnTimer = Math.max(36, 76 - rSpeed * 3.5 + Math.random() * 34);
                            }
                            for (var i = obstacles.length - 1; i >= 0; i--) {
                                var o = obstacles[i];
                                o.x -= rSpeed * dt;
                                var cx = Math.max(o.x, Math.min(player.x, o.x + o.w));
                                var cy = Math.max(rGroundY - o.h, Math.min(player.y, rGroundY));
                                var dx = player.x - cx, dy = player.y - cy;
                                if (dx * dx + dy * dy < (player.r - 2) * (player.r - 2)) {
                                    rDead = true;
                                    var finalScore = Math.floor(rScore);
                                    if (finalScore > rBest) { rBest = finalScore; writeBest('kaspa_runner_best', rBest); }
                                    updateHud(finalScore, rBest);
                                }
                                if (o.x + o.w < -10) obstacles.splice(i, 1);
                            }
                            for (var c = coins.length - 1; c >= 0; c--) {
                                var coin = coins[c];
                                coin.x -= rSpeed * dt;
                                var cdx = player.x - coin.x, cdy = player.y - coin.y;
                                if (cdx * cdx + cdy * cdy < (player.r + coin.r) * (player.r + coin.r)) {
                                    rScore += 15;
                                    coins.splice(c, 1);
                                } else if (coin.x < -12) {
                                    coins.splice(c, 1);
                                }
                            }
                            var shown = Math.floor(rScore);
                            if (shown > rBest) { rBest = shown; writeBest('kaspa_runner_best', rBest); }
                            updateHud(shown, rBest);
                        }

                        ctx.fillStyle = '#0B0F17';
                        ctx.fillRect(0, 0, rW, rH);
                        ctx.fillStyle = 'rgba(112,199,186,0.22)';
                        for (var s = 0; s < stars.length; s++) {
                            var st = stars[s];
                            if (rRunning && !rDead) st.x -= rSpeed * 0.2 * dt;
                            if (st.x < 0) st.x = rW;
                            ctx.fillRect(st.x, st.y, st.s * 2, st.s * 2);
                        }
                        ctx.strokeStyle = '#70C7BA';
                        ctx.lineWidth = 2;
                        ctx.beginPath();
                        ctx.moveTo(0, rGroundY);
                        ctx.lineTo(rW, rGroundY);
                        ctx.stroke();

                        ctx.fillStyle = '#EF4444';
                        for (var k = 0; k < obstacles.length; k++) {
                            var ob = obstacles[k];
                            ctx.beginPath();
                            ctx.moveTo(ob.x, rGroundY);
                            ctx.lineTo(ob.x + ob.w * 0.5, rGroundY - ob.h);
                            ctx.lineTo(ob.x + ob.w, rGroundY);
                            ctx.closePath();
                            ctx.fill();
                        }
                        ctx.fillStyle = '#FACC15';
                        for (var j = 0; j < coins.length; j++) {
                            ctx.beginPath();
                            ctx.arc(coins[j].x, coins[j].y, coins[j].r, 0, Math.PI * 2);
                            ctx.fill();
                        }
                        ctx.fillStyle = rDead ? '#F87171' : '#70C7BA';
                        ctx.beginPath();
                        ctx.arc(player.x, player.y, player.r, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.fillStyle = '#0B0F17';
                        ctx.font = 'bold 11px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        ctx.fillText('K', player.x, player.y + 0.5);

                        if (!rRunning || rDead) {
                            ctx.fillStyle = 'rgba(11,15,23,0.72)';
                            ctx.fillRect(0, 0, rW, rH);
                            ctx.fillStyle = '#FFFFFF';
                            ctx.font = 'bold 15px sans-serif';
                            ctx.fillText(rDead ? 'Node Crashed — Tap to Restart' : 'Tap to Launch BlockDAG Runner', rW / 2, rH / 2 - 8);
                            ctx.fillStyle = '#70C7BA';
                            ctx.font = '12px sans-serif';
                            ctx.fillText('Score: ' + Math.floor(rScore) + '   Best: ' + rBest, rW / 2, rH / 2 + 16);
                        }
                    }
                    requestAnimationFrame(stepRunner);

                    // --- Game 2: Kaspa 2048 ---
                    var board = [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];
                    var score2048 = 0, best2048 = readBest('kaspa_2048_best');

                    function tileBg(v) {
                        switch(v) {
                            case 0: return '#111827';
                            case 2: return '#1E293B';
                            case 4: return '#263549';
                            case 8: return '#0F766E';
                            case 16: return '#0D9488';
                            case 32: return '#14B8A6';
                            case 64: return '#0EA5E9';
                            case 128: return '#0284C7';
                            case 256: return '#6366F1';
                            case 512: return '#7C3AED';
                            case 1024: return '#D97706';
                            default: return '#70C7BA';
                        }
                    }

                    function addTile() {
                        var empty = [];
                        for (var r = 0; r < 4; r++) {
                            for (var c = 0; c < 4; c++) {
                                if (board[r][c] === 0) empty.push([r, c]);
                            }
                        }
                        if (empty.length === 0) return;
                        var pick = empty[Math.floor(Math.random() * empty.length)];
                        board[pick[0]][pick[1]] = Math.random() < 0.88 ? 2 : 4;
                    }

                    function render2048() {
                        var grid = document.getElementById('grid2048');
                        if (!grid) return;
                        grid.innerHTML = '';
                        for (var r = 0; r < 4; r++) {
                            for (var c = 0; c < 4; c++) {
                                var v = board[r][c];
                                var cell = document.createElement('div');
                                cell.className = 'cell-2048';
                                cell.style.background = tileBg(v);
                                cell.style.color = (v >= 2048 || v === 32) ? '#0B0F17' : '#F8FAFC';
                                cell.textContent = v > 0 ? String(v) : '';
                                grid.appendChild(cell);
                            }
                        }
                        if (activeGame === '2048') {
                            if (score2048 > best2048) { best2048 = score2048; writeBest('kaspa_2048_best', best2048); }
                            updateHud(score2048, best2048);
                        }
                    }

                    window.reset2048 = function() {
                        board = [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];
                        score2048 = 0;
                        addTile(); addTile();
                        render2048();
                    };

                    window.move2048 = function(dr, dc) {
                        var moved = false;
                        function slide(line) {
                            var nz = line.filter(function(x) { return x !== 0; });
                            var out = [];
                            for (var i = 0; i < nz.length; i++) {
                                if (i + 1 < nz.length && nz[i] === nz[i + 1]) {
                                    var merged = nz[i] * 2;
                                    score2048 += merged;
                                    out.push(merged);
                                    i++;
                                } else {
                                    out.push(nz[i]);
                                }
                            }
                            while (out.length < 4) out.push(0);
                            return out;
                        }
                        if (dc !== 0) {
                            for (var r = 0; r < 4; r++) {
                                var row = board[r].slice();
                                if (dc > 0) row.reverse();
                                var res = slide(row);
                                if (dc > 0) res.reverse();
                                for (var c = 0; c < 4; c++) {
                                    if (board[r][c] !== res[c]) moved = true;
                                    board[r][c] = res[c];
                                }
                            }
                        } else if (dr !== 0) {
                            for (var c2 = 0; c2 < 4; c2++) {
                                var col = [board[0][c2], board[1][c2], board[2][c2], board[3][c2]];
                                if (dr > 0) col.reverse();
                                var resCol = slide(col);
                                if (dr > 0) resCol.reverse();
                                for (var r2 = 0; r2 < 4; r2++) {
                                    if (board[r2][c2] !== resCol[r2]) moved = true;
                                    board[r2][c2] = resCol[r2];
                                }
                            }
                        }
                        if (moved) { addTile(); render2048(); }
                    };

                    var gEl = document.getElementById('grid2048');
                    var tx = 0, ty = 0;
                    if (gEl) {
                        gEl.addEventListener('touchstart', function(e) {
                            if (e.touches && e.touches[0]) { tx = e.touches[0].clientX; ty = e.touches[0].clientY; }
                        }, { passive: true });
                        gEl.addEventListener('touchend', function(e) {
                            if (!e.changedTouches || !e.changedTouches[0]) return;
                            var dx = e.changedTouches[0].clientX - tx;
                            var dy = e.changedTouches[0].clientY - ty;
                            if (Math.abs(dx) < 18 && Math.abs(dy) < 18) return;
                            if (Math.abs(dx) > Math.abs(dy)) window.move2048(0, dx > 0 ? 1 : -1);
                            else window.move2048(dy > 0 ? 1 : -1, 0);
                        }, { passive: true });
                    }
                    window.reset2048();

                    window.selectOfflineGame = function(name) {
                        activeGame = name;
                        var pr = document.getElementById('paneRunner');
                        var p2 = document.getElementById('pane2048');
                        var tr = document.getElementById('tabRunner');
                        var t2 = document.getElementById('tab2048');
                        if (pr) pr.style.display = name === 'runner' ? 'block' : 'none';
                        if (p2) p2.style.display = name === '2048' ? 'block' : 'none';
                        if (tr) tr.className = 'arcade-tab' + (name === 'runner' ? ' active' : '');
                        if (t2) t2.className = 'arcade-tab' + (name === '2048' ? ' active' : '');
                        if (name === 'runner') {
                            sizeRunnerCanvas();
                            updateHud(Math.floor(rScore), rBest);
                        } else {
                            render2048();
                        }
                    };

                    window.toggleOfflineArcade = function() {
                        var wrap = document.getElementById('arcadeWrap');
                        if (!wrap) return;
                        var opening = wrap.style.display === 'none';
                        wrap.style.display = opening ? 'block' : 'none';
                        if (opening) {
                            sizeRunnerCanvas();
                            if (!rRunning) resetRunner();
                        }
                    };

                    document.addEventListener('keydown', function(e) {
                        if (activeGame === 'runner' && (e.code === 'Space' || e.code === 'ArrowUp')) {
                            e.preventDefault();
                            runnerJump();
                        } else if (activeGame === '2048') {
                            if (e.code === 'ArrowLeft') window.move2048(0, -1);
                            else if (e.code === 'ArrowRight') window.move2048(0, 1);
                            else if (e.code === 'ArrowUp') window.move2048(-1, 0);
                            else if (e.code === 'ArrowDown') window.move2048(1, 0);
                        }
                    });
                })();
            </script>
        """.trimIndent()
    }

    private fun offlineArcadeCss(): String = """
        .arcade-wrap { margin-top: 16px; background: #0E131D; border: 1px solid #222B3C; border-radius: 14px; padding: 12px; width: 100%; }
        .arcade-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
        .arcade-tabs { display: flex; gap: 6px; }
        .arcade-tab { background: #1A2232; color: #94A3B8; border: 1px solid #2A3447; border-radius: 8px; padding: 6px 10px; font-size: 12px; font-weight: 600; cursor: pointer; }
        .arcade-tab.active { background: rgba(112,199,186,0.18); color: #70C7BA; border-color: #70C7BA; }
        .arcade-scores { display: flex; gap: 10px; font-size: 12px; color: #94A3B8; }
        .arcade-scores strong { color: #FFFFFF; }
        .game-pane { width: 100%; }
        #runnerCanvas { width: 100%; display: block; border-radius: 10px; border: 1px solid #1E293B; touch-action: none; }
        .game-hint { font-size: 11px; color: #64748B; margin-top: 8px; }
        .grid-2048 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; background: #0B0F17; padding: 8px; border-radius: 10px; border: 1px solid #1E293B; touch-action: none; }
        .cell-2048 { aspect-ratio: 1 / 1; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 18px; user-select: none; }
        .dpad-row { display: flex; gap: 6px; margin-top: 10px; justify-content: center; }
        .dpad-btn { flex: 1; min-height: 38px; background: #1A2232; color: #E2E8F0; border: 1px solid #2A3447; border-radius: 8px; font-weight: 700; font-size: 14px; cursor: pointer; }
        .dpad-btn.new-btn { background: rgba(112,199,186,0.16); color: #70C7BA; border-color: #70C7BA; }
    """.trimIndent()

    /**
     * Generates a standalone zero-network HTML5 arcade page (BlockDAG Runner & Kaspa 2048).
     */
    fun generateOfflineArcadeHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Kaspa Offline Arcade</title>
                <style>
                    * { box-sizing: border-box; }
                    html, body { background-color: #0B0F17; color: #E2E8F0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 0; overflow-x: hidden; }
                    body { min-height: 100dvh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 16px; }
                    .card { background: #151A26; border: 1px solid #1E293B; border-radius: 18px; padding: 20px 18px; max-width: 440px; width: 100%; box-shadow: 0 12px 30px rgba(0,0,0,0.55); text-align: center; }
                    .top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
                    h2 { color: #FFFFFF; font-size: 18px; margin: 0; font-weight: 700; }
                    .home-link { background: #1E293B; color: #E2E8F0; border: none; border-radius: 8px; padding: 7px 12px; font-size: 12px; font-weight: 600; text-decoration: none; }
                    .sub { color: #94A3B8; font-size: 12.5px; margin: 4px 0 0; text-align: left; }
                    ${offlineArcadeCss()}
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="top-bar">
                        <h2>Kaspa Offline Arcade</h2>
                        <a class="home-link" href="kaspa-action://home">Home</a>
                    </div>
                    <p class="sub">Zero-network HTML5 games — works completely offline.</p>
                    ${buildOfflineArcadeMarkup(autoOpenGame = true)}
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates a modern, clean HTML recovery / error card when web navigation encounters a network error,
     * preventing indefinite blank screens and providing a manual retry trigger plus playable offline games.
     */
    fun generateErrorHtml(failingUrl: String, errorDescription: String?): String {
        val errorMsg = when {
            errorDescription?.contains("ERR_INTERNET_DISCONNECTED", ignoreCase = true) == true ->
                "No internet connection. Please check your mobile data or Wi-Fi."
            errorDescription?.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) == true ->
                "DNS server resolution failed. The website host could not be reached."
            errorDescription?.contains("ERR_CONNECTION_TIMED_OUT", ignoreCase = true) == true ->
                "Connection timed out. The server took too long to respond."
            errorDescription?.contains("ERR_CONNECTION_REFUSED", ignoreCase = true) == true ->
                "The server refused the connection."
            !errorDescription.isNullOrBlank() -> errorDescription
            else -> "Network connection lost or web server unreachable."
        }
        val safeRetryUrl = failingUrl.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: "https://kaspa.org"
        val quotedJsUrl = escapeJsString(safeRetryUrl)
        val safeHtmlErrorMsg = escapeHtml(errorMsg)
        val safeHtmlUrl = escapeHtml(failingUrl)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; }
                    html, body { overflow-x: hidden; }
                    body { background-color: #0B0F17; color: #E2E8F0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 85vh; margin: 0; padding: 18px; text-align: center; }
                    .card { background: #151A26; border: 1px solid #1E293B; border-radius: 18px; padding: 26px 20px; max-width: 420px; width: 100%; box-shadow: 0 12px 30px rgba(0,0,0,0.55); }
                    .icon { font-size: 36px; margin-bottom: 10px; line-height: 1; }
                    h2 { color: #FFFFFF; font-size: 20px; margin: 0 0 8px; font-weight: 700; }
                    p { color: #94A3B8; font-size: 13.5px; line-height: 1.55; margin: 0 0 14px; }
                    .url { color: #00E5FF; font-family: monospace; font-size: 11.5px; word-break: break-all; background: #0B0F17; padding: 10px 12px; border-radius: 8px; margin-bottom: 18px; border: 1px solid #1E293B; }
                    .actions { display: flex; flex-direction: column; gap: 10px; }
                    .btn { background: #00E5FF; color: #0B0F17; border: none; padding: 13px 24px; border-radius: 10px; font-weight: 700; font-size: 14.5px; cursor: pointer; width: 100%; display: inline-block; box-sizing: border-box; transition: all 0.2s ease; text-decoration: none; }
                    .btn:active { transform: scale(0.97); opacity: 0.85; }
                    .btn:disabled { background: #1E293B; color: #64748B; cursor: not-allowed; }
                    .btn-secondary { background: #1E293B; color: #70C7BA; border: 1px solid rgba(112,199,186,0.4); padding: 11px 20px; border-radius: 10px; font-weight: 700; font-size: 13.5px; cursor: pointer; width: 100%; }
                    .spinner { display: inline-block; width: 14px; height: 14px; border: 2px solid #64748B; border-top-color: #00E5FF; border-radius: 50%; animation: spin 0.8s linear infinite; vertical-align: middle; margin-right: 8px; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    ${offlineArcadeCss()}
                </style>
                <script>
                    var isRetrying = false;
                    function openNativeOfflineArcade() {
                        window.location.href = 'kaspa-action://arcade';
                    }
                    function retryLoading() {
                        if (isRetrying) return;
                        isRetrying = true;
                        var btn = document.getElementById('retryBtn');
                        if (btn) {
                            btn.disabled = true;
                            btn.innerHTML = '<span class="spinner"></span>Reconnecting...';
                        }
                        var targetUrl = $quotedJsUrl;
                        try {
                            if (window.KaspaNative && typeof window.KaspaNative.retryConnection === 'function') {
                                window.KaspaNative.retryConnection(targetUrl);
                                return;
                            }
                        } catch(e) {}
                        window.location.href = 'kaspa-action://retry?url=' + encodeURIComponent(targetUrl);
                    }
                </script>
            </head>
            <body>
                <div class="card">
                    <div class="icon">📡</div>
                    <h2>Connection Problem</h2>
                    <p>$safeHtmlErrorMsg</p>
                    <div class="url">$safeHtmlUrl</div>
                    <div class="actions">
                        <button id="retryBtn" class="btn" onclick="retryLoading()">Retry Connection</button>
                        <button class="btn-secondary" onclick="openNativeOfflineArcade()">Play Offline Arcade</button>
                    </div>
                    ${buildOfflineArcadeMarkup(autoOpenGame = false)}
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates a security warning page when SSL verification fails, protecting users
     * from MITM attacks without silent crashes or blank screens.
     */
    fun generateSslErrorHtml(failingUrl: String, sslReason: String): String {
        val safeReason = escapeHtml(sslReason)
        val safeUrl = escapeHtml(failingUrl)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, sans-serif; background: #0A0E17; color: #F0F4F8; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                    .card { background: #131B2E; border: 1px solid #EF4444; border-radius: 14px; padding: 28px; max-width: 400px; text-align: center; }
                    h2 { color: #EF4444; margin-top: 0; font-size: 18px; font-weight: 700; }
                    p { color: #94A3B8; font-size: 13px; line-height: 1.5; }
                    .url { word-break: break-all; font-family: monospace; background: #0D121D; padding: 10px; border-radius: 6px; font-size: 11px; margin: 14px 0; color: #F59E0B; }
                    .btn { background: #EF4444; color: white; border: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; cursor: pointer; width: 100%; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Security Warning: Untrusted Certificate</h2>
                    <p>$safeReason</p>
                    <p>Kaspa Privacy Shield halted this connection to prevent data interception.</p>
                    <div class="url">$safeUrl</div>
                    <button class="btn" onclick="history.back()">Return to Safety</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
