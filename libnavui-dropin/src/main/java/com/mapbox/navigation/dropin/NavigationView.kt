package com.mapbox.navigation.dropin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.internal.extensions.attachCreated
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.dropin.actionbutton.ActionButtonsCoordinator
import com.mapbox.navigation.dropin.analytics.AnalyticsComponent
import com.mapbox.navigation.dropin.backpress.BackPressedComponent
import com.mapbox.navigation.dropin.databinding.MapboxNavigationViewLayoutBinding
import com.mapbox.navigation.dropin.infopanel.InfoPanelCoordinator
import com.mapbox.navigation.dropin.internal.extensions.navigationViewAccessToken
import com.mapbox.navigation.dropin.internal.extensions.toComponentActivity
import com.mapbox.navigation.dropin.internal.extensions.toViewModelStoreOwner
import com.mapbox.navigation.dropin.maneuver.ManeuverCoordinator
import com.mapbox.navigation.dropin.map.MapLayoutCoordinator
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.dropin.map.scalebar.ScalebarPlaceholderCoordinator
import com.mapbox.navigation.dropin.navigationview.MapboxNavigationViewApi
import com.mapbox.navigation.dropin.navigationview.NavigationViewContext
import com.mapbox.navigation.dropin.navigationview.NavigationViewListener
import com.mapbox.navigation.dropin.navigationview.NavigationViewModel
import com.mapbox.navigation.dropin.permission.LocationPermissionComponent
import com.mapbox.navigation.dropin.roadname.RoadNameCoordinator
import com.mapbox.navigation.dropin.speedlimit.SpeedLimitCoordinator
import com.mapbox.navigation.dropin.tripsession.TripSessionComponent
import com.mapbox.navigation.ui.app.internal.SharedApp
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import com.mapbox.navigation.ui.utils.internal.lifecycle.ViewLifecycleRegistry

/**
 * An Android [View] that creates the drop-in UI.
 *
 * [NavigationView] when started will ask for [Manifest.permission.ACCESS_FINE_LOCATION] and
 * [Manifest.permission.ACCESS_COARSE_LOCATION].
 * If denied the view will point to a null island.
 * If accepted, this view will always start in Free Drive state with the puck pointing to
 * users current location.
 *
 * The [NavigationView] is spread across the following states:
 * - Free Drive: A user will be in free drive state when there is no destination or route set.
 * - DestinationPreview: A user will be in destination preview when a destination is set.
 * - RoutePreview: A user will be in route preview when a destination is set along with a route
 * joining the origin/destination pair.
 * - Active Guidance: A user will be in active guidance when a single destination or multi waypoint
 * route is set and the user has explicitly initiated start navigation action.
 * - Arrival: A user will be in arrival state when they are about to reach the final destination.
 *
 * [NavigationView] by default does not uses [MapboxReplayer], rather it uses the actual location
 * engine and hooks onto the users current location derived from device GPS. However, this can be
 * overridden and instead you can simulate active guidance by enabling `isReplayEnabled`.
 *
 * Note: [NavigationView] is `Experimental` and the API(s) are subject to breaking changes.
 *
 * A Mapbox access token must also be set by the developer (to initialize navigation).
 *
 * @param viewModelStoreOwner Defaults to store owner tied to the hosting [Activity].
 * If you prefer the lifecycle of the [ViewModel]s internal to [NavigationView] to be tied to the hosting [Fragment] instead,
 * provide that [Fragment] as an argument. Providing a [Fragment]'s store owner allows to achieve tighter memory control if the resources
 * used by the [NavigationView] shouldn't outlive its hosting [Fragment] (in anticipation of potential [Fragment] recreation).
 */
@ExperimentalPreviewMapboxNavigationAPI
@UiThread
class NavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    accessToken: String = attrs.navigationViewAccessToken(context),
    viewModelStoreOwner: ViewModelStoreOwner = context.toViewModelStoreOwner()
) : FrameLayout(context, attrs), LifecycleOwner {

    private val binding: MapboxNavigationViewLayoutBinding =
        MapboxNavigationViewLayoutBinding.inflate(
            LayoutInflater.from(context),
            this
        )

    private val viewLifecycleRegistry: ViewLifecycleRegistry = ViewLifecycleRegistry(
        view = this,
        localLifecycleOwner = this
    )

    private val viewModelProvider by lazy {
        ViewModelProvider(viewModelStoreOwner)
    }

    private val viewModel: NavigationViewModel by lazyViewModel()

    private val navigationContext = NavigationViewContext(
        context = context,
        lifecycleOwner = this,
        viewModel = viewModel,
    )

    init {
        keepScreenOn = true
        captureSystemBarsInsets()

        SharedApp.setup()
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(
                NavigationOptions.Builder(context)
                    .accessToken(accessToken)
                    .build()
            )
        }

        MapboxNavigationApp.attach(this)

        val componentActivity = context.toComponentActivity()
        attachCreated(
            AnalyticsComponent(),
            LocationPermissionComponent(componentActivity, navigationContext.store),
            TripSessionComponent(lifecycle, navigationContext.store),
            MapLayoutCoordinator(navigationContext, binding),
            BackPressedComponent(
                componentActivity.onBackPressedDispatcher,
                navigationContext.store,
                lifecycleOwner = this,
            ),
            ScalebarPlaceholderCoordinator(navigationContext, binding.scalebarLayout),
            ManeuverCoordinator(navigationContext, binding.guidanceLayout),
            InfoPanelCoordinator(
                navigationContext,
                binding.infoPanelLayout,
                binding.guidelineBottom
            ),
            ActionButtonsCoordinator(navigationContext, binding.actionListLayout),
            SpeedLimitCoordinator(navigationContext, binding.speedLimitLayout),
            RoadNameCoordinator(navigationContext, binding.roadNameLayout),
            LeftFrameCoordinator(navigationContext, binding.emptyLeftContainer),
            RightFrameCoordinator(navigationContext, binding.emptyRightContainer)
        )
    }

    private fun captureSystemBarsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navigationContext.systemBarsInsets.value = systemBarsInsets
            insets
        }
    }

    /**
     * Provides access to [ViewLifecycleRegistry]
     */
    override fun getLifecycle(): Lifecycle = viewLifecycleRegistry

    /**
     * Api for changing navigation state.
     */
    val api: NavigationViewApi = MapboxNavigationViewApi(navigationContext.store)

    /**
     * Customize view by providing your own [UIBinder] components.
     */
    fun customizeViewBinders(action: ViewBinderCustomization.() -> Unit) {
        navigationContext.applyBinderCustomization(action)
    }

    /**
     * Customize standalone UI components styles by providing your own custom styles.
     */
    fun customizeViewStyles(action: ViewStyleCustomization.() -> Unit) {
        navigationContext.applyStyleCustomization(action)
    }

    /**
     * Provide custom map styles, route line and arrow options.
     */
    fun customizeViewOptions(action: ViewOptionsCustomization.() -> Unit) {
        navigationContext.applyOptionsCustomization(action)
    }

    /**
     * Add a listener that will be notified when [NavigationView] changes.
     */
    fun addListener(listener: NavigationViewListener) {
        navigationContext.listenerRegistry.registerListener(listener)
    }

    /**
     * Remove a listener for [NavigationView] changes.
     */
    fun removeListener(listener: NavigationViewListener) {
        navigationContext.listenerRegistry.unregisterListener(listener)
    }

    /**
     * Registers [MapViewObserver].
     */
    fun registerMapObserver(observer: MapViewObserver) {
        navigationContext.mapViewOwner.registerObserver(observer)
    }

    /**
     * Unregisters [MapViewObserver].
     */
    fun unregisterMapObserver(observer: MapViewObserver) {
        navigationContext.mapViewOwner.unregisterObserver(observer)
    }

    /**
     * Set [RouteOptionsInterceptor]. It allows to modify default [RouteOptions.Builder].
     */
    fun setRouteOptionsInterceptor(routeOptionsInterceptor: RouteOptionsInterceptor?) {
        if (routeOptionsInterceptor != null) {
            navigationContext.routeOptionsProvider.setInterceptor { routeOptions ->
                routeOptionsInterceptor.intercept(routeOptions)
            }
        } else {
            navigationContext.routeOptionsProvider.setInterceptor { it }
        }
    }

    private inline fun <reified T : ViewModel> lazyViewModel(): Lazy<T> = lazy {
        viewModelProvider[T::class.java]
    }
}
