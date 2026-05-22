package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.model.RegisterEntry
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegisterViewModel(
    private val repository: BeancountRepository,
    private val calculator: BalanceCalculator,
) {
    private val _accounts = MutableStateFlow<List<String>>(emptyList())
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _entries = MutableStateFlow<List<RegisterEntry>>(emptyList())
    val entries: StateFlow<List<RegisterEntry>> = _entries.asStateFlow()

    /**
     * Refresh the list of accounts the picker offers. If an account is already
     * selected, its register entries are recomputed so they reflect the current
     * ledger.
     */
    fun loadAccounts() {
        _accounts.value = calculator.selectableAccounts(repository.getTransactions())
        _selectedAccount.value?.let { selectAccount(it) }
    }

    /** Select [account] and recompute its register entries. */
    fun selectAccount(account: String) {
        _selectedAccount.value = account
        // computeRegister builds entries oldest-first so the running balance
        // accumulates chronologically; reverse for a newest-first display.
        _entries.value =
            calculator.computeRegister(repository.getTransactions(), account).reversed()
    }

    /**
     * Clear all register state. Used when the active ledger changes so a stale
     * account selection from the previous ledger is not carried over — without
     * this, [loadAccounts] would re-select an account that may not exist in the
     * new ledger.
     */
    fun reset() {
        _accounts.value = emptyList()
        _selectedAccount.value = null
        _entries.value = emptyList()
    }
}
