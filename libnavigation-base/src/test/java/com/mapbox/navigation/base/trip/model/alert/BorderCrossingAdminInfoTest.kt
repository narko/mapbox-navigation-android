package com.mapbox.navigation.base.trip.model.alert

import com.mapbox.navigation.testing.BuilderTest
import org.junit.Test

class BorderCrossingAdminInfoTest :
    BuilderTest<BorderCrossingAdminInfo, BorderCrossingAdminInfo.Builder>() {
    override fun getImplementationClass() = BorderCrossingAdminInfo::class

    override fun getFilledUpBuilder() = BorderCrossingAdminInfo.Builder(
        "US",
        "USA"
    )

    @Test
    override fun trigger() {
        // see docs
    }
}