package work.integro.carling.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import work.integro.carling.MainActivity
import work.integro.carling.R

private const val TAG = "TripService"

/**
 * 주행 기록이 끊기지 않게 앱 프로세스를 유지하는 포그라운드 서비스.
 * 실제 기록은 CarlingApplication의 TripTracker가 한다 — 이 서비스는 아무 일도 하지 않고 자리만 지킨다
 * (Pleos 권고: 백그라운드 상주·폴링 최소화).
 */
class TripService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            Log.i(TAG, "포그라운드 서비스 시작")
        } catch (e: Exception) {
            // 플랫폼 정책상 허용되지 않으면 서비스 없이 프로세스만 유지된다
            Log.w(TAG, "포그라운드 서비스 시작 실패", e)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "함께 달리기", NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("카링이 함께 달릴 준비를 하고 있어요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, TripService::class.java)) }
                .onFailure { Log.w(TAG, "서비스 시작 요청 실패", it) }
        }
    }
}
