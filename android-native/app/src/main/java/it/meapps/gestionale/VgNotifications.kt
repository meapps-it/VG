package it.meapps.gestionale

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class VgHybridApplication : Application() {
 override fun onCreate() { super.onCreate(); VgPush.initialize(this); VgPush.channel(this) }
}
object VgPush {
 fun channel(context:Context) {
  context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("vg_orders","Nuovi ordini Shop & Parallelo",NotificationManager.IMPORTANCE_HIGH))
 }
 fun initialize(context:Context,config:JSONObject?=null):Boolean = runCatching {
  val prefs=context.getSharedPreferences("vg_push",Context.MODE_PRIVATE)
  val data=config?:JSONObject(prefs.getString("config",null)?:return@runCatching false)
  if(FirebaseApp.getApps(context).isEmpty())FirebaseApp.initializeApp(context,FirebaseOptions.Builder().setApplicationId(data.getString("applicationId")).setApiKey(data.getString("apiKey")).setProjectId(data.getString("projectId")).setGcmSenderId(data.getString("senderId")).build())
  if(config!=null)prefs.edit().putString("config",config.toString()).apply()
  true
 }.getOrDefault(false)
 suspend fun register(context:Context,token:String) {
  val api=SupabaseApi(context);val session=api.restoreSession()?:return
  request(session.accessToken,"register",JSONObject().put("token",token))
 }
 fun request(jwt:String,action:String,body:JSONObject?=null):JSONObject {
  val builder=Request.Builder().url(BuildConfig.SUPABASE_URL+"/functions/v1/vg-shop-push?action="+action).header("Authorization","Bearer "+jwt)
  if(body!=null)builder.post(body.toString().toRequestBody("application/json".toMediaType()))
  return OkHttpClient().newCall(builder.build()).execute().use { response ->
   val result=JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
   if(!response.isSuccessful)error(result.optString("error","Servizio notifiche non disponibile"))
   result
  }
 }
}
class VgMessagingService : FirebaseMessagingService() {
 override fun onNewToken(token:String) {getSharedPreferences("vg_push",MODE_PRIVATE).edit().putString("token",token).apply();CoroutineScope(Dispatchers.IO).launch {runCatching {VgPush.register(applicationContext,token)}}}
 override fun onMessageReceived(message:RemoteMessage) {
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&android.os.Build.VERSION.SDK_INT>=33)return
  VgPush.channel(this)
  val id=message.data["orderId"]?.hashCode()?:message.messageId?.hashCode()?:1
  val intent=Intent(this,VgPwaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
  val pending=PendingIntent.getActivity(this,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(this,"vg_orders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(message.notification?.title?:"Nuovo ordine Shop & Parallelo").setContentText(message.notification?.body?:"È arrivata una nuova richiesta d’ordine.").setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
  getSystemService(NotificationManager::class.java).notify(id,notification)
 }
}
