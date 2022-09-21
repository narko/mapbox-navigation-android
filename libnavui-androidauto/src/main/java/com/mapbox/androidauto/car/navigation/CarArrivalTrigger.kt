package com.mapbox.androidauto.car.navigation

import com.mapbox.androidauto.ArrivalState
import com.mapbox.androidauto.MapboxCarApp
import com.mapbox.androidauto.internal.logAndroidAuto
import com.mapbox.maps.extension.androidauto.MapboxCarMapObserver
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver

/**
 * When attached this will observe when the final destination is reached and change the car app
 * state to the [ArrivalState]. This is not a [MapboxCarMapObserver] because arrival can happen
 * even when the map is not showing.
 */
class CarArrivalTrigger : MapboxNavigationObserver {

    private val arrivalObserver = object : ArrivalObserver {

        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            triggerArrival()
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
            // not implemented
        }

        override fun onWaypointArrival(routeProgress: RouteProgress) {
            // not implemented
        }
    }

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        mapboxNavigation.registerArrivalObserver(arrivalObserver)
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
        mapboxNavigation.unregisterArrivalObserver(arrivalObserver)
    }

    /**
     * Manually trigger the arrival.
     */
    fun triggerArrival() {
        logAndroidAuto("CarArrivalTrigger triggerArrival")
        MapboxCarApp.updateCarAppState(ArrivalState)
    }
}