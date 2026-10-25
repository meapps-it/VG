package it.meapps.gestionale

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VgAppearanceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun eval(view: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync { view.evaluateJavascript(script) { result = it; done.countDown() } }
        check(done.await(20, TimeUnit.SECONDS)) { "WebView JavaScript timeout" }
        return JSONTokener(result).nextValue().toString()
    }
    private fun await(view: WebView, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            if (eval(view, "Boolean($expression)") == "true") return
            Thread.sleep(100)
        }
        error("WebView condition not reached: $expression; status=" + eval(view,"document.getElementById('vg-font-status')?.textContent"))
    }
    @Test fun persistAcrossProcessStop() {
        val stage = InstrumentationRegistry.getArguments().getString("appearancePersistence") ?: return
        val ctx = instrumentation.targetContext
        if (stage == "prepare") {
            VgAppearance(ctx).percent = 145
            // Flush the same SharedPreferences file before the runner is force-stopped.
            ctx.getSharedPreferences("vg_android_settings",0).edit().commit()
            assertEquals(145,VgAppearance(ctx).percent)
        } else {
            assertEquals(145,VgAppearance(ctx).percent)
            ActivityScenario.launch(VgPwaActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val view = web(activity.window.decorView)!!
                    assertEquals(VgTextScale.zoom(145,activity.resources.configuration.fontScale),view.settings.textZoom)
                }
            }
        }
    }
    @Test fun realWebViewFontScalingPersistenceAndNativeDialogs() {
        val ctx = instrumentation.targetContext
        ctx.getSharedPreferences("vg_android_settings",0).edit().clear().commit()
        ActivityScenario.launch(VgPwaActivity::class.java).use { scenario ->
            lateinit var view: WebView
            fun fixture() {
                scenario.onActivity { activity ->
                    view = web(activity.window.decorView)!!
                    view.stopLoading()
                    view.loadDataWithBaseURL("https://meapps-it.github.io/VG/",
                        """<html><head><script src="https://meapps-it.github.io/VG/vg-quality-pricing.js"></script><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}.card{padding:12px}p{font-size:16px}button{font-size:16px}</style></head><body><div id="sec_impostazioni"></div><p id="sample">Articoli e clienti</p><i class="fa" style="font-family:monospace" id="icon">*</i></body></html>""",
                        "text/html","UTF-8","https://meapps-it.github.io/VG/")
                }
                await(view, "document.getElementById('vg-font-range') && !document.getElementById('vg-font-range').disabled")
            }
            fixture()
            scenario.onActivity { assertEquals("https://meapps-it.github.io/VG/",view.url) }
            scenario.onActivity { activity ->
                val themeFont = activity.obtainStyledAttributes(intArrayOf(android.R.attr.fontFamily))
                try { assertNull("VG theme must not inherit a forced font family",themeFont.getString(0)) }
                finally { themeFont.recycle() }
                val fonts = VgAppearance(activity).fontState()
                android.util.Log.i("VgAppearanceTest", "Native faces=" + fonts.getJSONArray("faces").length() + "; " + fonts.getString("fontStatus"))
            }
            assertEquals("75",eval(view,"VgQualityPricing.profit(210.01,'ORIGINALE')"))
            assertEquals("290",eval(view,"VgQualityPricing.calculate(210,'ORIGINALE').sell"))
            assertEquals("90",eval(view,"VgQualityPricing.profit(220.01,'STANDARD')"))
            assertEquals("100%", eval(view, "document.getElementById('vg-font-value').textContent"))
            await(view, "document.documentElement.style.getPropertyValue('--vg-android-font') === 'VgAndroidSystem'")
            assertEquals("monospace",eval(view,"getComputedStyle(document.getElementById('icon')).fontFamily"))
            var baselineHeight = 0.0
            for (percent in listOf(75,100,150)) {
                eval(view, "document.getElementById('vg-font-range').value='$percent';document.getElementById('vg-font-range').dispatchEvent(new Event('input',{bubbles:true}));'ok'")
                await(view, "document.getElementById('vg-font-value').textContent === '$percent%'")
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(VgTextScale.zoom(percent, activity.resources.configuration.fontScale), view.settings.textZoom)
                    assertEquals(percent,VgAppearance(activity).percent)
                    assertEquals(activity.resources.configuration.fontScale * percent/100f,
                        VgAppearance(activity).dialogContext().resources.configuration.fontScale,0.001f)
                }
                // textZoom must change rendered text, not merely a label or CSS variable.
                Thread.sleep(250)
                val height = eval(view,"document.getElementById('sample').getBoundingClientRect().height").toDouble()
                if (percent == 75) baselineHeight = height
                if (percent == 150) assertTrue("150% text must be larger than 75%",height > baselineHeight * 1.7)
                assertEquals("true",eval(view,"(()=>{const b=document.getElementById('vg-font-reset');return b.scrollHeight<=b.clientHeight+2})()"))
            }
            scenario.recreate()
            fixture()
            await(view,"document.getElementById('vg-font-value').textContent === '150%'")
            eval(view,"document.getElementById('vg-font-reset').click();'ok'")
            await(view,"document.getElementById('vg-font-value').textContent === '100%'")
            assertEquals(100,VgAppearance(ctx).percent)
            // Script navigation cannot read clipboard; an explicit tap can.
            val copied = "CODICE: TEST-CLIP\nMODELLO: \"Prova\""
            instrumentation.runOnMainSync {
                ctx.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("test",copied))
            }
            eval(view,"window.vgReceiveArticleClipboard=text=>window.testClip=text;const b=document.createElement('button');b.id='native-paste';b.textContent='Incolla';b.onclick=()=>location.href='vgapp://clipboard';document.body.appendChild(b);location.href='vgapp://clipboard';'ok'")
            Thread.sleep(250)
            assertEquals("undefined",eval(view,"typeof window.testClip"))
            eval(view,"document.getElementById('native-paste').scrollIntoView({block:'center'});'ok'")
            val point=org.json.JSONObject(eval(view,"JSON.stringify((()=>{const r=document.getElementById('native-paste').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth}})())"))
            val origin=IntArray(2)
            var scale=1f
            instrumentation.runOnMainSync {view.getLocationOnScreen(origin);scale=view.width/point.getDouble("width").toFloat()}
            val x=origin[0]+point.getDouble("x").toFloat()*scale
            val y=origin[1]+point.getDouble("y").toFloat()*scale
            val now=android.os.SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                val event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,x,y,0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(event);event.recycle()
            }
            await(view,"typeof window.testClip === 'string'")
            assertEquals(copied,eval(view,"window.testClip"))
            // An untrusted top-level origin never receives the bridge or APK controls.
            scenario.onActivity { view.loadDataWithBaseURL("https://example.org/","<html><body>Untrusted</body></html>","text/html","UTF-8","https://example.org/") }
            await(view,"document.body && document.body.textContent === 'Untrusted'")
            assertEquals("undefined",eval(view,"typeof window.VgAppearanceBridge"))
            assertEquals("false",eval(view,"Boolean(document.getElementById('vg-font-control'))"))
        }
    }
}
