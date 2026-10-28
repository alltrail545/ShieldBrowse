package com.shieldbrowse.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class MainActivity : Activity() {

    companion object {
        // GeckoRuntime.create() may only run once per process — reuse it.
        var sharedRuntime: GeckoRuntime? = null
    }

    class Tab(val session: GeckoSession, val isPrivate: Boolean) {
        var url: String = ""
        var title: String = ""
        var canBack: Boolean = false
        var canFwd: Boolean = false
    }

    private lateinit var runtime: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var homeOverlay: ScrollView
    private lateinit var panelHost: FrameLayout
    private lateinit var tabsBtn: TextView
    private lateinit var backBtn: TextView
    private lateinit var fwdBtn: TextView
    private var panel: View? = null

    private val tabs = mutableListOf<Tab>()
    private var cur = -1
    private fun currentTab(): Tab? = if (cur in tabs.indices) tabs[cur] else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = sharedRuntime ?: GeckoRuntime.create(this).also { sharedRuntime = it }
        ExtSupport.applyPrivacy(runtime, Store.blockingOn(this), Store.jsOn(this))
        buildUi()
        val startUrl = intent?.dataString?.takeIf { it.startsWith("http") }
        createTab(startUrl, isPrivate = false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val u = intent?.dataString?.takeIf { it.startsWith("http") } ?: return
        createTab(u, isPrivate = false)
    }

    // ---------------------------------------------------------- UI shell

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // top bar: URL field + tabs button + new tab
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8))
        urlBar = EditText(this)
        urlBar.hint = "সার্চ বা ওয়েব ঠিকানা"
        urlBar.setSingleLine(true)
        urlBar.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_URI
        urlBar.imeOptions = EditorInfo.IME_ACTION_GO
        urlBar.setTextColor(Ui.TEXT)
        urlBar.setHintTextColor(Ui.MUTED)
        urlBar.textSize = 15f
        urlBar.background = Ui.cardBg(this, 999)
        urlBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10))
        urlBar.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { goToInput(); true } else false
        }
        urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) urlBar.selectAll()
        }
        top.addView(urlBar)
        tabsBtn = Ui.toolButton(this, "▢") { showTabsPanel() }
        top.addView(tabsBtn)
        top.addView(Ui.toolButton(this, "+") { createTab(null, isPrivate = false) })
        column.addView(top)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = View.GONE
        column.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)))

        // content: GeckoView + native home overlay
        val content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        geckoView = GeckoView(this)
        content.addView(geckoView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        homeOverlay = buildHome()
        content.addView(homeOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(content)

        // bottom bar
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER
        bottom.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4))
        backBtn = Ui.toolButton(this, "◀") { currentTab()?.session?.goBack() }
        fwdBtn = Ui.toolButton(this, "▶") { currentTab()?.session?.goForward() }
        bottom.addView(backBtn)
        bottom.addView(fwdBtn)
        bottom.addView(Ui.toolButton(this, "⟳") { currentTab()?.session?.reload() })
        bottom.addView(Ui.toolButton(this, "⌂") { showHome() })
        bottom.addView(Ui.toolButton(this, "★") { toggleBookmark() })
        bottom.addView(Ui.toolButton(this, "⋮") { showMenu() })
        column.addView(bottom)

        panelHost = FrameLayout(this)
        panelHost.visibility = View.GONE
        root.addView(panelHost, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    private fun buildHome(): ScrollView {
        val sv = ScrollView(this)
        sv.background = Ui.aurora(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 28))
        sv.addView(col)

        val title = Ui.text(this, "🛡 Shield Browser", 26f, bold = true)
        title.gravity = Gravity.CENTER
        col.addView(title)
        val sub = Ui.text(this, "প্রাইভেসি-প্রথম • বিজ্ঞাপনমুক্ত", 14f, Ui.SECONDARY)
        sub.gravity = Gravity.CENTER
        col.addView(sub)
        col.addView(Ui.gap(this, 18))

        val card = Ui.card(this)
        Ui.cardHeader(this, card, "দ্রুত লিংক",
            "নতুন ট্যাবে এই পেজটা দেখায়। উপরের বারে ঠিকানা বা সার্চ লিখলেই পেজ খুলবে।")
        val links = listOf(
            "DuckDuckGo" to "https://duckduckgo.com/",
            "উইকিপিডিয়া" to "https://bn.wikipedia.org/",
            "YouTube" to "https://www.youtube.com/",
            "GitHub" to "https://github.com/")
        for ((name, u) in links) {
            card.addView(Ui.ghostButton(this, name) { loadInCurrent(u) })
        }
        col.addView(card)

        val priv = Ui.card(this)
        Ui.cardHeader(this, priv, "প্রাইভেসি",
            "ট্র্যাকিং প্রোটেকশন চালু থাকলে বিজ্ঞাপন ও ট্র্যাকার ব্লক হয়। প্রাইভেট ট্যাবের হিস্টোরি সেভ হয় না। কোনো ডেটা কোনো সার্ভারে পাঠানো হয় না — বুকমার্ক ও হিস্টোরি শুধু এই ফোনে থাকে।")
        priv.addView(Ui.text(this,
            if (Store.blockingOn(this)) "ট্র্যাকার ব্লকিং: চালু" else "ট্র্যাকার ব্লকিং: বন্ধ",
            14.5f, Ui.SECONDARY))
        priv.addView(Ui.gap(this, 6))
        priv.addView(Ui.ghostButton(this, "প্রাইভেট ট্যাব খুলুন") {
            createTab(null, isPrivate = true)
        })
        col.addView(priv)
        return sv
    }

    // ---------------------------------------------------------- tabs

    private fun createTab(url: String?, isPrivate: Boolean, switchTo: Boolean = true): Tab {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(Store.blockingOn(this))
            .build()
        val session = GeckoSession(settings)
        val tab = Tab(session, isPrivate)
        wireDelegates(tab)
        session.open(runtime)
        tabs.add(tab)
        if (switchTo) switchTab(tabs.size - 1)
        if (!url.isNullOrEmpty()) {
            tab.url = url
            session.loadUri(url)
        }
        refreshChrome()
        return tab
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        tabs.forEachIndexed { i, t -> ExtSupport.setActive(t.session, i == index) }
        cur = index
        val tab = tabs[cur]
        geckoView.setSession(tab.session)
        refreshChrome()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        try { tabs[index].session.close() } catch (_: Throwable) { }
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            cur = -1
            createTab(null, isPrivate = false)
            return
        }
        switchTab(minOf(index, tabs.size - 1))
    }

    private fun wireDelegates(tab: Tab) {
        val session = tab.session
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(s: GeckoSession, url: String?) {
                tab.url = url ?: ""
                if (!tab.isPrivate && tab.url.startsWith("http")) {
                    Store.addHistory(this@MainActivity, tab.title, tab.url)
                }
                if (tab === currentTab()) refreshChrome()
            }
            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                tab.canBack = canGoBack
                if (tab === currentTab()) refreshChrome()
            }
            override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) {
                tab.canFwd = canGoForward
                if (tab === currentTab()) refreshChrome()
            }
            override fun onLoadRequest(
                s: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                if (uri.startsWith("tel:") || uri.startsWith("mailto:") ||
                    uri.startsWith("sms:") || uri.startsWith("geo:") ||
                    uri.startsWith("intent:") || uri.startsWith("market:")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                    } catch (_: Throwable) {
                        Ui.toast(this@MainActivity, "খোলা যায়নি")
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            override fun onNewSession(s: GeckoSession, uri: String?): GeckoResult<GeckoSession> {
                val t = createTab(uri, tab.isPrivate)
                return GeckoResult.fromValue(t.session)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                if (tab === currentTab()) {
                    progress.visibility = View.VISIBLE
                    progress.progress = 0
                }
            }
            override fun onPageStop(s: GeckoSession, success: Boolean) {
                if (tab === currentTab()) progress.visibility = View.GONE
            }
            override fun onProgressChange(s: GeckoSession, p: Int) {
                if (tab === currentTab()) progress.progress = p
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                tab.title = title ?: ""
                if (!tab.isPrivate && tab.url.startsWith("http") && tab.title.isNotEmpty()) {
                    Store.addHistory(this@MainActivity, tab.title, tab.url)
                }
                if (tab === currentTab()) refreshChrome()
            }
        }
    }

    private fun refreshChrome() {
        val tab = currentTab()
        tabsBtn.text = "▢ ${tabs.size}"
        if (tab != null && !urlBar.hasFocus()) {
            urlBar.setText(tab.url)
            urlBar.setSelection(0)
        }
        homeOverlay.visibility = if (tab == null || tab.url.isEmpty()) View.VISIBLE else View.GONE
        backBtn.alpha = if (tab?.canBack == true) 1f else 0.35f
        fwdBtn.alpha = if (tab?.canFwd == true) 1f else 0.35f
    }

    // ---------------------------------------------------------- navigation

    private fun smartUrl(input: String): String {
        val q = input.trim()
        if (q.startsWith("http://") || q.startsWith("https://") || q.startsWith("about:")) return q
        val looksLikeHost = !q.contains(" ") && q.contains(".") &&
            !q.startsWith(".") && !q.endsWith(".")
        if (looksLikeHost) return "https://$q"
        return Store.searchUrl(Store.searchEngine(this), q)
    }

    private fun goToInput() {
        val raw = urlBar.text.toString()
        if (raw.isBlank()) return
        loadInCurrent(smartUrl(raw))
        urlBar.clearFocus()
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        } catch (_: Throwable) { }
    }

    private fun loadInCurrent(url: String) {
        val tab = currentTab() ?: createTab(url, isPrivate = false)
        tab.url = url
        tab.session.loadUri(url)
        refreshChrome()
    }

    private fun showHome() {
        val tab = currentTab() ?: return
        tab.url = ""
        tab.title = ""
        refreshChrome()
    }

    private fun toggleBookmark() {
        val tab = currentTab() ?: return
        if (!tab.url.startsWith("http")) {
            Ui.toast(this, "আগে একটা পেজ খুলুন")
            return
        }
        val added = Store.toggleBookmark(this, tab.title.ifEmpty { tab.url }, tab.url)
        Ui.toast(this, if (added) "বুকমার্ক করা হয়েছে" else "বুকমার্ক সরানো হয়েছে")
    }

    override fun onBackPressed() {
        if (panel != null) { closePanel(); return }
        val tab = currentTab()
        if (tab != null && tab.canBack) { tab.session.goBack(); return }
        if (tab != null && tab.url.isNotEmpty()) { showHome(); return }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ---------------------------------------------------------- panels

    private fun openPanel(title: String, build: (LinearLayout) -> Unit) {
        val sv = ScrollView(this)
        sv.background = Ui.aurora(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 24))
        sv.addView(col)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0)
        val t = Ui.text(this, title, 20f, bold = true)
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(t)
        head.addView(Ui.toolButton(this, "✕") { closePanel() })
        col.addView(head)
        build(col)
        panelHost.removeAllViews()
        panelHost.addView(sv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panelHost.visibility = View.VISIBLE
        panel = sv
    }

    private fun closePanel() {
        panelHost.visibility = View.GONE
        panelHost.removeAllViews()
        panel = null
        refreshChrome()
    }

    private fun showTabsPanel() {
        openPanel("ট্যাবসমূহ (${tabs.size})") { col ->
            val card = Ui.card(this)
            tabs.forEachIndexed { i, tab ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
                val label = Ui.text(this,
                    (if (i == cur) "● " else "○ ") +
                        (if (tab.isPrivate) "🕶 " else "") +
                        tab.title.ifEmpty { tab.url.ifEmpty { "নতুন ট্যাব" } },
                    14.5f)
                label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                label.setOnClickListener { switchTab(i); closePanel() }
                row.addView(label)
                val x = Ui.toolButton(this, "✕") { closeTab(i); showTabsPanel() }
                row.addView(x)
                card.addView(row)
            }
            col.addView(card)
            val actions = Ui.card(this)
            actions.addView(Ui.primaryButton(this, "নতুন ট্যাব") {
                createTab(null, isPrivate = false); closePanel()
            })
            actions.addView(Ui.ghostButton(this, "নতুন প্রাইভেট ট্যাব") {
                createTab(null, isPrivate = true); closePanel()
            })
            col.addView(actions)
        }
    }

    private fun listRow(parent: LinearLayout, title: String, url: String,
                        onOpen: () -> Unit, onDelete: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7))
        val tv = Ui.text(this, title.ifEmpty { url } + "\n" + url, 13.5f, Ui.SECONDARY)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tv.setOnClickListener { onOpen() }
        row.addView(tv)
        row.addView(Ui.toolButton(this, "✕") { onDelete() })
        parent.addView(row)
    }

    private fun showBookmarksPanel() {
        openPanel("বুকমার্ক") { col ->
            val card = Ui.card(this)
            val items = Store.bookmarks(this)
            if (items.isEmpty()) {
                card.addView(Ui.text(this, "কোনো বুকমার্ক নেই। পেজ খুলে ★ চাপ দিন।", 14.5f, Ui.SECONDARY))
            }
            for (o in items) {
                val u = o.optString("u")
                listRow(card, o.optString("t"), u,
                    onOpen = { loadInCurrent(u); closePanel() },
                    onDelete = { Store.removeBookmark(this, u); showBookmarksPanel() })
            }
            col.addView(card)
        }
    }

    private fun showHistoryPanel() {
        openPanel("হিস্টোরি") { col ->
            val card = Ui.card(this)
            val items = Store.history(this)
            if (items.isEmpty()) {
                card.addView(Ui.text(this, "কোনো হিস্টোরি নেই। প্রাইভেট ট্যাবের হিস্টোরি কখনো সেভ হয় না।", 14.5f, Ui.SECONDARY))
            }
            for (o in items) {
                val u = o.optString("u")
                listRow(card, o.optString("t"), u,
                    onOpen = { loadInCurrent(u); closePanel() },
                    onDelete = { Store.removeHistory(this, u); showHistoryPanel() })
            }
            col.addView(card)
            if (items.isNotEmpty()) {
                val danger = Ui.card(this)
                danger.addView(Ui.ghostButton(this, "সব হিস্টোরি মুছুন") {
                    AlertDialog.Builder(this).setTitle("সব হিস্টোরি মুছবেন?")
                        .setMessage("এটা ফেরানো যাবে না। বুকমার্ক থাকবে।")
                        .setNegativeButton("না", null)
                        .setPositiveButton("মুছুন") { _, _ ->
                            Store.clearHistory(this); showHistoryPanel()
                        }.show()
                })
                col.addView(danger)
            }
        }
    }

    private fun showExtensionsPanel() {
        data class Addon(val name: String, val desc: String, val amo: String)
        val addons = listOf(
            Addon("uBlock Origin", "শক্তিশালী বিজ্ঞাপন ও ট্র্যাকার ব্লকার",
                "https://addons.mozilla.org/en-US/android/addon/ublock-origin/"),
            Addon("Privacy Badger", "লুকানো ট্র্যাকার ব্লকার",
                "https://addons.mozilla.org/en-US/android/addon/privacy-badger17/"),
            Addon("Dark Reader", "সব সাইটে ডার্ক মোড",
                "https://addons.mozilla.org/en-US/android/addon/darkreader/"),
            Addon("ClearURLs", "লিংক থেকে ট্র্যাকিং কোড সরায়",
                "https://addons.mozilla.org/en-US/android/addon/clearurls/"))
        openPanel("এক্সটেনশন") { col ->
            val info = Ui.card(this)
            Ui.cardHeader(this, info, "Firefox Add-ons",
                "এগুলো Firefox-এর অফিসিয়াল Add-ons (AMO) থেকে ইনস্টল হয়। Chrome Web Store-এর এক্সটেনশন Android-এর কোনো GeckoView ব্রাউজারে চলে না। ইনস্টল চাপলে প্রথমে সরাসরি ইনস্টলের চেষ্টা হবে, সাথে অ্যাড-অনের পেজটাও খুলে যাবে — পেজের ইনস্টল বাটন থেকেও করা যাবে।")
            info.addView(Ui.text(this,
                "ইনস্টল API: " + if (ExtSupport.installApiAvailable(runtime)) "পাওয়া গেছে" else "পাওয়া যায়নি — পেজ থেকে ইনস্টল করুন",
                14f, Ui.SECONDARY))
            info.addView(Ui.gap(this, 6))
            info.addView(Ui.primaryButton(this, "Add-ons স্টোর খুলুন") {
                createTab("https://addons.mozilla.org/en-US/android/", isPrivate = false)
                closePanel()
            })
            col.addView(info)
            val card = Ui.card(this)
            Ui.cardHeader(this, card, "সাজেস্ট করা", null)
            for (a in addons) {
                card.addView(Ui.text(this, a.name, 15.5f, bold = true))
                card.addView(Ui.text(this, a.desc, 13.5f, Ui.SECONDARY))
                card.addView(Ui.ghostButton(this, "ইনস্টল — ${a.name}") {
                    val ok = ExtSupport.tryInstall(runtime, a.amo)
                    Ui.toast(this, if (ok) "ইনস্টল শুরু হয়েছে" else "পেজ থেকে ইনস্টল করুন")
                    createTab(a.amo, isPrivate = false)
                    closePanel()
                })
                card.addView(Ui.gap(this, 8))
            }
            col.addView(card)
        }
    }

    private fun switchRow(parent: LinearLayout, label: String, info: String?,
                          checked: Boolean, onChange: (Boolean) -> Unit) {
        if (info != null) {
            val head = Ui.text(this, label, 15f, bold = true)
            parent.addView(head)
            parent.addView(Ui.text(this, info, 12.5f, Ui.MUTED))
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6))
        val tv = Ui.text(this, if (info == null) label else "চালু / বন্ধ", 14.5f)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)
        val sw = Switch(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        row.addView(sw)
        parent.addView(row)
    }

    private fun showSettingsPanel() {
        openPanel("সেটিংস") { col ->
            val search = Ui.card(this)
            Ui.cardHeader(this, search, "সার্চ ইঞ্জিন",
                "ঠিকানার বারে শব্দ লিখলে এই ইঞ্জিনে সার্চ হবে। ডিফল্ট DuckDuckGo — ট্র্যাকিং করে না।")
            search.addView(Ui.text(this, "বর্তমান: " + Store.engineName(Store.searchEngine(this)), 14.5f, Ui.SECONDARY))
            search.addView(Ui.ghostButton(this, "সার্চ ইঞ্জিন বদলান") {
                val ids = arrayOf("ddg", "google", "bing")
                val names = arrayOf("DuckDuckGo", "Google", "Bing")
                AlertDialog.Builder(this).setTitle("সার্চ ইঞ্জিন")
                    .setItems(names) { _, which ->
                        Store.setSearchEngine(this, ids[which])
                        showSettingsPanel()
                    }.show()
            })
            col.addView(search)

            val priv = Ui.card(this)
            Ui.cardHeader(this, priv, "প্রাইভেসি ও ব্লকিং", null)
            switchRow(priv, "অ্যাড ও ট্র্যাকার ব্লকিং",
                "বন্ধ করলে নতুন ট্যাব থেকে কার্যকর; পুরোপুরি প্রয়োগে অ্যাপ বন্ধ করে আবার খুলুন।",
                Store.blockingOn(this)) { on ->
                Store.setBlockingOn(this, on)
                Ui.toast(this, if (on) "ব্লকিং চালু" else "ব্লকিং বন্ধ")
            }
            switchRow(priv, "JavaScript",
                "বন্ধ করলে অনেক সাইট ভেঙে যেতে পারে। পরের চালু থেকে কার্যকর।",
                Store.jsOn(this)) { on ->
                Store.setJsOn(this, on)
                Ui.toast(this, "পরের চালু থেকে কার্যকর হবে")
            }
            col.addView(priv)

            val data = Ui.card(this)
            Ui.cardHeader(this, data, "ডেটা", "বুকমার্ক ও হিস্টোরি শুধু এই ফোনে থাকে। অ্যাপ আনইনস্টল করলে সব মুছে যাবে।")
            data.addView(Ui.ghostButton(this, "সব হিস্টোরি মুছুন") {
                Store.clearHistory(this)
                Ui.toast(this, "হিস্টোরি মোছা হয়েছে")
            })
            col.addView(data)

            val about = Ui.card(this)
            about.addView(Ui.text(this, "Shield Browser v1.0.0 • GeckoView (Firefox) ইঞ্জিন", 13.5f, Ui.MUTED))
            col.addView(about)
        }
    }

    private fun showMenu() {
        val items = arrayOf(
            "নতুন ট্যাব", "নতুন প্রাইভেট ট্যাব", "বুকমার্ক", "হিস্টোরি",
            "ট্যাবসমূহ", "এক্সটেনশন", "পেজ শেয়ার করুন", "রিলোড", "সেটিংস")
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> createTab(null, isPrivate = false)
                1 -> createTab(null, isPrivate = true)
                2 -> showBookmarksPanel()
                3 -> showHistoryPanel()
                4 -> showTabsPanel()
                5 -> showExtensionsPanel()
                6 -> sharePage()
                7 -> currentTab()?.session?.reload()
                8 -> showSettingsPanel()
            }
        }.show()
    }

    private fun sharePage() {
        val tab = currentTab() ?: return
        if (tab.url.isEmpty()) { Ui.toast(this, "আগে একটা পেজ খুলুন"); return }
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, tab.title)
        send.putExtra(Intent.EXTRA_TEXT, tab.url)
        startActivity(Intent.createChooser(send, "শেয়ার"))
    }
}
