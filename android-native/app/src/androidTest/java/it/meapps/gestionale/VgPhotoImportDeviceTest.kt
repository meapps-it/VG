package it.meapps.gestionale

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONTokener
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VgPhotoImportDeviceTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private fun web(v:View):WebView? {
  if(v is WebView)return v
  if(v is ViewGroup)for(i in 0 until v.childCount)web(v.getChildAt(i))?.let{return it}
  return null
 }
 private fun eval(view:WebView,script:String):String {
  val latch=CountDownLatch(1);var value=""
  instrumentation.runOnMainSync{view.evaluateJavascript(script){value=it;latch.countDown()}}
  check(latch.await(15,TimeUnit.SECONDS));return JSONTokener(value).nextValue().toString()
 }
 private fun await(view:WebView,expression:String){
  val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
  while(System.nanoTime()<deadline){if(eval(view,"Boolean($expression)")=="true")return;Thread.sleep(100)}
  error("Photo input did not reach: $expression; state="+eval(view,"document.body.innerText"))
 }
 private fun tap(view:WebView){
  val point=JSONObject(eval(view,"JSON.stringify((()=>{const r=document.getElementById('photo').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})())"))
  val origin=IntArray(2);var scale=1f
  instrumentation.runOnMainSync{view.getLocationOnScreen(origin);scale=view.width/point.getDouble("w").toFloat()}
  val x=origin[0]+point.getDouble("x").toFloat()*scale;val y=origin[1]+point.getDouble("y").toFloat()*scale
  val now=SystemClock.uptimeMillis()
  instrumentation.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0))
  instrumentation.sendPointerSync(MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,x,y,0))
 }
 @Test fun selectedProviderPhotosReachRealWebViewAndDecode(){
  val ctx=instrumentation.targetContext
  val directory=File(ctx.cacheDir,"camera").apply{mkdirs()}
  val original=File(directory,"photo-provider-without-image-mime.bin")
  val bitmap=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888)
  original.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".files",original)
  assertEquals("application/octet-stream",ctx.contentResolver.getType(uri))
  var multiple=false;var cancel=false;var launches=0
  val monitor=object:Instrumentation.ActivityMonitor(){
   override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
    if(intent.action!=Intent.ACTION_OPEN_DOCUMENT)return null
    launches++
    assertEquals("image/*",intent.type)
    assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false))
    if(cancel)return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
    val result=Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if(multiple){result.clipData=ClipData.newRawUri("foto",uri).apply{addItem(ClipData.Item(uri))}}
    else result.data=uri
    return Instrumentation.ActivityResult(Activity.RESULT_OK,result)
   }
  }
  instrumentation.addMonitor(monitor)
  try{
   ActivityScenario.launch(VgPwaActivity::class.java).use{scenario->
    lateinit var view:WebView
    scenario.onActivity{activity->
     view=web(activity.window.decorView)!!;view.stopLoading()
     val html="""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><input id="photo" type="file" accept="image/*" multiple style="height:64px;width:280px"><div id="status">Pronto</div><script>
       window.received=0;window.decoded=0;document.getElementById('photo').onchange=async function(){
        window.received+=this.files.length;
        for(const file of this.files){const image=new Image();image.onload=()=>{window.decoded++;document.getElementById('status').textContent='Anteprima '+window.decoded};image.src=URL.createObjectURL(file);document.body.appendChild(image)}
        this.value='';
       };
      </script></body></html>"""
     view.loadDataWithBaseURL("https://meapps-it.github.io/VG/__photo_device_fixture.html",html,"text/html","UTF-8",null)
    }
    await(view,"document.getElementById('photo')")
    tap(view);await(view,"window.decoded===1")
    assertEquals("1",eval(view,"window.received"))
    assertEquals("12",eval(view,"document.querySelector('img').naturalWidth"))
    multiple=true;tap(view);await(view,"window.decoded===2")
    assertEquals("2",eval(view,"window.received")) // duplicate provider URI is imported once
    cancel=true;tap(view);Thread.sleep(500)
    assertEquals("2",eval(view,"window.decoded"));assertEquals(3,launches)
   }
  }finally{instrumentation.removeMonitor(monitor);original.delete()}
 }
 @Test fun unreadableProviderFilesFailBeforeWebViewAndDoNotReplaceExistingPhotos(){
  val ctx=instrumentation.targetContext
  val file=File(File(ctx.cacheDir,"camera").apply{mkdirs()},"not-an-image.jpg").apply{writeText("not an image")}
  try{
   val uri=FileProvider.getUriForFile(ctx,ctx.packageName+".files",file)
   try{VgPhotoImport.prepare(ctx,Intent().setData(uri));fail("Invalid image must be rejected")}
   catch(expected:IllegalArgumentException){assertTrue(expected.message.orEmpty().contains("JPG o PNG"))}
   assertTrue("Source must be preserved",file.exists())
  }finally{file.delete()}
 }
}
