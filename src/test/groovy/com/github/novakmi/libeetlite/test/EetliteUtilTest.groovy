/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import com.github.novakmi.libeetlite.EetUtil
import com.sun.net.httpserver.HttpServer
import groovy.util.logging.Slf4j
import org.testng.Assert
import org.testng.annotations.Test

import java.net.InetSocketAddress

@Slf4j
class EetliteUtilTest {

    @Test(groups = ["unit"])
    public void base64Test() {
        log.trace('==> base64Test')
        def ret = EetUtil.toBase64("Hello Eetlite".getBytes("UTF-8"))
        log.trace("ret ${ret}")
        Assert.assertEquals(ret, "SGVsbG8gRWV0bGl0ZQ==")
        log.trace('<== base64Test')
    }

    @Test(groups = ["unit"])
    public void bytesToHexTest() {
        log.trace('==> bytesToHexTest')
        byte[] bytes = [57, 21, 3, 23, -95, -69, -47, -85, -118, -38, 7, 92, 89, -96, 100, -116, 81, 73, 108, -110]
        def hex = EetUtil.bytesToHex(bytes)
        Assert.assertEquals(hex, "39150317A1BBD1AB8ADA075C59A0648C51496C92")
        log.trace('<== bytesToHexTest')
    }

    @Test(groups = ["unit"])
    public void digestValueIsDeterministic() {
        def digest = EetUtil.makeDigestValue('<value>one</value>')

        Assert.assertEquals(EetUtil.makeDigestValue('<value>one</value>'), digest)
        Assert.assertNotEquals(EetUtil.makeDigestValue('<value>two</value>'), digest)
    }

    @Test(groups = ["unit"])
    public void canonicalizeXmlTest() {
        def xml = '<root b="2" a="1"><!--comment-->text</root>'

        Assert.assertEquals(EetUtil.canonicalizeXml(xml), '<root a="1" b="2">text</root>')
    }

    @Test(groups = ["unit"])
    public void canonicalizeXmlKeepsInclusiveNamespaces() {
        def xml = '''<ds:SignedInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#">
                <ec:InclusiveNamespaces xmlns:ec="http://www.w3.org/2001/10/xml-exc-c14n#"
                        PrefixList="soap"></ec:InclusiveNamespaces>
            </ds:CanonicalizationMethod>
        </ds:SignedInfo>'''

        def canonical = EetUtil.canonicalizeXml(xml)

        Assert.assertTrue(canonical.startsWith(
                '<ds:SignedInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#" ' +
                'xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">'))
    }

    @Test(groups = ["unit"])
    public void canonicalizeXmlRejectsNull() {
        Assert.expectThrows(IllegalArgumentException) {
            EetUtil.canonicalizeXml(null)
        }
    }

    @Test(groups = ["local"])
    public void sendSOAPRequestUsesPostAndReturnsResponse() {
        def requestXml = '<request>payload</request>'
        def responseXml = '<response>ok</response>'
        def server = HttpServer.create(new InetSocketAddress(0), 0)
        def requestMethod = null
        def contentType = null
        def receivedBody = null

        server.createContext('/') { exchange ->
            requestMethod = exchange.requestMethod
            contentType = exchange.getRequestHeaders().getFirst('Content-Type')
            receivedBody = exchange.requestBody.getText('UTF-8')
            exchange.responseHeaders.set('Content-Type', 'text/xml; charset=utf-8')
            byte[] responseBytes = responseXml.getBytes('UTF-8')
            exchange.sendResponseHeaders(200, responseBytes.length)
            exchange.responseBody.write(responseBytes)
            exchange.close()
        }
        server.start()

        try {
            def response = EetUtil.sendSOAPRequest(
                    "http://localhost:${server.address.port}/", requestXml)

            Assert.assertEquals(requestMethod, 'POST')
            Assert.assertEquals(contentType, 'text/xml; charset=utf-8')
            Assert.assertEquals(receivedBody, requestXml)
            Assert.assertEquals(response, responseXml)
        } finally {
            server.stop(0)
        }
    }

    @Test(groups = ["unit"])
    public void getDateUtcTest() {
        log.trace('==> getDateUtcTest')

        def date = EetUtil.getDateUtc()

        log.trace('<== getDateUtcTest')
    }

}
