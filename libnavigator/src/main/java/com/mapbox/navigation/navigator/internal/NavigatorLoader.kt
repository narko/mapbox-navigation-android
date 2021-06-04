package com.mapbox.navigation.navigator.internal

import com.mapbox.navigation.base.options.DeviceProfile
import com.mapbox.navigation.base.options.DeviceType
import com.mapbox.navigation.navigator.internal.NavigatorLoader.customConfig
import com.mapbox.navigator.CacheFactory
import com.mapbox.navigator.CacheHandle
import com.mapbox.navigator.ConfigFactory
import com.mapbox.navigator.GraphAccessor
import com.mapbox.navigator.HistoryRecorderHandle
import com.mapbox.navigator.Navigator
import com.mapbox.navigator.NavigatorConfig
import com.mapbox.navigator.ProfileApplication
import com.mapbox.navigator.ProfilePlatform
import com.mapbox.navigator.RoadObjectMatcher
import com.mapbox.navigator.Router
import com.mapbox.navigator.RunLoopExecutorFactory
import com.mapbox.navigator.SettingsProfile
import com.mapbox.navigator.TilesConfig

/**
 * This class is expected to gain more responsibility as we define [customConfig].
 * The custom config can be exposed through the [DeviceProfile]
 */
internal object NavigatorLoader {

    fun createNavigator(
        deviceProfile: DeviceProfile,
        navigatorConfig: NavigatorConfig,
        tilesConfig: TilesConfig
    ): NativeComponents {
        val config = ConfigFactory.build(
            settingsProfile(deviceProfile),
            navigatorConfig,
            deviceProfile.customConfig
        )
        val runLoopExecutor = RunLoopExecutorFactory.build()
        val historyRecorder = null
        val cache = CacheFactory.build(tilesConfig, config, historyRecorder)

        val navigator = Navigator(
            config,
            runLoopExecutor,
            cache,
            historyRecorder
        )
        val nativeRouter = Router(cache, historyRecorder)
        val graphAccessor = GraphAccessor(cache)
        val roadObjectMatcher = RoadObjectMatcher(cache)

        return NativeComponents(
            navigator,
            nativeRouter,
            historyRecorder,
            graphAccessor,
            cache,
            roadObjectMatcher
        )
    }

    private fun settingsProfile(deviceProfile: DeviceProfile): SettingsProfile {
        return when (deviceProfile.deviceType) {
            DeviceType.HANDHELD -> {
                SettingsProfile(ProfileApplication.MOBILE, ProfilePlatform.ANDROID)
            }
            DeviceType.AUTOMOBILE -> {
                SettingsProfile(ProfileApplication.AUTO, ProfilePlatform.ANDROID)
            }
            else -> throw NotImplementedError("Unknown device profile")
        }
    }

    private fun customConfig(deviceProfile: DeviceProfile): String {
        return deviceProfile.customConfig
    }

    internal data class NativeComponents(
        val navigator: Navigator,
        val nativeRouter: Router,
        val historyRecorderHandle: HistoryRecorderHandle?,
        val graphAccessor: GraphAccessor,
        val cache: CacheHandle,
        val roadObjectMatcher: RoadObjectMatcher,
    )
}
