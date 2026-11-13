package work.integro.carling.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.ReportPeriod
import work.integro.carling.game.ReportTrip
import work.integro.carling.game.TripPurpose
import java.net.URLDecoder
import java.time.LocalDate
import java.time.ZoneId

class DrivingLogTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 20)

    private fun trips(n: Int) = (1..n).map { i ->
        val at = LocalDate.of(2026, 11, 1 + i % 19).atTime(8 + i % 10, 5).atZone(zone).toInstant().toEpochMilli() + i
        ReportTrip(at, at, 12f + i, 1500, 1600, 90, TripPurpose.entries[i % 10], true, null, null, null, harsh = 0)
    }

    @Test
    fun mailtoFitsQrAndRoundTrips() {
        val list = trips(40)
        val r = ReportAggregator.build(ReportPeriod.THIS_MONTH, today, zone, list, 0)
        val (uri, n) = mailtoFor("me@example.com", r, list, emptyList(), null, null)
        assertTrue(uri.toByteArray().size <= QR_MAX_BYTES)
        assertTrue(n in 1 until 40)
        assertTrue(uri.startsWith("mailto:me@example.com?subject="))
        val body = URLDecoder.decode(uri.substringAfter("&body="), "UTF-8")
        assertTrue(body.contains("운행 ${r.totals.count}회"))
        assertTrue(body.contains("외 ${40 - n}건"))
        // QR로 만들었다가 다시 읽으면 같은 주소
        val m = QRCodeWriter().encode(uri, com.google.zxing.BarcodeFormat.QR_CODE, 600, 600, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
        val src = object : LuminanceSource(m.width, m.height) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = ByteArray(width) { x -> if (m[x, y]) 0 else -1 }
            override fun getMatrix(): ByteArray = ByteArray(width * height) { i -> if (m[i % width, i / width]) 0 else -1 }
        }
        assertEquals(uri, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(src))).text)
    }
}
