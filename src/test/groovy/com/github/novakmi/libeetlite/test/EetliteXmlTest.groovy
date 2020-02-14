/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import com.github.novakmi.libeetlite.EetUtil
import com.github.novakmi.libeetlite.EetXml
import groovy.util.logging.Slf4j
import org.testng.Assert
import org.testng.annotations.Test
import wslite.soap.SOAPClient
import wslite.soap.SOAPResponse

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Slf4j
class EetliteXmlTest {

        private getTestConfig() {
                log.trace('==> getTestConfig')

                def trzba_var = [
                        porad_cis : "0/6460/ZQ42",
                        dat_trzby : EetUtil.nowToIso(),
                        celk_trzba: "7896.00",
                        rezim     : "0",
                ]

                def trzba_fix = [
                        dic_popl : "CZ00000019",
                        id_provoz: "123",
                        id_pokl  : "Q-126-R",
                ]

                def hlavicka = [
                        overeni      : "0",
                        prvni_zaslani: "1",
                ]

                def config_fix = [
                        cert_popl_path: "${System.getProperty("testDataDir")}/cert/EET_CA1_Playground-CZ00000019.p12",
                        //cert_popl: "${System.getProperty("testDataDir")}/cert/EET_CA1_Playground-CZ683555118.p12",
                        //cert_popl: "${System.getProperty("testDataDir")}/cert/EET_CA1_Playground-CZ1212121218.p12",
                        cert_pass     : "eet",
                        url           : "https://pg.eet.cz:443/eet/services/EETServiceSOAP/v3"
                ]

                def config = hlavicka + trzba_var + trzba_fix + config_fix

                log.trace('<== getTestConfig config={}', config)
                return config
        }

        private process(config) {
                log.trace('==> process config={}', config)
                if (config.cert_popl_path != null) {
                        config.cert_popl = new FileInputStream(config.cert_popl_path)
                }
                def message = EetXml.makeMsg(config)
                config.cert_popl.close()
                def toSend = message.xml.toString()
                log.debug "toSend: {}", toSend
                SOAPClient client = new SOAPClient(config.url)
                SOAPResponse response = client.send(toSend)

                log.trace "response {}", response
                def respText = response.text
                log.debug "indented response: {}", EetXml.indentXml(respText)

                def processed = EetXml.processResponse(respText)
                log.trace("resp={}", processed)
                log.trace "fik.size()=${processed.fik?.size()}"
                log.trace "warnings.size()=${processed.warnings?.size()}"
                log.trace "errors.size()=${processed.errors?.size()}"
                log.trace "bkp ${message.bkp}"
                log.trace "pkp ${message.pkp}"
                log.trace "rezim ${config.rezim}"
                def retVal = new Tuple2(message, processed)

                log.trace('<== process retVal={}', retVal)
                return retVal
        }

        @Test(groups = ["basic", "internet"])
        public void getFikTest() {
                log.trace('==> getFikTest')

                def config = getTestConfig()
                def (message, processed) = process(config)

                Assert.assertFalse(message.failed)
                Assert.assertEquals(processed.warnings.size(), 0)
                Assert.assertEquals(processed.errors.size(), 0)
                Assert.assertEquals(processed.fik?.size(), 39)
                //Assert.assertTrue(processed.fik?.endsWith("ff"))  // TODO fa is returned
                Assert.assertFalse(processed.failed)

                log.info "<== run fik {}", processed.fik

                log.trace('<== getFikTest')
        }

        @Test(groups = ["basic", "internet"])
        public void getFikTestWarnings() {
                log.trace('==> getFikTestWarnings')

                def config = getTestConfig()
                config.dat_trzby = "2016-07-14T18:45:15+02:00"
                def (message, processed) = process(config)
                Assert.assertEquals(processed.warnings.size(), 1)
                def (kod, text) = processed.warnings[0]
                Assert.assertEquals(kod as int, 5)
                Assert.assertEquals(text, "Datum a cas prijeti trzby je vyrazne v minulosti")

                config.dic_popl = "CZ1212121218"
                (message, processed) = process(config)
                Assert.assertEquals(processed.warnings.size(), 2)
                (kod, text) = processed.warnings[0]
                Assert.assertEquals(kod as int, 1)
                Assert.assertEquals(text, "DIC poplatnika v datove zprave se neshoduje s DIC v certifikatu")
                (kod, text) = processed.warnings[1]
                Assert.assertEquals(kod as int, 5)
                Assert.assertEquals(text, "Datum a cas prijeti trzby je vyrazne v minulosti")

                log.trace('<== getFikTestWarnings')
        }

        @Test(groups = ["basic", "internet"])
        public void testError() {
                log.trace('==> testError')

                def config = getTestConfig()
                config.dat_trzby = "2016-07-14T18:45:15.000+02:00"
                def (message, processed) = process(config)
                Assert.assertEquals(processed.errors.size(), 1)
                Assert.assertTrue(processed.failed)
                def (kod, text) = processed.errors[0]
                Assert.assertEquals(kod as int, 3)
                Assert.assertEquals(text, "XML zprava nevyhovela kontrole XML schematu")
                log.trace('<== testError')
        }

}

