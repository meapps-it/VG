import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.internal.apk.AndroidBinXmlParser;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipFile;

// Credentials are passed through private files/environment, never command arguments or logs.
public class VgDeliverySigner {
 public static void main(String[] args) throws Exception {
  var input=new File(args[0]);var output=new File(args[1]);
  try(var zip=new ZipFile(input)){
   var xml=new AndroidBinXmlParser(ByteBuffer.wrap(zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes()));
   String packageName=null;
   while(xml.next()!=AndroidBinXmlParser.EVENT_END_DOCUMENT){
    if(xml.getEventType()==AndroidBinXmlParser.EVENT_START_ELEMENT&&xml.getName().equals("manifest")){
     for(int i=0;i<xml.getAttributeCount();i++)if(xml.getAttributeName(i).equals("package"))packageName=xml.getAttributeStringValue(i);
     break;
    }
   }
   if(!"com.vg.gestionale.seconda".equals(packageName))throw new SecurityException("Unexpected Android package");
  }
  char[] password=System.getenv("VG_DELIVERY_PASSWORD").toCharArray();
  var store=KeyStore.getInstance("PKCS12");try(var stream=new FileInputStream(args[2])){store.load(stream,password);}
  var cert=(X509Certificate)store.getCertificate(args[3]);
  var fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
  if(!fingerprint.equals(args[4]))throw new SecurityException("Private delivery key identity mismatch");
  var signer=new ApkSigner.SignerConfig.Builder("VG",(PrivateKey)store.getKey(args[3],password),List.of(cert)).build();
  new ApkSigner.Builder(List.of(signer)).setInputApk(input).setOutputApk(output)
   .setMinSdkVersion(26).setV1SigningEnabled(false).setV2SigningEnabled(true)
   .setV3SigningEnabled(true).setOtherSignersSignaturesPreserved(false).setAlignmentPreserved(true).build().sign();
  Arrays.fill(password,'\0');
  var result=new ApkVerifier.Builder(output).build().verify();
  if(!result.isVerified()||result.getSignerCertificates().size()!=1)throw new SecurityException("Delivery APK signature invalid");
  var actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getSignerCertificates().get(0).getEncoded()));
  if(!actual.equals(fingerprint))throw new SecurityException("Final APK identity mismatch");
  System.out.println("PASS private delivery signature SHA256="+actual);
 }
}
