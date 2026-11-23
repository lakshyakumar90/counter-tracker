package com.pace.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePolicyTest {
    @Test fun absentRowsAreRestoredAndNewerLocalRowsArePreserved() {
        assertTrue(BackupRestorePolicy.shouldApply(null, 100L))
        assertTrue(BackupRestorePolicy.shouldApply(99L, 100L))
        assertFalse(BackupRestorePolicy.shouldApply(100L, 100L))
        assertFalse(BackupRestorePolicy.shouldApply(101L, 100L))
    }

    @Test fun equalSummaryCanRepairMissingImmutableTrackWithoutReplacingPresentTrack() {
        assertTrue(BackupRestorePolicy.shouldApplyActivity(100L, 100L, remoteHasTrack = true, localHasTrack = false))
        assertFalse(BackupRestorePolicy.shouldApplyActivity(100L, 100L, remoteHasTrack = true, localHasTrack = true))
        assertFalse(BackupRestorePolicy.shouldApplyActivity(101L, 100L, remoteHasTrack = true, localHasTrack = false))
    }
}
