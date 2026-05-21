package com.example.beans.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.beans.calculator.BalanceCalculator
import com.example.beans.frecency.FrecencyTracker
import com.example.beans.parser.BeancountParser
import com.example.beans.repository.BeancountRepository
import com.example.beans.ui.theme.BeansTheme
import com.example.beans.viewmodel.BalanceSheetViewModel
import com.example.beans.viewmodel.RegisterViewModel
import com.example.beans.viewmodel.TransactionViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parser = BeancountParser()
        val repository = BeancountRepository(parser)
        val calculator = BalanceCalculator()
        val transactionViewModel = TransactionViewModel(repository)
        val balanceSheetViewModel = BalanceSheetViewModel(repository, calculator)
        val registerViewModel = RegisterViewModel(repository, calculator)
        val prefs = getSharedPreferences("beans_prefs", Context.MODE_PRIVATE)
        val frecencyTracker = FrecencyTracker(prefs)

        setContent {
            BeansTheme {
                BeansApp(
                    repository = repository,
                    transactionViewModel = transactionViewModel,
                    balanceSheetViewModel = balanceSheetViewModel,
                    registerViewModel = registerViewModel,
                    prefs = prefs,
                    frecencyTracker = frecencyTracker,
                )
            }
        }
    }
}

@Composable
fun BeansApp(
    repository: BeancountRepository,
    transactionViewModel: TransactionViewModel,
    balanceSheetViewModel: BalanceSheetViewModel,
    registerViewModel: RegisterViewModel,
    prefs: SharedPreferences,
    frecencyTracker: FrecencyTracker,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var fileLoaded by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<String?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }

    fun openFileFromUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val inputStream = context.contentResolver.openInputStream(uri)
        val content = inputStream?.bufferedReader()?.readText() ?: ""
        inputStream?.close()
        val localFile = File(context.filesDir, "current.beancount")
        localFile.writeText(content)
        repository.loadFromContent(content, localFile)
        sourceUri = uri

        // Save to recent files
        RecentFiles.addRecent(prefs, uri.toString())

        // Seed frecency from existing data
        for (payee in repository.getAllPayees()) {
            frecencyTracker.recordPayee(payee)
        }
        for (account in repository.getAllAccounts()) {
            frecencyTracker.recordAccount(account)
        }

        transactionViewModel.loadTransactions()
        fileLoaded = true
    }

    fun saveToSource() {
        repository.save()
        sourceUri?.let { uri ->
            try {
                val content = File(context.filesDir, "current.beancount").readText()
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(content.toByteArray())
                }
            } catch (_: Exception) {
                // URI may have lost permissions — local file is still saved
            }
        }
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let { openFileFromUri(it) }
        }

    val fileCreator =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val localFile = File(context.filesDir, "current.beancount")
                localFile.writeText("; New Beancount Ledger\n")
                repository.loadFromContent("; New Beancount Ledger\n", localFile)
                sourceUri = it

                RecentFiles.addRecent(prefs, it.toString())

                transactionViewModel.loadTransactions()
                fileLoaded = true
            }
        }

    if (!fileLoaded) {
        val recentFiles = remember { RecentFiles.getRecent(prefs) }
        FilePickerScreen(
            onOpenFile = { filePicker.launch(arrayOf("*/*")) },
            onNewFile = { fileCreator.launch("ledger.beancount") },
            recentFiles = recentFiles,
            onRecentFileClick = { uriString ->
                try {
                    openFileFromUri(Uri.parse(uriString))
                } catch (_: Exception) {
                    RecentFiles.removeRecent(prefs, uriString)
                }
            },
            onRecentFileRemove = { uriString ->
                RecentFiles.removeRecent(prefs, uriString)
            },
        )
    } else {
        NavHost(navController = navController, startDestination = "transactions") {
            composable("transactions") {
                val transactions by transactionViewModel.transactions.collectAsState()
                TransactionListScreen(
                    transactions = transactions,
                    onAddClick = {
                        editingTransaction = null
                        navController.navigate("editor")
                    },
                    onTransactionClick = { txn ->
                        editingTransaction = txn.id
                        navController.navigate("editor")
                    },
                    onDeleteClick = { txn ->
                        transactionViewModel.deleteTransaction(txn.id)
                        saveToSource()
                    },
                    onBalanceSheetClick = {
                        balanceSheetViewModel.computeBalances()
                        navController.navigate("balanceSheet")
                    },
                    onRegisterClick = {
                        registerViewModel.loadAccounts()
                        navController.navigate("register")
                    },
                    onDuplicateClick = { txn ->
                        val duplicate =
                            txn.copy(
                                id =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString(),
                            )
                        transactionViewModel.addTransaction(duplicate)
                        saveToSource()
                    },
                )
            }

            composable("editor") {
                val transactions by transactionViewModel.transactions.collectAsState()
                val existingTxn =
                    editingTransaction?.let { id ->
                        transactions.find { it.id == id }
                    }
                TransactionEditorScreen(
                    existingTransaction = existingTxn,
                    frecencyTracker = frecencyTracker,
                    allAccounts = repository.getAllAccounts(),
                    allPayees = repository.getAllPayees(),
                    onSave = { txn ->
                        if (editingTransaction != null) {
                            transactionViewModel.updateTransaction(txn)
                        } else {
                            transactionViewModel.addTransaction(txn)
                        }
                        // Record frecency for payee and accounts
                        if (txn.payee.isNotEmpty()) {
                            frecencyTracker.recordPayee(txn.payee)
                        }
                        for (posting in txn.postings) {
                            frecencyTracker.recordAccount(posting.account)
                        }
                        saveToSource()
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable("balanceSheet") {
                val grouped by balanceSheetViewModel.groupedBalances.collectAsState()
                BalanceSheetScreen(
                    groupedBalances = grouped,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("register") {
                val accounts by registerViewModel.accounts.collectAsState()
                val selectedAccount by registerViewModel.selectedAccount.collectAsState()
                val entries by registerViewModel.entries.collectAsState()
                RegisterScreen(
                    accounts = accounts,
                    selectedAccount = selectedAccount,
                    entries = entries,
                    onAccountSelect = { registerViewModel.selectAccount(it) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

object RecentFiles {
    private const val KEY = "recent_files"
    private const val MAX_RECENT = 10

    fun getRecent(prefs: SharedPreferences): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n").filter { it.isNotEmpty() }
    }

    fun addRecent(
        prefs: SharedPreferences,
        uriString: String,
    ) {
        val existing = getRecent(prefs).toMutableList()
        existing.remove(uriString)
        existing.add(0, uriString)
        val trimmed = existing.take(MAX_RECENT)
        prefs.edit().putString(KEY, trimmed.joinToString("\n")).apply()
    }

    fun removeRecent(
        prefs: SharedPreferences,
        uriString: String,
    ) {
        val existing = getRecent(prefs).toMutableList()
        existing.remove(uriString)
        prefs.edit().putString(KEY, existing.joinToString("\n")).apply()
    }
}

@Composable
fun FilePickerScreen(
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
    modifier: Modifier = Modifier,
    recentFiles: List<String> = emptyList(),
    onRecentFileClick: (String) -> Unit = {},
    onRecentFileRemove: (String) -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Beans",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Beancount File Manager",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onOpenFile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Beancount File")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNewFile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create New Ledger")
            }

            if (recentFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(recentFiles) { uriString ->
                        val displayName = Uri.parse(uriString).lastPathSegment ?: uriString
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onRecentFileClick(uriString) },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onRecentFileRemove(uriString) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
