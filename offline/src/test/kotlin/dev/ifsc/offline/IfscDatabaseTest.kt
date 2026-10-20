package dev.ifsc.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.ifsc.Ifsc

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IfscDatabaseTest {

    @Test
    fun readsBundledDatabase() {
        val context = RuntimeEnvironment.getApplication()

        IfscDatabase.open(context).use { database ->
            assertEquals(Ifsc.branchCount, database.countBranches())
            assertTrue(database.countBranches() > 180_000)
            assertEquals(IfscOfflineData.VERSION, database.dataVersion)
            assertEquals("HDFC Bank", Ifsc.bankName("HDFC0000001"))

            val branch = database.lookup("hdfc0000001")
            assertNotNull(branch)
            assertEquals("HDFC0000001", branch!!.ifsc)
            assertEquals("HDFC", branch.bankCode)
            assertEquals("HDFC Bank", branch.bank)
            assertEquals("TULSIANI CHMBRS - NARIMAN PT", branch.branch)
            assertEquals("GREATER MUMBAI", branch.city)
            assertEquals("MAHARASHTRA", branch.state)
            assertEquals("400240003", branch.micr)
            assertEquals("HDFCINBB", branch.swift)
            assertEquals("IN-MH", branch.iso3166)
            assertTrue(branch.upi)
            assertTrue(branch.rtgs)
            assertTrue(branch.neft)
            assertTrue(branch.imps)

            assertNull(database.lookup("XXXX0000000"))
            assertNull(database.lookup(""))
        }
    }

    @Test
    fun searchesAndListsBranches() {
        val context = RuntimeEnvironment.getApplication()

        IfscDatabase.open(context).use { database ->
            val results = database.search("NARIMAN")
            assertTrue(results.any { it.ifsc == "HDFC0000001" })
            assertEquals("HDFC0000001", database.search("HDFC0000001").firstOrNull()?.ifsc)

            val hdfcBranches = database.branchesOfBank("hdfc", limit = 5)
            assertEquals(5, hdfcBranches.size)
            assertTrue(hdfcBranches.all { it.bankCode == "HDFC" })

            assertTrue(database.search("").isEmpty())
            assertTrue(database.branchesOfBank("XXXX").isEmpty())
            assertFalse(database.search("MUMBAI").isEmpty())
        }
    }

    @Test
    fun reusesInstalledDatabase() {
        val context = RuntimeEnvironment.getApplication()

        IfscDatabase.open(context).use { first ->
            assertEquals(Ifsc.branchCount, first.countBranches())
        }
        IfscDatabase.open(context).use { second ->
            assertEquals(Ifsc.branchCount, second.countBranches())
        }
    }
}
