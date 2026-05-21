package com.example.beans.repository

import com.example.beans.model.Transaction
import com.example.beans.parser.BeancountFile
import com.example.beans.parser.BeancountParser
import com.example.beans.parser.TransactionEntry
import java.io.File

class BeancountRepository(
    private val parser: BeancountParser,
) {
    private var file: File? = null
    private var syncTarget: File? = null
    private var beancountFile: BeancountFile = BeancountFile(emptyList())

    fun loadFile(file: File) {
        this.file = file
        val content = file.readText()
        beancountFile = parser.parse(content)
    }

    fun loadFromContent(
        content: String,
        localFile: File,
    ) {
        this.file = localFile
        beancountFile = parser.parse(content)
    }

    fun setSyncTarget(target: File) {
        this.syncTarget = target
    }

    fun getTransactions(): List<Transaction> = beancountFile.transactions

    fun getTransactionById(id: String): Transaction? = beancountFile.transactions.find { it.id == id }

    fun addTransaction(transaction: Transaction) {
        val newEntries = beancountFile.entries + TransactionEntry(transaction)
        beancountFile = BeancountFile(newEntries)
    }

    fun updateTransaction(transaction: Transaction) {
        val newEntries =
            beancountFile.entries.map { entry ->
                if (entry is TransactionEntry && entry.transaction.id == transaction.id) {
                    TransactionEntry(transaction, entry.lineNumber)
                } else {
                    entry
                }
            }
        beancountFile = BeancountFile(newEntries)
    }

    fun deleteTransaction(id: String) {
        val newEntries =
            beancountFile.entries.filter { entry ->
                !(entry is TransactionEntry && entry.transaction.id == id)
            }
        beancountFile = BeancountFile(newEntries)
    }

    fun save() {
        val f = file ?: throw IllegalStateException("No file loaded")
        val content = parser.serialize(beancountFile)
        f.writeText(content)
        syncTarget?.writeText(content)
    }

    fun getBeancountFile(): BeancountFile = beancountFile

    fun getAllAccounts(): Set<String> =
        beancountFile.transactions
            .flatMap { txn ->
                txn.postings.map { it.account }
            }.toSet()

    fun getAllPayees(): Set<String> =
        beancountFile.transactions
            .map { it.payee }
            .filter { it.isNotEmpty() }
            .toSet()
}
