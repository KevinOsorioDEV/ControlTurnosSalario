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
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftPlanner
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CHANNEL_ID = "salida"
private const val NOTIFICATION_ID = 1
private const val EXTRA_END = "salida_programada"
private val END_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * Avisa si el turno abierto sigue sin salida media hora después de lo programado. La alarma no
 * es exacta (no pide permisos especiales): basta con que llegue unos minutos después.
 */
class ShiftReminder(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(end: LocalDateTime? = null): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ShiftReminderReceiver::class.java).apply { end?.let { putExtra(EXTRA_END, it.format(END_FORMAT)) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Programa el aviso del turno abierto, o lo quita si no hay ninguno. */
    fun sync(open: Shift?) {
        alarms.cancel(pendingIntent())
        // Con la app abierta ya se ve el turno: el aviso anterior sobra.
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        if (open == null) return
        val at = ShiftPlanner.reminderAt(open)
        // Si ya pasó, la app pregunta por la salida olvidada al marcarla.
        if (at.isBefore(LocalDateTime.now())) return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(open.scheduledEnd))
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
            NotificationChannel(CHANNEL_ID, "Recordatorio de salida", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisa si olvidaste marcar la salida de tu turno."
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val end = intent.getStringExtra(EXTRA_END)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("¿Ya saliste?")
            .setContentText(
                if (end != null) "Tu turno terminaba a las $end. Marca la salida para que se pague bien."
                else "Tu turno sigue abierto. Marca la salida para que se pague bien.",
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
