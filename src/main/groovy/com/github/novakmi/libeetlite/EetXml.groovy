/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite

import groovy.util.logging.Slf4j
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlParser

import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

@Slf4j
class EetXml {

    static eicPattern = /^CZ[0-9]{8,10}$/
    static idPrefixPattern = /^[0-9a-zA-Z\.,:;\/#\-_ ]/
    static finPattern = /^((0|-?[1-9]\d{0,7})\.\d\d|-0\.(0[1-9]|[1-9]\d))$/
    // should be in alphabetic order - canonicalization
    // parameter map attributes: opt ... optional?
    //                           pattern ... regexp
    static dataFields = ["celk_trzba"      : [opt: 1, pattern: finPattern],
                         "cerp_zuct"       : [opt: 0, pattern: finPattern],
                         "dat_trzby"       : [opt: 1],
                         "eic_popl"        : [opt: 1, pattern: eicPattern],
                         "eic_poverujiciho": [opt: 0, pattern: eicPattern],
                         "id_jednotky"     : [opt: 1, pattern: /^[1-9][0-9]{0,8}$/],
                         "id_pokl"         : [opt: 1, pattern: /^[0-9a-zA-Z\.,:;\/#\-_ ]{1,20}$/],
                         "porad_cis"       : [opt: 1, pattern: /^[0-9a-zA-Z\.,:;\/#\-_ ]{1,25}$/],
                         "povereni_vice_popl": [opt: 0],
                         "urceno_cerp_zuct": [opt: 0, pattern: finPattern]]

    static String indentXml(def xml, def indent = 4) {
        log.trace "==> indentXml {} indent", xml, indent

        def res = xml?.toString()
        if (xml != null) {
            try {
                def factory = TransformerFactory.newInstance()
                factory.setAttribute("indent-number", indent)
                Transformer transformer = factory.newTransformer()
                transformer.setOutputProperty(OutputKeys.INDENT, 'yes')
                StreamResult result = new StreamResult(new StringWriter())
                transformer.transform(new StreamSource(new ByteArrayInputStream(xml.toString().bytes)), result)
                res = result.writer.toString()
            } catch (Exception e) {
                log.warn("Text nelze formatovat jako XML: {}", e.message)
            }
        }

        log.trace "<== indentXml {}", res
        return res
    }

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

    static makeHeader(config, id, body, uniques, keyMap) {
        log.debug "==> makeHeader {}", body

        def binarySecToken = EetUtil.makeSecToken(keyMap)
        def tokenId = "${uniques.tokenId}"

        // 1. Vygenerujeme nekanonizovaný SignedInfo jako XML objekt/string
        def builder = new StreamingMarkupBuilder()
        builder.expandEmptyElements = true
        builder.useDoubleQuotes = true
        def rawSignedInfo = builder.bind {
            out << makeSignedInfo(id, body)
        }.toString()

        // 2. Kanonikalizujeme SignedInfo pomocí C14N (povinné pro XML Signature)
        def canonicalSignedInfo = EetUtil.canonicalizeXml(rawSignedInfo)

        // 3. Podpis spočítáme z KANONIKALIZOVANÉHO řetězce
        def signatureValue = EetUtil.makeSignatureValue(config, keyMap, canonicalSignedInfo)
        //def signatureValue = EetUtil.makeSignatureValue(config, keyMap, rawSignedInfo)
        def retVal = {
            "SOAP-ENV:Header"("xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/") {
                "wsse:Security"("xmlns:wsse": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                    "xmlns:wsu": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
                    "soap:mustUnderstand": "1") {
                    "wsse:BinarySecurityToken"(EncodingType: "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary",
                        ValueType: "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-x509-token-profile-1.0#X509v3",
                        "wsu:Id": tokenId, binarySecToken)
                    "ds:Signature"("xmlns:ds": "http://www.w3.org/2000/09/xmldsig#", Id: "${uniques.signatureId}") {

                        // Do výsledného headeru vložíme přímo SignedInfo
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

    static makeBody(config, id, date) {
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
            // Odstraněn neplatný atribut xml:id
            "soap:Body"("xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:wsu": "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd",
                "wsu:Id": "${id}") {
                "Trzba"("xmlns": "http://fs.gov.cz/eet/schema/v4") {
                    "Hlavicka"(dat_odesl: date, overeni: config.overeni, prvni_zaslani: config.prvni_zaslani, uuid_zpravy: uuid)
                    "Data"(dataMap)
                }
            }
        }

        // DŮLEŽITÉ: Nastavení řešení metod na delegate builderu
        retVal.resolveStrategy = Closure.DELEGATE_FIRST

        log.debug "<== makeBody"
        return retVal
    }

    /**
     *
     * @param config
     * @return map with attributes
     *   failed ... did processing fail (see errors)
     *   xml ... xml to send
     *   warnings ... warning messages (if any)  - array of Tuple2 objects (kod varovani, text)
     *   errors ... error messages (if any, failed is set to true) - array of Tuple2 objects (kod chyby - String, text)
     */
    static def makeMsg(config) {
        log.debug "==> makeMsg"

        def retVal = [:]
        retVal.xml = null
        retVal.failed = false
        retVal.warnings = []
        retVal.errors = []

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
        def keyMap = EetUtil.makeKeyMap(config)

        // 1. Sestavení těla zprávy jako řetězce pro výpočet podpisu
        def bodyClosure = makeBody(config, id, EetUtil.getDateUtc())
        def bodyXml = builder.bind {
            bodyClosure.delegate = delegate
            bodyClosure()
        }.toString()

        // 2. Vytvoření hlavičky s podepsaným tělem
        def headerClosure = makeHeader(config, id, bodyXml, uniques, keyMap)

        // 3. Sestavení finální SOAP obálky vložení již hotového bodyXml bez re-evaluace uzávěr
        def finalXml = builder.bind {
            "soap:Envelope"("xmlns:soap": "http://schemas.xmlsoap.org/soap/envelope/") {
                headerClosure.delegate = delegate
                headerClosure()

                mkp.yieldUnescaped(bodyXml)
            }
        }

        retVal.xml = finalXml.toString()

        log.debug "failed {}", retVal.failed
        log.debug "xml indented: {}", indentXml(retVal.xml, 4)
        log.debug "xml {}", retVal.xml
        log.trace "errors {}", retVal.errors
        log.trace "warnings {}", retVal.warnings
        log.debug "<== makeMsg"
        return retVal
    }

    /**
     * Process response XML and extract values
     * @param responseXml
     * @return map with attributes
     *   failed ... did processing fail (see errors)
     *   pok ... value of POK
     *   warnings ... warnings (if any) - array of Tuple2 objects (kod varovani, text)
     *   errors ... error messages (if any failed is set to true) - array of Tuple2 objects (kod chyby - String, text)
     */
    static processResponse(responseXml) {
        log.debug "==> processResponse {}", responseXml
        def retVal = [:]
        retVal.pok = null
        retVal.failed = false
        retVal.overeni_ok = false
        retVal.warnings = []
        retVal.errors = []  // array of Tuple2 objects (kod, text)

        log.debug("Raw responseXml: {}", responseXml)

        if (responseXml == null || responseXml.trim().startsWith("<!DOCTYPE html") || responseXml.trim().startsWith("<html")) {
            log.error("Server nevrátil SOAP XML, ale HTML stránku: {}", responseXml)
            return [failed: true, errors: [[-1, "Server vrátil HTML místo SOAP XML"]]]
        }

        // 1. Konfigurace a POUŽITÍ parseru
        def parser = new XmlParser(false, false)
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)

        def envelope = parser.parseText(responseXml)
        log.trace("envelope={}", envelope)

        // Robustní vyhodnocení jména uzlu nebo atributu (odstraní prefiks "eet:" i zpracuje QName)
        def extractLocalName = { obj ->
            if (obj == null) return ""
            if (obj instanceof groovy.namespace.QName) return obj.localPart
            String str = obj.toString()
            int idx = str.indexOf(":")
            return idx >= 0 ? str.substring(idx + 1) : str
        }

        // 2. Extrakce Varovani
        def warnings = envelope.'**'.findAll { node ->
            extractLocalName(node.name()) == "Varovani"
        }
        warnings.each { warn ->
            log.trace("warn={}", warn)
            def textVal = warn.value() ? warn.value()[0].toString() : ""
            retVal.warnings += [new Tuple2(warn.@kod_varov?.toString(), textVal)]
        }

        // 3. Extrakce Chyba
        def errors = envelope.'**'.findAll { node ->
            extractLocalName(node.name()) == "Chyba"
        }
        errors.each { error ->
            log.trace("error={}", error)
            def textVal = error.value() ? error.value()[0].toString() : ""
            retVal.errors += [new Tuple2(error.@kod?.toString(), textVal)]
            retVal.failed = true
        }

        // 4. Extrakce Potvrzeni a POK
        def potvrzeni = envelope.'**'.find { node ->
            extractLocalName(node.name()) == "Potvrzeni"
        }

        // Node.attribute('pok') extracts the raw String attribute cleanly
        if (potvrzeni != null) {
            log.info("Našel jsem Potvrzeni uzel: name={}, attrs={}", potvrzeni.name(), potvrzeni.attributes())
            // Získání atributu 'pok'
            def pokEntry = potvrzeni.attributes().find { k, v ->
                extractLocalName(k) == "pok"
            }

            if (pokEntry != null) {
                retVal.pok = pokEntry.value?.toString()
            }
        } else {
            log.info("Nenalezen uzel Potvrzeni")
        }

        // 5. Vyhodnocení stavu
        if (retVal.pok != null) {
            retVal.failed = false
        } else if (retVal.errors.size() == 1 && retVal.errors[0].first == "0") {
            retVal.errors = []
            retVal.overeni_ok = true
            retVal.failed = false
        } else {
            retVal.failed = true
        }

        log.debug "<== processResponse ret {}", retVal
        return retVal
    }
}
