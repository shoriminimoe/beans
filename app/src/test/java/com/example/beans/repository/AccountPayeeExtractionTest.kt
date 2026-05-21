package com.example.beans.repository

import com.example.beans.parser.BeancountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountPayeeExtractionTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: BeancountRepository

    private val sampleContent =
        """
        2024-01-01 * "Store A" "Groceries"
          Expenses:Food  20.00 USD
          Assets:Cash

        2024-01-02 * "Coffee Shop" "Latte"
          Expenses:Food:Coffee  5.00 USD
          Assets:Cash

        2024-01-03 * "Transfer"
          Assets:Bank:Savings  500.00 USD
          Assets:Bank:Checking
        """.trimIndent()

    @Before
    fun setUp() {
        repository = BeancountRepository(BeancountParser())
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
    }

    @Test
    fun `getAllAccounts returns all unique accounts`() {
        val accounts = repository.getAllAccounts()
        assertTrue(accounts.contains("Expenses:Food"))
        assertTrue(accounts.contains("Assets:Cash"))
        assertTrue(accounts.contains("Expenses:Food:Coffee"))
        assertTrue(accounts.contains("Assets:Bank:Savings"))
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertEquals(5, accounts.size)
    }

    @Test
    fun `getAllPayees returns all unique non-empty payees`() {
        val payees = repository.getAllPayees()
        assertTrue(payees.contains("Store A"))
        assertTrue(payees.contains("Coffee Shop"))
        assertEquals(2, payees.size)
        // "Transfer" transaction has no payee (narration only), so not included
    }

    @Test
    fun `empty file returns empty sets`() {
        val emptyFile = tempFolder.newFile("empty.beancount")
        emptyFile.writeText("")
        val emptyRepo = BeancountRepository(BeancountParser())
        emptyRepo.loadFile(emptyFile)

        assertTrue(emptyRepo.getAllAccounts().isEmpty())
        assertTrue(emptyRepo.getAllPayees().isEmpty())
    }
}
