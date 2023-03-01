package com.mapbox.navigation.core.routerefresh

import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.testing.factories.createDirectionsRoute
import com.mapbox.navigation.testing.factories.createRouteOptions
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRefreshValidatorTest {

    private val noUuidMessage = "DirectionsRoute#requestUuid is blank. " +
        "This can be caused by a route being generated by " +
        "an Onboard router (in offline mode). " +
        "Make sure to switch to an Offboard route when possible, " +
        "only Offboard routes support the refresh feature."

    private val route = mockk<NavigationRoute>(relaxed = true)

    @Test
    fun `validateRoute valid`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = true)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = "uuid")

        assertEquals(
            RouteRefreshValidator.RouteValidationResult.Valid,
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `validateRoute enableRefresh is null`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = null)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = "uuid")

        assertEquals(
            RouteRefreshValidator.RouteValidationResult
                .Invalid("RouteOptions#enableRefresh is false"),
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `validateRoute enableRefresh is false`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = false)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = "uuid")

        assertEquals(
            RouteRefreshValidator.RouteValidationResult
                .Invalid("RouteOptions#enableRefresh is false"),
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `validateRoute requestUuid is null`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = true)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = null)

        assertEquals(
            RouteRefreshValidator.RouteValidationResult.Invalid(noUuidMessage),
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `validateRoute requestUuid is empty`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = true)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = "")

        assertEquals(
            RouteRefreshValidator.RouteValidationResult.Invalid(noUuidMessage),
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `validateRoute requestUuid is blank`() {
        every { route.routeOptions } returns createRouteOptions(enableRefresh = true)
        every { route.directionsRoute } returns createDirectionsRoute(requestUuid = "   ")

        assertEquals(
            RouteRefreshValidator.RouteValidationResult.Invalid(noUuidMessage),
            RouteRefreshValidator.validateRoute(route)
        )
    }

    @Test
    fun `joinValidationErrorMessages empty list`() {
        assertEquals("", RouteRefreshValidator.joinValidationErrorMessages(emptyList()))
    }

    @Test
    fun `joinValidationErrorMessages all valid`() {
        val list = listOf<Pair<RouteRefreshValidator.RouteValidationResult, NavigationRoute>>(
            RouteRefreshValidator.RouteValidationResult.Valid to mockk(relaxed = true),
            RouteRefreshValidator.RouteValidationResult.Valid to mockk(relaxed = true),
            RouteRefreshValidator.RouteValidationResult.Valid to mockk(relaxed = true),
        )
        assertEquals("", RouteRefreshValidator.joinValidationErrorMessages(list))
    }

    @Test
    fun `joinValidationErrorMessages one invalid`() {
        val list = listOf<Pair<RouteRefreshValidator.RouteValidationResult, NavigationRoute>>(
            RouteRefreshValidator.RouteValidationResult.Valid to mockk(relaxed = true),
            RouteRefreshValidator.RouteValidationResult.Valid to mockk(relaxed = true),
            RouteRefreshValidator.RouteValidationResult.Invalid("some reason") to
                mockk(relaxed = true) { every { id } returns "id#0" }
        )
        assertEquals("id#0 some reason", RouteRefreshValidator.joinValidationErrorMessages(list))
    }

    @Test
    fun `joinValidationErrorMessages all invalid`() {
        val list = listOf<Pair<RouteRefreshValidator.RouteValidationResult, NavigationRoute>>(
            RouteRefreshValidator.RouteValidationResult.Invalid("reason 1") to
                mockk(relaxed = true) { every { id } returns "id#0" },
            RouteRefreshValidator.RouteValidationResult.Invalid("reason 2") to
                mockk(relaxed = true) { every { id } returns "id#1" },
            RouteRefreshValidator.RouteValidationResult.Invalid("reason 3") to
                mockk(relaxed = true) { every { id } returns "id#2" }
        )
        assertEquals(
            "id#0 reason 1. id#1 reason 2. id#2 reason 3",
            RouteRefreshValidator.joinValidationErrorMessages(list)
        )
    }
}
