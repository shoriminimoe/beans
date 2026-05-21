package com.example.beans.viewmodel

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateTransactionTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BeancountRepository
    private lateinit var viewModel: TransactionViewModel

    private val sampleContent =
        """
        2024-01-01 * "Store A" "Groceries"
          Expenses:Food  20.00 USD
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
    fun `duplicate transaction creates new transaction with different id`() =
        runTest(testDispatcher) {
            viewModel.loadTransactions()
            advanceUntilIdle()

            val original = viewModel.transactions.value[0]
            val duplicate =
                original.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )

            viewModel.addTransaction(duplicate)
            advanceUntilIdle()

            assertEquals(2, viewModel.transactions.value.size)
            assertNotEquals(original.id, viewModel.transactions.value[1].id)
            assertEquals(original.payee, viewModel.transactions.value[1].payee)
            assertEquals(original.narration, viewModel.transactions.value[1].narration)
            assertEquals(
                original.postings.size,
                viewModel.transactions.value[1]
                    .postings.size,
            )
        }

    @Test
    fun `duplicate transaction preserves all fields except id`() =
        runTest(testDispatcher) {
            viewModel.loadTransactions()
            advanceUntilIdle()

            val original = viewModel.transactions.value[0]
            val duplicate =
                original.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )

            assertEquals(original.date, duplicate.date)
            assertEquals(original.flag, duplicate.flag)
            assertEquals(original.payee, duplicate.payee)
            assertEquals(original.narration, duplicate.narration)
            assertEquals(original.postings, duplicate.postings)
            assertNotEquals(original.id, duplicate.id)
        }

    @Test
    fun `duplicate transaction persists to file`() =
        runTest(testDispatcher) {
            viewModel.loadTransactions()
            advanceUntilIdle()

            val original = viewModel.transactions.value[0]
            val duplicate =
                original.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )

            viewModel.addTransaction(duplicate)
            viewModel.save()
            advanceUntilIdle()

            val file = tempFolder.root.listFiles()!!.first()
            val content = file.readText()
            // Should contain "Store A" twice (original + duplicate)
            val count = content.split("Store A").size - 1
            assertEquals(2, count)
        }
}
