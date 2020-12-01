/**
 * (c) Copyright IBM Corporation 2020.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */


import org.codehaus.groovy.util.StringUtil

import javax.mail.internet.*;
import javax.mail.*
import com.urbancode.air.AirPluginTool;
import com.urbancode.ud.client.SystemClient


String SMTP_EXAMPLE_COM = "smtp.example.com"
String SEND_ADDRESS_EXAMPLE = "sender@example.com"

def apTool = new AirPluginTool(this.args[0], this.args[1]);

// get the step properties
def props = apTool.getStepProperties();


// get the properties from the step definition
def toAddress = props['toList'];
def subject = props['subject'];
def message = props['message'];
def attach = props['attachment'];

// define the properties we will get from the system configuration
def host = props['host'].trim();
def port = props['port'].trim();
def secure = props['secure'];
def fromAddress = props['fromAddress'].trim();
def username = props['username'].trim();
def password = props['password'];


// create the rest client and get the system configuration
com.urbancode.air.XTrustProvider.install();

if (!host || !port || secure == "none" || !fromAddress) {
    println "[Ok] Retrieving Host, Port, TLS Security and Sender Email Address from the Deploy server's General Settings."
    // get the user, password, and weburl needed to create a rest client
    def udUser = apTool.getAuthTokenUsername();
    def udPass = apTool.getAuthToken();
    def weburl = System.getenv("AH_WEB_URL");

    def client = new SystemClient(new URI(weburl), udUser, udPass);
    def values = client.getSystemConfiguration();

    // find the system configuration properties we are interested in
    values.keys().each() { key ->
    	if (key == "deployMailHost") {
    		host = values.optString(key);
    	}
    	if (key == "deployMailPort") {
    		port = values.optString(key);
    	}
    	if (key == "deployMailSecure") {
    		secure = values.optString(key);
    	}
    	if (key == "deployMailSender") {
    		fromAddress = values.optString(key);
    	}
    }
}

if (host == SMTP_EXAMPLE_COM || fromAddress == SEND_ADDRESS_EXAMPLE) {
    throw new RuntimeException("[Error] Mail Server Settings have not been set. " +
        "Confirm the settings have been configured in IBM UrbanCode Deploy's General Settings.")
}
//tokenize out the recipients in case they came in as a list
StringTokenizer tok = new StringTokenizer(toAddress,",");
ArrayList emailTos = new ArrayList();
while(tok.hasMoreElements()){
  emailTos.add(new InternetAddress(tok.nextElement().toString()));
}

// create a new mail session and message
Properties mprops = new Properties();
mprops.put("mail.smtp.host", host);
mprops.put("mail.smtp.port", port);
mprops.put("mail.smtp.starttls.enable", secure);
Session lSession;
if (username && password) {
    mprops.put("mail.smtp.auth", "true");
    lSession = Session.getInstance(mprops,
        new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
} else {
    lSession = Session.getDefaultInstance(mprops, null);
}

String errorFile


try {
    MimeMessage msg = new MimeMessage(lSession);

    // populate the to, from, subject, and text of the message
    InternetAddress[] to = new InternetAddress[emailTos.size()];
    to = (InternetAddress[]) emailTos.toArray(to);
    msg.setRecipients(MimeMessage.RecipientType.TO,to);
    msg.setFrom(new InternetAddress(fromAddress));
    msg.setSubject(subject);
//    msg.setText(message);


if (attach.equals(null)) {
    msg.setContent(message, "text/plain")
   // msg.setContent(message, "text/html")
} else {
//tokenize out the attachment file names
    StringTokenizer tokenFiles = new StringTokenizer(attach,",")
    String actualPath
    String myDir
    String myExp1
    String myExp2
    def expression1
    def expression2
    def attachTemp
    def preexp
    boolean  attachPattern

    BodyPart messageBodyPart = new MimeBodyPart()
    messageBodyPart.setContent(message,"text/plain")
   // messageBodyPart.setContent(message,"text/html")
    Multipart multipart = new MimeMultipart()
    multipart.addBodyPart(messageBodyPart)

    while(tokenFiles.hasMoreTokens()) {
        attachTemp =  tokenFiles.nextToken()
        attachTemp = attachTemp.trim()
        if (attachTemp.isEmpty())
            continue;

        expression1 = ""
        expression2 =""
        errorFile  = attachTemp

        attachPattern = false
        if (attachTemp.contains("*.")) {
            def modifiedPath = attachTemp.replaceAll("\\*", "asterisk")
            def canonicalPath = new File(modifiedPath).getCanonicalFile()
            myDir = new File(canonicalPath.getParent())
            def tempDir = new File(myDir)
            if (!tempDir.canRead()) {
                errorFile = myDir
                println("Warning: File not attached. Either file/directory does not exist or insufficient permission for " +  errorFile)
                 continue
            }
            def asteriskFile = canonicalPath.getName()
            def myFile = asteriskFile.replaceAll( "asterisk", "\\*")
            attachPattern = true
            (expression1, expression2) = myFile.tokenize(".")
            myExp1 = expression1
            myExp2 = expression2
            expression1 =  (expression1 != "*") ? expression1 : ""
            expression2 =  (expression1 != "*") ? expression2 : ""
        }

        if (attachPattern == true) {
            preexp = "${expression1}.*.${expression2}.*"
            def count = 0
            new File(myDir).eachFileMatch(~/${preexp}/) { file ->

                errorFile = file.getAbsolutePath()
                if (file.isDirectory()) {
                    println("Warning: File not attached. " + errorFile + " is a directory.")
                }
                else if (!file.canRead()) {
                    println("Warning: File not attached. Either file/directory does not exist or insufficient permission for " +  errorFile)
                } else if (file.isFile()) {
                    actualPath = file.getAbsoluteFile()
                    errorFile = actualPath
                    messageBodyPart = new MimeBodyPart()
                    messageBodyPart.attachFile(actualPath)
                    multipart.addBodyPart(messageBodyPart)
                    count++
                }
            }
            if (count == 0) {
                println("Warning: File not attached. No file meeting the criteria " + myExp1 + "." + myExp2 + " found in " + myDir)
            }
        }
        else {
              actualPath = new File(attachTemp).getCanonicalFile()
              def tempFile = new File(actualPath)
              if (tempFile.isDirectory()) {
                  errorFile = actualPath
                  println("Warning: File not attached. " + errorFile + " is a directory.")
              }
              else if (!tempFile.canRead()) {
                  errorFile = tempFile.getAbsolutePath()
                  println("Warning: File not attached. Either file/directory does not exist or insufficient permission for " +  errorFile)
              }
              else {
                  messageBodyPart = new MimeBodyPart()
                  messageBodyPart.attachFile(actualPath)
                  multipart.addBodyPart(messageBodyPart)
              }
          }
    }
    // Send the complete message parts
     msg.setContent(multipart)
}
    // send the message
    Transport.send(msg)

}  catch (SecurityException ex) {
    println("Warning: File not attached. Either file/directory does not exist or insufficient permission for " +  errorFile)
}  catch (FileNotFoundException ex) {
    println("Warning: File not attached. " + errorFile + " not found")
}  catch (IOException ex) {
    println("Warning: File not attached. I/O Exception occurred for file " + errorFile)
}  catch (MessagingException ex) {
    println "Error: Email not sent: Message processing error: " + ex.getMessage()
    System.exit(1)
}  catch (Exception ex) {
    println "Error: Email not sent: Error occurred: " + ex.getMessage()
    System.exit(1)
}
println "Email(s) sent!"
