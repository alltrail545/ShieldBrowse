package com.shieldbrowse.app

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * GeckoView APIs that differ between releases (content-blocking category setters,
 * session setActive, WebExtensionController.install signatures) are called by
 * reflection, so a missing/renamed method can never break compilation or crash
 * the browser — the feature simply falls back and the notes say so honestly.
 */
object ExtSupport {

    fun applyPrivacy(runtime: GeckoRuntime, blocking: Boolean, jsOn: Boolean) {
        try {
            val settings = runtime.javaClass.getMethod("getSettings").invoke(runtime) ?: return
            // JavaScript on/off (runtime level)
            try {
                settings.javaClass.getMethod("setJavaScriptEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(settings, jsOn)
            } catch (_: Throwable) { }
            // Strict enhanced tracking protection when blocking is on
            if (blocking) {
                try {
                    val cb = settings.javaClass.getMethod("getContentBlocking").invoke(settings)
                    if (cb != null) {
                        val settingsCls = Class.forName("org.mozilla.geckoview.ContentBlocking\$Settings")
                        val strict = try {
                            settingsCls.getField("ETP_LEVEL_STRICT").getInt(null)
                        } catch (_: Throwable) { 2 }
                        cb.javaClass.getMethod(
                            "setEnhancedTrackingProtectionCategory", Int::class.javaPrimitiveType)
                            .invoke(cb, strict)
                    }
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    fun setActive(session: GeckoSession, active: Boolean) {
        try {
            session.javaClass.getMethod("setActive", Boolean::class.javaPrimitiveType)
                .invoke(session, active)
        } catch (_: Throwable) { }
    }

    private fun extensionController(runtime: GeckoRuntime): Any? {
        return try {
            runtime.javaClass.getMethod("getWebExtensionController").invoke(runtime)
        } catch (_: Throwable) { null }
    }

    /** True when this GeckoView build exposes WebExtensionController.install(String). */
    fun installApiAvailable(runtime: GeckoRuntime): Boolean {
        val c = extensionController(runtime) ?: return false
        return try {
            c.javaClass.getMethod("install", String::class.java)
            true
        } catch (_: Throwable) { false }
    }

    /**
     * Ask GeckoView to install an extension from a URI (AMO page / .xpi).
     * Returns true if the call was accepted synchronously; async rejection is
     * still possible, so the UI also opens the add-on page as a fallback path.
     */
    fun tryInstall(runtime: GeckoRuntime, uri: String): Boolean {
        val c = extensionController(runtime) ?: return false
        return try {
            val m = c.javaClass.getMethod("install", String::class.java)
            m.invoke(c, uri)
            true
        } catch (_: Throwable) { false }
    }
}
