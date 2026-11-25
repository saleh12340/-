package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.LocalWarehouseDataSource
import com.example.data.WarehouseRepository
import com.example.domain.Invoice
import com.example.domain.UiState
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.BackupAndSettingsScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.InvoicesScreen
import com.example.ui.screens.ProductFormScreen
import com.example.ui.screens.ReceiptActionDialog
import com.example.ui.state.WarehouseViewModel
import com.example.ui.theme.LedgerNavy
import com.example.ui.theme.WarehouseLedgerTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private const val PREFS_APP = "warehouse_app_prefs"
private const val KEY_LOCAL_MODE = "local_mode_enabled"
private const val LOCAL_USER_ID = "local_user"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#132C4A")),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        )
        setContent {
            WarehouseLedgerTheme(darkTheme = false) {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE) }
    // Firebase is optional: AI Studio/CI builds may not include google-services.json.
    // Never access FirebaseAuth until the default Firebase app is initialized.
    val firebaseConfigured = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty()
    }
    var currentUser by remember(firebaseConfigured) {
        mutableStateOf(if (firebaseConfigured) Firebase.auth.currentUser else null)
    }
    var isLocalMode by remember(firebaseConfigured) {
        mutableStateOf(prefs.getBoolean(KEY_LOCAL_MODE, false) || !firebaseConfigured)
    }
    var pendingSyncFromLocal by remember { mutableStateOf(false) }

    DisposableEffect(firebaseConfigured) {
        if (firebaseConfigured) {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
            }
            Firebase.auth.addAuthStateListener(listener)
            onDispose {
                Firebase.auth.removeAuthStateListener(listener)
            }
        } else {
            onDispose { }
        }
    }

    val user = currentUser
    when {
        user != null -> {
            val uid = user.uid
            key(uid) {
                MainWarehouseApp(
                    currentUserId = uid,
                    isCloudMode = true,
                    userDisplayName = user.displayName ?: user.email ?: "حساب Google",
                    shouldSyncLocalOnStart = pendingSyncFromLocal,
                    onLocalSyncHandled = { pendingSyncFromLocal = false },
                    onGoogleLinkedFromLocal = {},
                    onSwitchToLocalMode = {
                        prefs.edit().putBoolean(KEY_LOCAL_MODE, true).apply()
                        isLocalMode = true
                    }
                )
            }
        }
        isLocalMode -> {
            key(LOCAL_USER_ID) {
                MainWarehouseApp(
                    currentUserId = LOCAL_USER_ID,
                    isCloudMode = false,
                    userDisplayName = "الوضع المحلي",
                    shouldSyncLocalOnStart = false,
                    onLocalSyncHandled = {},
                    onGoogleLinkedFromLocal = {
                        pendingSyncFromLocal = true
                        prefs.edit().putBoolean(KEY_LOCAL_MODE, false).apply()
                        isLocalMode = false
                        currentUser = Firebase.auth.currentUser
                    },
                    onSwitchToLocalMode = {
                        prefs.edit().putBoolean(KEY_LOCAL_MODE, false).apply()
                        isLocalMode = false
                    }
                )
            }
        }
        else -> {
            AuthScreen(
                onAuthSuccess = {
                    prefs.edit().putBoolean(KEY_LOCAL_MODE, false).apply()
                    isLocalMode = false
                    currentUser = Firebase.auth.currentUser
                },
                onContinueLocally = {
                    prefs.edit().putBoolean(KEY_LOCAL_MODE, true).apply()
                    isLocalMode = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWarehouseApp(
    currentUserId: String,
    isCloudMode: Boolean,
    userDisplayName: String,
    shouldSyncLocalOnStart: Boolean,
    onLocalSyncHandled: () -> Unit,
    onGoogleLinkedFromLocal: () -> Unit,
    onSwitchToLocalMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: WarehouseViewModel = viewModel(
        key = "${if (isCloudMode) "cloud" else "local"}_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dataSource = if (isCloudMode) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    WarehouseRepository(FirebaseFirestore.getInstance(databaseId))
                } else {
                    LocalWarehouseDataSource(app)
                }
                WarehouseViewModel(
                    dataSource = dataSource,
                    currentUserId = currentUserId
                )
            }
        }
    )

    LaunchedEffect(shouldSyncLocalOnStart, isCloudMode, currentUserId) {
        if (shouldSyncLocalOnStart && isCloudMode) {
            onLocalSyncHandled()
            val localBundle = LocalWarehouseDataSource(context).exportBackup(LOCAL_USER_ID).getOrNull()
            if (localBundle != null &&
                (localBundle.products.isNotEmpty() || localBundle.contacts.isNotEmpty() || localBundle.invoices.isNotEmpty())
            ) {
                viewModel.importBackupBundle(localBundle)
                    .onSuccess { summary ->
                        viewModel.showMessage(
                            "تم ربط Google ومزامنة البيانات المحلية: +${summary.importedProducts} صنف، +${summary.importedContacts} حساب، +${summary.importedInvoices} فاتورة"
                        )
                    }
                    .onFailure { err ->
                        viewModel.showMessage(err.localizedMessage ?: "تم الربط، وتعذرت مزامنة بعض البيانات المحلية")
                    }
            } else {
                viewModel.showMessage("تم ربط حساب Google بنجاح")
            }
        }
    }

    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoicesState.collectAsStateWithLifecycle()
    val paymentsState by viewModel.paymentsState.collectAsStateWithLifecycle()
    val productFormState by viewModel.productFormState.collectAsStateWithLifecycle()
    val isProductFormOpen by viewModel.isProductFormOpen.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val activeReceiptInvoice by viewModel.activeReceiptInvoice.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(1) }
    var pendingEditInvoice by remember { mutableStateOf<Invoice?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    if (isProductFormOpen) {
        ProductFormScreen(
            state = productFormState,
            onDismiss = viewModel::closeProductForm,
            onNameChange = viewModel::updateProductName,
            onBarcodeChange = viewModel::updateProductBarcode,
            onBaseUnitNameChange = viewModel::updateBaseUnitName,
            onDefaultUnitChange = viewModel::updateDefaultUnit,
            onAddTier = viewModel::addUnitTier,
            onRemoveTier = viewModel::removeUnitTier,
            onTierNameChange = viewModel::updateUnitTierName,
            onTierFactorChange = viewModel::updateUnitTierFactor,
            onLargeUnitCostChange = viewModel::updateLargeUnitCost,
            onBaseCostManualChange = viewModel::updateBaseCostManually,
            onBaseSellPriceChange = viewModel::updateBaseSellPrice,
            onStockBaseQtyChange = viewModel::updateStockBaseQty,
            onMinStockAlertChange = viewModel::updateMinStockAlert,
            onTrackExpiryChange = viewModel::updateTrackExpiry,
            onExpiryDateChange = viewModel::updateExpiryDate,
            onSubmit = viewModel::submitProductForm
        )
        return
    }

    activeReceiptInvoice?.let { receiptInvoice ->
        val contacts = (contactsState as? UiState.Success)?.data ?: emptyList()
        val matchedPhone = contacts.firstOrNull { it.id == receiptInvoice.contactId }?.phone
        ReceiptActionDialog(
            invoice = receiptInvoice,
            contactPhone = matchedPhone,
            onDismiss = viewModel::dismissInvoiceReceipt,
            onFeedbackMessage = viewModel::showMessage
        )
    }

    val destinations = listOf(
        Triple("المخزون", Icons.Filled.Inventory2, "nav_inventory"),
        Triple("الفواتير", Icons.AutoMirrored.Filled.ReceiptLong, "nav_invoices"),
        Triple("الحسابات", Icons.Filled.AccountBalanceWallet, "nav_accounts"),
        Triple("الإعدادات", Icons.Filled.Settings, "nav_settings")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (selectedTab != 1) {
                    TopAppBar(
                        expandedHeight = 48.dp,
                        title = {
                            Text(
                                text = when (selectedTab) {
                                    0 -> "المخزون والأصناف"
                                    2 -> "حسابات العملاء والموردين"
                                    else -> "الإعدادات والنظام المتقدم"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        },
                        actions = {
                            if (selectedTab != 3) {
                                IconButton(
                                    onClick = { selectedTab = 3 },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("top_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "الإعدادات",
                                        tint = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (isCloudMode) {
                                        signOut(credentialManager = credentialManager, scope = scope) {
                                            onSwitchToLocalMode()
                                        }
                                    } else {
                                        selectedTab = 3
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = if (isCloudMode) Icons.AutoMirrored.Filled.Logout else Icons.Filled.Backup,
                                    contentDescription = if (isCloudMode) "فصل الحساب ($userDisplayName)" else "الوضع المحلي - النسخ والربط",
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = LedgerNavy,
                            titleContentColor = androidx.compose.ui.graphics.Color.White,
                            actionIconContentColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEachIndexed { index, (label, icon, tag) ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEachIndexed { index, (label, icon, tag) ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag(tag)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> InventoryScreen(
                        productsState = productsState,
                        onAddProductClick = viewModel::openNewProductForm,
                        onEditProductClick = viewModel::openEditProductForm,
                        onDeleteProductClick = { viewModel.deleteProduct(it.id) }
                    )
                    1 -> InvoicesScreen(
                        productsState = productsState,
                        contactsState = contactsState,
                        invoicesState = invoicesState,
                        isBusy = isBusy,
                        onPostInvoice = viewModel::submitInvoice,
                        onUpdateInvoice = viewModel::updateInvoice,
                        onDeleteInvoice = { invoice, onDone ->
                            viewModel.deleteInvoice(invoice.id, onDone)
                        },
                        onQuickCreateProduct = viewModel::createProductFromPurchase,
                        onOpenReceipt = viewModel::openInvoiceReceipt,
                        initialEditInvoice = pendingEditInvoice,
                        onInitialEditHandled = { pendingEditInvoice = null }
                    )
                    2 -> AccountsScreen(
                        contactsState = contactsState,
                        invoicesState = invoicesState,
                        paymentsState = paymentsState,
                        isBusy = isBusy,
                        onSaveContact = viewModel::saveContact,
                        onDeleteContact = { contact, onDone ->
                            viewModel.deleteContact(contact.id, onDone)
                        },
                        onRecordPayment = viewModel::submitPayment,
                        onUpdatePayment = viewModel::updatePayment,
                        onDeletePayment = { payment, onDone ->
                            viewModel.deletePayment(payment.id, onDone)
                        },
                        onEditInvoice = { inv ->
                            pendingEditInvoice = inv
                            selectedTab = 1
                        },
                        onOpenReceipt = viewModel::openInvoiceReceipt
                    )
                    else -> BackupAndSettingsScreen(
                        isCloudMode = isCloudMode,
                        userDisplayName = userDisplayName,
                        productsState = productsState,
                        contactsState = contactsState,
                        invoicesState = invoicesState,
                        paymentsState = paymentsState,
                        onExportBackupJson = viewModel::exportBackupJson,
                        onImportBackupBundle = viewModel::importBackupBundle,
                        onGoogleLinkedSuccess = onGoogleLinkedFromLocal,
                        onSignOutToLocal = {
                            scope.launch {
                                signOut(credentialManager = credentialManager, scope = scope) {
                                    onSwitchToLocalMode()
                                }
                            }
                        },
                        onShowMessage = viewModel::showMessage
                    )
                }
            }
        }
    }
}
