/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import com.github.novakmi.libeetlite.EetUtil
import com.github.novakmi.libeetlite.EetXml
import groovy.util.logging.Slf4j
import org.testng.Assert
import org.testng.annotations.Test

import java.util.regex.Matcher

@Slf4j
class EetliteXmlTest {

        private getTestConfig() {
                log.trace('==> getTestConfig')

                def trzba_var = [
                    porad_cis : "0/6460/ZQ42",
                    dat_trzby : EetUtil.nowToIso(),
                    celk_trzba: "7896.00",
                ]

                def trzba_fix = [
                    eic_popl   : "CZ00000019",
                    id_jednotky: "123",
                    id_pokl    : "Q-126-R",
                ]

                def hlavicka = [
                    overeni      : "false",
                    prvni_zaslani: "true",
                ]

                def passFile = new File("${System.getProperty("testDataDir")}/cert/password_pokladni_cert_playground.txt")
                def cert_pass = passFile.text.trim()

                def config_fix = [
                    cert_popl_path: "${System.getProperty("testDataDir")}/cert/CA_EET-Playground-CZ00000019.p12",
                    //cert_popl_path: "${System.getProperty("testDataDir")}/cert/CA_EET-Playground-CZ683555118.p12",
                    //cert_popl_path: "${System.getProperty("testDataDir")}/cert/CA_EET-Playground-CZ8551015704.p12",
                    cert_pass     : cert_pass,
                    url           : "https://pg.trzbyeet.gov.cz/eet/services/EETServiceSOAP/v4"
                ]

                def config = hlavicka + trzba_var + trzba_fix + config_fix

                log.trace('<== getTestConfig config={}', config)
                return config
        }

        private process(config, validate = true) {
                log.trace('==> process config={}', config)
                if (config.cert_popl_path != null) {
                        config.cert_popl = new FileInputStream(config.cert_popl_path)
                }
                def message = EetXml.makeMsg(config)
                config.cert_popl.close()
                def toSend = message.xml.toString()
                log.info "toSend: {}", toSend

                if (validate) {
                        // validate XML against schema
                        InputStream xsdStream = new FileInputStream(new File("${System.getProperty("schemaFile")}"))
                        def valErr = EetUtil.validateXml(toSend, xsdStream)
                        if (valErr.size() > 0) {
                                throw new IllegalStateException("Validation errors: ${valErr.join(', ')}")
                        }
                        log.info "Validation passed"
                }
                def respText = EetUtil.sendSOAPRequest(config.url, toSend)
                log.info "Response received: {}", respText
                log.info "indented response: {}", EetXml.indentXml(respText)

                def processed = EetXml.processResponse(respText)
                log.trace("resp={}", processed)
                log.trace "pok.size()=${processed.pok?.size()}"
                log.trace "warnings.size()=${processed.warnings?.size()}"
                log.trace "errors.size()=${processed.errors?.size()}"

                def retVal = new Tuple2(message, processed)

                log.trace('<== process retVal={}', retVal)
                return retVal
        }

        @Test(groups = ["internet"])
        public void getPokTest() {
                log.trace('==> getPokTest')

                def config = getTestConfig()
                def (message, processed) = process(config)

                Assert.assertFalse(message.failed)
                Assert.assertEquals(processed.warnings.size() as int, 0)
                Assert.assertEquals(processed.errors.size() as int, 0)
                Assert.assertEquals(processed.pok?.size() as int, 39)
                Assert.assertFalse(processed.failed)

                log.info "<== run pok {}", processed.pok

                log.trace('<== getPokTest')
        }

        @Test(groups = ["internet"])
        public void getPokTestWarnings() {
                log.trace('==> getPokTestWarnings')

                def config = getTestConfig()
                config.dat_trzby = "2016-07-14T18:45:15+02:00"
                def (message, processed) = process(config)
                Assert.assertEquals(processed.warnings.size() as int, 1)
                def (kod, text) = processed.warnings[0]
                Assert.assertEquals(kod as int, 5)
                Assert.assertEquals(text, "Datum a cas prijeti trzby je vyrazne v minulosti")

                config.eic_popl = "CZ1212121218"
                (message, processed) = process(config)
                Assert.assertEquals(processed.warnings.size() as int, 2)
                (kod, text) = processed.warnings[0]
                Assert.assertEquals(kod as int, 1)
                Assert.assertEquals(text, "EIC poplatnika v datove zprave se neshoduje s EIC v certifikatu")
                (kod, text) = processed.warnings[1]
                Assert.assertEquals(kod as int, 5)
                Assert.assertEquals(text, "Datum a cas prijeti trzby je vyrazne v minulosti")

                log.trace('<== getPokTestWarnings')
        }

        @Test(groups = ["internet"])
        public void testVerificationMode() {
                log.trace('==> testVerificationMode')

                def config = getTestConfig()
                config.overeni = "true"
                def (message, processed) = process(config)

                Assert.assertFalse(message.failed)
                Assert.assertFalse(processed.failed)
                Assert.assertTrue(processed.overeni_ok || processed.pok != null)
                Assert.assertEquals(processed.errors.size() as int, 0)

                log.trace('<== testVerificationMode')
        }

        @Test(groups = ["local"])
        public void testInvalidDateLocal() {
                log.trace('==> testInvalidDateLocal')
                def config = getTestConfig()
                config.dat_trzby = "2016-07-14T18:45:15.000+02:00"
                Assert.expectThrows(IllegalStateException) {
                        process(config, true)
                }
                log.trace('<== testInvalidDateLocal')
        }

        @Test(groups = ["internet"])
        public void testInvalidDate() {
                log.trace('==> testInvalidDate')

                def config = getTestConfig()
                config.dat_trzby = "2016-07-14T18:45:15.000+02:00"
                def (message, processed) = process(config, false)
                Assert.assertEquals(processed.errors.size() as int, 1)
                Assert.assertTrue(processed.failed)
                def (kod, text) = processed.errors[0]
                Assert.assertEquals(kod as int, 3)
                Assert.assertEquals(text, "XML zprava nevyhovela kontrole XML schematu")
                log.trace('<== testInvalidDate')
        }

        @Test(groups = ["internet"])
        public void testInvalidSignature() {
                log.trace('==> testInvalidSignature')

                def config = getTestConfig()
                config.cert_popl = new FileInputStream(config.cert_popl_path)
                def message
                try {
                        message = EetXml.makeMsg(config)
                } finally {
                        config.cert_popl.close()
                }

                def xml = message.xml.toString()
                def signaturePattern = /(<ds:SignatureValue>)([^<]+)(<\/ds:SignatureValue>)/
                def matcher = xml =~ signaturePattern
                Assert.assertTrue(matcher.find(), 'Generated message must contain SignatureValue')
                def signatureValue = matcher.group(2)
                def tamperedSignature = (signatureValue[0] == 'A' ? 'B' : 'A') + signatureValue.substring(1)
                def tamperedXml = xml.replaceFirst(
                        signaturePattern,
                        Matcher.quoteReplacement("${matcher.group(1)}${tamperedSignature}${matcher.group(3)}"))

                def respText = EetUtil.sendSOAPRequest(config.url, tamperedXml)
                def processed = EetXml.processResponse(respText)

                Assert.assertEquals(processed.errors.size() as int, 1)
                def (kod, text) = processed.errors[0]
                Assert.assertEquals(kod as int, 4)
                Assert.assertEquals(text, "Neplatny podpis SOAP zpravy")
                Assert.assertTrue(processed.failed)

                log.trace('<== testInvalidSignature')
        }

        @Test(groups = ["internet"])
        public void testModifiedSignedBody() {
                log.trace('==> testModifiedSignedBody')

                def config = getTestConfig()
                config.cert_popl = new FileInputStream(config.cert_popl_path)
                def message
                try {
                        message = EetXml.makeMsg(config)
                } finally {
                        config.cert_popl.close()
                }

                def tamperedXml = message.xml.toString().replaceFirst(
                        'celk_trzba="7896\\.00"', 'celk_trzba="7897.00"')
                Assert.assertNotEquals(tamperedXml, message.xml.toString())

                def processed = EetXml.processResponse(
                        EetUtil.sendSOAPRequest(config.url, tamperedXml))

                Assert.assertEquals(processed.errors.size() as int, 1)
                def (kod, text) = processed.errors[0]
                Assert.assertEquals(kod as int, 4)
                Assert.assertEquals(text, "Neplatny podpis SOAP zpravy")
                Assert.assertTrue(processed.failed)

                log.trace('<== testModifiedSignedBody')
        }

        @Test(groups = ["internet"])
        public void testMissingRequiredField() {
                log.trace('==> testMissingRequiredField')

                def config = getTestConfig()
                config.id_pokl = null
                config.cert_popl = new FileInputStream(config.cert_popl_path)
                def message
                try {
                        message = EetXml.makeMsg(config)
                } finally {
                        config.cert_popl.close()
                }

                def respText = EetUtil.sendSOAPRequest(config.url, message.xml.toString())
                def processed = EetXml.processResponse(respText)

                Assert.assertEquals(processed.errors.size() as int, 1)
                def (kod, text) = processed.errors[0]
                Assert.assertEquals(kod as int, 3)
                Assert.assertEquals(text, "XML zprava nevyhovela kontrole XML schematu")
                Assert.assertTrue(processed.failed)

                log.trace('<== testMissingRequiredField')
        }

        @Test(groups = ["local"])
        public void testMissingRequiredFieldLocal() {
                log.trace('==> testMissingRequiredFieldLocal')

                def config = getTestConfig()
                config.id_pokl = null
                Assert.expectThrows(IllegalStateException) {
                        process(config, true)
                }

                log.trace('<== testMissingRequiredFieldLocal')
        }

        @Test(groups = ["internet"])
        public void testOptionalFinancialFields() {
                log.trace('==> testOptionalFinancialFields')

                def config = getTestConfig()
                config.cerp_zuct = "10.00"
                config.urceno_cerp_zuct = "20.00"
                def (message, processed) = process(config)

                Assert.assertFalse(message.failed)
                Assert.assertFalse(processed.failed)
                Assert.assertEquals(processed.errors.size() as int, 0)
                Assert.assertNotNull(processed.pok)

                log.trace('<== testOptionalFinancialFields')
        }

        @Test(groups = ["local"])
        public void testOptionalFinancialFieldsPassSchemaValidation() {
                log.trace('==> testOptionalFinancialFieldsPassSchemaValidation')

                def config = getTestConfig()
                config.cerp_zuct = "10.00"
                config.urceno_cerp_zuct = "20.00"
                config.cert_popl = new FileInputStream(config.cert_popl_path)
                try {
                        def message = EetXml.makeMsg(config)
                        def schema = new FileInputStream(System.getProperty("schemaFile"))
                        try {
                                Assert.assertEquals(EetUtil.validateXml(message.xml.toString(), schema), [])
                        } finally {
                                schema.close()
                        }
                } finally {
                        config.cert_popl.close()
                }

                log.trace('<== testOptionalFinancialFieldsPassSchemaValidation')
        }

}
