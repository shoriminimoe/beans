package com.example.beans.viewmodel

import com.example.beans.model.Transaction
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransactionViewModel(
    private val repository: BeancountRepository,
) {
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    fun loadTransactions() {
        refresh()
    }

    fun addTransaction(transaction: Transaction) {
        repository.addTransaction(transaction)
        refresh()
    }

    fun updateTransaction(transaction: Transaction) {
        repository.updateTransaction(transaction)
        refresh()
    }

    fun deleteTransaction(id: String) {
        repository.deleteTransaction(id)
        refresh()
    }

    fun save() {
        repository.save()
    }

    /**
     * Re-read the ledger and publish it newest-first. The repository keeps
     * transactions in file order; the list screen shows the most recent date
     * at the top.
     */
    private fun refresh() {
        _transactions.value = repository.getTransactions().sortedByDescending { it.date }
    }
}
