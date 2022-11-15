package com.mapbox.navigation.dropin.infopanel

import android.view.LayoutInflater
import android.view.ViewGroup
import com.mapbox.navigation.core.internal.extensions.navigationListOf
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.dropin.databinding.MapboxInfoPanelHeaderRoutePreviewLayoutBinding
import com.mapbox.navigation.dropin.internal.extensions.startNavigationButtonComponent
import com.mapbox.navigation.dropin.internal.extensions.tripProgressComponent
import com.mapbox.navigation.dropin.navigationview.NavigationViewContext
import com.mapbox.navigation.ui.base.lifecycle.UIBinder

internal class InfoPanelHeaderRoutesPreviewBinder(
    private val context: NavigationViewContext
) : UIBinder {

    override fun bind(viewGroup: ViewGroup): MapboxNavigationObserver {
        viewGroup.removeAllViews()
        val binding = MapboxInfoPanelHeaderRoutePreviewLayoutBinding.inflate(
            LayoutInflater.from(viewGroup.context),
            viewGroup
        )

        return context.run {
            navigationListOf(
                tripProgressComponent(binding.tripProgressLayout),
                startNavigationButtonComponent(binding.startNavigationContainer)
            )
        }
    }
}
