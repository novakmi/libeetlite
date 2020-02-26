/* (c) Michal Novák, libeetlite, it.novakmi@gmail.com, see LICENSE file */

package com.github.novakmi.libeetlite.test

import com.github.novakmi.libeetlite.EetUtil
import groovy.util.logging.Slf4j
import org.testng.Assert
import org.testng.annotations.Test

@Slf4j
class EetliteUtilTest {

    @Test(groups = ["basic"])
    public void base64Test() {
        log.trace('==> base64Test')
        def ret = EetUtil.toBase64("Hello Eetlite".getBytes("UTF-8"))
        log.trace("ret ${ret}")
        Assert.assertEquals(ret, "SGVsbG8gRWV0bGl0ZQ==")
        log.trace('<== base64Test')
    }

    @Test(groups = ["basic"])
    public void bytesToHexTest() {
        log.trace('==> bytesToHexTest')
        byte[] bytes = [57, 21, 3, 23, -95, -69, -47, -85, -118, -38, 7, 92, 89, -96, 100, -116, 81, 73, 108, -110]
        def hex = EetUtil.bytesToHex(bytes)
        Assert.assertEquals(hex, "39150317A1BBD1AB8ADA075C59A0648C51496C92")
        log.trace('<== bytesToHexTest')
    }

}
