package io.github.ozzz.personalagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Keeps only the user-started, bounded task alive while Taobao is foreground. */
class TaskService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("automation", "自动化任务", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "automation")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("正在执行淘金币测试")
            .setContentText("返回 Personal Agent 可取消；任务结束自动停止")
            .setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTimeout(startId: Int) { stopSelf() }
}
