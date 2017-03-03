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

}
