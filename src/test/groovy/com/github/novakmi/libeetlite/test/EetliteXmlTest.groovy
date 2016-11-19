/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import groovy.util.logging.Slf4j
import org.testng.Assert
import org.testng.annotations.Test
import wslite.soap.SOAPClient
import wslite.soap.SOAPResponse

@Slf4j
class EetliteXmlTest {

    @Test(groups = ["basic", "internet"])
    public void getFikTest() {
        log.trace('==> getFikTest')

        def trzba_var = [
                porad_cis : "0/6460/ZQ42",
                dat_trzby : "2016-07-14T18:45:15+02:00",
                celk_trzba: "7896.00",
        ]

        def trzba_fix = [
                dic_popl : "CZ1212121218",
                id_provoz: "123",
                id_pokl  : "Q-126-R",
                rezim    : "0",
        ]

        def hlavicka = [
                overeni      : "0",
                prvni_zaslani: "1",
        ]

        def config_fix = [
                cert_popl: "${System.getProperty("testDataDir")}/cert/01000003.p12",
                cert_pass: "eet",
                url      : "https://pg.eet.cz:443/eet/services/EETServiceSOAP/v3"
        ]

        def config = hlavicka + trzba_var + trzba_fix + config_fix

        def message = EetXml.makeMsg(config)

        def toSend = message.xml.toString()
        log.debug "toSend: {}", toSend
        SOAPClient client = new SOAPClient(config.url)
        SOAPResponse response = client.send(toSend)

        log.trace "response {}", response
        def respText = response.text
        log.debug "indented response: {}", EetXml.indentXml(respText)

        def fik = EetXml.processResponse(respText)
        log.trace "fik.size()=${fik.size()}"
        log.trace "bkp ${message.bkp}"
        Assert.assertEquals(fik.size(), 39)
        Assert.assertTrue(fik.endsWith("ff"))

        log.info "<== run fik {}", fik

        log.trace('<== getFikTest')
    }

}

