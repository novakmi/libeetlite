/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite

import groovy.util.logging.Slf4j

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
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

    static def getUnique() {
        log.trace  "==> getUnique"

        def ret = UUID.randomUUID()

        log.trace "<== getUnique {}", ret
        return ret
    }

    /**
     * https://gist.github.com/kdabir/6bfe265d2f3c2f9b438b
     * @return
     */
    static def getDateUtc() {
        log.trace "==> getDateUtc"

        def ret = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))

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
        StringBuffer sb = new StringBuffer(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase();
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
        def retVal = null
        final String AES = "AES"
        byte[] bytekey = hexStringToByteArray(key)
        SecretKeySpec sks = new SecretKeySpec(bytekey, AES)
        Cipher cipher = Cipher.getInstance(AES)
        cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters())
        byte[] encrypted = cipher.doFinal(str.getBytes())
        String encryptedStr = byteArrayToHexString(encrypted)
        retVal = "${prefix}${encryptedStr}"
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
    public static String decrypt(encstr, key, prefix="") {
        log.trace("==> decrypt encstr=n/a")
        def lenPref = prefix.length()
        final String AES = "AES"
        String retVal = null
        if (encstr.length() > lenPref && encstr.startsWith(prefix)) {
            encstr = encstr[prefix.length()..-1]
            byte[] bytekey = hexStringToByteArray(key);
            SecretKeySpec sks = new SecretKeySpec(bytekey, AES);
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, sks);
            byte[] decrypted = cipher.doFinal(hexStringToByteArray(encstr));
            retVal = new String(decrypted);
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
    public static String dateToIso(date, dateFormatter) {
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
    public static String isoToDate(isoDate, dateFormatter) {
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
    public static String nowToIso() {
        log.trace("==> nowToIso isoDate={}")
        OffsetDateTime dt = OffsetDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.SECONDS),
                ZoneId.of("Europe/Prague"))
        def isoDate = dt.format(DateTimeFormatter.ISO_DATE_TIME)
        log.trace("nowToIso={}", isoDate)
        return isoDate
    }

    /**
     * Find out if config has "ZjednodusenyRezim"
     * @param config
     * @return true or false
     */
    public static boolean isZjednodusenyRezim(config) {
        log.trace "==> isZjednodusenyRezim config.rezim={}", config.rezim
        def retVal = config.rezim != "0"
        log.trace "<== isZjednodusenyRezim retVal={}", retVal
        return retVal
    }

    /**
     * Find out if config has "overeni"
     * @param config
     * @return true or false
     */
    public static boolean isOvereni(config) {
        log.trace "==> isOvereni config.overeni={}", config.overeni
        def retVal = config.overeni != "0"
        log.trace "<== isOvereni retVal={}", retVal
        return retVal
    }

    /**
     * Fix response for "overeni" - remove error caused by "overeni" and set "overeni_ok"
     * @param resp
     */
    public static void fixOvereniResponse(resp) {
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

        final KeyStore keystore = KeyStore.getInstance("pkcs12");
        keystore.load(config.cert_popl, config.cert_pass.toCharArray())
        def al
        def aliases = keystore.aliases()
        if (aliases.hasMoreElements()) {
            al = aliases.nextElement();
            log.trace "Client alias {}", al
        } else {
            def ex = new Exception("Certificate {} alias not found!", config.cert_popl)
            log.error ex
        }
        def keyMap =  [keystore: keystore, alias: al]

        log.trace "<== makeKeyMap {}", keyMap
        return keyMap
    }

    static def makeSecToken(keyMap) {
        log.trace  "==> makeSecToken"

        X509Certificate certificate = (X509Certificate) keyMap.keystore.getCertificate(keyMap.alias);
        String token = toBase64(certificate.getEncoded())

        log.trace  "<== makeSecToken {}", token
        return token
    }

    static def makeDigestValue(body) {
        log.trace "==> makeDigestValue {}", body

        final java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256")
        d.reset();
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
        signature.update(signedInfo.getBytes("UTF-8"));
        def ret = toBase64(signature.sign())

        log.trace "<== makeSignatureValue {}", ret
        return ret
    }

    static def bytesToHex(bytes) {
        log.trace "==> bytesToHex bytes={}", bytes

        StringBuffer sb = new StringBuffer()
        for (byte b:bytes) { sb.append(String.format("%02X", b))}
        def ret = sb.toString()

        log.trace "<== bytesToHex ret {}", ret
        return ret
    }

    static def makeBkp(pkpValText) {
        log.trace "==> makeBkp pkpValText={}", pkpValText

        final java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-1")
        d.reset()
        d.update(pkpValText)
        final byte[] bytes = d.digest()
        def hex = bytesToHex(bytes)
        def ret = "${hex[0..7]}-${hex[8..15]}-${hex[16..23]}-${hex[24..31]}-${hex[32..-1]}"

        log.trace "<== makeBkp ret {}", ret
        return ret
    }

    static def makePkp(config, keyMap) {
        log.trace "==> makePkp"

        def ret
        def pkpPlain = "$config.dic_popl|$config.id_provoz|$config.id_pokl|$config.porad_cis|$config.dat_trzby|$config.celk_trzba"
        log.trace "pkpPlain {}", pkpPlain

        log.trace "config.cert_popl {}", config.cert_popl
        //log.trace "config.cert_pass {}", config.cert_pass //comment :-)

        final Signature signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyMap.keystore.getKey(keyMap.alias, config.cert_pass.toCharArray()))
        signature.update(pkpPlain.getBytes("UTF-8"));
        ret = signature.sign()

        log.trace "<== makePkp {}", ret
        return ret
    }
}
