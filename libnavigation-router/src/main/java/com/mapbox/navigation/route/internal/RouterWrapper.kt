/**
 * Tampering with any file that contains billing code is a violation of Mapbox Terms of Service and will result in enforcement of the penalties stipulated in the ToS.
 */

package com.mapbox.navigation.route.internal

import com.mapbox.annotation.module.MapboxModule
import com.mapbox.annotation.module.MapboxModuleType
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directionsrefresh.v1.models.DirectionsRefreshResponse
import com.mapbox.navigation.base.ExperimentalMapboxNavigationAPI
import com.mapbox.navigation.base.internal.route.InternalRouter
import com.mapbox.navigation.base.internal.utils.parseDirectionsResponse
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouter
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.NavigationRouterRefreshCallback
import com.mapbox.navigation.base.route.NavigationRouterRefreshError
import com.mapbox.navigation.base.route.RouteRefreshCallback
import com.mapbox.navigation.base.route.RouteRefreshError
import com.mapbox.navigation.base.route.RouterCallback
import com.mapbox.navigation.base.route.RouterFactory
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.route.toDirectionsRoutes
import com.mapbox.navigation.base.route.toNavigationRoute
import com.mapbox.navigation.navigator.internal.mapToRoutingMode
import com.mapbox.navigation.navigator.internal.mapToSdkRouteOrigin
import com.mapbox.navigation.route.internal.util.ACCESS_TOKEN_QUERY_PARAM
import com.mapbox.navigation.route.internal.util.redactQueryParam
import com.mapbox.navigation.utils.internal.ThreadController
import com.mapbox.navigation.utils.internal.logI
import com.mapbox.navigation.utils.internal.logW
import com.mapbox.navigator.RouteRefreshOptions
import com.mapbox.navigator.RouterErrorType
import com.mapbox.navigator.RouterInterface
import com.mapbox.navigator.RoutingProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@MapboxModule(MapboxModuleType.NavigationRouter)
class RouterWrapper(
    private val accessToken: String,
    private val router: RouterInterface,
    private val threadController: ThreadController,
) : NavigationRouter, InternalRouter {

    private val mainJobControl by lazy { threadController.getMainScopeAndRootJob() }

    override fun getRoute(routeOptions: RouteOptions, callback: NavigationRouterCallback): Long {
        val routeUrl = routeOptions.toUrl(accessToken).toString()

        return router.getRoute(routeUrl) { result, origin ->
            val urlWithoutToken = URL(routeUrl.redactQueryParam(ACCESS_TOKEN_QUERY_PARAM))
            result.fold(
                {
                    mainJobControl.scope.launch {
                        if (it.type == RouterErrorType.REQUEST_CANCELLED) {
                            logI(
                                TAG,
                                """
                                    Route request cancelled:
                                    $routeOptions
                                    $origin
                                """.trimIndent()
                            )
                            callback.onCanceled(routeOptions, origin.mapToSdkRouteOrigin())
                        } else {
                            val failureReasons = listOf(
                                RouterFailure(
                                    url = urlWithoutToken,
                                    routerOrigin = origin.mapToSdkRouteOrigin(),
                                    message = it.message,
                                    code = it.code
                                )
                            )

                            logW(
                                TAG,
                                """
                                    Route request failed with:
                                    $failureReasons
                                """.trimIndent()
                            )

                            callback.onFailure(failureReasons, routeOptions)
                        }
                    }
                },
                {
                    mainJobControl.scope.launch {
                        parseDirectionsResponse(
                            ThreadController.IODispatcher,
                            it,
                            routeOptions
                        ).fold(
                            { throwable ->
                                callback.onFailure(
                                    listOf(
                                        RouterFailure(
                                            urlWithoutToken,
                                            origin.mapToSdkRouteOrigin(),
                                            "failed for response: $it",
                                            throwable = throwable
                                        )
                                    ),
                                    routeOptions
                                )
                            },
                            { response ->
                                logI(TAG, "Response metadata: ${response.metadata()}")
                                val navigationRoutes = mutableListOf<NavigationRoute>()
                                for (i in 0 until response.routes().size) {
                                    navigationRoutes.add(
                                        i,
                                        NavigationRoute(
                                            directionsResponse = response,
                                            routeIndex = i,
                                            routeOptions = routeOptions
                                        )
                                    )
                                }
                                callback.onRoutesReady(
                                    navigationRoutes,
                                    origin.mapToSdkRouteOrigin()
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    override fun getRoute(routeOptions: RouteOptions, callback: RouterCallback): Long {
        return getRoute(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    callback.onRoutesReady(routes.toDirectionsRoutes(), routerOrigin)
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    callback.onFailure(reasons, routeOptions)
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    callback.onCanceled(routeOptions, routerOrigin)
                }
            }
        )
    }

    @OptIn(ExperimentalMapboxNavigationAPI::class)
    override fun getRouteRefresh(
        route: NavigationRoute,
        legIndex: Int,
        callback: NavigationRouterRefreshCallback
    ): Long {
        val routeOptions = route.routeOptions
        val requestUuid = route.directionsResponse.uuid()
        val routeIndex = route.routeIndex
        if (requestUuid == null || requestUuid.isBlank()) {
            val errorMessage =
                """
                   Route refresh failed because of a empty or null param:
                   requestUuid = $requestUuid
                """.trimIndent()

            logW(TAG, errorMessage)

            callback.onFailure(
                RouterFactory.buildNavigationRouterRefreshError(
                    "Route refresh failed",
                    Exception(errorMessage)
                )
            )

            return REQUEST_FAILURE
        }

        val refreshOptions = RouteRefreshOptions(
            requestUuid,
            routeIndex,
            legIndex,
            RoutingProfile(routeOptions.profile().mapToRoutingMode(), routeOptions.user())
        )

        return router.getRouteRefresh(
            refreshOptions
        ) { result, _ ->
            result.fold(
                {
                    mainJobControl.scope.launch {
                        val errorMessage =
                            """
                               Route refresh failed.
                               message = ${it.message}
                               code = ${it.code}
                               type = ${it.type}
                               requestId = ${it.requestId}
                               legIndex = $legIndex
                            """.trimIndent()

                        logW(TAG, errorMessage)

                        callback.onFailure(
                            RouterFactory.buildNavigationRouterRefreshError(
                                "Route refresh failed", Exception(errorMessage)
                            )
                        )
                    }
                },
                {
                    mainJobControl.scope.launch {
                        val refreshedNavigationRoute = withContext(ThreadController.IODispatcher) {
                            val refreshResponse = DirectionsRefreshResponse.fromJson(it)
                            val updateLegs =
                                route.directionsRoute.legs()?.mapIndexed { index, routeLeg ->
                                    if (index < refreshOptions.legIndex) {
                                        routeLeg
                                    } else {
                                        routeLeg.toBuilder().annotation(
                                            refreshResponse.route()?.legs()?.get(index)
                                                ?.annotation()
                                        ).build()
                                    }
                                }
                            val refreshedRoute = route.directionsRoute.toBuilder()
                                .legs(updateLegs)
                                .build()
                            NavigationRoute(
                                route.directionsResponse
                                    .toBuilder()
                                    .routes(
                                        route.directionsResponse.routes().apply {
                                            removeAt(route.routeIndex)
                                            add(route.routeIndex, refreshedRoute)
                                        }
                                    )
                                    .build(),
                                route.routeIndex,
                                routeOptions
                            )
                        }
                        callback.onRefreshReady(refreshedNavigationRoute)
                    }
                }
            )
        }
    }

    override fun getRouteRefresh(
        route: DirectionsRoute,
        legIndex: Int,
        callback: RouteRefreshCallback
    ): Long {
        return getRouteRefresh(
            route.toNavigationRoute(),
            legIndex,
            object : NavigationRouterRefreshCallback {
                override fun onRefreshReady(route: NavigationRoute) {
                    callback.onRefresh(route.directionsRoute)
                }

                override fun onFailure(error: NavigationRouterRefreshError) {
                    callback.onError(
                        RouteRefreshError(error.message, error.throwable)
                    )
                }
            }
        )
    }

    override fun cancelRouteRequest(requestId: Long) {
        router.cancelRouteRequest(requestId)
    }

    override fun cancelRouteRefreshRequest(requestId: Long) {
        router.cancelRouteRefreshRequest(requestId)
    }

    override fun cancelAll() {
        router.cancelAll()
    }

    override fun shutdown() {
        router.cancelAll()
    }

    private companion object {
        private const val TAG = "MbxRouterWrapper"
        private const val REQUEST_FAILURE = -1L
    }
}
