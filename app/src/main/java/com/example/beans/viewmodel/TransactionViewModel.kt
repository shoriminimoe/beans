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
        _transactions.value = repository.getTransactions()
    }

    fun addTransaction(transaction: Transaction) {
        repository.addTransaction(transaction)
        _transactions.value = repository.getTransactions()
    }

    fun updateTransaction(transaction: Transaction) {
        repository.updateTransaction(transaction)
        _transactions.value = repository.getTransactions()
    }

    fun deleteTransaction(id: String) {
        repository.deleteTransaction(id)
        _transactions.value = repository.getTransactions()
    }

    fun save() {
        repository.save()
    }
}
