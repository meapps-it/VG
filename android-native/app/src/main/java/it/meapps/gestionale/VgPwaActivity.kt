package it.meapps.gestionale

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.webkit.*
import android.widget.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener

class VgPwaActivity : ComponentActivity() {
 private lateinit var web:WebView
 private val appearance by lazy { VgAppearance(this) }
 private val appearanceScript by lazy { assets.open("vg-android-appearance.js").bufferedReader().use { it.readText() } }
 private var fontState:JSONObject?=null
 private var fontRevision=0
 private val io=CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.IO)
 private var fileCallback:ValueCallback<Array<Uri>>?=null
 private val filePicker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data));fileCallback=null}
 private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)registerPush()else message("Notifiche disattivate","Puoi abilitarle nelle impostazioni Android.")}
 private val client=OkHttpClient()
 private fun trusted(url:String?):Boolean {val uri=Uri.parse(url?:return false);return uri.scheme=="https"&&uri.host=="meapps-it.github.io"&&(uri.path=="/VG"||uri.path?.startsWith("/VG/")==true)}
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(Color.TRANSPARENT,Color.TRANSPARENT))
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(247,248,252))}
  val statusInset=android.view.View(this).apply{setBackgroundColor(Color.rgb(15,23,42))}
  layout.addView(statusInset,LinearLayout.LayoutParams(-1,0))
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(layout){view,insets->
   val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
   view.setPadding(bars.left,0,bars.right,maxOf(bars.bottom,insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom))
   if(statusInset.layoutParams.height!=bars.top){statusInset.layoutParams=statusInset.layoutParams.apply{height=bars.top}}
   insets
  }
  web=WebView(this);layout.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(layout)
  web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.allowFileAccess=false;web.settings.allowContentAccess=true;web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
  web.settings.userAgentString=web.settings.userAgentString+" VgHybrid/"+BuildConfig.VERSION_NAME+" VgClipboard/1"
  web.settings.textZoom=appearance.zoom()
  configureAppearance()
  web.settings.useWideViewPort=true
  web.settings.loadWithOverviewMode=true
  CookieManager.getInstance().setAcceptCookie(true)
  web.webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
    val uri=request.url
    if(uri.scheme=="vgapp"){
     if(trusted(view.url)&&request.isForMainFrame&&request.hasGesture())when(uri.host){
      "preview"->openAdminPreview()
      "password"->passwordDialog()
      "notifications"->configurePush()
      "refresh"->web.reload()
      "clipboard"->pasteArticleClipboard()
     }
     return true
    }
    if(trusted(uri.toString()))return false
    if(request.isForMainFrame&&request.hasGesture())runCatching{startActivity(Intent(Intent.ACTION_VIEW,uri))}
    return true
   }
   override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
    if(trusted(request.url.toString())&&request.url.lastPathSegment=="vg-quality-pricing.js")
     return WebResourceResponse("application/javascript","UTF-8",assets.open("vg-quality-pricing.js"))
    if(trusted(request.url.toString())&&request.url.lastPathSegment=="vg-font-settings.js")
     return WebResourceResponse("application/javascript","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
    return null
   }
   override fun onPageCommitVisible(view:WebView,url:String){if(trusted(url))applyAppearance()}
   override fun onPageFinished(view:WebView,url:String){if(trusted(url)){applyAppearance();captureSession(silent=true){jwt->io.launch{runCatching{val token=getSharedPreferences("vg_push",MODE_PRIVATE).getString("token",null);if(token!=null)VgPush.request(jwt,"register",JSONObject().put("token",token))}}}}}
  }
  web.webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(view:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {fileCallback?.onReceiveValue(null);fileCallback=callback;return runCatching{filePicker.launch(params.createIntent());true}.getOrElse{fileCallback=null;false}}}
  web.setDownloadListener{url,_,_,_,_->runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(web.canGoBack())web.goBack()else finish()}})
  if(savedInstanceState!=null)web.restoreState(savedInstanceState)else web.loadUrl("https://meapps-it.github.io/VG/")
 }
 override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);web.saveState(outState)}
 override fun onDestroy(){fileCallback?.onReceiveValue(null);io.coroutineContext[kotlinx.coroutines.Job]?.cancel();web.destroy();super.onDestroy()}
 override fun onResume(){
  super.onResume()
  if(::web.isInitialized){
   web.settings.textZoom=appearance.zoom()
   fontState=null
   fontRevision++
   applyAppearance()
  }
 }
 private fun configureAppearance(){
  val origins=setOf("https://meapps-it.github.io")
  if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
   WebViewCompat.addWebMessageListener(web,"VgAppearanceBridge",origins){view,msg,origin,mainFrame,_->
    if(mainFrame&&origin.toString()=="https://meapps-it.github.io"&&trusted(view.url)) {
     runCatching {
      val data=JSONObject(msg.data.orEmpty())
      when(data.optString("action")) {
       "set"->{appearance.percent=data.getInt("percent");web.settings.textZoom=appearance.zoom();applyAppearance(false)}
       "get"->applyAppearance()
      }
     }
    }
   }
  }
  if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
   WebViewCompat.addDocumentStartJavaScript(web,appearanceScript,origins)
 }
 private fun applyAppearance(includeFont:Boolean=true){
  if(!trusted(web.url))return
  web.settings.textZoom=appearance.zoom()
  val state=appearance.state()
  if(includeFont){
   val fonts=fontState?:appearance.fontState().also{fontState=it}
   state.put("fontRevision",fontRevision).put("sources",fonts.getJSONArray("sources")).put("faces",fonts.getJSONArray("faces")).put("fontStatus",fonts.getString("fontStatus"))
  }
  web.evaluateJavascript(appearanceScript+";window.__vgApplyAppearance&&window.__vgApplyAppearance("+state.toString()+");",null)
 }
 private fun pasteArticleClipboard(){
  val clipboard=getSystemService(android.content.ClipboardManager::class.java)
  val text=clipboard?.primaryClip?.takeIf{it.itemCount>0}?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
  web.evaluateJavascript("window.vgReceiveArticleClipboard&&window.vgReceiveArticleClipboard("+JSONObject.quote(text)+");",null)
 }
 private fun message(title:String,text:String){runOnUiThread{if(!isFinishing)AlertDialog.Builder(appearance.dialogContext()).setTitle(title).setMessage(text).setPositiveButton("OK",null).show()}}
 private fun captureSession(silent:Boolean=false,action:(String)->Unit){
  if(!trusted(web.url)){if(!silent)message("Accesso richiesto","Apri il gestionale e accedi con il tuo account amministratore.");return}
  web.evaluateJavascript("(function(){try{return localStorage.getItem('sb-qfjwtawsqfwmsmrrgqfi-auth-token')||''}catch(e){return ''}})()") {value->
   runCatching{val raw=JSONTokener(value).nextValue() as? String?:"";val data=JSONObject(raw);val token=data.optString("access_token");if(token.isBlank())error("Accedi prima al gestionale con il tuo account.");val user=data.optJSONObject("user");val session=Session(token,data.optString("refresh_token"),data.optLong("expires_at",System.currentTimeMillis()/1000+data.optLong("expires_in",3600)),user?.optString("id").orEmpty(),user?.optString("email").orEmpty());SessionStore(this).save(session);action(token)}.onFailure{if(!silent)message("Accesso richiesto","Accedi prima al gestionale con il tuo account amministratore.")}
  }
 }
 private fun catalog(jwt:String,action:String,body:JSONObject?=null):JSONObject{
  val req=Request.Builder().url(BuildConfig.SUPABASE_URL+"/functions/v1/vg-shop-catalog?action="+action).header("Authorization","Bearer "+jwt)
  if(body!=null)req.post(body.toString().toRequestBody("application/json".toMediaType()))
  return client.newCall(req.build()).execute().use{response->val result=JSONObject(response.body?.string().orEmpty().ifBlank{"{}"});if(!response.isSuccessful)error(result.optString("error","Servizio non disponibile"));result}
 }
 private fun openAdminPreview(){captureSession{jwt->io.launch{runCatching{catalog(jwt,"admin-session",JSONObject()).getString("token")}.onSuccess{token->withContext(Dispatchers.Main){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://simplexapp.it/shop/#admin_token="+token)))}}.onFailure{message("Anteprima non disponibile",it.message?:"Riprova")}}}}
 private fun passwordDialog(){val input=EditText(appearance.dialogContext()).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;hint="Almeno 6 caratteri"};AlertDialog.Builder(appearance.dialogContext()).setTitle("Password del catalogo clienti").setMessage("Imposta una password unica. Dopo la modifica i clienti dovranno reinserirla.").setView(input).setNegativeButton("Annulla",null).setPositiveButton("Imposta"){_,_->val password=input.text.toString();if(password.length<6)message("Password troppo breve","Usa almeno 6 caratteri.")else captureSession{jwt->io.launch{runCatching{catalog(jwt,"set-password",JSONObject().put("password",password))}.onSuccess{message("Password aggiornata","La nuova password è attiva per il catalogo clienti.")}.onFailure{message("Errore",it.message?:"Riprova")}}}}.show()}
 private fun configurePush(){captureSession{jwt->io.launch{runCatching{VgPush.request(jwt,"config")}.onSuccess{result->withContext(Dispatchers.Main){val config=result.optJSONObject("client");if(config==null||!result.optBoolean("configured")){message("Notifiche non ancora configurate","Manca il collegamento Firebase del progetto. Le notifiche a app chiusa non sono ancora attive.");return@withContext};if(!VgPush.initialize(this@VgPwaActivity,config)){message("Configurazione non valida","Verifica il progetto Firebase.");return@withContext};if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this@VgPwaActivity,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else registerPush()}}.onFailure{message("Notifiche non disponibili",it.message?:"Riprova")}}}}
 private fun registerPush(){if(!VgPush.initialize(this)){message("Configurazione mancante","Collega prima il progetto Firebase.");return};FirebaseMessaging.getInstance().token.addOnSuccessListener{token->getSharedPreferences("vg_push",MODE_PRIVATE).edit().putString("token",token).apply();captureSession{jwt->io.launch{runCatching{VgPush.request(jwt,"register",JSONObject().put("token",token))}.onSuccess{message("Dispositivo registrato","Le notifiche dei nuovi ordini sono abilitate su questo telefono.")}.onFailure{message("Registrazione non riuscita",it.message?:"Riprova")}}}}.addOnFailureListener{message("Notifiche non disponibili",it.message?:"Riprova")}}
}
