package com.mapbox.navigation.dropin

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.dropin.component.infopanel.InfoPanelBehavior
import com.mapbox.navigation.dropin.util.TestStore
import com.mapbox.navigation.testing.MainCoroutineRule
import com.mapbox.navigation.ui.app.internal.State
import com.mapbox.navigation.ui.app.internal.camera.TargetCameraMode
import com.mapbox.navigation.ui.app.internal.destination.Destination
import com.mapbox.navigation.ui.app.internal.navigation.NavigationState
import com.mapbox.navigation.ui.app.internal.routefetch.RoutePreviewState
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@ExperimentalPreviewMapboxNavigationAPI
class NavigationViewListenerRegistryTest {

    @get:Rule
    var coroutineRule = MainCoroutineRule()

    private lateinit var sut: NavigationViewListenerRegistry
    private lateinit var testStore: TestStore
    private lateinit var infoPanelBehaviorFlow: MutableStateFlow<Int?>
    private lateinit var testListener: NavigationViewListener

    @Before
    fun setUp() {
        testStore = TestStore()
        infoPanelBehaviorFlow = MutableStateFlow(null)
        val mockInfoPanelBehavior = mockk<InfoPanelBehavior> {
            every { infoPanelBehavior } returns infoPanelBehaviorFlow.asStateFlow()
        }
        testListener = spyk(object : NavigationViewListener() {})

        sut = NavigationViewListenerRegistry(
            testStore,
            mockInfoPanelBehavior,
            coroutineRule.coroutineScope
        )
    }

    @Test
    fun onDestinationChanged() {
        sut.registerListener(testListener)

        val point = Point.fromLngLat(1.0, 2.0)
        val destination = Destination(Point.fromLngLat(1.0, 2.0))
        testStore.setState(State(destination = destination))

        verifyOrder {
            testListener.onDestinationChanged(null)
            testListener.onDestinationChanged(point)
        }
    }

    @Test
    fun onFreeDrive() {
        sut.registerListener(testListener)

        val navigationState = NavigationState.FreeDrive
        testStore.setState(State(navigation = navigationState))

        verify { testListener.onFreeDrive() }
    }

    @Test
    fun onDestinationPreview() {
        sut.registerListener(testListener)

        val navigationState = NavigationState.DestinationPreview
        testStore.setState(State(navigation = navigationState))

        verify { testListener.onDestinationPreview() }
    }

    @Test
    fun onRoutePreview() {
        sut.registerListener(testListener)

        val navigationState = NavigationState.RoutePreview
        testStore.setState(State(navigation = navigationState))

        verify { testListener.onRoutePreview() }
    }

    @Test
    fun onActiveNavigation() {
        sut.registerListener(testListener)

        val navigationState = NavigationState.ActiveNavigation
        testStore.setState(State(navigation = navigationState))

        verify { testListener.onActiveNavigation() }
    }

    @Test
    fun onArrival() {
        sut.registerListener(testListener)

        val navigationState = NavigationState.Arrival
        testStore.setState(State(navigation = navigationState))

        verify { testListener.onArrival() }
    }

    @Test
    fun onIdleCameraMode() {
        sut.registerListener(testListener)

        val cameraMode = TargetCameraMode.Idle
        val cameraState = testStore.state.value.camera.copy(cameraMode = cameraMode)
        testStore.setState(State(camera = cameraState))

        verify { testListener.onIdleCameraMode() }
    }

    @Test
    fun onFollowingCameraMode() {
        sut.registerListener(testListener)

        val cameraMode = TargetCameraMode.Following
        val cameraState = testStore.state.value.camera.copy(cameraMode = cameraMode)
        testStore.setState(State(camera = cameraState))

        verify { testListener.onFollowingCameraMode() }
    }

    @Test
    fun onOverviewCameraMode() {
        sut.registerListener(testListener)

        val cameraMode = TargetCameraMode.Overview
        val cameraState = testStore.state.value.camera.copy(cameraMode = cameraMode)
        testStore.setState(State(camera = cameraState))

        verify { testListener.onOverviewCameraMode() }
    }

    @Test
    fun onCameraPaddingChanged() {
        sut.registerListener(testListener)

        val padding = EdgeInsets(1.0, 2.0, 3.0, 4.0)
        val cameraState = testStore.state.value.camera.copy(cameraPadding = padding)
        testStore.setState(State(camera = cameraState))

        verify { testListener.onCameraPaddingChanged(padding) }
    }

    @Test
    fun onAudioGuidanceStateChanged() {
        sut.registerListener(testListener)

        val isMuted = true
        val audioState = testStore.state.value.audio.copy(isMuted = isMuted)
        testStore.setState(State(audio = audioState))

        verify { testListener.onAudioGuidanceStateChanged(isMuted) }
    }

    @Test
    fun onRouteFetchedSuccessful_routesEmpty() {
        sut.registerListener(testListener)

        val routes = emptyList<NavigationRoute>()
        testStore.setState(State(previewRoutes = RoutePreviewState.Ready(routes)))

        verify {
            testListener.onRouteFetchSuccessful(routes)
        }
    }

    @Test
    fun onRouteFetchedSuccessful_routesNotEmpty() {
        sut.registerListener(testListener)

        val routes = listOf<NavigationRoute>(mockk())
        testStore.setState(State(previewRoutes = RoutePreviewState.Ready(routes)))

        verify {
            testListener.onRouteFetchSuccessful(routes)
        }
    }

    @Test
    fun onRouteFetchedFailed() {
        sut.registerListener(testListener)

        val reasons = listOf<RouterFailure>(mockk())
        val options = mockk<RouteOptions>()
        testStore.setState(State(previewRoutes = RoutePreviewState.Failed(reasons, options)))

        verify {
            testListener.onRouteFetchFailed(reasons, options)
        }
    }

    @Test
    fun onRouteFetchedCanceled() {
        sut.registerListener(testListener)

        val origin = mockk<RouterOrigin>()
        val options = mockk<RouteOptions>()
        testStore.setState(State(previewRoutes = RoutePreviewState.Canceled(options, origin)))

        verify {
            testListener.onRouteFetchCanceled(options, origin)
        }
    }

    @Test
    fun onRouteFetching() {
        sut.registerListener(testListener)

        val id = 1L
        testStore.setState(State(previewRoutes = RoutePreviewState.Fetching(id)))

        verify {
            testListener.onRouteFetching(id)
        }
    }

    @Test
    fun onInfoPanelHidden() {
        sut.registerListener(testListener)
        val newState = BottomSheetBehavior.STATE_HIDDEN

        infoPanelBehaviorFlow.value = newState

        verify {
            testListener.onInfoPanelHidden()
        }
    }

    @Test
    fun onInfoPanelExpanded() {
        sut.registerListener(testListener)
        val newState = BottomSheetBehavior.STATE_EXPANDED

        infoPanelBehaviorFlow.value = newState

        verify {
            testListener.onInfoPanelExpanded()
        }
    }

    @Test
    fun onInfoPanelCollapsed() {
        sut.registerListener(testListener)
        val newState = BottomSheetBehavior.STATE_COLLAPSED

        infoPanelBehaviorFlow.value = newState

        verify {
            testListener.onInfoPanelCollapsed()
        }
    }
}
