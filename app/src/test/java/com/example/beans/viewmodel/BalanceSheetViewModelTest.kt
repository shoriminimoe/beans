package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
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

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceSheetViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BeancountRepository
    private lateinit var viewModel: BalanceSheetViewModel

    private val sampleContent = """
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
        Dispatchers.setMain(testDispatcher)
        repository = BeancountRepository(BeancountParser())
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
        viewModel = BalanceSheetViewModel(repository, BalanceCalculator())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `compute balances from repository`() = runTest(testDispatcher) {
        viewModel.computeBalances()
        advanceUntilIdle()

        val balances = viewModel.balances.value
        assertFalse(balances.isEmpty())
        // Assets:Bank:Checking = 1000 - 50 + 3000 = 3950
        assertEquals(BigDecimal("3950.00"), balances["Assets:Bank:Checking"]?.get("USD"))
    }

    @Test
    fun `grouped balances have correct account types`() = runTest(testDispatcher) {
        viewModel.computeBalances()
        advanceUntilIdle()

        val grouped = viewModel.groupedBalances.value
        assertTrue(grouped.containsKey("Assets"))
        assertTrue(grouped.containsKey("Expenses"))
        assertTrue(grouped.containsKey("Income"))
        assertTrue(grouped.containsKey("Equity"))
    }

    @Test
    fun `income has negative balance (beancount convention)`() = runTest(testDispatcher) {
        viewModel.computeBalances()
        advanceUntilIdle()

        val balances = viewModel.balances.value
        assertEquals(BigDecimal("-3000.00"), balances["Income:Salary"]?.get("USD"))
    }

    @Test
    fun `expenses have positive balance`() = runTest(testDispatcher) {
        viewModel.computeBalances()
        advanceUntilIdle()

        val balances = viewModel.balances.value
        assertEquals(BigDecimal("50.00"), balances["Expenses:Food"]?.get("USD"))
    }

    @Test
    fun `empty repository has empty balances`() = runTest(testDispatcher) {
        val emptyFile = tempFolder.newFile("empty.beancount")
        emptyFile.writeText("")
        val emptyRepo = BeancountRepository(BeancountParser())
        emptyRepo.loadFile(emptyFile)
        val emptyVm = BalanceSheetViewModel(emptyRepo, BalanceCalculator())

        emptyVm.computeBalances()
        advanceUntilIdle()

        assertTrue(emptyVm.balances.value.isEmpty())
        assertTrue(emptyVm.groupedBalances.value.isEmpty())
    }
}
