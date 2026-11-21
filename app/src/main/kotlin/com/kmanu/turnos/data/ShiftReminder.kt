package com.kmanu.turnos.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kmanu.turnos.MainActivity
import com.kmanu.turnos.R
import com.kmanu.turnos.TurnosApplication
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftPlanner
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "salida"
private const val EXTRA_KIND = "tipo"
private const val EXTRA_END = "salida_programada"
private const val EXTRA_MINUTES = "minutos"
private val END_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** Los dos avisos del turno abierto. Cada uno tiene su alarma y su notificación. */
private enum class Notice(val requestCode: Int, val notificationId: Int) {
    /** Unos minutos antes de la salida programada, según Ajustes. */
    BEFORE_EXIT(1, 2),

    /** Media hora después de la salida si el turno sigue abierto. */
    FORGOTTEN_EXIT(0, 1),
}

/**
 * Programa las notificaciones del turno abierto. Se usan alarmas exactas para que el aviso llegue
 * al minuto configurado aunque el teléfono esté en reposo; solo muestran una notificación normal.
 */
class ShiftReminder(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(notice: Notice, extras: Intent.() -> Unit = {}): PendingIntent = PendingIntent.getBroadcast(
        context,
        notice.requestCode,
        Intent(context, ShiftReminderReceiver::class.java).putExtra(EXTRA_KIND, notice.name).apply(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Programa los avisos del turno abierto, o los quita si no hay ninguno. */
    fun sync(open: Shift?, minutesBefore: Int) {
        val notifications = NotificationManagerCompat.from(context)
        Notice.entries.forEach { notice ->
            alarms.cancel(pendingIntent(notice))
            // Con la app abierta ya se ve el turno: los avisos anteriores sobran.
            notifications.cancel(notice.notificationId)
        }
        if (open == null) return
        val now = LocalDateTime.now()
        val end = open.scheduledEnd.format(END_FORMAT)

        ShiftPlanner.exitNoticeAt(open, minutesBefore, now)?.let { at ->
            schedule(at, pendingIntent(Notice.BEFORE_EXIT) {
                putExtra(EXTRA_END, end)
                putExtra(EXTRA_MINUTES, minutesBefore)
            })
        }
        // Si ya pasó, la app pregunta por la salida olvidada al marcarla.
        ShiftPlanner.reminderAt(open).takeIf { it.isAfter(now) }?.let { at ->
            schedule(at, pendingIntent(Notice.FORGOTTEN_EXIT) { putExtra(EXTRA_END, end) })
        }
    }

    private fun schedule(at: LocalDateTime, intent: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // Sin permiso de alarma exacta llega igual, con unos minutos de diferencia.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    /** Reprograma los avisos desde los datos guardados, sin la app abierta. */
    suspend fun resync(app: TurnosApplication) {
        val open = app.repository.shifts.first().firstOrNull { it.end == null }
        sync(open, app.settingsStore.settings.first().exitNoticeMinutes)
    }
}

class ShiftReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Avisos de salida", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa antes de tu salida y si olvidaste marcarla."
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notice = intent.getStringExtra(EXTRA_KIND)?.let { kind -> Notice.entries.firstOrNull { it.name == kind } }
            ?: Notice.FORGOTTEN_EXIT
        val end = intent.getStringExtra(EXTRA_END)
        val (title, text) = when (notice) {
            Notice.BEFORE_EXIT -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                "Sales en $minutes min" to (if (end != null) "Tu turno termina a las $end." else "Tu turno está por terminar.")
            }
            Notice.FORGOTTEN_EXIT -> "¿Ya saliste?" to (
                if (end != null) "Tu turno terminaba a las $end. Marca la salida para que se pague bien."
                else "Tu turno sigue abierto. Marca la salida para que se pague bien."
                )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notice.notificationId, notification)
    }
}

/** Android borra las alarmas al reiniciar el teléfono o actualizar la app: aquí se vuelven a programar. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as TurnosApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.reminder.resync(app)
            } finally {
                pending.finish()
            }
        }
    }
}
