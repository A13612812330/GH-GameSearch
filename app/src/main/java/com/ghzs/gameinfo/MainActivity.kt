package com.ghzs.gameinfo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : Activity() {
    private val loginUrl = "https://and-admin.ghzs.com/#/welcome/"
    private val gameInfoUrl = "https://and-admin.ghzs.com/#/games-manage/games-info?href=games_info_menu"
    private val apiBase = "https://admin-api.ghzs.com"
    private val pageSize = 15
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val typeOptions = listOf(
        TypeOption("单机游戏", "local"),
        TypeOption("网络游戏", "online"),
        TypeOption("福利游戏", "welfare"),
        TypeOption("gj单机", "gjlocal"),
        TypeOption("gj网游", "gjonline"),
        TypeOption("模拟器游戏", "simulator"),
        TypeOption("应用类", "application")
    )

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loginPanel: LinearLayout
    private lateinit var queryPanel: LinearLayout
    private lateinit var accountInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginStatusText: TextView
    private lateinit var keywordInput: EditText
    private lateinit var queryStatusText: TextView
    private lateinit var countText: TextView
    private lateinit var candidateList: LinearLayout
    private lateinit var detailPanel: LinearLayout
    private lateinit var loading: ProgressBar
    private lateinit var debugBackButton: Button
    private lateinit var allTypeBox: CheckBox
    private lateinit var typeDropdownButton: Button
    private lateinit var typeDropdownPanel: LinearLayout

    private val typeBoxes = mutableListOf<CheckBox>()
    private var account = ""
    private var password = ""
    private var loginStarted = false
    private var debugVisible = false
    private var token = ""
    private var cookie = ""
    private var currentPage = 1
    private var totalCount = 0
    private var lastKeyword = ""
    private var lastTypes = emptyList<String>()
    private var loginProbeText = ""
    private var backendRouteRequested = false
    private var suppressTypeFilterChange = false
    private var pendingTypeSearch: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        configureWebView()
        showLogin()
        webView.loadUrl(loginUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.userAgentString = desktopUserAgent
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(Bridge(), "GameInfoBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                refreshCookie()
                val cookieToken = authTokenFromCookie()
                if (cookieToken.isNotBlank()) {
                    onTokenReady(cookieToken)
                    ensureBackendAtGameInfo(url.orEmpty())
                    return
                }
                readTokenFromWeb()
                if (loginStarted && token.isBlank() && url.orEmpty().contains("/welcome")) {
                    runLoginScript()
                }
            }
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(246, 248, 252)) }
        webView = WebView(this).apply {
            visibility = View.VISIBLE
            alpha = 0f
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        loginPanel = buildLoginPanel()
        queryPanel = buildQueryPanel()
        root.addView(loginPanel)
        root.addView(queryPanel)

        debugBackButton = Button(this).apply {
            text = "返回工具页"
            visibility = View.GONE
            setOnClickListener {
                debugVisible = false
                hideWebView()
            }
        }
        root.addView(debugBackButton, FrameLayout.LayoutParams(dp(112), dp(44), Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = topSafePadding(2)
            rightMargin = dp(12)
        })
        setContentView(root)
    }

    private fun buildLoginPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), topSafePadding(24), dp(22), dp(22))

            addView(TextView(this@MainActivity).apply {
                text = "游戏信息查询"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(22, 32, 52))
            })
            addView(TextView(this@MainActivity).apply {
                text = "登录后台后，前台只展示查询工具"
                textSize = 13f
                setTextColor(Color.rgb(105, 116, 135))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(28))
            }, wideParams())

            accountInput = input("后台账号", false)
            passwordInput = input("后台密码", true)
            addView(accountInput, wideParams())
            addView(passwordInput, wideParams())
            addView(primaryButton("登录") { startLogin() }, wideParams())
            addView(ghostButton("显示后台页面") { toggleDebugWebView() }, wideParams())

            loginStatusText = TextView(this@MainActivity).apply {
                text = "如果登录卡住，可以点“显示后台页面”查看实际位置"
                textSize = 12f
                setTextColor(Color.rgb(105, 116, 135))
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, 0)
            }
            addView(loginStatusText, wideParams())
        }
    }

    private fun buildQueryPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), topSafePadding(10), dp(14), dp(12))
        }

        val queryHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        queryHeader.addView(sectionTitle("游戏信息查询"), LinearLayout.LayoutParams(0, -2, 1f))
        queryHeader.addView(debugIconButton(), LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            leftMargin = dp(8)
        })
        content.addView(queryHeader)
        loading = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        content.addView(loading, LinearLayout.LayoutParams(-1, dp(5)))

        queryStatusText = TextView(this).apply {
            text = "输入游戏名称或游戏ID后搜索"
            textSize = 12f
            setTextColor(Color.rgb(105, 116, 135))
            setPadding(0, dp(8), 0, dp(10))
        }
        content.addView(queryStatusText)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        keywordInput = input("游戏名称 / 游戏ID", false).apply {
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ ->
                searchFromStart()
                true
            }
        }
        searchRow.addView(keywordInput, LinearLayout.LayoutParams(0, dp(44), 1f))
        searchRow.addView(ghostButton("清空") { clearSearchInput() }, LinearLayout.LayoutParams(dp(64), dp(44)).apply {
            leftMargin = dp(8)
        })
        searchRow.addView(primaryButton("搜索") { searchFromStart() }, LinearLayout.LayoutParams(dp(72), dp(44)).apply {
            leftMargin = dp(8)
        })
        content.addView(searchRow)
        content.addView(typeFilterView())

        detailPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(Color.WHITE, Color.rgb(226, 232, 242), 8)
        }
        detailPanel.addView(sectionTitle("游戏详情"))
        detailPanel.addView(meta("选择候选游戏后显示详情"))
        content.addView(detailPanel)

        val resultHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(6))
        }
        resultHeader.addView(sectionTitle("候选结果"), LinearLayout.LayoutParams(0, -2, 1f))
        countText = TextView(this).apply {
            text = "0 条"
            textSize = 13f
            setTextColor(Color.rgb(105, 116, 135))
        }
        resultHeader.addView(countText)
        content.addView(resultHeader)

        candidateList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(candidateList)

        val pager = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        pager.addView(ghostButton("上一页") {
            if (currentPage > 1) {
                currentPage -= 1
                searchGames(currentPage)
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })
        pager.addView(ghostButton("下一页") {
            if (currentPage * pageSize < totalCount) {
                currentPage += 1
                searchGames(currentPage)
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        content.addView(pager)

        scrollView.addView(content)
        panel.addView(scrollView, LinearLayout.LayoutParams(-1, -1))
        return panel
    }

    private fun typeFilterView(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        typeDropdownButton = ghostButton("游戏类型：未筛选  ▾") {
            toggleTypeDropdown()
        }
        wrap.addView(typeDropdownButton, LinearLayout.LayoutParams(-1, dp(46)))

        typeDropdownPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = roundedBg(Color.WHITE, Color.rgb(226, 232, 242), 8)
        }
        allTypeBox = CheckBox(this).apply {
            text = "全部"
            textSize = 13f
            setOnClickListener { onAllTypesChanged(isChecked) }
        }
        typeDropdownPanel.addView(allTypeBox)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        typeOptions.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { option ->
                val box = CheckBox(this).apply {
                    text = option.label
                    tag = option.value
                    textSize = 13f
                    setOnCheckedChangeListener { _, _ -> onTypeFilterChanged() }
                }
                typeBoxes.add(box)
                row.addView(box, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            grid.addView(row)
        }
        typeDropdownPanel.addView(grid)
        wrap.addView(typeDropdownPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return wrap
    }

    private fun startLogin() {
        refreshCookie()
        val existingToken = authTokenFromCookie()
        if (existingToken.isNotBlank()) {
            loginStatus("已检测到登录状态，正在进入游戏信息页...")
            onTokenReady(existingToken)
            ensureBackendAtGameInfo(webView.url.orEmpty())
            return
        }
        readTokenFromWeb()
        account = accountInput.text.toString().trim()
        password = passwordInput.text.toString()
        if (account.isBlank() || password.isBlank()) {
            loginStatus("请输入账号和密码")
            return
        }
        loginStarted = true
        loginStatus("正在登录后台...")
        webView.loadUrl(loginUrl)
        runLoginScript()
    }

    private fun runLoginScript() {
        val script = """
            (function(){
              var report = function(data) {
                try { window.GameInfoBridge.loginProbe(JSON.stringify(data)); } catch(e) {}
              };
              function setVal(el, val) {
                if (!el) return false;
                el.focus();
                var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                setter.call(el, val);
                el.dispatchEvent(new Event('input', {bubbles:true}));
                el.dispatchEvent(new Event('change', {bubbles:true}));
                el.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true, key:'Enter'}));
                return true;
              }
              function visible(el) {
                if (!el) return false;
                var r = el.getBoundingClientRect();
                var style = getComputedStyle(el);
                return r.width > 0 && r.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              }
              function loginErrorText() {
                var nodes = Array.prototype.slice.call(document.querySelectorAll('.el-message, .el-notification, .el-form-item__error, .login-error, [class*=error], [class*=message]')).filter(visible);
                var texts = nodes.map(function(el){ return (el.innerText || el.textContent || '').trim(); }).filter(Boolean);
                var hit = texts.find(function(t){ return /错|失败|异常|无效|不存在|密码|账号|验证码|过期|禁止|error|fail/i.test(t); });
                return hit || '';
              }
              var submitted = false;
              function findAndSubmit(attempt) {
                var inputs = Array.prototype.slice.call(document.querySelectorAll('input')).filter(visible);
                var pwd = inputs.find(function(i){ return (i.type || '').toLowerCase() === 'password'; });
                var user = inputs.find(function(i){
                  var t = (i.type || 'text').toLowerCase();
                  return i !== pwd && (t === 'text' || t === 'email' || t === 'tel' || t === 'search' || !i.type);
                });
                var remember = inputs.find(function(i){ return (i.type || '').toLowerCase() === 'checkbox'; });
                var buttons = Array.prototype.slice.call(document.querySelectorAll('button,input[type=submit]')).filter(visible);
                var submit = buttons.find(function(b){
                  return !b.disabled && ((b.type || '').toLowerCase() === 'submit' || /login|sign|登录|登 录/i.test(b.innerText || b.value || b.textContent || ''));
                }) || buttons.find(function(b){ return !b.disabled; });
                var errorText = loginErrorText();
                report({
                  phase: submitted ? (errorText ? 'login-failed' : 'login-wait') : 'login-attempt',
                  attempt: attempt,
                  href: location.href,
                  inputs: inputs.length,
                  hasUser: !!user,
                  hasPassword: !!pwd,
                  hasSubmit: !!submit,
                  error: errorText,
                  tokenLength: ((localStorage.getItem('ldap-token') || sessionStorage.getItem('ldap-token') || '').length)
                });
                if (submitted) return !!errorText;
                if (!user || !pwd) return false;
                setVal(user, ${JSONObject.quote(account)});
                setVal(pwd, ${JSONObject.quote(password)});
                if (remember && !remember.checked) remember.click();
                submitted = true;
                if (submit) {
                  submit.click();
                } else {
                  pwd.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true, key:'Enter', code:'Enter'}));
                  pwd.dispatchEvent(new KeyboardEvent('keypress', {bubbles:true, key:'Enter', code:'Enter'}));
                  pwd.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true, key:'Enter', code:'Enter'}));
                  var form = pwd.closest('form');
                  if (form) form.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true}));
                }
                return false;
              }
              var tries = 0;
              var timer = setInterval(function(){
                tries += 1;
                var done = findAndSubmit(tries);
                if (done || tries >= 24) {
                  if (!done && submitted) report({phase:'login-timeout', attempt:tries, href:location.href, error:loginErrorText(), tokenLength: ((localStorage.getItem('ldap-token') || sessionStorage.getItem('ldap-token') || '').length)});
                  clearInterval(timer);
                }
              }, 500);
              findAndSubmit(0);
              return true;
            })();
        """.trimIndent()
        webView.postDelayed({ webView.evaluateJavascript(script, null) }, 500)
        webView.postDelayed({ readTokenFromWeb() }, 1800)
        webView.postDelayed({ readTokenFromWeb() }, 3500)
        webView.postDelayed({ readTokenFromWeb() }, 6500)
        webView.postDelayed({ readTokenFromWeb() }, 10000)
    }

    private fun readTokenFromWeb() {
        val script = """
            (function(){
              function pickToken(store) {
                var preferred = ['ldap-token', 'token', 'access_token', 'accessToken', 'authorization', 'Authorization'];
                for (var p = 0; p < preferred.length; p++) {
                  var direct = store.getItem(preferred[p]);
                  if (direct) return direct;
                }
                for (var i = 0; i < store.length; i++) {
                  var key = store.key(i) || '';
                  var lower = key.toLowerCase();
                  if (lower.indexOf('token') !== -1 || lower.indexOf('access') !== -1 || lower.indexOf('authorization') !== -1 || lower.indexOf('auth') !== -1) {
                    var value = store.getItem(key);
                    if (value) return value;
                  }
                }
                return '';
              }
              var token = pickToken(localStorage) || pickToken(sessionStorage);
              var keys = [];
              try {
                for (var i = 0; i < localStorage.length; i++) keys.push('L:' + localStorage.key(i));
                for (var j = 0; j < sessionStorage.length; j++) keys.push('S:' + sessionStorage.key(j));
              } catch(e) {}
              window.GameInfoBridge.authReady(token, location.href, keys.join(','));
              return token;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, ValueCallback { raw ->
            val parsed = cleanJsString(raw)
            if (parsed.isNotBlank()) {
                onTokenReady(parsed)
                ensureBackendAtGameInfo(webView.url.orEmpty())
            } else {
                refreshCookie()
                val cookieToken = authTokenFromCookie()
                if (cookieToken.isNotBlank()) {
                    onTokenReady(cookieToken)
                    ensureBackendAtGameInfo(webView.url.orEmpty())
                }
            }
        })
    }

    inner class Bridge {
        @JavascriptInterface
        fun authReady(rawToken: String?, href: String?, keys: String?) {
            runOnUiThread {
                val tokenLength = rawToken?.length ?: 0
                loginProbeText = "后台地址：${href.orEmpty().take(90)}\nToken长度：$tokenLength\n存储键：${keys.orEmpty().take(120)}"
                if (!rawToken.isNullOrBlank()) {
                    onTokenReady(rawToken)
                    ensureBackendAtGameInfo(href.orEmpty())
                } else if (loginStarted) {
                    refreshCookie()
                    val cookieToken = authTokenFromCookie()
                    if (cookieToken.isNotBlank()) {
                        onTokenReady(cookieToken)
                        ensureBackendAtGameInfo(href.orEmpty())
                    } else {
                        loginStatus("还没读到登录凭证\n$loginProbeText")
                    }
                }
            }
        }

        @JavascriptInterface
        fun loginProbe(json: String?) {
            runOnUiThread {
                val text = try {
                    val obj = JSONObject(json.orEmpty())
                    val error = obj.optString("error")
                    val phase = obj.optString("phase")
                    val base = "后台地址：${obj.optString("href").take(80)}\n账号框：${yesNo(obj.optBoolean("hasUser"))}  密码框：${yesNo(obj.optBoolean("hasPassword"))}  登录按钮：${yesNo(obj.optBoolean("hasSubmit"))}\n输入框数量：${obj.optInt("inputs")}  尝试次数：${obj.optInt("attempt")}  Token长度：${obj.optInt("tokenLength")}"
                    when {
                        error.isNotBlank() -> "登录失败：${error.take(80)}\n$base"
                        phase == "login-timeout" -> "登录失败：未检测到登录成功，请点“显示后台页面”查看后台提示\n$base"
                        phase == "login-wait" -> "正在等待后台登录结果\n$base"
                        else -> base
                    }
                } catch (_: Exception) {
                    json.orEmpty()
                }
                loginProbeText = text
                if (loginStarted) {
                    if (text.startsWith("登录失败")) loginStatus(text) else loginStatus("正在尝试自动登录\n$text")
                }
            }
        }
    }

    private fun onTokenReady(rawToken: String) {
        val parsed = parseToken(rawToken)
        if (parsed.isBlank()) return
        token = parsed
        loginStarted = false
        showQuery()
        loginStatus("登录成功")
        queryStatus("已登录，后台正在进入游戏信息页，可以搜索")
    }

    private fun refreshCookie() {
        cookie = listOf(
            CookieManager.getInstance().getCookie("https://and-admin.ghzs.com").orEmpty(),
            CookieManager.getInstance().getCookie("https://sso.ghzs.com").orEmpty(),
            CookieManager.getInstance().getCookie("https://admin-api.ghzs.com").orEmpty()
        ).filter { it.isNotBlank() }.distinct().joinToString("; ")
    }

    private fun extractCookieValue(cookieText: String, name: String): String {
        return cookieText.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            .orEmpty()
    }

    private fun authTokenFromCookie(): String {
        val exactNames = listOf("ldap-token", "token", "access_token", "accessToken", "authorization", "Authorization")
        exactNames.forEach { name ->
            val value = extractCookieValue(cookie, name)
            if (value.isNotBlank()) return value
        }
        return cookie.split(";")
            .map { it.trim() }
            .firstNotNullOfOrNull { part ->
                val key = part.substringBefore("=").trim().lowercase()
                val value = part.substringAfter("=", "").trim()
                if (
                    value.isNotBlank() &&
                    (key.contains("token") || key.contains("access") || key.contains("authorization") || key.contains("auth"))
                ) value else null
            }
            .orEmpty()
    }

    private fun ensureBackendAtGameInfo(currentUrl: String) {
        if (token.isBlank()) return
        if (currentUrl.contains("/games-manage/games-info")) return
        if (backendRouteRequested) return
        backendRouteRequested = true
        runBackendMenuNavigation()
        webView.postDelayed({ runBackendMenuNavigation() }, 2500)
        webView.postDelayed({ runBackendMenuNavigation() }, 6000)
    }

    private fun runBackendMenuNavigation() {
        val script = """
            (function(){
              if (location.href.indexOf('/games-manage/games-info') !== -1) return 'already';
              var targetHref = '#/games-manage/games-info?href=games_info_menu';
              var target = document.querySelector('a[href="' + targetHref + '"]');
              var menus = Array.prototype.slice.call(document.querySelectorAll('.el-submenu'));
              var parent = menus.find(function(el) {
                var text = (el.innerText || el.textContent || '').trim();
                return text.indexOf('游戏管理') === 0;
              });
              if (parent) {
                var title = parent.querySelector('.el-submenu__title') || parent;
                title.click();
              }
              setTimeout(function(){
                var link = document.querySelector('a[href="' + targetHref + '"]');
                if (link) {
                  try { sessionStorage.setItem('android-selected-menu', 'games_info_menu'); } catch(e) {}
                  link.click();
                } else {
                  location.href = 'https://and-admin.ghzs.com/#/games-manage/games-info?href=games_info_menu';
                }
              }, 800);
              return target ? 'click-link' : 'open-parent';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun parseToken(raw: String): String {
        val cleaned = cleanJsString(raw).trim()
        if (cleaned.isBlank() || cleaned == "null" || cleaned == "undefined") return ""
        return try {
            val obj = JSONObject(cleaned)
            obj.optString("token")
                .ifBlank { obj.optString("access_token") }
                .ifBlank { obj.optString("value") }
                .ifBlank { cleaned }
        } catch (_: Exception) {
            cleaned.trim('"')
        }
    }

    private fun cleanJsString(raw: String?): String {
        if (raw == null) return ""
        var text = raw
        if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length - 1)
        }
        return text.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }

    private fun searchFromStart() {
        currentPage = 1
        lastKeyword = keywordInput.text.toString().trim()
        lastTypes = selectedTypes()
        searchGames(currentPage)
    }

    private fun searchGames(page: Int) {
        if (token.isBlank()) {
            queryStatus("未读取到登录凭证，请先登录；如果已经登录，点“显示后台页面”确认")
            showLogin()
            return
        }
        val keyword = lastKeyword.ifBlank { keywordInput.text.toString().trim() }
        if (keyword.isBlank()) {
            queryStatus("请输入游戏名称或游戏ID")
            return
        }
        setLoading(true)
        queryStatus("正在搜索...")
        candidateList.removeAllViews()
        detailPanel.removeViews(1, max(0, detailPanel.childCount - 1))

        thread {
            try {
                val fields = if (keyword.matches(Regex("^[0-9a-fA-F]{12,24}$"))) "_id" else "name"
                val params = linkedMapOf(
                    "page" to page.toString(),
                    "page_size" to pageSize.toString(),
                    "view" to "datatable",
                    "keyword" to keyword,
                    "keyword_fields" to fields,
                    "filter" to gameFilter(lastTypes),
                    "sort" to "download:-1"
                )
                val response = apiGet("/games", params)
                val array = JSONArray(response.body)
                val items = sortByDownloadDesc(filterByTypes(array, lastTypes))
                runOnUiThread {
                    totalCount = response.total ?: array.length()
                    currentPage = page
                    renderCandidates(items)
                    setLoading(false)
                    queryStatus("搜索完成，第 $currentPage 页")
                    countText.text = resultCountText(items.length(), totalCount)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    queryStatus("搜索失败：${friendlyError(e)}")
                }
            }
        }
    }

    private fun loadDetail(item: JSONObject) {
        val id = item.optString("_id").ifBlank { item.optString("id") }
        if (id.isBlank()) {
            queryStatus("这个候选结果没有游戏ID")
            return
        }
        setLoading(true)
        queryStatus("正在读取详情...")
        thread {
            try {
                val response = apiGet("/games/$id", linkedMapOf("view" to "detail"))
                val detail = JSONObject(response.body)
                val merged = mergeGame(item, detail)
                merged.put("mirror_details", loadMirrorDetails(merged))
                runOnUiThread {
                    setLoading(false)
                    renderDetail(merged)
                    queryStatus("详情已读取")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    renderDetail(item)
                    queryStatus("详情读取失败，已显示列表字段：${friendlyError(e)}")
                }
            }
        }
    }

    private fun apiGet(path: String, params: Map<String, String>): ApiResponse {
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val connection = URL("$apiBase$path?$query").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Origin", "https://and-admin.ghzs.com")
        connection.setRequestProperty("Referer", gameInfoUrl)
        connection.setRequestProperty("User-Agent", desktopUserAgent)
        connection.setRequestProperty("Token", token)
        if (cookie.isNotBlank()) connection.setRequestProperty("Cookie", cookie)

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        if (code == 401 || code == 403) throw IllegalStateException("登录已过期或无权限（$code）")
        if (code !in 200..299) throw IllegalStateException("后台返回 $code：${body.take(80)}")
        val total = connection.getHeaderField("total")?.toIntOrNull()
            ?: connection.getHeaderField("Total")?.toIntOrNull()
        connection.disconnect()
        return ApiResponse(body, total)
    }

    private fun loadMirrorDetails(game: JSONObject): JSONArray {
        val details = JSONArray()
        val mirrorId = game.optString("mirror_id")
        if (mirrorId.isNotBlank()) {
            details.put(loadMirrorDetail(mirrorId, "1"))
        }
        val mirrorId2 = game.optString("mirror_id2").ifBlank { mirrorId }
        if (mirrorId2.isNotBlank()) {
            details.put(loadMirrorDetail(mirrorId2, "2"))
        }
        return details
    }

    private fun loadMirrorDetail(mirrorId: String, identify: String): JSONObject {
        return try {
            val response = apiGet("/games/mirrors/$mirrorId", linkedMapOf("type" to identify))
            JSONObject(response.body).apply { put("identify", identify) }
        } catch (e: Exception) {
            JSONObject().apply {
                put("identify", identify)
                put("error", friendlyError(e))
            }
        }
    }

    private fun filterByTypes(array: JSONArray, types: List<String>): JSONArray {
        if (types.isEmpty() || types.size == typeOptions.size) return array
        val result = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val category = item.optString("category")
            val typeText = displayType(item)
            if (types.any { type -> category == type || typeText.contains(typeLabel(type)) }) result.put(item)
        }
        return result
    }

    private fun sortByDownloadDesc(array: JSONArray): JSONArray {
        val sorted = (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .sortedByDescending { downloadNumber(it) }
        return JSONArray().apply { sorted.forEach { put(it) } }
    }

    private fun downloadNumber(game: JSONObject): Double {
        return game.optString("download")
            .replace(",", "")
            .replace("次", "")
            .trim()
            .toDoubleOrNull() ?: game.optDouble("download", 0.0)
    }

    private fun renderCandidates(items: JSONArray) {
        candidateList.removeAllViews()
        if (items.length() == 0) {
            candidateList.addView(emptyText("没有找到候选结果"))
            return
        }
        for (i in 0 until items.length()) {
            candidateList.addView(candidateCard(items.optJSONObject(i) ?: continue))
        }
    }

    private fun candidateCard(item: JSONObject): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBg(Color.WHITE, Color.rgb(218, 226, 238), 8)
            setOnClickListener { loadDetail(item) }
        }
        val icon = ImageView(this).apply {
            setBackgroundColor(Color.rgb(230, 235, 244))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)).apply { rightMargin = dp(12) })
        loadImage(item.optString("ori_icon").ifBlank { item.optString("icon") }, icon)

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = formatName(item)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 32, 52))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            text = "7天下载量：${item.optString("download", "-")}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(38, 82, 160))
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBg(Color.rgb(238, 243, 252), Color.rgb(216, 225, 241), 8)
            setSingleLine(true)
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        info.addView(titleRow)
        info.addView(meta("ID：${item.optString("_id", "-")}"))
        info.addView(meta("游戏类型：${displayType(item)}"))
        info.addView(singleLineMeta("游戏标签：${displayTags(item)}"))
        mirrorBlockNotice(item)?.let { notice ->
            info.addView(warningMeta(notice))
        }
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        return withBottomMargin(card)
    }

    private fun renderDetail(game: JSONObject) {
        detailPanel.removeViews(1, max(0, detailPanel.childCount - 1))
        detailPanel.addView(meta("已打开游戏详情弹窗"))

        val dialog = Dialog(this)
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(16))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(sectionTitle("游戏详情"), LinearLayout.LayoutParams(0, -2, 1f))
        topRow.addView(ghostButton("关闭") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(72), dp(38)))
        content.addView(topRow)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBg(Color.WHITE, Color.rgb(226, 232, 242), 8)
        }
        val icon = ImageView(this).apply {
            setBackgroundColor(Color.rgb(230, 235, 244))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)).apply { rightMargin = dp(10) })
        loadImage(game.optString("ori_icon").ifBlank { game.optString("icon") }, icon)

        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(this).apply {
            text = formatName(game)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 32, 52))
            setTextIsSelectable(true)
        })
        titleBox.addView(meta("ID：${game.optString("_id", "-")}"))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        content.addView(detailSection("基础信息").apply {
            addView(detailGridRow(
                "游戏类型" to displayType(game),
                "7天下载量" to game.optString("download", "-")
            ))
            addView(detailTile("游戏标签", displayTags(game)))
            addView(detailTile("备注", game.optString("remark", "-").ifBlank { "-" }))
        })
        content.addView(detailSection("合作信息").apply {
            addView(detailGridRow(
                "合作平台" to cooperationPlatform(game),
                "合作方式" to cooperationMethod(game)
            ))
        })
        content.addView(detailSection("APK包信息").apply {
            val apk = game.optJSONObject("apk")
            addView(detailTile("包名", apkValue(apk, "package", "pkg", "package_name")))
            addView(detailGridRow(
                "版本号" to apkValue(apk, "version", "version_name", "versionName"),
                "包大小" to apkSize(apk),
            ))
            addView(detailGridRow(
                "APK合作平台" to apkPlatform(apk),
                "APK合作方式" to apkCooperationMethod(apk)
            ))
            addView(detailTile("更新时间", apkValue(apk, "update_time", "updated_at", "time")))
            addView(detailTile("APK链接", apkLink(apk)))
        })
        content.addView(detailSection("游戏重要信息").apply {
            addView(detailGridRow(
                "厂商名称" to manufacturerName(game),
                "供应商" to supplierName(game)
            ))
            addView(detailGridRow(
                "统一社会信用代码" to importantText(game, "credit_code", "social_credit_code", "creditCode"),
                "ICP备案号" to importantText(game, "icp", "icp_no", "icp_number", "icp_record")
            ))
        })
        renderMirrorDetails(content, game)

        scrollView.addView(content)
        dialog.setContentView(scrollView)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94f).toInt(), (resources.displayMetrics.heightPixels * 0.88f).toInt())
    }

    private fun renderMirrorDetails(parent: LinearLayout, game: JSONObject) {
        val mirrors = game.optJSONArray("mirror_details") ?: return
        val activeMirrors = (0 until mirrors.length())
            .mapNotNull { mirrors.optJSONObject(it) }
            .filter { it.optString("error").isBlank() && it.optString("status") == "on" }
        if (activeMirrors.isEmpty()) return
        parent.addView(detailSection("镜像信息").apply {
            activeMirrors.forEach { mirror ->
                addView(mirrorCard(mirror), LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                })
            }
        }
        )
    }

    private fun mirrorCard(mirror: JSONObject): LinearLayout {
        val apk = mirror.optJSONObject("data")?.optJSONObject("apk") ?: mirror.optJSONObject("apk")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBg(Color.rgb(252, 253, 255), Color.rgb(226, 232, 242), 8)
            addView(TextView(this@MainActivity).apply {
                text = "镜像${mirror.optString("identify", "-")}"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(220, 38, 38))
                setTextIsSelectable(true)
            })
            addView(detailGridRow(
                "镜像开关" to statusLabel(mirror.optString("status")),
                "生效规则" to ruleLabel(mirror.optString("rule"))
            ))
            addView(detailGridRow(
                "省份" to jsonArrayText(mirror.optJSONArray("province")),
                "选择城市" to jsonArrayText(mirror.optJSONArray("city"))
            ))
            addView(detailTile("备注", mirror.optString("remark", "-").ifBlank { "-" }))
            addView(TextView(this@MainActivity).apply {
                text = "镜像APK"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(38, 82, 160))
                setPadding(0, dp(8), 0, dp(6))
                setTextIsSelectable(true)
            })
            addView(detailTile("APK包", apkSummary(apk)))
            addView(detailGridRow(
                "APK合作平台" to apkPlatform(apk),
                "APK合作方式" to apkCooperationMethod(apk)
            ))
            addView(detailTile("包大小", apkSize(apk)))
            addView(detailTile("APK链接", apkLink(apk)))
        }
    }

    private fun mirrorInfoText(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label：$value"
            textSize = 12f
            setTextColor(Color.rgb(42, 52, 70))
            setPadding(0, dp(3), 0, dp(3))
            setTextIsSelectable(true)
        }
    }

    private fun mergeGame(base: JSONObject, detail: JSONObject): JSONObject {
        val merged = JSONObject(base.toString())
        detail.keys().forEach { key -> merged.put(key, detail.opt(key)) }
        return merged
    }

    private fun formatName(game: JSONObject): String {
        val name = game.optString("name", "-")
        val subtitle = game.optString("subtitle").ifBlank { game.optString("game_subtitle") }
        return if (subtitle.isBlank()) name else "$name（$subtitle）"
    }

    private fun displayType(game: JSONObject): String {
        return game.optString("category_name")
            .ifBlank { typeLabel(game.optString("category")) }
            .ifBlank { "-" }
    }

    private fun displayTags(game: JSONObject): String {
        val raw = game.opt("type_tag") ?: game.opt("tags") ?: return "-"
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { index ->
                val value = raw.opt(index)
                when (value) {
                    is JSONObject -> value.optString("name").ifBlank { value.optString("label") }
                    else -> value?.toString()
                }
            }.filter { it.isNotBlank() }.joinToString("、").ifBlank { "-" }
            is JSONObject -> raw.optString("name").ifBlank { raw.toString() }
            else -> raw.toString().ifBlank { "-" }
        }
    }

    private fun statusLabel(value: String): String {
        return when (value) {
            "on" -> "打开"
            "off" -> "关闭"
            else -> value.ifBlank { "-" }
        }
    }

    private fun ruleLabel(value: String): String {
        return when (value) {
            "in" -> "指定地区"
            "out", "exclude" -> "排除地区"
            else -> value.ifBlank { "-" }
        }
    }

    private fun jsonArrayText(array: JSONArray?): String {
        if (array == null || array.length() == 0) return "-"
        return (0 until array.length())
            .mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
            .joinToString("、")
            .ifBlank { "-" }
    }

    private fun apkSummary(apk: JSONObject?): String {
        if (apk == null) return "-"
        val parts = listOf(
            apkValue(apk, "package", "pkg", "package_name").takeIf { it != "-" }.orEmpty(),
            apkValue(apk, "version", "version_name", "versionName").takeIf { it != "-" }.orEmpty(),
            apkSize(apk).takeIf { it != "-" }.orEmpty()
        ).filter { it.isNotBlank() }
        return parts.joinToString(" / ").ifBlank { "-" }
    }

    private fun apkValue(apk: JSONObject?, vararg keys: String): String {
        if (apk == null) return "-"
        keys.forEach { key ->
            val value = apk.optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return "-"
    }

    private fun apkSize(apk: JSONObject?): String {
        if (apk == null) return "-"
        val sizeText = apkValue(apk, "size", "file_size", "fileSize")
        if (sizeText != "-") return sizeText
        val sizeNumber = apkValue(apk, "size_number", "sizeNumber")
        return if (sizeNumber != "-") "${sizeNumber}MB" else "-"
    }

    private fun apkLink(apk: JSONObject?): String {
        if (apk == null) return "-"
        return apk.optString("url")
            .ifBlank { apk.optString("real_url") }
            .ifBlank { "-" }
    }

    private fun apkPlatform(apk: JSONObject?): String {
        if (apk == null) return "-"
        return platformLabel(apk.optString("platform").ifBlank { apk.optString("channel") })
    }

    private fun apkCooperationMethod(apk: JSONObject?): String {
        if (apk == null) return "-"
        return listOf(
            apk.optString("cooperation"),
            apk.optString("cooperate"),
            apk.optString("cooperation_method"),
            apk.optString("cooperation_type"),
            apk.optString("cooperate_model"),
            apk.optString("profit")
        ).firstNotNullOfOrNull { normalizeCooperationMethod(it) } ?: "-"
    }

    private fun manufacturerName(game: JSONObject): String {
        val manufacturer = game.optJSONObject("manufacturer")
        return importantText(
            game,
            "manufacturer_name",
            "manufacturerName",
            "manufacturer",
            "company_name",
            "companyName",
            "vendor_name",
            "vendorName",
            "developer_name",
            "developerName"
        ).takeIf { it != "-" }
            ?: manufacturer?.optString("name").orEmpty().ifBlank { manufacturer?.optString("value").orEmpty() }.ifBlank { "-" }
    }

    private fun supplierName(game: JSONObject): String {
        return importantText(game, "supplier", "supplier_name", "supplierName", "provider", "provider_name")
    }

    private fun importantText(game: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val raw = game.opt(key) ?: return@forEach
            val value = when (raw) {
                is JSONObject -> raw.optString("name")
                    .ifBlank { raw.optString("value") }
                    .ifBlank { raw.optString("label") }
                else -> raw.toString()
            }
            if (value.isNotBlank() && value != "null") return value
        }
        return "-"
    }

    private fun mirrorBlockNotice(game: JSONObject): String? {
        val messages = mutableListOf<String>()
        if (game.optString("block_status") == "on") messages.add("仅开启游戏屏蔽")
        if (game.optString("mirror_status") == "on" || game.optString("mirror_status2") == "on") {
            messages.add("仅开启游戏镜像")
        }
        return messages.joinToString(" / ").ifBlank { null }
    }

    private fun cooperationPlatform(game: JSONObject): String {
        val apk = game.optJSONObject("apk")
        val mirrorApk = game.optJSONObject("mirror_data")?.optJSONObject("apk")
        val raw = apk?.optString("platform").orEmpty()
            .ifBlank { mirrorApk?.optString("platform").orEmpty() }
            .ifBlank { game.optString("platform") }
            .ifBlank { "-" }
        return platformLabel(raw)
    }

    private fun cooperationMethod(game: JSONObject): String {
        val apk = game.optJSONObject("apk")
        val mirrorApk = game.optJSONObject("mirror_data")?.optJSONObject("apk")
        return listOf(
            apk?.optString("cooperation"),
            apk?.optString("cooperate"),
            apk?.optString("cooperation_method"),
            apk?.optString("profit"),
            mirrorApk?.optString("cooperation"),
            mirrorApk?.optString("cooperate"),
            mirrorApk?.optString("cooperation_method"),
            mirrorApk?.optString("profit"),
            game.optString("cooperation"),
            game.optString("cooperate"),
            game.optString("cooperation_method"),
            game.optString("profit")
        ).firstNotNullOfOrNull { normalizeCooperationMethod(it.orEmpty()) } ?: "无合作"
    }

    private fun normalizeCooperationMethod(value: String): String? {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return null
        return when (cleaned.uppercase()) {
            "S", "A", "D" -> cleaned.uppercase()
            "CPS" -> "S"
            "CPA" -> "A"
            "CPD" -> "D"
            "NONE" -> "无合作"
            "无合作" -> "无合作"
            else -> null
        }
    }

    private fun platformLabel(value: String): String {
        return when (value.trim()) {
            "9u" -> "九游"
            "gf" -> "官方"
            "xm" -> "小米"
            "baidu" -> "百度"
            "gp" -> "果盘"
            "dl" -> "当乐"
            "zq" -> "指趣"
            "tt" -> "TT语音"
            "360" -> "360"
            "af" -> "安锋"
            "ow" -> "偶玩"
            "lenovo" -> "联想"
            "hanfeng" -> "汉风"
            "sw" -> "说玩"
            "yxf" -> "游戏fan"
            "ly" -> "乐游"
            "lb" -> "猎宝"
            "yyb" -> "应用宝"
            "xiongmao" -> "熊猫玩"
            "lehh" -> "乐嗨嗨"
            "igs" -> "igs"
            "yq" -> "优趣"
            "" -> "-"
            else -> value
        }
    }

    private fun loadImage(src: String, target: ImageView) {
        if (src.isBlank() || !src.startsWith("http")) return
        thread {
            try {
                val bitmap: Bitmap = BitmapFactory.decodeStream(URL(src).openStream())
                runOnUiThread { target.setImageBitmap(bitmap) }
            } catch (_: Exception) {
            }
        }
    }

    private fun selectedTypes(): List<String> = typeBoxes.filter { it.isChecked }.mapNotNull { it.tag as? String }

    private fun gameFilter(types: List<String>): String {
        val active = "active:显示"
        if (types.isEmpty() || types.size == typeOptions.size) return active
        return "$active,game_category:${types.joinToString("-")}"
    }

    private fun typeLabel(value: String): String {
        return typeOptions.firstOrNull { it.value == value }?.label ?: value
    }

    private fun selectedTypeLabels(): List<String> = selectedTypes().map { typeLabel(it) }

    private fun toggleTypeDropdown() {
        typeDropdownPanel.visibility = if (typeDropdownPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        updateTypeDropdownText()
    }

    private fun updateTypeDropdownText() {
        if (!::typeDropdownButton.isInitialized) return
        val labels = selectedTypeLabels()
        val summary = when {
            labels.isEmpty() -> "未筛选"
            labels.size == typeOptions.size -> "全部"
            labels.size <= 2 -> labels.joinToString("、")
            else -> "已选 ${labels.size} 项"
        }
        val arrow = if (::typeDropdownPanel.isInitialized && typeDropdownPanel.visibility == View.VISIBLE) "▴" else "▾"
        typeDropdownButton.text = "游戏类型：$summary  $arrow"
    }

    private fun clearSearchInput() {
        keywordInput.setText("")
        lastKeyword = ""
        candidateList.removeAllViews()
        detailPanel.removeViews(1, max(0, detailPanel.childCount - 1))
        detailPanel.addView(meta("选择候选游戏后显示详情"))
        countText.text = "0 条"
        queryStatus("已清空关键词")
    }

    private fun onAllTypesChanged(checked: Boolean) {
        if (suppressTypeFilterChange) return
        suppressTypeFilterChange = true
        typeBoxes.forEach {
            if (it.isChecked != checked) it.isChecked = checked
        }
        suppressTypeFilterChange = false
        updateTypeDropdownText()
        scheduleTypeFilterSearch()
    }

    private fun onTypeFilterChanged() {
        if (suppressTypeFilterChange) return
        syncAllTypeBox()
        updateTypeDropdownText()
        scheduleTypeFilterSearch()
    }

    private fun scheduleTypeFilterSearch() {
        lastTypes = selectedTypes()
        pendingTypeSearch?.let { root.removeCallbacks(it) }
        val task = Runnable {
            if (token.isBlank()) return@Runnable
            val keyword = keywordInput.text.toString().trim()
            if (keyword.isBlank()) return@Runnable
            currentPage = 1
            lastKeyword = keyword
            lastTypes = selectedTypes()
            searchGames(currentPage)
        }
        pendingTypeSearch = task
        root.postDelayed(task, 250)
    }

    private fun resultCountText(pageFilteredCount: Int, backendTotal: Int): String {
        val types = lastTypes
        return if (types.isEmpty() || types.size == typeOptions.size) {
            "共 $backendTotal 条"
        } else {
            "共 $backendTotal 条"
        }
    }

    private fun syncAllTypeBox() {
        val allChecked = typeBoxes.isNotEmpty() && typeBoxes.all { it.isChecked }
        if (allTypeBox.isChecked != allChecked) {
            suppressTypeFilterChange = true
            allTypeBox.isChecked = allChecked
            suppressTypeFilterChange = false
        }
    }

    private fun showLogin() {
        loginPanel.visibility = View.VISIBLE
        queryPanel.visibility = View.GONE
        hideWebView()
    }

    private fun showQuery() {
        loginPanel.visibility = View.GONE
        queryPanel.visibility = View.VISIBLE
        hideWebView()
    }

    private fun toggleDebugWebView() {
        debugVisible = !debugVisible
        if (debugVisible) {
            webView.alpha = 1f
            webView.bringToFront()
            debugBackButton.visibility = View.VISIBLE
            debugBackButton.bringToFront()
        } else {
            hideWebView()
        }
    }

    private fun hideWebView() {
        webView.alpha = 0f
        debugBackButton.visibility = View.GONE
        loginPanel.bringToFront()
        queryPanel.bringToFront()
    }

    private fun loginStatus(text: String) {
        if (::loginStatusText.isInitialized) loginStatusText.text = text
    }

    private fun queryStatus(text: String) {
        if (::queryStatusText.isInitialized) queryStatusText.text = text
    }

    private fun setLoading(show: Boolean) {
        loading.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun friendlyError(e: Exception): String = e.message?.take(160) ?: "未知错误"

    private fun yesNo(value: Boolean): String = if (value) "有" else "无"

    private fun input(hintText: String, passwordMode: Boolean): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(44)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBg(Color.WHITE, Color.rgb(214, 222, 235), 8)
            inputType = if (passwordMode) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private fun primaryButton(textValue: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(Color.rgb(38, 105, 255), Color.rgb(38, 105, 255), 8)
            setOnClickListener { action() }
        }
    }

    private fun ghostButton(textValue: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            setTextColor(Color.rgb(38, 82, 160))
            background = roundedBg(Color.rgb(238, 243, 252), Color.rgb(216, 225, 241), 8)
            setOnClickListener { action() }
        }
    }

    private fun debugIconButton(): Button {
        return Button(this).apply {
            text = "!"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(38, 82, 160))
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            background = roundedBg(Color.rgb(238, 243, 252), Color.rgb(216, 225, 241), 16)
            setOnClickListener { toggleDebugWebView() }
        }
    }

    private fun roundedBg(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun sectionTitle(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 32, 52))
        }
    }

    private fun meta(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 12f
            setTextColor(Color.rgb(105, 116, 135))
            setPadding(0, dp(2), 0, 0)
        }
    }

    private fun singleLineMeta(textValue: String): TextView {
        return meta(textValue).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun warningMeta(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(220, 38, 38))
            setPadding(0, dp(3), 0, 0)
        }
    }

    private fun emptyText(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 116, 135))
            setPadding(0, dp(22), 0, dp(22))
        }
    }

    private fun infoRow(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label：$value"
            textSize = 14f
            setTextColor(Color.rgb(42, 52, 70))
            setPadding(0, dp(5), 0, dp(5))
        }
    }

    private fun detailSection(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBg(Color.WHITE, Color.rgb(226, 232, 242), 8)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(22, 32, 52))
                setPadding(0, 0, 0, dp(8))
            })
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
    }

    private fun detailGridRow(left: Pair<String, String>, right: Pair<String, String>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            addView(detailTile(left.first, left.second), LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(6)
            })
            addView(detailTile(right.first, right.second), LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(6)
            })
        }
    }

    private fun detailTile(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBg(Color.rgb(248, 250, 253), Color.rgb(232, 237, 246), 8)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(105, 116, 135))
            })
            addView(TextView(this@MainActivity).apply {
                text = value.ifBlank { "-" }
                textSize = 14f
                setTextColor(Color.rgb(22, 32, 52))
                setPadding(0, dp(4), 0, 0)
                setTextIsSelectable(true)
            })
        }
    }

    private fun withBottomMargin(view: LinearLayout): LinearLayout {
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        return view
    }

    private fun wideParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, dp(48)).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
            topMargin = dp(8)
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun topSafePadding(extraDp: Int): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
        return statusBarHeight + dp(extraDp)
    }

    data class TypeOption(val label: String, val value: String)
    data class ApiResponse(val body: String, val total: Int?)
}
