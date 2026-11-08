package com.example

import com.example.data.PersonRecord
import com.example.data.SearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class ExampleUnitTest {

    @Test
    fun `test password hashing algorithm consistency`() {
        val password = "CyberPass1234"
        val salt = "test_salt_sample"

        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        val hash1 = Base64.getEncoder().encodeToString(md.digest(password.toByteArray(Charsets.UTF_8)))

        val md2 = MessageDigest.getInstance("SHA-256")
        md2.update(salt.toByteArray(Charsets.UTF_8))
        val hash2 = Base64.getEncoder().encodeToString(md2.digest(password.toByteArray(Charsets.UTF_8)))

        assertEquals(hash1, hash2)

        val md3 = MessageDigest.getInstance("SHA-256")
        md3.update(salt.toByteArray(Charsets.UTF_8))
        val wrongHash = Base64.getEncoder().encodeToString(md3.digest("WrongPassword".toByteArray(Charsets.UTF_8)))

        assertFalse(hash1 == wrongHash)
    }

    @Test
    fun `test record model creation and search fields`() {
        val record = PersonRecord(
            id = 101L,
            nationalCode = "0012345678",
            cardNo = "6037991234567890",
            fullName = "Null Emperor",
            birthDate = "1370/05/20",
            mobile = "09121234567"
        )

        assertEquals("0012345678", record.nationalCode)
        assertEquals("6037991234567890", record.cardNo)
        assertEquals("Null Emperor", record.fullName)
        assertEquals(SearchField.ALL.label, "SMART AUTO")
        assertEquals(SearchField.NATIONAL_CODE.dbColumn, "national_code")
    }

    @Test
    fun `test synthetic generator math formula`() {
        val seed = 42L
        val natCode = String.format("%010d", (seed * 1103515245L + 12345L) % 1_000_000_0000L)
        val cardNo = "6037" + String.format("%012d", (seed * 1664525L + 1013904223L) % 1_000_000_000_000L)
        val mobile = "09" + String.format("%09d", (seed * 214013L + 2531011L) % 1_000_000_000L)

        assertEquals(10, natCode.length)
        assertEquals(16, cardNo.length)
        assertEquals(11, mobile.length)
        assertTrue(mobile.startsWith("09"))
        assertTrue(cardNo.startsWith("6037"))
    }
}
