package dev.ifsc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IfscTest {

    @Test
    fun validatesKnownCodes() {
        assertTrue(Ifsc.validate("KKBK0000261"))
        assertTrue(Ifsc.validate("UTIB0000001"))
        assertTrue(Ifsc.validate("HDFC0002854"))
        assertTrue(Ifsc.validate("IDIB000A001"))
        assertTrue(Ifsc.validate("BARB0DBAGIY"))
        assertTrue(Ifsc.validate("AANB00000SC"))
    }

    @Test
    fun rejectsInvalidCodes() {
        assertFalse(Ifsc.validate(""))
        assertFalse(Ifsc.validate("KKBK000026"))
        assertFalse(Ifsc.validate("KKBK00002611"))
        assertFalse(Ifsc.validate("BOTM0XEEMRA"))
        assertFalse(Ifsc.validate("PUNB0000000"))
        assertFalse(Ifsc.validate("KKBK1000261"))
        assertFalse(Ifsc.validate("SBMY0040704"))
        assertFalse(Ifsc.validate("ABCD0000001"))
    }

    @Test
    fun normalizesInput() {
        assertTrue(Ifsc.validate("  kkbk0000261  "))
        assertEquals("KKBK0000261", Ifsc.normalize(" kkbk0000261 "))
        assertEquals("KKBK", Ifsc.bankCode("kkbk0000261"))
    }

    @Test
    fun resolvesBankNames() {
        assertEquals("Kotak Mahindra Bank", Ifsc.bankName("KKBK0000261"))
        assertEquals("Kotak Mahindra Bank", Ifsc.bankName("KKBK"))
        assertEquals("Punjab National Bank", Ifsc.bankName("PUNB0244200"))
        assertEquals("NSE Clearing Limited", Ifsc.bankName("XNSE0000001"))
    }

    @Test
    fun resolvesSubletBranches() {
        assertEquals("Khamgaon Urban Co-operative Bank", Ifsc.bankName("HDFC0CKUB01"))
        assertEquals("Tumkur District Central Bank", Ifsc.bankName("KSCB0006001"))
        assertEquals(
            "Kolkata Police Co-operative Bank",
            Ifsc.bankName("WBSC0KPCB01"),
        )
        assertEquals(
            "Amravati District Central Co-operative Bank",
            Ifsc.bankName("YESB0ADB002"),
        )
    }

    @Test
    fun resolvesBankMetadata() {
        val punb = Ifsc.bankDetails("PUNB")
        assertNotNull(punb)
        assertEquals("PUNB", punb!!.code)
        assertEquals("Punjab National Bank", punb.name)
        assertEquals("PSB", punb.type)
        assertEquals("PUNB0244200", punb.ifsc)
        assertEquals("110024001", punb.micr)
        assertEquals("508568", punb.iin)
        assertTrue(punb.achCredit)
        assertTrue(punb.achDebit)
        assertTrue(punb.apbs)
        assertTrue(punb.nachDebit)
        assertTrue(punb.upi)

        val fino = Ifsc.bankDetails("fino")
        assertNotNull(fino)
        assertEquals("Fino Payments Bank", fino!!.name)
        assertEquals("PB", fino.type)
        assertTrue(fino.achCredit)
        assertFalse(fino.achDebit)
        assertTrue(fino.apbs)
        assertFalse(fino.nachDebit)
        assertTrue(fino.upi)

        // Historical bank code that only exists in banknames.json.
        val xnse = Ifsc.bankDetails("XNSE")
        assertNotNull(xnse)
        assertEquals("NSE Clearing Limited", xnse!!.name)
        assertNull(xnse.micr)
        assertNull(xnse.type)

        assertNull(Ifsc.bankDetails("ABCD"))
        assertNull(Ifsc.bankDetails("PUNB0000261"))
    }

    @Test
    fun validatesBankCodes() {
        assertTrue(Ifsc.validateBankCode("PUNB"))
        assertTrue(Ifsc.validateBankCode("punb"))
        assertTrue(Ifsc.validateBankCode("XNSE"))
        assertTrue(Ifsc.validateBankCode("LUCB")) // valid code, no display name
        assertFalse(Ifsc.validateBankCode("ABCD"))
        assertFalse(Ifsc.validateBankCode("PUNB0"))
        assertFalse(Ifsc.validateBankCode(""))
    }

    @Test
    fun exposesDatasetMetadata() {
        assertTrue(Ifsc.dataVersion.matches(Regex("""v\d+\.\d+\.\d+""")))
        assertTrue(Ifsc.branchCount > 180_000)
        assertTrue(Ifsc.bankCodes.size > 1_500)
    }

    @Test
    fun bankNameOfInvalidCodeIsNull() {
        assertNull(Ifsc.bankName(""))
        assertNull(Ifsc.bankName("BOTM0XEEMRA"))
        assertNull(Ifsc.bankName("HDFC0000000"))
        assertNull(Ifsc.bankName("ABCD"))
    }
}
