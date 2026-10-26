package it.meapps.gestionale

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Materialize provider streams before handing them to WebView's file input.
 * Gallery/cloud providers may return short-lived URIs or generic MIME types.
 * The private copy has a verified image type, a stable name and readable bytes.
 */
internal object VgPhotoImport {
 fun prepare(context:Context, data:Intent?):Array<Uri> {
  val selected=linkedSetOf<Uri>()
  data?.clipData?.let{clip->for(i in 0 until clip.itemCount) selected.add(clip.getItemAt(i).uri)}
  data?.data?.let(selected::add)
  require(selected.isNotEmpty()){ "Android non ha restituito alcuna foto. Riprova dal selettore File." }
  require(selected.size<=20){ "Seleziona al massimo 20 foto alla volta." }
  val directory=File(context.cacheDir,"article-imports").apply{mkdirs()}
  val created=mutableListOf<File>()
  try {
   return selected.map{uri->
    require(uri.scheme=="content"){ "La foto selezionata non proviene da un archivio Android leggibile." }
    val temporary=File(directory,UUID.randomUUID().toString()+".tmp").also{created.add(it)}
    context.contentResolver.openInputStream(uri)?.use{input->temporary.outputStream().use{input.copyTo(it)}}
      ?:error("Impossibile leggere la foto. Aprila dal selettore File e riprova.")
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(temporary.absolutePath,bounds)
    require(bounds.outWidth>0 && bounds.outHeight>0){ "Formato foto non leggibile: esporta la foto come JPG o PNG." }
    val extension=MimeTypeMap.getSingleton().getExtensionFromMimeType(bounds.outMimeType)
      ?:error("Formato foto non riconosciuto. Usa JPG o PNG.")
    val photo=File(directory,temporary.nameWithoutExtension+"."+extension)
    check(temporary.renameTo(photo)){ "Impossibile preparare la foto per l'anteprima." }
    created.add(photo)
    FileProvider.getUriForFile(context,context.packageName+".files",photo)
   }.toTypedArray()
  } catch(error:Exception){created.forEach{it.delete()};throw error}
 }
}
