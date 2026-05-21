package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

class BalanceSheetViewModel(
    private val repository: BeancountRepository,
    private val calculator: BalanceCalculator,
) {
    private val _balances = MutableStateFlow<Map<String, Map<String, BigDecimal>>>(emptyMap())
    val balances: StateFlow<Map<String, Map<String, BigDecimal>>> = _balances.asStateFlow()

    private val _groupedBalances = MutableStateFlow<Map<String, Map<String, Map<String, BigDecimal>>>>(emptyMap())
    val groupedBalances: StateFlow<Map<String, Map<String, Map<String, BigDecimal>>>> = _groupedBalances.asStateFlow()

    fun computeBalances() {
        val transactions = repository.getTransactions()
        val computed = calculator.computeBalances(transactions)
        _balances.value = computed
        _groupedBalances.value = calculator.groupByAccountType(computed)
    }
}
