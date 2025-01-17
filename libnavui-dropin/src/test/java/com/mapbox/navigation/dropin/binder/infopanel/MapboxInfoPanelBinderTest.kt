package com.mapbox.navigation.dropin.binder.infopanel

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.core.internal.extensions.MapboxNavigationObserverChain
import com.mapbox.navigation.dropin.NavigationViewContext
import com.mapbox.navigation.dropin.component.infopanel.InfoPanelComponent
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
@RunWith(RobolectricTestRunner::class)
internal class MapboxInfoPanelBinderTest {

    private lateinit var ctx: Context
    private lateinit var sut: MapboxInfoPanelBinder

    @MockK
    lateinit var mockNavContext: NavigationViewContext

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        ctx = ApplicationProvider.getApplicationContext()

        sut = MapboxInfoPanelBinder()
    }

    @Test
    fun `bind should return MapboxNavigationObserver with InfoPanelComponent`() {
        sut.setNavigationViewContext(mockNavContext)

        val observers = (sut.bind(FrameLayout(ctx)) as MapboxNavigationObserverChain).toList()

        assertEquals(2, observers.size)
        assertNotNull(
            "Expected InfoPanelComponent",
            observers.firstOrNull { it is InfoPanelComponent }
        )
    }

    @Test
    @Suppress("MaxLineLength")
    fun `bind should NOT return MapboxNavigationObserver with InfoPanelComponent if NavigationViewContext is not set`() {
        val observers = (sut.bind(FrameLayout(ctx)) as MapboxNavigationObserverChain).toList()

        assertNull(observers.firstOrNull { it is InfoPanelComponent })
    }
}
