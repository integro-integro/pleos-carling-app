package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.game.ImprovementRules
import work.integro.carling.game.Metric
import work.integro.carling.game.PeriodStat
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.ReportData
import work.integro.carling.game.ReportTrip
import work.integro.carling.game.Savings
import work.integro.carling.game.Verdict
import work.integro.carling.vehicle.VehicleHealth
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** 리포트에서 차량 상태를 읽는 함수(앱이 넣어 준다). 미리보기·시험에선 null. */
val LocalVehicleHealth = staticCompositionLocalOf<suspend () -> VehicleHealth?> { { null } }

private val Zone: ZoneId = ZoneId.systemDefault()

// ======================= ⑧ 운전 개선 =======================

/**
 * ⑧ 내 운전, 얼마나 나아졌나: 같은 단위의 최근 6개 기간을 나란히. 지표 4개(운전점수·급조작·효율·부드러운 운행)를
 * 작은 막대로 보여 주고, 마지막 기간을 그 앞 기간과 비교해 좋아짐·아쉬움을 표시한다.
 */
@Composable
internal fun ImprovementBlock(stats: List<PeriodStat>, effUnit: String) {
    val verdicts = remember(stats) { ImprovementRules.verdicts(stats) }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("⑧", "내 운전, 얼마나 나아졌나", ImprovementRules.headline(verdicts))
            Spacer(Modifier.height(10.dp))
            val rows = listOf(
                Triple(Metric.SCORE, "운전점수", stats.map { it.score?.toDouble() }),
                Triple(Metric.HARSH, "급조작 · 100km당", stats.map { it.harshPer100 }),
                Triple(Metric.EFFICIENCY, "효율 · $effUnit", stats.map { it.efficiency?.toDouble() }),
                Triple(Metric.SMOOTH, "부드러운 운행 비율", stats.map { it.smoothShare?.toDouble() }),
            )
            rows.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    pair.forEach { (m, title, values) ->
                        MetricTrend(title, m, values, stats.map { it.short }, verdicts.firstOrNull { it.metric == m }, Modifier.weight(1f))
                    }
                }
            }
            Text("마지막 막대가 지금 기간이에요. 운행이 없던 기간은 비어 있어요. 급조작은 낮을수록 좋아요.", fontSize = 13.sp, color = Faint)
        }
    }
}

private fun fmt(m: Metric, v: Double): String = when (m) {
    Metric.SCORE -> "${v.toInt()}점"
    Metric.HARSH -> "%.1f회".format(v)
    Metric.EFFICIENCY -> "%.1f".format(v)
    Metric.SMOOTH -> "${v.toInt()}%"
}

@Composable
private fun MetricTrend(title: String, m: Metric, values: List<Double?>, labels: List<String>, verdict: Verdict?, modifier: Modifier) {
    Column(modifier.background(Paper, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
            verdict?.let { v ->
                val same = abs(v.changePct) < ImprovementRules.MIN_CHANGE_PCT
                val (text, color) = when {
                    same -> "비슷해요" to Muted
                    v.better -> "▲ 좋아졌어요" to Color(0xFF1F9C74)
                    else -> "▼ 아쉬워요" to Coral
                }
                Text(text, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold)
            }
        }
        Text(values.lastOrNull()?.let { fmt(m, it) } ?: "—", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
        verdict?.let { Text("앞 기간 ${fmt(m, it.before)} → ${if (it.changePct >= 0) "+" else ""}${it.changePct}%", fontSize = 13.sp, color = Muted) }
        Spacer(Modifier.height(8.dp))
        val max = (values.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bar = (slot * 0.56f).coerceAtMost(30f)
            values.forEachIndexed { i, v ->
                val last = i == values.lastIndex
                if (v == null) drawCircle(Faint, 3f, Offset(i * slot + slot / 2, size.height - 4f))
                else {
                    val h = (v / max * (size.height - 4f)).toFloat().coerceAtLeast(3f)
                    drawRoundRect(if (last) Lilac else Color(0xFFCFC8F7), Offset(i * slot + (slot - bar) / 2, size.height - h), Size(bar, h), CornerRadius(bar / 3, bar / 3))
                }
            }
        }
        Row { labels.forEach { Text(it, fontSize = 11.sp, color = Faint, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f)) } }
    }
}

// ======================= 연비로 아낀 금액 =======================

/** 효율 블록 아래: 같은 거리를 직전 기간 효율로 달렸을 때와 비교한 금액, 단가 조절. */
@Composable
internal fun SavingsRow(r: ReportData, price: Int, onPrice: ((Int) -> Unit)?) {
    val ev = r.ev != false
    val s: Savings? = ImprovementRules.savings(r.totals.km, r.avgEfficiency, r.prevEfficiency, price)
    val unit = if (ev) "kWh" else "L"
    Column(Modifier.fillMaxWidth().background(if (s?.saved != false) MintSoft else Color(0xFFFFE9E4), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💰", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        s == null -> "${r.period.previousLabel} ${r.efficiencyName} 기록이 있어야 아낀 금액을 계산해요"
                        abs(s.won) < 100 -> "${r.period.previousLabel}와 거의 같은 비용으로 달렸어요"
                        s.saved -> "${r.period.previousLabel}보다 %,d원 아꼈어요".format(s.won)
                        else -> "${r.period.previousLabel}보다 %,d원 더 썼어요".format(-s.won)
                    },
                    fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold,
                )
                s?.let {
                    Text(
                        "이번 ${ReportAggregator.km(r.totals.km)}에 %.1f%s(%,d원) · 앞 기간 효율이었다면 %.1f%s(%,d원)".format(
                            it.energyNow, unit, (it.energyNow * price).toInt(), it.energyAtPrev, unit, (it.energyAtPrev * price).toInt(),
                        ),
                        fontSize = 15.sp, color = Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("단가: $unit 당 %,d원".format(price), fontSize = 16.sp, color = Ink)
            if (onPrice != null) {
                val step = if (ev) 10 else 50
                Spacer(Modifier.width(10.dp))
                Stepper("－") { onPrice(price - step) }
                Spacer(Modifier.width(6.dp))
                Stepper("＋") { onPrice(price + step) }
            }
            Spacer(Modifier.weight(1f))
            Text(if (ev) "기본: 공공 급속 충전 단가" else "기본: 휘발유 평균가", fontSize = 13.sp, color = Faint)
        }
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Text(label, fontSize = 20.sp, color = Lilac, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.White, CircleShape).border(1.dp, Line, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 2.dp))
}

// ======================= ⑨ 차량 상태 =======================

/** ⑨ 지금 차량 상태: 타이어 4바퀴, 오일·브레이크액, 워셔액, 남은 거리, 켜진 경고등. */
@Composable
internal fun VehicleHealthBlock(h: VehicleHealth?, loading: Boolean) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("⑨", "지금 차량 상태", h?.let { if (it.issues == 0) "살펴볼 곳이 없어요" else "살펴볼 곳 ${it.issues}개" })
            Spacer(Modifier.height(10.dp))
            if (h == null) {
                Text(if (loading) "차량 상태를 읽고 있어요…" else "차량 상태를 받을 수 없어요(지원하지 않는 차량이거나 연결 전).", fontSize = 17.sp, color = Muted)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TireMap(h, Modifier.size(210.dp, 230.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRow("남은 주행 가능 거리", h.rangeKm?.let { "${it.toInt()}km" } ?: "—", null)
                    StatusRow(if (h.ev == true) "배터리" else "연료", h.energyPercent?.let { "$it%" } ?: "—", h.energyPercent?.let { it < 20 })
                    StatusRow("엔진오일", levelText(h.engineOil), h.engineOil?.let { it == "LOW" || it == "CRITICALLY_LOW" })
                    StatusRow("브레이크액", levelText(h.brakeFluid), h.brakeFluid?.let { it == "LOW" || it == "CRITICALLY_LOW" })
                    StatusRow("워셔액", h.lowWasher?.let { if (it) "부족해요" else "충분해요" } ?: "—", h.lowWasher)
                    StatusRow("누적 주행거리", h.odometerKm?.let { "%,dkm".format(it.toInt()) } ?: "—", null)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !h.telltalesKnown -> "경고등 정보를 받지 못했어요."
                    h.warnings.isEmpty() -> "✅ 켜진 경고등이 없어요."
                    else -> "⚠️ 켜진 경고등: " + h.warnings.joinToString(", ")
                },
                fontSize = 17.sp, color = if (h.warnings.isEmpty()) Ink else Coral, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().background(if (h.warnings.isEmpty()) MintSoft else Color(0xFFFFE9E4), RoundedCornerShape(14.dp)).padding(12.dp),
            )
        }
    }
}

private fun levelText(s: String?): String = when (s) {
    null -> "—"
    "NORMAL" -> "정상"
    "LOW" -> "부족해요"
    "CRITICALLY_LOW" -> "매우 부족해요"
    "HIGH" -> "많아요"
    else -> "확인 필요"
}

@Composable
private fun StatusRow(label: String, value: String, warn: Boolean?) {
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(when (warn) { true -> Coral; false -> Mint; null -> Faint }, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 16.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 18.sp, color = if (warn == true) Coral else Ink, fontWeight = FontWeight.Bold)
    }
}

/** 위에서 본 차와 네 바퀴 공기압. 부족한 바퀴는 코랄. */
@Composable
private fun TireMap(h: VehicleHealth, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.size(210.dp, 230.dp)) {
            val bodyW = size.width * 0.42f
            val bodyH = size.height * 0.78f
            val left = (size.width - bodyW) / 2
            val top = (size.height - bodyH) / 2
            drawRoundRect(LilacSoft, Offset(left, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.35f, bodyW * 0.35f))
            drawRoundRect(Color.White, Offset(left + bodyW * 0.15f, top + bodyH * 0.22f), Size(bodyW * 0.7f, bodyH * 0.22f), CornerRadius(14f, 14f))
            val tw = bodyW * 0.22f
            val th = bodyH * 0.2f
            listOf(0 to (left - tw * 0.6f to top + bodyH * 0.12f), 1 to (left + bodyW - tw * 0.4f to top + bodyH * 0.12f), 2 to (left - tw * 0.6f to top + bodyH * 0.68f), 3 to (left + bodyW - tw * 0.4f to top + bodyH * 0.68f)).forEach { (i, p) ->
                val low = h.tires.getOrNull(i)?.low == true
                drawRoundRect(if (low) Coral else Ink.copy(alpha = 0.75f), Offset(p.first, p.second), Size(tw, th), CornerRadius(8f, 8f))
            }
        }
        // 바퀴 옆 숫자
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEachIndexed { i, a ->
            val t = h.tires.getOrNull(i)
            Text(
                t?.psi?.let { "${it.toInt()}psi" } ?: "—",
                fontSize = 15.sp, color = if (t?.low == true) Coral else Ink, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(a).padding(vertical = 18.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp)).padding(horizontal = 4.dp),
            )
        }
    }
}

// ======================= 운행일지 + QR =======================

/** QR에 넣을 수 있는 mailto 길이(바이트). 이보다 길면 운행 목록을 줄인다. */
const val QR_MAX_BYTES = 1_100

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/**
 * 운행일지 본문: 기간 요약, 개선 비교, 절약, 차량 상태, 운행 목록(최신순). [maxTrips]로 목록 길이를 줄인다.
 */
fun drivingLogText(r: ReportData, trips: List<ReportTrip>, verdicts: List<Verdict>, savings: Savings?, health: VehicleHealth?, maxTrips: Int): String = buildString {
    appendLine("[마이카링 운행일지] ${r.label} (${r.from} ~ ${r.to})")
    appendLine("운행 ${r.totals.count}회 · ${ReportAggregator.km(r.totals.km)} · 운전 ${minutesText(r.totals.sec)} · 운전일 ${r.totals.days}일")
    r.avgEfficiency?.let { appendLine("${r.efficiencyName} %.1f ${r.efficiencyUnit}".format(it) + (r.prevEfficiency?.let { p -> " (${r.period.previousLabel} %.1f)".format(p) } ?: "")) }
    savings?.let { appendLine(if (it.saved) "아낀 금액 %,d원".format(it.won) else "더 쓴 금액 %,d원".format(-it.won)) }
    r.avgScore?.let { appendLine("평균 운전점수 ${it}점 · 급조작 ${r.harshTotal ?: 0}회") }
    if (verdicts.isNotEmpty()) appendLine("개선: " + verdicts.joinToString(", ") { "${it.metric.label} ${if (it.changePct >= 0) "+" else ""}${it.changePct}%" })
    health?.let { h -> appendLine("차량: " + (if (h.issues == 0) "이상 없음" else "살펴볼 곳 ${h.issues}개") + (h.rangeKm?.let { " · 남은 거리 ${it.toInt()}km" } ?: "")) }
    val list = trips.sortedByDescending { it.startedAt }
    if (list.isNotEmpty() && maxTrips > 0) {
        appendLine()
        appendLine("날짜 시각 목적 거리 시간")
        list.take(maxTrips).forEach { t ->
            val d = Instant.ofEpochMilli(t.startedAt).atZone(Zone)
            appendLine("%d/%d %02d:%02d %s %skm %s".format(d.monthValue, d.dayOfMonth, d.hour, d.minute, t.custom ?: t.purpose?.label ?: "-", kmText(t.distanceKm), minutesText(t.movingSec)))
        }
        if (list.size > maxTrips) appendLine("외 ${list.size - maxTrips}건")
    }
}

/** 폰 카메라로 찍으면 메일 쓰기 화면이 열리는 주소. 길이에 맞춰 운행 목록을 줄인다. */
fun mailtoFor(email: String, r: ReportData, trips: List<ReportTrip>, verdicts: List<Verdict>, savings: Savings?, health: VehicleHealth?): Pair<String, Int> {
    val subject = "마이카링 운행일지 · ${r.label}"
    var n = trips.size
    while (true) {
        val uri = "mailto:${email}?subject=${enc(subject)}&body=${enc(drivingLogText(r, trips, verdicts, savings, health, n))}"
        if (uri.toByteArray().size <= QR_MAX_BYTES || n == 0) return uri to n
        n = if (n > 10) n - 5 else n - 1
    }
}

/** QR 그림(검은 점 = 모듈). */
fun qrBitmap(text: String, sizePx: Int = 720): ImageBitmap? = runCatching {
    val hints = mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8", com.google.zxing.EncodeHintType.MARGIN to 2, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L)
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = android.graphics.Bitmap.createBitmap(m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.rgb(38, 42, 92) else android.graphics.Color.WHITE)
    bmp.asImageBitmap()
}.getOrNull()

private val EmailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * 운행일지 만들기(사용자 확정 11/12 'QR → 폰 메일'): 왼쪽은 일지 미리보기, 오른쪽은 받는 메일 주소와 QR.
 * 폰으로 찍으면 내 주소·제목·본문이 채워진 메일 쓰기 화면이 열리고, 보내기만 누르면 된다. 서버·계정 없음.
 */
@Composable
fun DrivingLogDialog(r: ReportData, trips: List<ReportTrip>, verdicts: List<Verdict>, savings: Savings?, health: VehicleHealth?, savedEmail: String?, onSaveEmail: (String) -> Unit, onClose: () -> Unit) {
    var email by remember { mutableStateOf(savedEmail ?: "") }
    var confirmed by remember { mutableStateOf(savedEmail) }
    var error by remember { mutableStateOf<String?>(null) }
    val preview = remember(r, trips) { drivingLogText(r, trips, verdicts, savings, health, trips.size) }
    val mail = remember(confirmed, r, trips) { confirmed?.let { mailtoFor(it, r, trips, verdicts, savings, health) } }
    val qr = remember(mail) { mail?.let { qrBitmap(it.first) } }
    FullScrim {
        Panel(Modifier.width(1300.dp), padding = 30.dp) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("📝 운행일지 · ${r.label}", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("${r.from} ~ ${r.to} · 운행 ${trips.size}건", fontSize = 18.sp, color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        preview,
                        fontSize = 17.sp, color = Ink, lineHeight = 26.sp,
                        modifier = Modifier.fillMaxWidth().height(600.dp).background(Paper, RoundedCornerShape(18.dp)).verticalScroll(rememberScrollState()).padding(18.dp),
                    )
                }
                Spacer(Modifier.width(26.dp))
                Column(Modifier.width(470.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("내 메일로 보내기", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("받을 주소는 이 차 안에만 저장해요", fontSize = 15.sp, color = Muted)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim().take(80); error = null },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 19.sp, color = Ink),
                            placeholder = { Text("name@example.com", fontSize = 17.sp) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            modifier = Modifier.background(Color.White).width(320.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (!EmailPattern.matches(email)) error = "메일 주소를 확인해 주세요"
                            else { confirmed = email; onSaveEmail(email) }
                        }, modifier = Modifier.height(56.dp)) { Text("QR 만들기", fontSize = 17.sp) }
                    }
                    error?.let { Text(it, fontSize = 15.sp, color = Coral, modifier = Modifier.padding(top = 4.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.size(430.dp).background(Color.White, RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                        if (qr != null) Image(qr, contentDescription = "운행일지 메일 QR", filterQuality = FilterQuality.None, modifier = Modifier.size(410.dp))
                        else Text(if (confirmed == null) "메일 주소를 넣으면\nQR이 나타나요" else "QR을 만들지 못했어요", fontSize = 18.sp, color = Muted, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (mail != null) "폰 카메라로 찍으면 메일 쓰기 화면이 열려요. 보내기만 누르세요." +
                            if (mail.second < trips.size) "\nQR 크기 때문에 최근 ${mail.second}건만 담았어요." else ""
                        else "폰 카메라로 찍으면 내 주소·제목·본문이 채워진 메일이 열려요.",
                        fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f, fill = false))
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(58.dp)) { Text("닫기", fontSize = 20.sp) }
                }
            }
        }
    }
}
