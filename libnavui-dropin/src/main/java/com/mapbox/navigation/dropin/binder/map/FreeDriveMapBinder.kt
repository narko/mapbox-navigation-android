package com.mapbox.navigation.dropin.binder.map

import com.mapbox.maps.MapView
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.dropin.DropInNavigationViewContext
import com.mapbox.navigation.dropin.binder.Binder
import com.mapbox.navigation.dropin.binder.navigationListOf
import com.mapbox.navigation.dropin.component.camera.DropInCameraMode
import com.mapbox.navigation.dropin.component.camera.DropInNavigationCamera
import com.mapbox.navigation.dropin.component.location.LocationPuck

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
internal class FreeDriveMapBinder(
    private val navigationViewContext: DropInNavigationViewContext,
) : Binder<MapView> {

    private val cameraState = navigationViewContext.viewModel.cameraState

    override fun bind(mapView: MapView): MapboxNavigationObserver {
        cameraState.setCameraMode(DropInCameraMode.OVERVIEW)
        return navigationListOf(
            LocationPuck(mapView),
            DropInNavigationCamera(
                navigationViewContext.viewModel.cameraState,
                mapView
            ),
        )
    }
}
