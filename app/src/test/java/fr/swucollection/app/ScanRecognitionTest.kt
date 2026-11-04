package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanRecognitionTest {
    @Test fun correctsExpansionOcrOnlyBesideAnExplicitLanguageFooter() {
        assertEquals(ScanReference("SOR", "011"), ScanRecognition.extract("S0R FR 011/252"))
        assertEquals(ScanReference("JTL", "1093"), ScanRecognition.extract("JTI ◆ FR ★ L #1093\n002/250"))
        assertEquals(ScanReference("ASH", "614"), ScanRecognition.extract("A5HFRR6I4"))
        assertEquals(ScanReference("LOF", "552"), ScanRecognition.extract("L0F EN C 552"))
        assertNull(ScanRecognition.extract("S0R\n002/250"))
        assertNull(ScanRecognition.extract("JTI 1093"))
        assertNull(ScanRecognition.extract("S0R FR 011/252\nASH FR R 614"))
        assertNull(ScanRecognition.extract("5ORP FR 001/040"))
    }
    @Test fun readsFrenchFooterAndRetainsTheExactReference() {
        assertEquals(ScanReference("SOR", "001"), ScanRecognition.extract("Directeur Krennic\nAspire au pouvoir\nSOR • FR • 001/252"))
        assertEquals(ScanReference("HMW", "272"), ScanRecognition.extract("Croissance\nHMW FR 272/272"))
    }
    @Test fun handlesJoinedFootersAndFourDigitCollectorNumbers() {
        assertEquals(ScanReference("JTL", "1093"), ScanRecognition.extract("JTL FR 1093"))
        assertEquals(ScanReference("HMW", "001"), ScanRecognition.extract("HMWFR001/272"))
        assertEquals(ScanReference("HMW", "944"), ScanRecognition.extract("HMW FR 944/272"))
        assertEquals(ScanReference("TS26", "084"), ScanRecognition.extract("TS26 • FR • 084/084"))
    }
    @Test fun correctsNumericOcrAndAlternateSlashCharacters() {
        assertEquals(ScanReference("SOR", "011"), ScanRecognition.extract("SOR FR OIL／252"))
        assertEquals(ScanReference("HMW", "001"), ScanRecognition.extract("HMW FR 001⁄272"))
    }
    @Test fun doesNotConfusePromoCodesWithTheirMainSet() {
        assertEquals(ScanReference("HMWP", "001"), ScanRecognition.extract("HMWP FR 001/040"))
        assertEquals(ScanReference("JTLP", "012"), ScanRecognition.extract("JTLP FR 012/040"))
    }
    @Test fun rejectsMultipleCardsZeroUnknownSetsAndUnrelatedStatistics() {
        assertNull(ScanRecognition.extract("SOR FR 001/252\nSHD FR 002/262"))
        assertNull(ScanRecognition.extract("HMW FR 000/272"))
        assertNull(ScanRecognition.extract("XYZ FR 001/272"))
        assertNull(ScanRecognition.extract("SOR\nUne unité 3/4 reçoit +1/+1"))
        assertNull(ScanRecognition.extract("HMW FR 12345/99999"))
    }
    @Test fun manualEntryValidatesAndNormalizesOnlyTheReference() {
        assertEquals(ScanReference("HMW", "001"), ScanRecognition.manual(" hmw ", "1"))
        assertNull(ScanRecognition.manual("ZZZ", "1"))
        assertNull(ScanRecognition.manual("SOR", "-1"))
        assertNull(ScanRecognition.manual("SOR", "10000"))
    }
    @Test fun readsRaritySymbolsAndCollectorNumberedFinishesWithoutDenominator() {
        assertEquals(ScanReference("JTL", "788"), ScanRecognition.extract("GOLD LEADER\nJTL • FR ● 788"))
        assertEquals(ScanReference("JTL", "552"), ScanRecognition.extract("JTL FR C 552"))
        assertEquals(ScanReference("ASH", "614"), ScanRecognition.extract("ASHFRR614"))
        assertEquals(ScanReference("JTL", "1093"), ScanRecognition.extract("JTL\nFR\nL\n1093"))
        assertEquals(ScanReference("JTL", "551"), ScanRecognition.extract("JTL FR C 55I"))
        assertEquals(ScanReference("ASH", "614"), ScanRecognition.extract("ASH FR R 6I4"))
        assertEquals(ScanReference("JTL", "788"), ScanRecognition.extract("ＪＴＬ ＦＲ Ⓒ ７８８"))
    }
    @Test fun serialStampNeverReplacesTheCollectorReference() {
        assertEquals(ScanReference("JTL", "1093"), ScanRecognition.extract("JTL FR R 1093\n002/250"))
        assertNull(ScanRecognition.extract("JTL FR C 788\nASH FR R 614"))
        assertNull(ScanRecognition.extract("JTL FR R 000"))
        assertNull(ScanRecognition.extract("JTL FR C 12345"))
    }
}

