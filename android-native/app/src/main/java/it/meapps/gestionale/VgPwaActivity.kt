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
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
 private val io=CoroutineScope(kotlinx.coroutines.SupervisorJob()+Dispatchers.IO)
 private var fileCallback:ValueCallback<Array<Uri>>?=null
 private val filePicker=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode,result.data));fileCallback=null}
 private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)registerPush()else message("Notifiche disattivate","Puoi abilitarle nelle impostazioni Android.")}
 private val client=OkHttpClient()
 private fun trusted(url:String?):Boolean {val uri=Uri.parse(url?:return false);return uri.scheme=="https"&&uri.host=="meapps-it.github.io"&&(uri.path=="/VG"||uri.path?.startsWith("/VG/")==true)}
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(248,245,239))}
  fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(5),dp(8),dp(5))}
  val stats=Button(this).apply{text="Statistiche catalogo";setOnClickListener{showStats()}}
  val menuButton=Button(this).apply{text="Menu";setOnClickListener{button->PopupMenu(this@VgPwaActivity,button).apply{menu.add("Anteprima catalogo: escludi i miei accessi").setOnMenuItemClickListener{openAdminPreview();true};menu.add("Password clienti").setOnMenuItemClickListener{passwordDialog();true};menu.add("Attiva notifiche ordini").setOnMenuItemClickListener{configurePush();true};menu.add("Aggiorna gestionale").setOnMenuItemClickListener{web.reload();true};show()}}}
  bar.addView(stats,LinearLayout.LayoutParams(0,dp(52),1f));bar.addView(menuButton,LinearLayout.LayoutParams(dp(110),dp(52)));layout.addView(bar)
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(layout){view,insets->val bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets}
  web=WebView(this);layout.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(layout)
  web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.settings.allowFileAccess=false;web.settings.allowContentAccess=true;web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
  CookieManager.getInstance().setAcceptCookie(true)
  web.webViewClient=object:WebViewClient(){
   override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {if(trusted(request.url.toString()))return false;runCatching{startActivity(Intent(Intent.ACTION_VIEW,request.url))};return true}
   override fun onPageFinished(view:WebView,url:String){if(trusted(url))captureSession{jwt->io.launch{runCatching{val token=getSharedPreferences("vg_push",MODE_PRIVATE).getString("token",null);if(token!=null)VgPush.request(jwt,"register",JSONObject().put("token",token))}}}}
  }
  web.webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(view:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {fileCallback?.onReceiveValue(null);fileCallback=callback;return runCatching{filePicker.launch(params.createIntent());true}.getOrElse{fileCallback=null;false}}}
  web.setDownloadListener{url,_,_,_,_->runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(web.canGoBack())web.goBack()else finish()}})
  if(savedInstanceState!=null)web.restoreState(savedInstanceState)else web.loadUrl("https://meapps-it.github.io/VG/")
 }
 override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);web.saveState(outState)}
 override fun onDestroy(){fileCallback?.onReceiveValue(null);io.coroutineContext[kotlinx.coroutines.Job]?.cancel();web.destroy();super.onDestroy()}
 private fun message(title:String,text:String){runOnUiThread{if(!isFinishing)AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK",null).show()}}
 private fun captureSession(action:(String)->Unit){
  if(!trusted(web.url)){message("Accesso richiesto","Apri il gestionale e accedi con il tuo account amministratore.");return}
  web.evaluateJavascript("(function(){try{return localStorage.getItem('sb-qfjwtawsqfwmsmrrgqfi-auth-token')||''}catch(e){return ''}})()") {value->
   runCatching{val raw=JSONTokener(value).nextValue() as? String?:"";val data=JSONObject(raw);val token=data.optString("access_token");if(token.isBlank())error("Accedi prima al gestionale con il tuo account.");val user=data.optJSONObject("user");val session=Session(token,data.optString("refresh_token"),data.optLong("expires_at",System.currentTimeMillis()/1000+data.optLong("expires_in",3600)),user?.optString("id").orEmpty(),user?.optString("email").orEmpty());SessionStore(this).save(session);action(token)}.onFailure{message("Accesso richiesto","Accedi prima al gestionale con il tuo account amministratore.")}
  }
 }
 private fun catalog(jwt:String,action:String,body:JSONObject?=null):JSONObject{
  val req=Request.Builder().url(BuildConfig.SUPABASE_URL+"/functions/v1/vg-shop-catalog?action="+action).header("Authorization","Bearer "+jwt)
  if(body!=null)req.post(body.toString().toRequestBody("application/json".toMediaType()))
  return client.newCall(req.build()).execute().use{response->val result=JSONObject(response.body?.string().orEmpty().ifBlank{"{}"});if(!response.isSuccessful)error(result.optString("error","Servizio non disponibile"));result}
 }
 private fun openAdminPreview(){captureSession{jwt->io.launch{runCatching{catalog(jwt,"admin-session",JSONObject()).getString("token")}.onSuccess{token->withContext(Dispatchers.Main){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://simplexapp.it/shop/#admin_token="+token)))}}.onFailure{message("Anteprima non disponibile",it.message?:"Riprova")}}}}
 private fun passwordDialog(){val input=EditText(this).apply{inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;hint="Almeno 10 caratteri"};AlertDialog.Builder(this).setTitle("Password del catalogo clienti").setMessage("Imposta una password unica. Dopo la modifica i clienti dovranno reinserirla.").setView(input).setNegativeButton("Annulla",null).setPositiveButton("Imposta"){_,_->val password=input.text.toString();if(password.length<10)message("Password troppo breve","Usa almeno 10 caratteri.")else captureSession{jwt->io.launch{runCatching{catalog(jwt,"set-password",JSONObject().put("password",password))}.onSuccess{message("Password aggiornata","La nuova password è attiva per il catalogo clienti.")}.onFailure{message("Errore",it.message?:"Riprova")}}}}.show()}
 private fun showStats(){captureSession{jwt->io.launch{runCatching{catalog(jwt,"stats")}.onSuccess{data->withContext(Dispatchers.Main){
  val column=LinearLayout(this@VgPwaActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(30,20,30,20)}
  fun line(text:String,size:Float=16f){column.addView(TextView(this@VgPwaActivity).apply{this.text=text;textSize=size;setPadding(0,7,0,7);setTextColor(Color.rgb(25,44,65))})}
  line("Visite oggi: "+data.optInt("today"),23f);line("Ultimi 7 giorni: "+data.optInt("week"));line("Ultimi 30 giorni: "+data.optInt("month"));line("Dispositivi negli ultimi 30 giorni: "+data.optInt("devicesMonth"));line("I tuoi accessi amministratore sono esclusi.",14f)
  val daily=data.optJSONArray("daily");var max=1;if(daily!=null)for(i in 0 until daily.length())max=maxOf(max,daily.getJSONObject(i).optInt("visits"));line("Andamento giornaliero",18f)
  if(daily!=null)for(i in maxOf(0,daily.length()-14) until daily.length()){val day=daily.getJSONObject(i);line(day.optString("day")+" · "+day.optInt("visits")+" visite",13f);column.addView(ProgressBar(this@VgPwaActivity,null,android.R.attr.progressBarStyleHorizontal).apply{this.max=max;progress=day.optInt("visits")},LinearLayout.LayoutParams(-1,12))}
  line(data.optString("definition"),12f);line(if(data.optBoolean("pushConfigured"))"Servizio notifiche configurato" else "Notifiche: configurazione Firebase da completare",13f)
  val scroll=ScrollView(this@VgPwaActivity);scroll.addView(column);AlertDialog.Builder(this@VgPwaActivity).setTitle("Statistiche Shop & Parallelo").setView(scroll).setPositiveButton("Chiudi",null).show()
 }}.onFailure{message("Statistiche non disponibili",it.message?:"Riprova")}}}}
 private fun configurePush(){captureSession{jwt->io.launch{runCatching{VgPush.request(jwt,"config")}.onSuccess{result->withContext(Dispatchers.Main){val config=result.optJSONObject("client");if(config==null||!result.optBoolean("configured")){message("Notifiche non ancora configurate","Manca il collegamento Firebase del progetto. Le notifiche a app chiusa non sono ancora attive.");return@withContext};if(!VgPush.initialize(this@VgPwaActivity,config)){message("Configurazione non valida","Verifica il progetto Firebase.");return@withContext};if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this@VgPwaActivity,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else registerPush()}}.onFailure{message("Notifiche non disponibili",it.message?:"Riprova")}}}}
 private fun registerPush(){if(!VgPush.initialize(this)){message("Configurazione mancante","Collega prima il progetto Firebase.");return};FirebaseMessaging.getInstance().token.addOnSuccessListener{token->getSharedPreferences("vg_push",MODE_PRIVATE).edit().putString("token",token).apply();captureSession{jwt->io.launch{runCatching{VgPush.request(jwt,"register",JSONObject().put("token",token))}.onSuccess{message("Dispositivo registrato","Le notifiche dei nuovi ordini sono abilitate su questo telefono.")}.onFailure{message("Registrazione non riuscita",it.message?:"Riprova")}}}}.addOnFailureListener{message("Notifiche non disponibili",it.message?:"Riprova")}}
}
