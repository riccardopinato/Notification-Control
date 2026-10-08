package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.diagnostics.PhotoAccessScope
import com.riccardopinato.notificationcontrol.diagnostics.resolvePhotoAccessScope
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalQaProbeTest {
    @Test
    fun fullPhotoAccessWinsOverSelectedOnly() {
        assertEquals(
            PhotoAccessScope.FULL,
            resolvePhotoAccessScope(
                sdkInt = 35,
                fullGranted = true,
                selectedGranted = true
            )
        )
    }

    @Test
    fun android14SelectedPhotosAreNotTreatedAsFullLibraryAccess() {
        assertEquals(
            PhotoAccessScope.SELECTED_ONLY,
            resolvePhotoAccessScope(
                sdkInt = 34,
                fullGranted = false,
                selectedGranted = true
            )
        )
    }

    @Test
    fun missingPhotoPermissionIsReportedAsNone() {
        assertEquals(
            PhotoAccessScope.NONE,
            resolvePhotoAccessScope(
                sdkInt = 35,
                fullGranted = false,
                selectedGranted = false
            )
        )
    }
}
