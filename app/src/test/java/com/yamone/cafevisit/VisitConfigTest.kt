package com.yamone.cafevisit

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VisitConfigTest {
    @Test fun defaultRange() {
        val c = VisitConfig("https://cafe.naver.com/example", 1860, 2100)
        val random = Random(7)
        val values = (1..10000).map { c.chooseDelaySeconds(random) }
        assertTrue(values.all { it in 1860..2100 })
        assertTrue(values.contains(1860)); assertTrue(values.contains(2100))
    }
    @Test fun fixedRange() { assertEquals(1901, VisitConfig("https://cafe.naver.com/a", 1901, 1901).chooseDelaySeconds()) }
    @Test fun secondsInput() { assertEquals(1925, VisitConfig.fromInputs("cafe.naver.com/a", "32", "25", "35", "0").minSeconds) }
    @Test fun preservesEscaping() { assertEquals("https://cafe.naver.com/a?q=x%2Fy", VisitConfig.normalizeCafeUrl("https://cafe.naver.com/a?q=x%2Fy#top")) }
    @Test fun upgradesHttp() { assertEquals("https://cafe.naver.com/a", VisitConfig.normalizeCafeUrl("http://cafe.naver.com/a")) }
    @Test fun trustedNavigation() { assertTrue(VisitConfig.isNaverHttps(BrowserUrl.LOGIN)); assertFalse(VisitConfig.isNaverHttps("https://naver.com.evil.test/a")) }
    @Test(expected = IllegalArgumentException::class) fun rejectUserInfo() { VisitConfig.normalizeCafeUrl("https://secret@cafe.naver.com/a") }
    @Test(expected = IllegalArgumentException::class) fun rejectReversed() { VisitConfig("https://cafe.naver.com/a", 2100, 1860) }
    @Test(expected = IllegalArgumentException::class) fun rejectTooShort() { VisitConfig("https://cafe.naver.com/a", 1859, 2100) }
    @Test(expected = IllegalArgumentException::class) fun rejectInvalidSeconds() { VisitConfig.fromInputs("cafe.naver.com/a", "31", "60", "35", "0") }
    @Test(expected = IllegalArgumentException::class) fun rejectEmptyPath() { VisitConfig.normalizeCafeUrl("https://cafe.naver.com/") }
    @Test fun rejectOtherSchemes() { assertFalse(VisitConfig.isNaverHttps("javascript:alert(1)")); assertFalse(VisitConfig.isNaverHttps("file:///etc/passwd")) }
    private object BrowserUrl { const val LOGIN = "https://nid.naver.com/nidlogin.login" }
}
