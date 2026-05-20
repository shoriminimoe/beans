package com.example.beans.viewmodel

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import com.example.beans.parser.BeancountParser
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BeancountRepository
    private lateinit var viewModel: TransactionViewModel

    private val sampleContent = """
        2024-01-01 * "Store A" "Groceries"
          Expenses:Food  20.00 USD
          Assets:Cash

        2024-01-02 * "Coffee Shop" "Latte"
          Expenses:Food:Coffee  5.00 USD
          Assets:Cash
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = BeancountRepository(BeancountParser())
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
        viewModel = TransactionViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has transactions from repository`() = runTest(testDispatcher) {
        viewModel.loadTransactions()
        advanceUntilIdle()

        val transactions = viewModel.transactions.value
        assertEquals(2, transactions.size)
        assertEquals("Store A", transactions[0].payee)
    }

    @Test
    fun `add transaction updates state`() = runTest(testDispatcher) {
        viewModel.loadTransactions()
        advanceUntilIdle()

        val newTxn = Transaction(
            date = LocalDate.of(2024, 1, 3),
            flag = "*",
            payee = "New Place",
            narration = "Dinner",
            postings = listOf(
                Posting("Expenses:Food", Amount(BigDecimal("25.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        viewModel.addTransaction(newTxn)
        advanceUntilIdle()

        assertEquals(3, viewModel.transactions.value.size)
    }

    @Test
    fun `update transaction updates state`() = runTest(testDispatcher) {
        viewModel.loadTransactions()
        advanceUntilIdle()

        val existing = viewModel.transactions.value[0]
        val updated = existing.copy(narration = "Updated")

        viewModel.updateTransaction(updated)
        advanceUntilIdle()

        assertEquals("Updated", viewModel.transactions.value[0].narration)
    }

    @Test
    fun `delete transaction updates state`() = runTest(testDispatcher) {
        viewModel.loadTransactions()
        advanceUntilIdle()

        val toDelete = viewModel.transactions.value[0]
        viewModel.deleteTransaction(toDelete.id)
        advanceUntilIdle()

        assertEquals(1, viewModel.transactions.value.size)
        assertEquals("Coffee Shop", viewModel.transactions.value[0].payee)
    }

    @Test
    fun `save persists changes`() = runTest(testDispatcher) {
        viewModel.loadTransactions()
        advanceUntilIdle()

        val newTxn = Transaction(
            date = LocalDate.of(2024, 1, 3),
            flag = "*",
            payee = "Saved Place",
            narration = "Persisted",
            postings = listOf(
                Posting("Expenses:Misc", Amount(BigDecimal("10.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        viewModel.addTransaction(newTxn)
        viewModel.save()
        advanceUntilIdle()

        // Reload from file
        val file = tempFolder.root.listFiles()!!.first()
        val content = file.readText()
        assertTrue(content.contains("Saved Place"))
    }
}
