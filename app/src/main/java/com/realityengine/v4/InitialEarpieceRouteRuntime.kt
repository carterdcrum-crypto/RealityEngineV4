package com.realityengine.v4

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import java.util.Collections
import java.util.WeakHashMap

/**
 * Gives every newly connected call a normal phone-call starting point: the handset earpiece.
 *
 * This is intentionally a one-time preference per Call. Once earpiece routing succeeds, this
 * runtime stops touching that call's audio route so Speaker/Audio selections remain user-owned.
 */
object InitialEarpieceRouteRuntime {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val armedCalls = Collections.newSetFromMap(WeakHashMap<Call, Boolean>())
    private val routedCalls = Collections.newSetFromMap(WeakHashMap<Call, Boolean>())
    private var installed = false

    private val registryListener: () -> Unit = {
        mainHandler.post { armPrimaryIfNeeded() }
    }

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        CallSessionRegistry.addListener(registryListener)
        mainHandler.post { armPrimaryIfNeeded() }
    }

    private fun armPrimaryIfNeeded() {
        val call = CallSessionRegistry.primary() ?: return
        if (call.state != Call.STATE_ACTIVE || routedCalls.contains(call) || !armedCalls.add(call)) return

        // Telecom/Samsung can report ACTIVE slightly before the endpoint list is populated.
        // Retry only during the initial handoff window; after success, routing is entirely manual.
        longArrayOf(0L, 120L, 350L, 800L, 1_500L, 3_000L).forEach { delay ->
            mainHandler.postDelayed({ tryRouteToEarpiece(call) }, delay)
        }
    }

    private fun tryRouteToEarpiece(call: Call) {
        if (routedCalls.contains(call)) return
        if (CallSessionRegistry.primary() !== call || call.state != Call.STATE_ACTIVE) return
        val service = RealityInCallService.instance ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (service.currentCallEndpointSnapshot()?.endpointType == CallEndpoint.TYPE_EARPIECE) {
                routedCalls.add(call)
                return
            }
            val earpiece = service.availableCallEndpointsSnapshot()
                .firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
                ?: return
            service.selectCallEndpoint(earpiece) { error ->
                if (error == null && CallSessionRegistry.primary() === call) {
                    routedCalls.add(call)
                }
            }
            return
        }

        val audio = service.callAudioState ?: return
        if (audio.route == CallAudioState.ROUTE_EARPIECE) {
            routedCalls.add(call)
            return
        }
        if (audio.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) {
            @Suppress("DEPRECATION")
            service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            routedCalls.add(call)
        }
    }
}
