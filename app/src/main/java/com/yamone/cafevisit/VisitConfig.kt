package com.yamone.cafevisit

import java.net.URI
import java.util.Locale
import kotlin.random.Random

/** Pure JVM policy: no cookies, credentials or Android dependencies. */
data class VisitConfig(val cafeUrl: String, val minSeconds: Int, val maxSeconds: Int) {
    init {
        require(minSeconds in 1860..86400 && maxSeconds in minSeconds..86400) {
            "간격은 최소 31분, 최대 24시간이며 최소값이 최대값보다 작아야 합니다."
        }
        require(normalizeCafeUrl(cafeUrl) == cafeUrl) { "카페 주소를 확인하세요." }
    }
    fun chooseDelaySeconds(random: Random = Random.Default): Int =
        if (minSeconds == maxSeconds) minSeconds else random.nextInt(minSeconds, maxSeconds + 1)

    companion object {
        fun fromInputs(url: String, minM: String, minS: String, maxM: String, maxS: String): VisitConfig {
            fun seconds(m: String, s: String): Int {
                val mm = m.trim().toIntOrNull() ?: error("분을 숫자로 입력하세요.")
                val ss = s.trim().toIntOrNull() ?: error("초를 숫자로 입력하세요.")
                require(mm in 0..1440 && ss in 0..59) { "분은 0~1440, 초는 0~59로 입력하세요." }
                return mm * 60 + ss
            }
            return VisitConfig(normalizeCafeUrl(url), seconds(minM, minS), seconds(maxM, maxS))
        }
        fun normalizeCafeUrl(input: String): String {
            val u = parseHttps(input)
            require(u.host.lowercase(Locale.ROOT) in setOf("cafe.naver.com", "m.cafe.naver.com")) {
                "네이버 카페 주소만 등록할 수 있습니다."
            }
            require(!u.rawPath.isNullOrBlank() && u.rawPath != "/") { "카페 이름이 포함된 주소를 입력하세요." }
            // Preserve percent-encoding instead of double-encoding an existing query.
            return "https://${u.host.lowercase(Locale.ROOT)}${u.rawPath}" +
                (u.rawQuery?.let { "?$it" } ?: "")
        }
        fun navigationUrl(input: String): String {
            val u = parseHttps(input)
            require(isNaverHttps(u.toString())) { "앱 안에서는 네이버 HTTPS 주소만 열 수 있습니다." }
            return u.toASCIIString()
        }
        fun isNaverHttps(url: String?): Boolean = runCatching {
            val u = URI(url ?: "")
            val host = u.host?.lowercase(Locale.ROOT) ?: return@runCatching false
            u.scheme.equals("https", true) && u.rawUserInfo == null &&
                (u.port == -1 || u.port == 443) && (host == "naver.com" || host.endsWith(".naver.com"))
        }.getOrDefault(false)
        private fun parseHttps(input: String): URI {
            val text = input.trim()
            require(text.isNotBlank()) { "주소를 입력하세요." }
            val full = when {
                text.startsWith("http://", true) -> "https://" + text.substring(7)
                text.contains("://") -> text
                else -> "https://$text"
            }
            val u = runCatching { URI(full) }.getOrElse { error("주소 형식을 확인하세요.") }
            require(u.scheme.equals("https", true) && !u.host.isNullOrBlank() &&
                u.rawUserInfo == null && (u.port == -1 || u.port == 443)) { "안전한 HTTPS 주소를 입력하세요." }
            return u
        }
    }
}
