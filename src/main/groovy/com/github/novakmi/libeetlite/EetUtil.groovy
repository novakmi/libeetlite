/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite

import groovy.util.logging.Slf4j
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import java.security.KeyStore
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Slf4j
class EetUtil {

    static String sendSOAPRequest(String url, String xmlPayload) {
        log.trace "==> sendSOAPRequest url={}", url

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection()
        try {
            connection.requestMethod = 'POST'
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', 'text/xml; charset=utf-8')

            connection.outputStream.withWriter('UTF-8') { writer ->
                writer.write(xmlPayload)
            }

            InputStream responseStream = connection.responseCode >= 400
                    ? connection.errorStream
                    : connection.inputStream
            return responseStream?.withCloseable { it.getText('UTF-8') } ?: ''
        } finally {
            connection.disconnect()
        }
    }

    static String canonicalizeXml(String xml) {
        log.trace "==> canonicalizeXml {}", xml

        if (xml == null) {
            throw new IllegalArgumentException("XML to canonicalize must not be null")
        }

        Init.init()
        Canonicalizer canonicalizer = Canonicalizer.getInstance(
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS)
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        Element documentElement = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes("UTF-8"))).documentElement
        String inclusivePrefixes = documentElement
                .getElementsByTagNameNS("http://www.w3.org/2001/10/xml-exc-c14n#", "InclusiveNamespaces")
                .collect { it.getAttribute("PrefixList") }
                .find { it }
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        canonicalizer.canonicalizeSubtree(documentElement, inclusivePrefixes ?: "", output)
        byte[] canonicalXml = output.toByteArray()
        String retVal = new String(canonicalXml, "UTF-8")

        log.trace "<== canonicalizeXml {}", retVal
        return retVal
    }

    static def getUnique() {
        log.trace  "==> getUnique"

        def ret = UUID.randomUUID()

        log.trace "<== getUnique {}", ret
        return ret
    }

    /**
     * Convert current date into UTC String representation
     * @return
     */
    static def getDateUtc() {
        log.trace "==> getDateUtc"

        def format = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
        OffsetDateTime dt = OffsetDateTime.ofInstant(Instant.now(),ZoneId.of("UTC"))
        def ret = dt.format(formatter)

        log.trace "<== getDateUtc ret {}", ret
        return ret
    }

    private static byte[] hexStringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2]
        for (int i = 0; i < b.length; i++) {
            int index = i * 2
            int v = Integer.parseInt(s.substring(index, index + 2), 16)
            b[i] = (byte) v
        }
        return b
    }

    private static String byteArrayToHexString(byte[] b) {
        StringBuffer sb = new StringBuffer(b.length * 2)
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff
            if (v < 16) {
                sb.append('0')
            }
            sb.append(Integer.toHexString(v))
        }
        return sb.toString().toUpperCase()
    }

    // encrupt, decrypt basewd on http://narayanatutorial.com/java-tutorial/how-to-encrypt-and-decrypt-password-in-java
    /**
     * Encrypt string (AES)
     * @param str string to encrypt
     * @param key key (string)
     * @param prefix if not empty, prefix returned string with this value
     * @return encrypted string with prefixed value
     */
    static String encrypt(str, key, prefix = "") {
        log.trace("==> encrypt")

        final String AES = "AES"
        byte[] bytekey = hexStringToByteArray(key)
        SecretKeySpec sks = new SecretKeySpec(bytekey, AES)
        Cipher cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters())
        byte[] encrypted = cipher.doFinal(str.getBytes())
        String encryptedStr = byteArrayToHexString(encrypted)
        def retVal = "${prefix}${encryptedStr}"

        log.trace("==> encrypt retVal=n/a")
        return retVal
    }
    /**
     * Decrypt string (AES)
     * @param encstr  encrypted string
     * @param key     key (string)
     * @param prefix  if not empty, prefix to remove from encstr before decription
     * @return decrypted string
     */
    static String decrypt(encstr, key, prefix="") {
        log.trace("==> decrypt encstr=n/a")

        def lenPref = prefix.length()
        final String AES = "AES"
        String retVal
        if (encstr.length() > lenPref && encstr.startsWith(prefix)) {
            encstr = encstr[prefix.length()..-1]
            byte[] bytekey = hexStringToByteArray(key)
            SecretKeySpec sks = new SecretKeySpec(bytekey, AES)
            Cipher cipher = Cipher.getInstance(AES)
            cipher.init(Cipher.DECRYPT_MODE, sks)
            byte[] decrypted = cipher.doFinal(hexStringToByteArray(encstr))
            retVal = new String(decrypted)
        } else {
            log.trace("Not encrypted? Returning same string")
            retVal = encstr
        }

        log.trace("==> decrypt retVal=n/a")
        return retVal
    }

    /**
     * Convert date string to ISO date string format for CET/CESTS timezone only!!!
     * @param date      date string, e.g. "2017-02-28 23:13"
     * @param dateFormatter  date formatter corresponding to date pattern and locale (SimpleDateFormat)
     * @return  date as string in iso format, e.g. "2017-02-28T23:13:00+01:00"
     */
    static String dateToIso(String date, dateFormatter) {
        log.trace("==> dateToIso date={}", date)
        String isoDate = null
        if (date && date != "") {
            Date d = dateFormatter.parse(date)
            OffsetDateTime dt = OffsetDateTime.ofInstant(d.toInstant(), ZoneId.of("Europe/Prague"))
            isoDate = dt.format(DateTimeFormatter.ISO_DATE_TIME)
        } else {
            log.warn("dateToIso date={} !", date)
        }
        log.trace("<== dateToIso isoDate={}", isoDate)
        return isoDate
    }

    /**
     * Covert ISO date string to date string
     * @param isoDate  date in iso format  e.g. "2017-02-28T23:13:00+01:00"
     * @param dateFormatter  formatter corresponding to date pattern and locale (SimpleDateFormat)
     * @return dat string,  e.g. "2017-02-28 23:13"
     */
    static String isoToDate(isoDate, dateFormatter) {
        log.trace("==> isoToDate isoDate={}", isoDate)
        String date = null
        if (isoDate && isoDate != "") {
            OffsetDateTime dt = OffsetDateTime.parse(isoDate)
            date = dateFormatter.format(new Date(dt.toInstant().toEpochMilli()))
        } else {
            log.warn("isoToDate isoDate={} !", isoDate)
        }
        log.trace("<== isoToDate date={}", date)
        return date
    }

    /**
     * Get current time in iso format
     * @return  current time in iso format
     */
    static String nowToIso() {
        log.trace("==> nowToIso isoDate={}")
        OffsetDateTime dt = OffsetDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS),
                ZoneId.of("Europe/Prague"))
        def isoDate = dt.format(DateTimeFormatter.ISO_DATE_TIME)
        log.trace("nowToIso={}", isoDate)
        return isoDate
    }

    /**
     * Find out if config has "overeni"
     * @param config
     * @return true or false
     */
    static boolean isOvereni(config) {
        log.trace "==> isOvereni config.overeni={}", config.overeni
        def retVal = config.overeni != "0"
        log.trace "<== isOvereni retVal={}", retVal
        return retVal
    }

    /**
     * Fix response for "overeni" - remove error caused by "overeni" and set "overeni_ok"
     * @param resp
     */
    static void fixOvereniResponse(resp) {
        log.trace "==> fixOvereniResponse resp={}", resp
        if (resp.errors.size() == 1) { //check if error code 0 (overeni OK)
            if (resp.errors[0].first == "0") {
                log.trace "Found 'overeni ok' code 0"
                resp.errors = []
                resp.failed = false
                resp.overeni_ok = true
            }
        }
        log.trace "<== fixOvereniResponse resp={}", resp
    }

    /**
     * http://stackoverflow.com/questions/19743851/base64-java-encode-and-decode-a-string
     * (In java 1.8 swe can also use
     *    Base64.getEncoder().withoutPadding().encodeToString(someByteArray);
     * )
     * @param bytes
     * @return
     */
    static def toBase64(bytes) {
        log.trace "==> toBase64"

        //def ret = new String(DatatypeConverter.printBase64Binary(bytes)); //before JDK 8
        def ret = Base64.getEncoder().encodeToString(bytes) //since JDK 8

        log.trace "<== toBase64 {}", ret
        return ret
    }

    static def makeKeyMap(config) {
        log.trace "==> makeKeyMap"

        final KeyStore keystore = KeyStore.getInstance("pkcs12")
        keystore.load(config.cert_popl, config.cert_pass.toCharArray())
        def al = null
        def aliases = keystore.aliases()
        if (aliases.hasMoreElements()) {
            al = aliases.nextElement()
            log.trace "Client alias {}", al
        } else {
            def ex = new Exception("Certificate {} alias not found!", config.cert_popl)
            log.error "Failed to find certificate!", ex
        }
        def keyMap =  [keystore: keystore, alias: al]

        log.trace "<== makeKeyMap {}", keyMap
        return keyMap
    }

    static def makeSecToken(keyMap) {
        log.trace  "==> makeSecToken"

        X509Certificate certificate = (X509Certificate) keyMap.keystore.getCertificate(keyMap.alias)
        String token = toBase64(certificate.getEncoded())

        log.trace  "<== makeSecToken {}", token
        return token
    }

    static def makeDigestValue(body) {
        log.trace "==> makeDigestValue {}", body

        final java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256")
        d.reset()
        d.update(body.getBytes("UTF-8"))
        final byte[] bytes = d.digest()
        log.trace("bytes {}", bytes)
        def ret = toBase64(bytes)

        log.trace "<== makeDigestValue {}", ret
        return ret
    }

    static def makeSignatureValue(config, keyMap, signedInfo) {
        log.trace  "==> makeSignatureValue {}", signedInfo

        final Signature signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyMap.keystore.getKey(keyMap.alias, config.cert_pass.toCharArray()))
        signature.update(signedInfo.getBytes("UTF-8"))
        def ret = toBase64(signature.sign())

        log.trace "<== makeSignatureValue {}", ret
        return ret
    }

    static def bytesToHex(bytes) {
        log.trace "==> bytesToHex bytes={}", bytes

        StringBuffer sb = new StringBuffer()
        for (byte b : bytes) { sb.append(String.format("%02X", b)) }
        def ret = sb.toString()

        log.trace "<== bytesToHex ret {}", ret
        return ret
    }

    /**
     * Validates an XML string against a given XSD schema stream.
     *
     * @param xmlString The XML content to validate
     * @param xsdStream InputStream of the XSD schema
     * @return List of error/warning messages. Empty list if XML is valid.
     */
    static List<String> validateXml(String xmlString, InputStream xsdStream) {
        log.trace "==> validateXml xmlString={}, xsdStream={}", xmlString, xsdStream
        List<String> retVal = []

        if (!xsdStream) {
            retVal.add("VALIDATION_FAILED: XSD InputStream is null".toString())
        } else {
            if (!xmlString) {
                retVal.add("VALIDATION_FAILED: XML string is null or empty".toString())
            } else {

                // 1. Sanitize XML payload (xmlString)
                String cleanXml = xmlString.replace('\uFEFF', '').trim()

                if (cleanXml.contains('<Trzba') && cleanXml.contains('</Trzba>')) {
                    int startIdx = cleanXml.indexOf('<Trzba')
                    int endIdx = cleanXml.indexOf('</Trzba>') + '</Trzba>'.length()
                    cleanXml = cleanXml.substring(startIdx, endIdx)
                } else {
                    int firstTagIndex = cleanXml.indexOf('<')
                    if (firstTagIndex > 0) {
                        cleanXml = cleanXml.substring(firstTagIndex)
                    }
                }
                log.info("XML to validate: {}", cleanXml)

                try {
                    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

                    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all")
                    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")

                    def schema = factory.newSchema(new StreamSource(xsdStream))
                    def validator = schema.newValidator()

                    // Custom error handler to collect validation issues
                    validator.errorHandler = [
                        warning   : { SAXException e -> retVal.add("WARNING: ${e.message}".toString()) },
                        error     : { SAXException e -> retVal.add("ERROR: ${e.message}".toString()) },
                        fatalError: { SAXException e -> retVal.add("FATAL: ${e.message}".toString()) }
                    ] as ErrorHandler

                    validator.validate(new StreamSource(new StringReader(cleanXml)))
                } catch (Exception e) {
                    retVal.add("VALIDATION_FAILED: ${e.message}".toString())
                }
            }
        }
        log.trace "<== validateXml retVal={}", retVal
        return retVal
    }

    /**
    * Validates XML string against an XSD schema if the schema resource exists.
    *
    * @param xmlString The XML content to validate
    * @param xsdResourcePath Path to the XSD schema resource (e.g., "/schema/EETXMLSchema.xsd")
    * @return List of validation errors. Returns an empty list if XML is valid or if schema is missing (with a warning).
    */
    static List<String> validateXmlIfSchemaExists(String xmlString, String xsdResourcePath) {
        log.trace "==> validateXmlIfSchemaExists xmlString={}, xsdResourcePath={}", xmlString, xsdResourcePath
        InputStream xsdStream = EetUtil.class.getResourceAsStream(xsdResourcePath)
        def retVal = []

        if (xsdStream == null) {
            log.warn("XSD schema resource not found at '{}'. Skipping validation.", xsdResourcePath)
        } else {

            try {
                log.debug("XSD schema found at '{}', starting XML validation...", xsdResourcePath)
                retVal = validateXml(xmlString, xsdStream)
            } finally {
                xsdStream.close()
            }
            log.trace "<== validateXmlIfSchemaExists retVal={}", retVal
        }
        return retVal
    }

}
