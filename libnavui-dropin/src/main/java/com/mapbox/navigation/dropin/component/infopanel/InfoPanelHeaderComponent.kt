package com.mapbox.navigation.dropin.component.infopanel

import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.dropin.R
import com.mapbox.navigation.dropin.databinding.MapboxInfoPanelHeaderLayoutBinding
import com.mapbox.navigation.ui.app.internal.Store
import com.mapbox.navigation.ui.app.internal.destination.DestinationAction
import com.mapbox.navigation.ui.app.internal.navigation.NavigationState
import com.mapbox.navigation.ui.app.internal.navigation.NavigationStateAction
import com.mapbox.navigation.ui.app.internal.routefetch.RoutePreviewAction
import com.mapbox.navigation.ui.app.internal.routefetch.RoutePreviewState
import com.mapbox.navigation.ui.app.internal.routefetch.RoutesAction
import com.mapbox.navigation.ui.base.lifecycle.UIComponent
import com.mapbox.navigation.utils.internal.ifNonNull
import com.mapbox.navigation.utils.internal.toPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

@ExperimentalPreviewMapboxNavigationAPI
internal class InfoPanelHeaderComponent(
    private val store: Store,
    private val binding: MapboxInfoPanelHeaderLayoutBinding,
    @StyleRes private val routePreviewStyle: Int,
    @StyleRes private val endNavigationStyle: Int,
    @StyleRes private val startNavigationStyle: Int,
) : UIComponent() {

    private val resources get() = binding.root.resources

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        super.onAttached(mapboxNavigation)

        binding.routePreview.updateStyle(routePreviewStyle)
        binding.endNavigation.updateStyle(endNavigationStyle)
        binding.startNavigation.updateStyle(startNavigationStyle)

        // views visibility
        store.select { it.navigation }.observe {
            binding.poiName.isVisible = it == NavigationState.DestinationPreview
            binding.routePreview.isVisible = it == NavigationState.DestinationPreview
            binding.startNavigation.isVisible = it == NavigationState.DestinationPreview ||
                it == NavigationState.RoutePreview
            binding.endNavigation.isVisible = it == NavigationState.ActiveNavigation ||
                it == NavigationState.Arrival
            binding.tripProgressLayout.isVisible = it == NavigationState.ActiveNavigation ||
                it == NavigationState.RoutePreview
            binding.arrivedText.isVisible = it == NavigationState.Arrival
        }

        store.select { it.destination }.observe {
            val placeName = it?.features?.firstOrNull()?.placeName()
            binding.poiName.text = placeName
                ?: resources.getString(R.string.mapbox_drop_in_dropped_pin)
        }

        binding.routePreview.setOnClickListener {
            coroutineScope.launch {
                if (fetchRouteIfNeeded()) {
                    store.dispatch(NavigationStateAction.Update(NavigationState.RoutePreview))
                }
            }
        }

        binding.startNavigation.setOnClickListener {
            coroutineScope.launch {
                if (
                    fetchRouteIfNeeded() &&
                    store.state.value.previewRoutes is RoutePreviewState.Ready
                ) {
                    store.dispatch(
                        RoutesAction.SetRoutes(
                            (store.state.value.previewRoutes as RoutePreviewState.Ready).routes
                        )
                    )
                    store.dispatch(NavigationStateAction.Update(NavigationState.ActiveNavigation))
                }
            }
        }

        binding.endNavigation.setOnClickListener {
            store.dispatch(RoutesAction.SetRoutes(emptyList()))
            store.dispatch(RoutePreviewAction.Ready(emptyList()))
            store.dispatch(DestinationAction.SetDestination(null))
            store.dispatch(NavigationStateAction.Update(NavigationState.FreeDrive))
        }
    }

    /**
     * Dispatch FetchPoints action and wait for RoutePreviewState.Ready.
     * Method returns immediately if already in RoutePreviewState.Ready or RoutePreviewState.Fetching, or if
     * required location or destination data is missing.
     *
     * @return `true` once in RoutePreviewState.Ready state, otherwise `false`
     */
    private suspend fun fetchRouteIfNeeded(): Boolean {
        val storeState = store.state.value
        if (storeState.previewRoutes is RoutePreviewState.Ready) return true
        if (storeState.previewRoutes is RoutePreviewState.Fetching) return false

        return ifNonNull(
            storeState.location?.enhancedLocation?.toPoint(),
            storeState.destination
        ) { lastPoint, destination ->
            store.dispatch(RoutePreviewAction.FetchPoints(listOf(lastPoint, destination.point)))
            waitForReady()
        } ?: false
    }

    private suspend fun waitForReady(): Boolean {
        store.select { it.previewRoutes }.takeWhile { it is RoutePreviewState.Fetching }.collect()
        return store.state.value.previewRoutes is RoutePreviewState.Ready
    }
}
