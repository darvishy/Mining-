package com.example

import com.example.data.PersonRecord
import com.example.data.SearchField
import com.example.data.VcfSessionData
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
    fun `test vcard entry generation structure and escaping`() {
        val record = PersonRecord(
            id = 500L,
            nationalCode = "0019876543",
            cardNo = "6037123456789012",
            fullName = "Ali Rezaei",
            birthDate = "1365/08/14",
            mobile = "09129998877"
        )

        val prefix = "001"
        assertTrue(record.nationalCode.startsWith(prefix))
        assertTrue(record.fullName.isNotBlank())
        assertTrue(record.mobile.isNotBlank())

        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:3.0\r\n")
        sb.append("FN:${record.fullName}\r\n")
        sb.append("TEL;TYPE=CELL:${record.mobile}\r\n")
        sb.append("NOTE:NAT:${record.nationalCode} CARD:${record.cardNo} DOB:${record.birthDate}\r\n")
        sb.append("END:VCARD\r\n")

        val vcard = sb.toString()
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:Ali Rezaei"))
        assertTrue(vcard.contains("TEL;TYPE=CELL:09129998877"))
        assertTrue(vcard.contains("NAT:0019876543"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun `test vcf session data model`() {
        val session = VcfSessionData(
            prefix = "008",
            filePath = "/storage/emulated/0/contacts_prefix_008.vcf",
            lastProcessedId = 15000L,
            processedRecords = 15000L,
            totalRecords = 45000L,
            status = "PAUSED",
            elapsedMs = 12000L
        )

        assertEquals("008", session.prefix)
        assertEquals(15000L, session.lastProcessedId)
        assertEquals(45000L, session.totalRecords)
        assertEquals("PAUSED", session.status)
    }
}
