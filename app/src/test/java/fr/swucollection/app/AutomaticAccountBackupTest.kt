package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class AutomaticAccountBackupTest {
    @Test fun newPhoneNeverOverwritesAnExistingAccountBackupEvenWithAnEmptyCollection() {
        assertEquals(AutomaticBackupDecision.CONFLICT, AutomaticBackupPolicy.decide(false, null, "cloud-copy", null, "empty-phone"))
        assertEquals(AutomaticBackupDecision.SAVE, AutomaticBackupPolicy.decide(false, null, null, null, "my-cards"))
    }
    @Test fun anotherPhoneChangingTheHeadPausesUploadsInsteadOfMergingOrReplacingQuantities() {
        assertEquals(AutomaticBackupDecision.CONFLICT, AutomaticBackupPolicy.decide(true, "old", "other-phone", "before", "after"))
        assertEquals(AutomaticBackupDecision.CONFLICT, AutomaticBackupPolicy.decide(true, "old", "other-phone", "same", "same"))
        assertEquals(AutomaticBackupDecision.CONFLICT, AutomaticBackupPolicy.decide(true, "old", null, "before", "after"))
    }
    @Test fun acknowledgedUnchangedDataIsNotUploadedAgainButEditsAreSaved() {
        assertEquals(AutomaticBackupDecision.UNCHANGED, AutomaticBackupPolicy.decide(true, "head", "head", "same", "same"))
        assertEquals(AutomaticBackupDecision.SAVE, AutomaticBackupPolicy.decide(true, "head", "head", "before", "after"))
    }
    @Test fun changingTheSignedInAccountCannotUploadThePreviousOwnersCollection() {
        assertTrue(AutomaticBackupPolicy.canUpload(null, "alice"))
        assertTrue(AutomaticBackupPolicy.canUpload("alice", "alice"))
        assertFalse(AutomaticBackupPolicy.canUpload("alice", "bob"))
    }
    @Test fun editsAreCoalescedAndAClockRollbackDoesNotDelayBackupsIndefinitely() {
        assertEquals(240_000L, AutomaticBackupPolicy.remainingDelay(1_000_000, 1_060_000))
        assertEquals(0L, AutomaticBackupPolicy.remainingDelay(1_000_000, 1_600_000))
        assertEquals(0L, AutomaticBackupPolicy.remainingDelay(1_000_000, 900_000))
    }
}
