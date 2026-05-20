package com.example.beans.repository

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import com.example.beans.parser.BeancountParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDate

class BeancountRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: BeancountRepository

    private val sampleContent = """
        ; My ledger
        option "operating_currency" "USD"

        2024-01-01 * "Store A" "Groceries"
          Expenses:Food  20.00 USD
          Assets:Cash

        2024-01-02 * "Coffee Shop" "Latte"
          Expenses:Food:Coffee  5.00 USD
          Assets:Cash
    """.trimIndent()

    @Before
    fun setUp() {
        repository = BeancountRepository(BeancountParser())
    }

    @Test
    fun `load file and get transactions`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)

        repository.loadFile(file)
        val transactions = repository.getTransactions()

        assertEquals(2, transactions.size)
        assertEquals("Store A", transactions[0].payee)
        assertEquals("Coffee Shop", transactions[1].payee)
    }

    @Test
    fun `add transaction`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val newTxn = Transaction(
            date = LocalDate.of(2024, 1, 3),
            flag = "*",
            payee = "Restaurant",
            narration = "Dinner",
            postings = listOf(
                Posting("Expenses:Food:Dining", Amount(BigDecimal("35.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        repository.addTransaction(newTxn)
        val transactions = repository.getTransactions()

        assertEquals(3, transactions.size)
        assertEquals("Restaurant", transactions[2].payee)
    }

    @Test
    fun `add transaction persists to file`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val newTxn = Transaction(
            date = LocalDate.of(2024, 1, 3),
            flag = "*",
            payee = "New Place",
            narration = "New thing",
            postings = listOf(
                Posting("Expenses:Misc", Amount(BigDecimal("10.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        repository.addTransaction(newTxn)
        repository.save()

        val savedContent = file.readText()
        assertTrue(savedContent.contains("New Place"))
        assertTrue(savedContent.contains("New thing"))
    }

    @Test
    fun `update transaction`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val existing = repository.getTransactions()[0]
        val updated = existing.copy(narration = "Updated groceries")

        repository.updateTransaction(updated)
        val transactions = repository.getTransactions()

        assertEquals("Updated groceries", transactions[0].narration)
        assertEquals(2, transactions.size)
    }

    @Test
    fun `delete transaction`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val toDelete = repository.getTransactions()[0]
        repository.deleteTransaction(toDelete.id)

        val transactions = repository.getTransactions()
        assertEquals(1, transactions.size)
        assertEquals("Coffee Shop", transactions[0].payee)
    }

    @Test
    fun `delete and save persists`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val toDelete = repository.getTransactions()[0]
        repository.deleteTransaction(toDelete.id)
        repository.save()

        val savedContent = file.readText()
        assertFalse(savedContent.contains("Store A"))
        assertTrue(savedContent.contains("Coffee Shop"))
    }

    @Test
    fun `save preserves non-transaction content`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
        repository.save()

        val savedContent = file.readText()
        assertTrue(savedContent.contains("; My ledger"))
        assertTrue(savedContent.contains("option \"operating_currency\" \"USD\""))
    }

    @Test
    fun `load empty file`() {
        val file = tempFolder.newFile("empty.beancount")
        file.writeText("")
        repository.loadFile(file)

        assertTrue(repository.getTransactions().isEmpty())
    }

    @Test
    fun `get transaction by id`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        val first = repository.getTransactions()[0]
        val found = repository.getTransactionById(first.id)

        assertNotNull(found)
        assertEquals(first.payee, found!!.payee)
    }

    @Test
    fun `get transaction by invalid id returns null`() {
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)

        assertNull(repository.getTransactionById("nonexistent"))
    }
}
