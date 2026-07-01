package com.yourapp.util

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Watches Android's thermal state. When phone gets hot, we pause live captions (recording continues!)
 * to give the CPU a break — otherwise the app throttles and everything gets laggy.
 *
 * Usage:
 *   ThermalGuard(context).observe().collect { state ->
 *     when (state) {
 *       ThermalGuard.State.NORMAL, ThermalGuard.State.LIGHT -> resumeLiveCaptions()
 *       ThermalGuard.State.MODERATE                        -> reduceThreadCount()
 *       ThermalGuard.State.SEVERE, ThermalGuard.State.CRITICAL -> pauseLiveCaptions()
 *     }
 *   }
 */
class ThermalGuard(private val context: Context) {

    enum class State { NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL }

    fun observe(): Flow<State> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            trySend(State.NORMAL); awaitClose { }; return@callbackFlow
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(map(status))
        }

        pm.addThermalStatusListener(listener)
        trySend(map(pm.currentThermalStatus))

        awaitClose {
            pm.removeThermalStatusListener(listener)
        }
    }

    private fun map(status: Int): State = when (status) {
        PowerManager.THERMAL_STATUS_NONE      -> State.NORMAL
        PowerManager.THERMAL_STATUS_LIGHT     -> State.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE  -> State.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN  -> State.CRITICAL
        else                                  -> State.NORMAL
    }
}
