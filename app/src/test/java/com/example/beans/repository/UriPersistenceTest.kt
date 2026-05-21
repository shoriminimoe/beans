package com.example.beans.repository

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import com.example.beans.parser.BeancountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDate

class UriPersistenceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: BeancountRepository

    private val sampleContent =
        """
        2024-01-01 * "Store" "Groceries"
          Expenses:Food  20.00 USD
          Assets:Cash
        """.trimIndent()

    @Before
    fun setUp() {
        repository = BeancountRepository(BeancountParser())
    }

    @Test
    fun `save writes to both local file and sync target`() {
        val localFile = tempFolder.newFile("local.beancount")
        val syncTarget = tempFolder.newFile("sync_target.beancount")
        localFile.writeText(sampleContent)
        syncTarget.writeText(sampleContent)

        repository.loadFile(localFile)
        repository.setSyncTarget(syncTarget)

        repository.addTransaction(
            Transaction(
                date = LocalDate.of(2024, 1, 2),
                flag = "*",
                payee = "New Store",
                narration = "New stuff",
                postings =
                    listOf(
                        Posting("Expenses:Misc", Amount(BigDecimal("10.00"), "USD")),
                        Posting("Assets:Cash", null),
                    ),
            ),
        )
        repository.save()

        assertTrue(localFile.readText().contains("New Store"))
        assertTrue(syncTarget.readText().contains("New Store"))
    }

    @Test
    fun `save without sync target only writes local`() {
        val localFile = tempFolder.newFile("local.beancount")
        localFile.writeText(sampleContent)

        repository.loadFile(localFile)
        repository.addTransaction(
            Transaction(
                date = LocalDate.of(2024, 1, 2),
                flag = "*",
                payee = "Local Only",
                narration = "Test",
                postings =
                    listOf(
                        Posting("Expenses:Misc", Amount(BigDecimal("5.00"), "USD")),
                        Posting("Assets:Cash", null),
                    ),
            ),
        )
        repository.save()

        assertTrue(localFile.readText().contains("Local Only"))
    }

    @Test
    fun `loadFromContent parses string content directly`() {
        val localFile = tempFolder.newFile("local.beancount")
        localFile.writeText("")

        repository.loadFromContent(sampleContent, localFile)

        assertEquals(1, repository.getTransactions().size)
        assertEquals("Store", repository.getTransactions()[0].payee)
    }

    @Test
    fun `save after loadFromContent persists to local and sync`() {
        val localFile = tempFolder.newFile("local.beancount")
        val syncTarget = tempFolder.newFile("sync.beancount")
        localFile.writeText("")
        syncTarget.writeText("")

        repository.loadFromContent(sampleContent, localFile)
        repository.setSyncTarget(syncTarget)
        repository.save()

        assertTrue(localFile.readText().contains("Store"))
        assertTrue(syncTarget.readText().contains("Store"))
    }
}
