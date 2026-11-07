
package com.alzatube.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var root: LinearLayout

    private var customView: View? = null
    private var customViewCallback:
        WebChromeClient.CustomViewCallback? = null

    private var previousOrientation =
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    companion object {
        private const val AUDIO_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 6)
            setBackgroundColor(Color.rgb(25, 25, 25))
        }

        val title = TextView(this).apply {
            text = "AlzaTube Auto 1.0"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(8, 0, 8, 0)
        }

        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        toolbar.addView(makeButton("Back") {
            if (webView.canGoBack()) webView.goBack()
        })

        toolbar.addView(makeButton("Reload") {
            webView.reload()
        })

        toolbar.addView(makeButton("Landscape") {
            requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        })

        toolbar.addView(makeButton("Allow Music") {
            requestAudioPermission()
        })

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(this, true)

            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {

                override fun onShowCustomView(
                    view: View?,
                    callback: CustomViewCallback?
                ) {
                    if (view == null) return

                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }

                    customView = view
                    customViewCallback = callback

                    previousOrientation = requestedOrientation
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                    toolbar.visibility = View.GONE
                    webView.visibility = View.GONE

                    root.addView(
                        view,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    enterImmersiveMode()
                }

                override fun onHideCustomView() {
                    leaveCustomView()
                }
            }
        }

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        webView.loadUrl("https://m.youtube.com")
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Music access is already enabled",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        requestPermissions(
            arrayOf(permission),
            AUDIO_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED

            Toast.makeText(
                this,
                if (granted) "Music access granted"
                else "Music access denied",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setPadding(4, 2, 4, 2)
            setOnClickListener { action() }
        }
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun leaveImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun leaveCustomView() {
        val activeView = customView ?: return

        (activeView.parent as? ViewGroup)?.removeView(activeView)

        customView = null
        toolbar.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE

        leaveImmersiveMode()
        requestedOrientation = previousOrientation

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            leaveCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
