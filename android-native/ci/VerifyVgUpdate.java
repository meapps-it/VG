import com.android.apksig.ApkVerifier;
import com.android.apksig.internal.apk.AndroidBinXmlParser;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Check actual APK bytes, not their filenames or Gradle settings. */
public class VerifyVgUpdate {
 record Identity(String packageName,int version,String signer){}
 static Identity read(String path) throws Exception {
  var result=new ApkVerifier.Builder(new File(path)).build().verify();
  if(!result.isVerified()||result.getSignerCertificates().size()!=1)throw new SecurityException("Invalid or ambiguous APK signature");
  String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getSignerCertificates().get(0).getEncoded()));
  String packageName=null;int version=-1;
  try(var zip=new ZipFile(path)){
   var parser=new AndroidBinXmlParser(ByteBuffer.wrap(zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes()));
   while(parser.next()!=AndroidBinXmlParser.EVENT_END_DOCUMENT){
    if(parser.getEventType()==AndroidBinXmlParser.EVENT_START_ELEMENT&&parser.getName().equals("manifest")){
     for(int i=0;i<parser.getAttributeCount();i++){
      var name=parser.getAttributeName(i);if(name.equals("package"))packageName=parser.getAttributeStringValue(i);
      if(name.equals("versionCode"))version=parser.getAttributeIntValue(i);
     }
     break;
    }
   }
  }
  return new Identity(packageName,version,fingerprint);
 }
 public static void main(String[] args) throws Exception {
  var previous=read(args[0]);var next=read(args[1]);
  if(!"com.vg.gestionale.seconda".equals(previous.packageName)||!previous.packageName.equals(next.packageName))throw new SecurityException("Different package: not an update");
  if(!previous.signer.equals(next.signer))throw new SecurityException("Different signing certificate: cannot update this installation; preserve its data before planning a migration");
  if(next.version<=previous.version)throw new SecurityException("Version code must increase");
  System.out.println("PASS compatible VG update: versionCode "+previous.version+" -> "+next.version+", same package and signing certificate");
 }
}
