package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DatabaseHelper
import com.example.data.SearchField
import com.example.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.junit.Ignore("Robolectric offline environment")
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NullHUD", appName)
    }

    @Test
    fun `test security manager credentials setup and verification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val securityManager = SecurityManager(context)
        securityManager.resetSecurity()

        assertFalse(securityManager.isSetupCompleted())

        val success = securityManager.setupCredentials("CyberPass1234", true)
        assertTrue(success)
        assertTrue(securityManager.isSetupCompleted())
        assertTrue(securityManager.isBiometricEnabled())

        // Verify correct password
        assertTrue(securityManager.verifyPassword("CyberPass1234"))
        // Verify incorrect password rejected
        assertFalse(securityManager.verifyPassword("WrongPassword"))
    }

    @Test
    fun `test database helper bulk insertion and indexed search`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbHelper = DatabaseHelper(context)
        val db = dbHelper.writableDatabase

        dbHelper.clearAllRecords()
        assertEquals(0L, dbHelper.getTotalCount())

        // Test compiled statement bulk insert
        val stmt = dbHelper.createInsertStatement(db)
        db.beginTransaction()
        try {
            stmt.bindString(1, "0012345678")
            stmt.bindString(2, "6037991234567890")
            stmt.bindString(3, "Null Emperor")
            stmt.bindString(4, "1370/01/01")
            stmt.bindString(5, "09120000000")
            stmt.executeInsert()
            stmt.clearBindings()

            stmt.bindString(1, "0087654321")
            stmt.bindString(2, "5892101234567890")
            stmt.bindString(3, "Alex Hunter")
            stmt.bindString(4, "1375/05/12")
            stmt.bindString(5, "09351112233")
            stmt.executeInsert()
            stmt.clearBindings()

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        assertEquals(2L, dbHelper.getTotalCount())

        // Test indexed search by National Code
        val resultNat = dbHelper.searchRecords("0012345678", SearchField.NATIONAL_CODE)
        assertEquals(1, resultNat.records.size)
        assertEquals("Null Emperor", resultNat.records[0].fullName)
        assertEquals("09120000000", resultNat.records[0].mobile)
        assertEquals("idx_nat_code", resultNat.indexUsed)

        // Test indexed search by Card No
        val resultCard = dbHelper.searchRecords("6037", SearchField.CARD_NO)
        assertEquals(1, resultCard.records.size)
        assertEquals("6037991234567890", resultCard.records[0].cardNo)

        // Test Smart Auto search
        val resultAuto = dbHelper.searchRecords("09351112233", SearchField.ALL)
        assertEquals(1, resultAuto.records.size)
        assertEquals("Alex Hunter", resultAuto.records[0].fullName)

        // Test session save and restore for resume capability
        dbHelper.saveSession(
            sourceName = "TEST_IMPORT.csv",
            sourceUri = null,
            sourceType = "GENERATOR",
            processedRecords = 50000L,
            byteOffset = 0L,
            totalRecords = 1000000L,
            status = "PAUSED",
            elapsedMs = 4500L
        )

        val session = dbHelper.getActiveSession()
        assertNotNull(session)
        assertEquals("TEST_IMPORT.csv", session?.sourceName)
        assertEquals(50000L, session?.processedRecords)
        assertEquals("PAUSED", session?.status)
    }
}
