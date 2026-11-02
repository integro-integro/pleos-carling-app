package work.integro.carling.trip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 시동(인포테인먼트 부팅) 때 앱 프로세스를 깨운다.
 * 프로세스가 뜨면 CarlingApplication이 차량 신호·주행 기록을 시작하고,
 * TripService가 프로세스를 살려둬 화면을 열지 않아도 그날 주행이 기록된다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "수신: ${intent.action}")
        TripService.start(context)
    }
}
