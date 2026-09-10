package com.github.novakmi.libeetlite.test

import com.github.novakmi.libeetlite.EetXml
import com.github.novakmi.libeetlite.EetUtil
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlParser
import org.testng.Assert
import org.testng.annotations.Test

class EetXmlV4Test {

    private Map getSignedMessageConfig() {
        def testDataDir = System.getProperty("testDataDir")
        def certPath = "${testDataDir}/cert/CA_EET-Playground-CZ00000019.p12"
        [
                eic_popl: "CZ00000019",
                id_jednotky: "123",
                id_pokl: "Q-126-R",
                porad_cis: "0/6460/ZQ42",
                dat_trzby: "2026-09-12T10:00:00+02:00",
                celk_trzba: "7896.00",
                prvni_zaslani: "1",
                overeni: "0",
                cert_popl_path: certPath,
                cert_pass: new File("${testDataDir}/cert/password_pokladni_cert_playground.txt").text.trim()
        ]
    }

    @Test(groups = ["unit"])
    void createsV4BodyWithoutLegacyCodes() {
        def config = [
                eic_popl: "CZ00000019",
                id_jednotky: "123",
                id_pokl: "Q-126-R",
                porad_cis: "0/6460/ZQ42",
                dat_trzby: "2026-09-12T10:00:00+02:00",
                celk_trzba: "7896.00",
                prvni_zaslani: "1",
                overeni: "0"
        ]

        def builder = new StreamingMarkupBuilder()
        builder.useDoubleQuotes = true
        def xml = builder.bind(EetXml.makeBody(config, "BodyId", config.dat_trzby)).toString()

        Assert.assertTrue(xml.contains('http://fs.gov.cz/eet/schema/v4'))
        Assert.assertTrue(xml.contains('eic_popl="CZ00000019"'))
        Assert.assertTrue(xml.contains('id_jednotky="123"'))
        Assert.assertFalse(xml.contains('KontrolniKody'))
        Assert.assertFalse(xml.contains('rezim='))
    }

    @Test(groups = ["unit"])
    void parsesPokAndKeepsWarningsAndErrors() {
        def response = '''
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <eet:Odpoved xmlns:eet="http://fs.gov.cz/eet/schema/v4">
                  <eet:Hlavicka/>
                  <eet:Potvrzeni pok="58014b05-1bc5-46d0-8174-46bcf8ef8124-fa" test="true"/>
                  <eet:Varovani kod_varov="201">Test warning</eet:Varovani>
                </eet:Odpoved>
              </soap:Body>
            </soap:Envelope>
        '''

        def result = EetXml.processResponse(response)

        Assert.assertFalse(result.failed)
        Assert.assertEquals(result.pok, "58014b05-1bc5-46d0-8174-46bcf8ef8124-fa")
        Assert.assertEquals(result.warnings.size() as int, 1)
        Assert.assertEquals(result.errors.size() as int, 0)
    }

    @Test(groups = ["local"])
    void generatedMessageReferencesItsBodyAndContainsSignature() {
        def config = getSignedMessageConfig()
        config.cert_popl = new FileInputStream(config.cert_popl_path)

        try {
            def message = EetXml.makeMsg(config)
            def envelope = new XmlParser(false, false).parseText(message.xml.toString())
            def body = envelope.'**'.find { it.name().toString().endsWith(':Body') || it.name().toString() == 'Body' }
            def reference = envelope.'**'.find { it.name().toString().endsWith(':Reference') || it.name().toString() == 'Reference' }
            def signatureValue = envelope.'**'.find { it.name().toString().endsWith(':SignatureValue') || it.name().toString() == 'SignatureValue' }

            Assert.assertFalse(message.failed)
            Assert.assertNotNull(body)
            Assert.assertNotNull(reference)
            Assert.assertNotNull(signatureValue)
            Assert.assertTrue((signatureValue.text as String).trim().length() > 0)
            def bodyId = body.attributes().find { entry ->
                entry.key.toString().endsWith(':Id') || entry.key.toString() == 'Id'
            }.value
            Assert.assertEquals(reference.@URI, "#${bodyId}")
        } finally {
            config.cert_popl.close()
        }
    }

    @Test(groups = ["local"])
    void generatedMessagePassesSchemaValidation() {
        def config = getSignedMessageConfig()
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
    }
}
