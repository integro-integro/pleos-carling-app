package work.integro.carling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.DemoDrive
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.DevUi
import work.integro.carling.game.Rules
import work.integro.carling.game.Talk

/**
 * 시연 영상 녹화용 adb 명령(디버그 빌드 전용). 화면에 개발자 패널을 띄우지 않고 시뮬레이터를 조작한다.
 * 예: adb shell am broadcast --user 10 -a work.integro.carling.DEV --es cmd skip --ei min 5
 */
object DevCommands {
    const val ACTION = "work.integro.carling.DEV"

    fun register(context: Context, store: CarlingStore) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                runCatching { handle(store, i) }.onFailure { Log.e("DevCommands", "실패", it) }
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }

    private fun handle(store: CarlingStore, i: Intent) {
        val cmd = i.getStringExtra("cmd") ?: return
        Log.i("DevCommands", "명령: $cmd")
        when (cmd) {
            "reset" -> { store.devReset(); DemoDrive.armed = false; DemoDrive.reset() }
            // 시계를 정한 날짜·시각으로(앞뒤 모두)
            "at" -> {
                val zone = java.time.ZoneId.systemDefault()
                val date = i.getStringExtra("date")?.let(java.time.LocalDate::parse) ?: store.clock.today()
                val time = java.time.LocalTime.parse(i.getStringExtra("time") ?: "07:50")
                val target = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                store.clock.skipMillis(target - store.clock.now())
                store.settle(); store.expireCards(); store.publishMonthReport()
            }
            "consent" -> store.setLocationConsent(true)
            "arm" -> {
                DemoDrive.reset()
                DemoDrive.km = i.getStringExtra("km")?.toFloat() ?: DemoDrive.KM
                DemoDrive.minutes = i.getIntExtra("min", DemoDrive.MINUTES)
                DemoDrive.harsh = i.getIntExtra("harsh", 0)
                DemoDrive.talk = i.getStringExtra("talk")?.let { t -> Talk.entries.firstOrNull { it.name == t } }
                DemoDrive.autoConfirm = i.getBooleanExtra("confirm", false)
                i.getStringExtra("from")?.let { DemoDrive.from = it }
                i.getStringExtra("to")?.let { DemoDrive.to = it }
                DemoDrive.armed = true
            }
            "skip" -> DevSimulator.skip(store, i.getIntExtra("min", 5) * Rules.MINUTE_MILLIS)
            "ignition" -> store.markIgnition()
            "commute9" -> DevSimulator.morningCommutes(store)
            "night6" -> DevSimulator.nightFieldTrips(store)
            "weekend3" -> DevSimulator.weekendFamily(store)
            "experiment" -> DevSimulator.commuteExperiment(store)
            "refuels" -> store.devRefuels()
            "closeMonth" -> DevSimulator.closeMonth(store)
            "cabin" -> store.setCabin(work.integro.carling.game.CabinView.valueOf(i.getStringExtra("view") ?: "DASH"))
            "hideDev" -> DevUi.hidden = i.getBooleanExtra("on", true)
            "riding" -> store.setRiding(i.getBooleanExtra("on", true))
            // 쌓인 상자를 열고 단계·개성·레벨 축하 창을 본 것으로(녹화 준비용)
            "tidy" -> store.devTidy()
            "typeName" -> DevUi.typeName = i.getStringExtra("name")
        }
    }
}
