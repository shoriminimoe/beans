package com.example.beans.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.beans.model.Transaction
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<Transaction>,
    onAddClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit,
    onBalanceSheetClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onDuplicateClick: (Transaction) -> Unit,
    onSwitchLedger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Beans") },
                actions = {
                    IconButton(onClick = onSwitchLedger) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Switch ledger")
                    }
                    IconButton(onClick = onRegisterClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Register")
                    }
                    IconButton(onClick = onBalanceSheetClick) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Balance Sheet")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No transactions yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(transactions, key = { it.id }) { txn ->
                    TransactionCard(
                        transaction = txn,
                        onClick = { onTransactionClick(txn) },
                        onDelete = { onDeleteClick(txn) },
                        onDuplicate = { onDuplicateClick(txn) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header: date, flag, payee (bold), narration (plain)
                val headerText =
                    buildAnnotatedString {
                        append(transaction.date.format(formatter))
                        append(" ")
                        if (transaction.flag == "!") append("! ")
                        if (transaction.payee.isNotEmpty()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(transaction.payee)
                            }
                            append(" ")
                        }
                        append(transaction.narration)
                    }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Postings
                for (posting in transaction.postings) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = posting.account,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (posting.amount != null) {
                            Text(
                                text = "${posting.amount.value.toPlainString()} ${posting.amount.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    onClick = {
                        showMenu = false
                        val text =
                            buildString {
                                append(transaction.date.format(formatter))
                                append(" ${transaction.flag} ")
                                if (transaction.payee.isNotEmpty()) {
                                    append("\"${transaction.payee}\" ")
                                }
                                appendLine("\"${transaction.narration}\"")
                                for (p in transaction.postings) {
                                    append("  ${p.account}")
                                    if (p.amount != null) {
                                        append("  ${p.amount.value.toPlainString()} ${p.amount.currency}")
                                    }
                                    appendLine()
                                }
                            }.trimEnd()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("transaction", text))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = {
                        showMenu = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
