package com.kmanu.turnos

import android.app.Application
import com.kmanu.turnos.data.SettingsStore
import com.kmanu.turnos.data.ShiftRepository
import com.kmanu.turnos.data.createDatabase

class TurnosApplication : Application() {
    val repository by lazy { ShiftRepository(createDatabase(this).shiftDao()) }
    val settingsStore by lazy { SettingsStore(this) }
}
