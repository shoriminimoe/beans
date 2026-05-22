package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import com.example.beans.parser.BeancountParser
import com.example.beans.repository.BeancountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDate

class RegisterViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: BeancountRepository
    private lateinit var viewModel: RegisterViewModel

    private val sampleContent =
        """
        2024-01-01 * "Opening" "Initial balance"
          Assets:Bank:Checking  1000.00 USD
          Equity:Opening

        2024-01-02 * "Store" "Groceries"
          Expenses:Food  50.00 USD
          Assets:Bank:Checking

        2024-01-03 * "Salary" "Paycheck"
          Assets:Bank:Checking  3000.00 USD
          Income:Salary
        """.trimIndent()

    @Before
    fun setUp() {
        repository = BeancountRepository(BeancountParser())
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
        viewModel = RegisterViewModel(repository, BalanceCalculator())
    }

    @Test
    fun `initial state has no selection and no entries`() {
        assertNull(viewModel.selectedAccount.value)
        assertTrue(viewModel.entries.value.isEmpty())
    }

    @Test
    fun `loadAccounts populates selectable accounts with ancestors`() {
        viewModel.loadAccounts()

        val accounts = viewModel.accounts.value
        assertTrue(accounts.contains("Assets"))
        assertTrue(accounts.contains("Assets:Bank"))
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Expenses:Food"))
    }

    @Test
    fun `selectAccount records the selected account`() {
        viewModel.selectAccount("Assets:Bank:Checking")
        assertEquals("Assets:Bank:Checking", viewModel.selectedAccount.value)
    }

    @Test
    fun `selectAccount computes register with running balance`() {
        viewModel.selectAccount("Assets:Bank:Checking")

        val entries = viewModel.entries.value
        assertEquals(3, entries.size)
        // Entries are newest-first, so the top one carries the final running
        // balance: 1000 - 50 + 3000 = 3950.
        assertEquals(BigDecimal("3950.00"), entries.first().balance["USD"])
    }

    @Test
    fun `selectAccount on parent rolls up sub-account`() {
        viewModel.selectAccount("Assets:Bank")

        val entries = viewModel.entries.value
        assertEquals(3, entries.size)
        assertEquals(BigDecimal("3950.00"), entries.first().balance["USD"])
    }

    @Test
    fun `entries are ordered newest date first`() {
        viewModel.selectAccount("Assets:Bank:Checking")

        val dates = viewModel.entries.value.map { it.transaction.date }
        assertEquals(dates.sortedDescending(), dates)
        assertEquals(LocalDate.of(2024, 1, 3), dates.first())
    }

    @Test
    fun `reset clears accounts, selection, and entries`() {
        viewModel.loadAccounts()
        viewModel.selectAccount("Assets:Bank:Checking")
        assertTrue(viewModel.accounts.value.isNotEmpty())
        assertEquals("Assets:Bank:Checking", viewModel.selectedAccount.value)
        assertTrue(viewModel.entries.value.isNotEmpty())

        viewModel.reset()

        assertTrue(viewModel.accounts.value.isEmpty())
        assertNull(viewModel.selectedAccount.value)
        assertTrue(viewModel.entries.value.isEmpty())
    }

    @Test
    fun `loadAccounts after reset does not re-select a stale account`() {
        viewModel.selectAccount("Assets:Bank:Checking")
        viewModel.reset()

        viewModel.loadAccounts()

        assertNull(viewModel.selectedAccount.value)
        assertTrue(viewModel.entries.value.isEmpty())
    }

    @Test
    fun `loadAccounts refreshes entries for the already-selected account`() {
        viewModel.selectAccount("Assets:Bank:Checking")
        val countBefore = viewModel.entries.value.size

        repository.addTransaction(
            Transaction(
                date = LocalDate.of(2024, 1, 4),
                flag = "*",
                payee = "Extra",
                narration = "Extra deposit",
                postings =
                    listOf(
                        Posting("Assets:Bank:Checking", Amount(BigDecimal("10.00"), "USD")),
                        Posting("Income:Salary", null),
                    ),
            ),
        )

        viewModel.loadAccounts()

        assertEquals(countBefore + 1, viewModel.entries.value.size)
    }
}
