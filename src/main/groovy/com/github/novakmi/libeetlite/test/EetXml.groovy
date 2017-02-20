/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import groovy.util.logging.Slf4j
import groovy.xml.StreamingMarkupBuilder

import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

@Slf4j
class EetXml {

    static bkpPattern = /^([0-9a-fA-F]{8}-){4}[0-9a-fA-F]{8}$/
    static icPattern = /^CZ[0-9]{8,10}$/
    static idPrefixPattern = /^[0-9a-zA-Z\.,:;\/#\-_ ]/
    static finPattern = /^((0|-?[1-9]\d{0,7})\.\d\d|-0\.(0[1-9]|[1-9]\d))$/
    // should be in alphabetic order - canonicalization
    // parameter map attributes: opt ... optional?
    //                           pattern ... regexp
    static dataFields = ["celk_trzba"      : [opt: 1, pattern: finPattern],
                         "cerp_zuct"       : [opt: 0, pattern: finPattern],
                         "cest_sluz"       : [opt: 0, pattern: finPattern],
                         "dan1"            : [opt: 0, pattern: finPattern],
                         "dan2"            : [opt: 0, pattern: finPattern],
                         "dan3"            : [opt: 0, pattern: finPattern],
                         "dat_trzby"       : [opt: 1],
                         "dic_popl"        : [opt: 1, pattern: icPattern],
                         "dic_poverujiciho": [opt: 0, pattern: icPattern],
                         //"id_pokl"         : [opt: 1, pattern: /^[0-9a-zA-Z\.,:;\/#\-_ ]{1,20}$/],
                         "id_pokl"         : [opt: 1, pattern: /${idPrefixPattern}{1,20}$/],
                         "id_provoz"       : [opt: 1, pattern: /^[1-9][0-9]{0,5}$/],
                         "porad_cis"       : [opt: 1, pattern: /${idPrefixPattern}{1,25}$/],
                         "pouzit_zboz1"    : [opt: 0, pattern: finPattern],
                         "pouzit_zboz2"    : [opt: 0, pattern: finPattern],
                         "pouzit_zboz3"    : [opt: 0, pattern: finPattern],
                         "rezim"           : [opt: 1, pattern: /^[01]$/],
                         "urceno_cerp_zuct": [opt: 0, pattern: finPattern],
                         "zakl_dan1"       : [opt: 0, pattern: finPattern],
                         "zakl_dan2"       : [opt: 0, pattern: finPattern],
                         "zakl_dan3"       : [opt: 0, pattern: finPattern],
                         "zakl_nepodl_dph" : [opt: 0, pattern: finPattern],
    ]

    static String indentXml(def xml, def indent = 4) {
        log.trace "==> indentXml {} indent", xml, indent

        def factory = TransformerFactory.newInstance()
        factory.setAttribute("indent-number", indent);
        Transformer transformer = factory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, 'yes')
        StreamResult result = new StreamResult(new StringWriter())
        transformer.transform(new StreamSource(new ByteArrayInputStream(xml.toString().bytes)), result)
        def res = result.writer.toString()

        log.trace "==> indentXml {}", res
        return res
    }

    // TODO not needed, if body is buuild with builder builder.expandEmptyElements = true
//    static String canonicalizeXml(xml) {
//        log.trace "==> canonicalizeXml {}", xml
//
//        com.sun.org.apache.xml.internal.security.Init.init()
//        def algo = Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
//        Canonicalizer canon = Canonicalizer.getInstance(algo)
//        def canonXmlBytes = canon.canonicalize(xml.toString().getBytes("UTF-8"))
//        def canonXmlString = new String(canonXmlBytes)
//
//        log.trace "<== canonicalizeXml {}", canonXmlString
//        return canonXmlString
//    }

    static makeDigest(body) {
        log.debug "==> makeDigest {}", body

        def canonBody = body //EetXml.canonicalizeXml(body) //not needed  if  builder.expandEmptyElements = true
        def digestValue = EetUtil.makeDigestValue(canonBody)
        def ret = {
            "ds:DigestValue"(digestValue)
        }

        log.debug "<== makeDigest"
        return ret
    }

    static makeSignedInfo(id, body) {
        log.debug "==> makeSignedInfo {}", body
        def ret = {
            "ds:SignedInfo"("xmlns:ds": "http://www.w3.org/2000/09/xmldsig#", "xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/") {
                "ds:CanonicalizationMethod"(Algorithm: "http://www.w3.org/2001/10/xml-exc-c14n#") {
                    "ec:InclusiveNamespaces"("xmlns:ec": "http://www.w3.org/2001/10/xml-exc-c14n#", PrefixList: "soap")
                }
                "ds:SignatureMethod"(Algorithm: "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
                "ds:Reference"(URI: "#${id}") {
                    "ds:Transforms"() {
                        "ds:Transform"(Algorithm: "http://www.w3.org/2001/10/xml-exc-c14n#")
                    }
                    "ds:DigestMethod"(Algorithm: "http://www.w3.org/2001/04/xmlenc#sha256")
                    out << makeDigest(body)
                }
            }
        }
        log.debug "<== makeSignedInfo {}", ret
        return ret
    }

    static makeHeader(config, id, body, uniques) {
        log.debug "==> makeHeader {}", body

        final def keyMap = EetUtil.makeKeyMap(config)
        def binarySecToken = EetUtil.makeSecToken(keyMap)
        def tokenId = "${uniques.tokenId}"

        def builder = new StreamingMarkupBuilder()
        builder.expandEmptyElements = true
        builder.useDoubleQuotes = true
        def signedInfo = builder.bind {
            out << makeSignedInfo(id, body)
        }
        def sigInfo = signedInfo.toString()
        //def sigInfo = EetXml.canonicalizeXml(signedInfo.toString()) //Signature cannot be canonized!! (not valid TODO)!!
        def signatureValue = EetUtil.makeSignatureValue(config, keyMap, sigInfo)

        def retVal = {
            "SOAP-ENV:Header"("xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/") {
                "wsse:Security"("xmlns:wsse": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                        "xmlns:wsu": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
                        "soap:mustUnderstand": "1") {
                    "wsse:BinarySecurityToken"(EncodingType: "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                            ValueType: "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3",
                            "wsu:Id": tokenId, binarySecToken)
                    "ds:Signature"("xmlns:ds": "http://www.w3.org/2000/09/xmldsig#", Id: "${uniques.signatureId}") {
                        out << makeSignedInfo(id, body)
                        "ds:SignatureValue"(signatureValue)
                        "ds:KeyInfo"(Id: "${uniques.keyId}") {
                            "wsse:SecurityTokenReference"("xmlns:wsse": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                                    "xmlns:wsu": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
                                    "wsu:Id": "STR-${uniques.referenceId}") {
                                "wsse:Reference"(URI: "#${tokenId}",
                                        ValueType: "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3")
                            }
                        }
                    }
                }
            }
        }

        log.debug "<== makeHeader"
        return retVal

    }

    static checkPatterns(dataMap) {
        log.debug "==> checkPatterns dataMap={}", dataMap
        for (i in dataMap.keySet()) {
            if (dataFields[i].pattern) {
                if (dataMap[i] ==~ dataFields[i].pattern) {
                    log.trace "field ${i} val ${dataMap[i]} matches pattern ${dataFields[i].pattern}"
                } else {
                    log.error "field ${i} val ${dataMap[i]} does not match pattern ${dataFields[i].pattern} !"
                    // TODO
                }
            }
        }
        log.debug "<== checkPatterns"
    }

    static makeBody(config, id, date, pkpVal, bkpVal) {
        log.debug "==> makeBody id={}", id

        def uuid = UUID.randomUUID()
        def dataMap = [:]
        for (i in dataFields.keySet()) {
            if (config[i]) {
                dataMap[i] = config[i]
            } else {
                if (dataFields[i].opt) {
                    log.error "Missing field ${i}!"
                    // TODO error
                } else {
                    log.debug "Skipping optional field ${i}"
                }
            }
        }

        log.debug("dataMap ${dataMap}")
        checkPatterns(dataMap)

        def retVal = {
            "soap:Body"("xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/", "xmlns:wsu": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
                    "wsu:Id": "${id}", "xml:id": "${id}") {
                Trzba(xmlns: "http://fs.mfcr.cz/eet/schema/v3") {
                    Hlavicka(dat_odesl: date, overeni: config.overeni, prvni_zaslani: config.prvni_zaslani, uuid_zpravy: uuid)
                    Data(dataMap)
                    KontrolniKody {
                        pkp(cipher: "RSA2048", digest: "SHA256", encoding: "base64", pkpVal)
                        bkp(digest: "SHA1", encoding: "base16", bkpVal)
                    }
                }
            }
        }

        log.debug "<== makeBody"
        return retVal
    }

    /**
     *
     * @param config
     * @return map with xml and bkp [xml: ..., bkp: ....]
     */
    static def makeMsg(config) {
        log.debug "==> makeMsg"

        def retVal = [:]
        retVal.bkp = null
        retVal.xml = null

        def uniques = [
                bodyId     : "BodyId+${EetUtil.getUnique()}",
                tokenId    : "TokenId+${EetUtil.getUnique()}",
                signatureId: "SigId+${EetUtil.getUnique()}",
                keyId      : "KeyId+${EetUtil.getUnique()}",
                referenceId: "RefId+${EetUtil.getUnique()}",
        ]

        def id = "${uniques.bodyId}"
        def builder = new StreamingMarkupBuilder()
        builder.useDoubleQuotes = true
        builder.expandEmptyElements = true

        def pkpValText = EetUtil.makePkp(config)
        def pkpVal = EetUtil.toBase64(pkpValText)
        def bkpVal = EetUtil.makeBkp(pkpValText)

        log.debug "bkpVal ()", bkpVal
        if (bkpVal ==~ bkpPattern) {
            log.trace "BKP ${bkpVal} matches pattern ${bkpPattern}"
        } else {
            log.error "BKP ${bkpVal} does not match pattern ${bkpPattern} pattern!"
            //TODO
        }
        retVal.bkp = bkpVal
        retVal.pkp = pkpVal

        def final bodyClosure = makeBody(config, id, EetUtil.getDateUtc(), pkpVal, bkpVal)
        def body = builder.bind {
            out << bodyClosure
        }

        retVal.xml = builder.bind {
            "soap:Envelope"("xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/") {
                out << makeHeader(config, id, body.toString(), uniques)
                out << bodyClosure
            }
        }

        log.debug "xml indented: {}", indentXml(retVal.xml, 4)
        log.debug "xml {}", retVal.xml
        log.debug "bkp {}", retVal.bkp
        log.debug "pkp {}", retVal.pkp
        log.debug "<== makeMsg"
        return retVal
    }

    static processResponse(response) {
        log.debug "==> processResponse {}", response
        def ret
        def envelope = new XmlParser().parseText(response)
        def potvrzeni = envelope.'**'.find { node -> node.@fik } //find node with 'fik' attribute
        ret = potvrzeni.@fik
        log.debug "<== processResponse 2 ret {}", ret
        return ret
    }
}
