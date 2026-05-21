package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.model.RegisterEntry
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegisterViewModel(
    private val repository: BeancountRepository,
    private val calculator: BalanceCalculator
) {

    private val _accounts = MutableStateFlow<List<String>>(emptyList())
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _entries = MutableStateFlow<List<RegisterEntry>>(emptyList())
    val entries: StateFlow<List<RegisterEntry>> = _entries.asStateFlow()

    /** Refresh the list of accounts the picker offers. */
    fun loadAccounts() {
        _accounts.value = calculator.selectableAccounts(repository.getTransactions())
    }

    /** Select [account] and recompute its register entries. */
    fun selectAccount(account: String) {
        _selectedAccount.value = account
        _entries.value = calculator.computeRegister(repository.getTransactions(), account)
    }
}
