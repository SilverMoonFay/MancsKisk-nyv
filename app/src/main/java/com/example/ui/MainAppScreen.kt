package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.*
import java.io.*
import androidx.core.content.FileProvider
import androidx.compose.runtime.rememberCoroutineScope

fun copyUriToInternalStorage(context: android.content.Context, uriString: String, prefix: String): String {
    try {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme == "file") {
            if (!uriString.contains("/files/")) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return uriString
                val dir = File(context.filesDir, "pet_media")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                return android.net.Uri.fromFile(file).toString()
            }
            return uriString
        }
        val inputStream = context.contentResolver.openInputStream(uri) ?: return uriString
        val dir = File(context.filesDir, "pet_media")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val extension = when (context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            else -> {
                val name = uri.lastPathSegment ?: ""
                if (name.contains(".")) name.substringAfterLast(".") else "jpg"
            }
        }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.$extension")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return android.net.Uri.fromFile(file).toString()
    } catch (e: Exception) {
        android.util.Log.e("FileUtils", "Error copying uri to internal storage: ${e.message}", e)
        return uriString
    }
}

fun decodeAndRotateBitmap(context: android.content.Context, uri: android.net.Uri): android.graphics.Bitmap? {
    return try {
        val inputStreamForDecode = context.contentResolver.openInputStream(uri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStreamForDecode) ?: return null
        
        var orientation = android.media.ExifInterface.ORIENTATION_UNDEFINED
        try {
            val inputStreamForExif = context.contentResolver.openInputStream(uri)
            if (inputStreamForExif != null) {
                val exifInterface = android.media.ExifInterface(inputStreamForExif)
                orientation = exifInterface.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_UNDEFINED
                )
                inputStreamForExif.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainAppScreen", "Error reading ExifInterface: ${e.message}")
        }
        
        // Fallback to MediaStore query if orientation is undefined or normal but might be rotated in DB
        if (orientation == android.media.ExifInterface.ORIENTATION_UNDEFINED || orientation == android.media.ExifInterface.ORIENTATION_NORMAL) {
            try {
                val projection = arrayOf(android.provider.MediaStore.Images.ImageColumns.ORIENTATION)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val degrees = cursor.getInt(0)
                        orientation = when (degrees) {
                            90 -> android.media.ExifInterface.ORIENTATION_ROTATE_90
                            180 -> android.media.ExifInterface.ORIENTATION_ROTATE_180
                            270 -> android.media.ExifInterface.ORIENTATION_ROTATE_270
                            else -> android.media.ExifInterface.ORIENTATION_NORMAL
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainAppScreen", "Error querying MediaStore orientation: ${e.message}")
            }
        }

        val matrix = android.graphics.Matrix()
        var needsRotation = true
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> needsRotation = false
        }
        if (needsRotation) {
            val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } else {
            bitmap
        }
    } catch (e: Exception) {
        android.util.Log.e("MainAppScreen", "Error auto-rotating bitmap: ${e.message}", e)
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (ex: Exception) {
            null
        }
    }
}

fun isNetworkAvailable(context: android.content.Context): Boolean {
    return try {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } else {
            false
        }
    } catch (e: Exception) {
        true // default to true to try anyway if any error occurs
    }
}

fun formatHungarianAmount(amount: Double): String {
    val rounded = amount.toLong()
    val str = rounded.toString()
    val sb = StringBuilder()
    var count = 0
    for (i in str.length - 1 downTo 0) {
        if (count > 0 && count % 3 == 0) {
            sb.append(' ')
        }
        sb.append(str[i])
        count++
    }
    return sb.reverse().toString() + " Ft"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: PetViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("mancskiskonyv_prefs", Context.MODE_PRIVATE) }
    var backupEmail by remember { mutableStateOf(sharedPrefs.getString("backup_email", "petofanni337@gmail.com") ?: "petofanni337@gmail.com") }
    var showBudgetLimits by remember { mutableStateOf(sharedPrefs.getBoolean("show_budget_limits", true)) }
    
    val pets by viewModel.allPets.collectAsStateWithLifecycle()
    val currentPet by viewModel.currentPet.collectAsStateWithLifecycle()

    // Seed initial data once
    LaunchedEffect(Unit) {
        viewModel.seedInitialDataIfEmpty()
        viewModel.loadFamilyMembers(context)
        viewModel.loadExpenseCategories(context)
    }

    LaunchedEffect(currentPet) {
        currentPet?.let { pet ->
            viewModel.checkAndSeedRoutineTemplates(context, pet.id)
            viewModel.loadBreedAiInfo(context, pet.id, pet.type, pet.breed)
        } ?: run {
            viewModel.breedAiInfo.value = null
        }
    }

    LaunchedEffect(pets) {
        if (viewModel.selectedPetId.value == null && pets.isNotEmpty()) {
            viewModel.selectPet(pets.first().id)
        }
    }
    val currentPetWeights by viewModel.currentPetWeights.collectAsStateWithLifecycle()
    val currentPetVaccinations by viewModel.currentPetVaccinations.collectAsStateWithLifecycle()
    val currentPetParasiteProtections by viewModel.currentPetParasiteProtections.collectAsStateWithLifecycle()
    val currentPetDailyRoutinesToday by viewModel.currentPetDailyRoutinesToday.collectAsStateWithLifecycle()
    val currentPetDailyRoutinesAll by viewModel.currentPetDailyRoutines.collectAsStateWithLifecycle()
    val currentPetMedications by viewModel.currentPetMedications.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val budgetLimits by viewModel.budgetLimits.collectAsStateWithLifecycle()
    val selectedFamilyMember by viewModel.selectedFamilyMember.collectAsStateWithLifecycle()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var prefilledExpenseImageUri by remember { mutableStateOf<String?>(null) }

    val isReceiptScanning by viewModel.isReceiptScanning.collectAsStateWithLifecycle()
    val receiptScanResult by viewModel.receiptScanResult.collectAsStateWithLifecycle()
    val receiptError by viewModel.receiptError.collectAsStateWithLifecycle()

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = decodeAndRotateBitmap(context, it)
                if (bitmap != null) {
                    if (!isNetworkAvailable(context)) {
                        Toast.makeText(context, "Nincs internetkapcsolat. Az AI szkenneléshez internet szükséges. De az adatokat kitöltheted manuálisan a kiválasztott képpel!", Toast.LENGTH_LONG).show()
                        val savedPath = copyUriToInternalStorage(context, it.toString(), "receipt")
                        prefilledExpenseImageUri = savedPath
                        showAddExpenseDialog = true
                    } else {
                        viewModel.scanReceipt(bitmap)
                    }
                } else {
                    Toast.makeText(context, "Hiba a kép betöltésekor!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainAppScreen", "Error reading receipt: ${e.message}")
                Toast.makeText(context, "Hiba a kép beolvasása közben!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var tempReceiptPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val receiptCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempReceiptPhotoUri?.let { uri ->
                try {
                    val bitmap = decodeAndRotateBitmap(context, uri)
                    if (bitmap != null) {
                        if (!isNetworkAvailable(context)) {
                            Toast.makeText(context, "Nincs internetkapcsolat. Az AI szkenneléshez internet szükséges. De az adatokat kitöltheted manuálisan a lefotózott képpel!", Toast.LENGTH_LONG).show()
                            val savedPath = copyUriToInternalStorage(context, uri.toString(), "receipt")
                            prefilledExpenseImageUri = savedPath
                            showAddExpenseDialog = true
                        } else {
                            viewModel.scanReceipt(bitmap)
                        }
                    } else {
                        Toast.makeText(context, "Hiba a kép betöltésekor!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainAppScreen", "Error reading taken receipt photo: ${e.message}")
                    Toast.makeText(context, "Hiba a kép beolvasása közben!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                val tempFile = java.io.File(context.cacheDir, "receipt_temp.jpg")
                if (tempFile.exists()) tempFile.delete()
                tempFile.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "com.aistudio.mancskiskonyv.petcare.fileprovider",
                    tempFile
                )
                tempReceiptPhotoUri = uri
                receiptCameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.util.Log.e("MainAppScreen", "Error launching receipt camera after permission: ${e.message}")
                Toast.makeText(context, "Nem sikerült elindítani a kamerát!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "A fényképezéshez kamera engedély szükséges!", Toast.LENGTH_SHORT).show()
        }
    }

    var showScanReceiptOptions by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val backupFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (jsonString.isNotEmpty()) {
                        val success = viewModel.restoreFromJsonString(jsonString)
                        if (success) {
                            Toast.makeText(context, "Sikeres visszaállítás! Minden adat helyreállítva.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Hiba: Érvénytelen vagy sérült mentésfájl.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "A kiválasztott fájl üres.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Nem sikerült beolvasni a fájlt: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val zipImportFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val success = viewModel.importPetDataZip(context, uri)
                    if (success) {
                        Toast.makeText(context, "Sikeres importálás! Minden adat betöltve a ZIP-ből.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Hiba: Érvénytelen vagy sérült ZIP-fájl.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Nem sikerült az importálás: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var currentTab by remember { mutableStateOf(0) } // 0: Pets, 1: Budget, 2: Health/Vaccines, 3: Protection/Routine, 4: Emergency

    // Dialog trigger states
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showAddPetDialog by remember { mutableStateOf(false) }
    var showEditPetDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showAddVaccineDialog by remember { mutableStateOf(false) }
    var showAddParasiteDialog by remember { mutableStateOf(false) }
    var showAddLimitDialog by remember { mutableStateOf(false) }
    var showAddRoutineTemplateDialog by remember { mutableStateOf(false) }
    var showManageMembersDialog by remember { mutableStateOf(false) }
    var showAddMedicationReminderDialog by remember { mutableStateOf(false) }
    var medicationReminderToEdit by remember { mutableStateOf<MedicationReminderEntity?>(null) }
    var routineToComplete by remember { mutableStateOf<RoutineTemplateEntity?>(null) }

    // Calendar Integration States
    var showCalendarIntegrationDialog by remember { mutableStateOf(false) }
    var calendarEventTitle by remember { mutableStateOf("") }
    var calendarEventDescription by remember { mutableStateOf("") }
    var calendarEventTime by remember { mutableStateOf(0L) }

    // Edit item states
    var weightToEdit by remember { mutableStateOf<WeightEntity?>(null) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var vaccinationToEdit by remember { mutableStateOf<VaccinationEntity?>(null) }
    var parasiteToEdit by remember { mutableStateOf<ParasiteEntity?>(null) }
    var routineTemplateToEdit by remember { mutableStateOf<RoutineTemplateEntity?>(null) }
    var dailyRoutineToEdit by remember { mutableStateOf<DailyRoutineEntity?>(null) }
    var budgetLimitToEdit by remember { mutableStateOf<BudgetLimitEntity?>(null) }
    var largeImageViewUri by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "MancsKiskönyv",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBackupRestoreDialog = true },
                        modifier = Modifier.testTag("backup_restore_button")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Biztonsági mentés", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (currentTab == 0 && currentPet != null) {
                        IconButton(
                            onClick = { showEditPetDialog = true },
                            modifier = Modifier.testTag("edit_pet_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(
                        onClick = { showAddPetDialog = true },
                        modifier = Modifier.testTag("add_pet_button")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Új kedvenc", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Pets, contentDescription = "Kedvencek") },
                    label = { Text("Kedvencek", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Payments, contentDescription = "Költségek") },
                    label = { Text("Pénzügyek", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.MedicalServices, contentDescription = "Oltások") },
                    label = { Text("Egészség", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.EventNote, contentDescription = "Rutin") },
                    label = { Text("Rutin", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Sürgős") },
                    label = { Text("Sürgősségi", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(paddingValues)
            ) {
            // Horizontally Scrollable Pet Selector at the top (Only if there are pets)
            if (pets.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(pets) { pet ->
                        val isSelected = currentPet?.id == pet.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPet(pet.id) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (pet.photoUri != null) {
                                        AsyncImage(
                                            model = pet.photoUri,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pet.name.take(1).uppercase(),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(pet.name, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                selectedBorderWidth = 1.5.dp,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }

            // Tab contents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (pets.isEmpty() && currentTab != 4) {
                    // Beautiful Empty State Banner
                    EmptyStateScreen(
                        onAddPet = { showAddPetDialog = true },
                        onImportZip = {
                            try {
                                zipImportFilePickerLauncher.launch("application/zip")
                            } catch (e: Exception) {
                                try {
                                    zipImportFilePickerLauncher.launch("*/*")
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Nem sikerült megnyitni a fájlválasztót: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                } else {
                    when (currentTab) {
                        0 -> {
                            if (currentPet == null) {
                                MancsKiskonyvWelcomeScreen(
                                    pets = pets,
                                    onAddPetClick = { showAddPetDialog = true },
                                    message = "Válassz ki egy kedvencet a fenti listából a profilja, súlybejegyzései, oltásai és teendői kezeléséhez!"
                                )
                            } else {
                                PetTabContent(
                                    pet = currentPet,
                                    weights = currentPetWeights,
                                    viewModel = viewModel,
                                    onAddWeightClick = { showAddWeightDialog = true },
                                    onDeletePet = { pet ->
                                        viewModel.requestDeletion("Kedvenc törlése", pet.name) {
                                            viewModel.deletePet(pet)
                                        }
                                    },
                                    onEditWeightClick = { w -> weightToEdit = w },
                                    onDeleteWeightClick = { w ->
                                        viewModel.requestDeletion("Súlybejegyzés törlése", "${w.weightKg} kg") {
                                            viewModel.deleteWeight(w.id)
                                        }
                                    }
                                )
                            }
                        }
                        1 -> BudgetTabContent(
                            viewModel = viewModel,
                            allExpenses = allExpenses,
                            budgetLimits = budgetLimits,
                            pets = pets,
                            selectedPet = currentPet,
                            onAddExpenseClick = { showAddExpenseDialog = true },
                            onAddLimitClick = { showAddLimitDialog = true },
                            onScanReceiptClick = { showScanReceiptOptions = true },
                            onEditExpense = { exp -> expenseToEdit = exp },
                            onEditLimit = { lim -> budgetLimitToEdit = lim },
                            onManageCategoriesClick = { showManageCategoriesDialog = true },
                            onImageClick = { uri -> largeImageViewUri = uri }
                        )
                        2 -> {
                            if (currentPet == null) {
                                MancsKiskonyvWelcomeScreen(
                                    pets = pets,
                                    onAddPetClick = { showAddPetDialog = true },
                                    message = "Válassz ki egy kedvencet a fenti listából az egészségügyi adatok (oltások, gyógyszerek, paraziták) kezeléséhez!"
                                )
                            } else {
                                HealthTabContent(
                                    viewModel = viewModel,
                                    pet = currentPet!!,
                                    vaccinations = currentPetVaccinations,
                                    parasiteProtections = currentPetParasiteProtections,
                                    medicationReminders = currentPetMedications,
                                    onAddVaccineClick = { showAddVaccineDialog = true },
                                    onEditVaccine = { v -> vaccinationToEdit = v },
                                    onAddMedicationClick = { showAddMedicationReminderDialog = true },
                                    onEditMedication = { med -> medicationReminderToEdit = med }
                                )
                            }
                        }
                        3 -> {
                            if (currentPet == null) {
                                MancsKiskonyvWelcomeScreen(
                                    pets = pets,
                                    onAddPetClick = { showAddPetDialog = true },
                                    message = "Válassz ki egy kedvencet a fenti listából a napi rutin feladatok és teendők megtekintéséhez!"
                                )
                            } else {
                                RoutineTabContent(
                                    viewModel = viewModel,
                                    pet = currentPet!!,
                                    parasites = currentPetParasiteProtections,
                                    routinesToday = currentPetDailyRoutinesToday,
                                    routinesAll = currentPetDailyRoutinesAll,
                                    selectedFamilyMember = selectedFamilyMember,
                                    onAddParasiteClick = { showAddParasiteDialog = true },
                                    onManageMembersClick = { showManageMembersDialog = true },
                                    onAddRoutineTemplateClick = { showAddRoutineTemplateDialog = true },
                                    onLogRoutineTemplateClick = { template -> routineToComplete = template },
                                    onEditParasite = { p -> parasiteToEdit = p },
                                    onEditRoutineTemplate = { t -> routineTemplateToEdit = t },
                                    onEditDailyRoutine = { r -> dailyRoutineToEdit = r }
                                )
                            }
                        }
                        4 -> EmergencyTabContent(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

    // --- ALL DIALOGS IMPLEMENTATION ---
    if (viewModel.showDeleteConfirmation.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation.value = false },
            title = { Text(viewModel.deleteConfirmationTitle.value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text(viewModel.deleteConfirmationText.value) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onDeleteConfirmAction()
                        viewModel.showDeleteConfirmation.value = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirmation.value = false }) {
                    Text("Mégse")
                }
            }
        )
    }

    if (showBackupRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBackupRestoreDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Megosztás & Adatkezelés", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Megosztás / Share App Section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Alkalmazás megosztása másokkal",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 13.sp
                            )
                            Text(
                                "Küldd el az alkalmazás linkjét másoknak, hogy ők is megnyithassák és sajátjukként használhassák a MancsKiskönyvet a saját telefonjukon!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "MancsKiskönyv alkalmazás letöltése")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Szia! Szeretném megosztani veled a MancsKiskönyv alkalmazást, amivel könnyedén nyomon követheted a kisállataid egészségét, oltásait és kiadásait!\n\n🌐 Próbáld ki a böngésződben (azonnal induló emulátor):\nhttps://ais-pre-eim3de4f4c4ap4feyr32ql-537644078935.europe-west2.run.app\n\n📲 Telepítsd közvetlenül a telefonodra (Android APK letöltés):\nhttps://github.com/SilverMoonFay/MancsKisk-nyv/raw/main/MancsKiskonyv.apk"
                                        )
                                    }
                                    val chooser = Intent.createChooser(shareIntent, "Alkalmazás megosztása:")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Alkalmazás linkjének küldése")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // 2. Oltási könyv, adatok importálása másoktól
                    Text(
                        "Adatok importálása másoktól",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    Text(
                        "Ha valaki megosztotta veled a kisállata adatait egy ZIP fájlban, itt tudod egyszerűen betölteni a saját alkalmazásodba:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    OutlinedButton(
                        onClick = {
                            showBackupRestoreDialog = false
                            try {
                                zipImportFilePickerLauncher.launch("application/zip")
                            } catch (e: Exception) {
                                try {
                                    zipImportFilePickerLauncher.launch("*/*")
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Nem sikerült megnyitni a fájlválasztót: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("backup_import_zip_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kisállat ZIP importálása")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    // 3. Gmail backup
                    Text(
                        "Gmail Biztonsági Adatmentés",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = backupEmail,
                        onValueChange = {
                            backupEmail = it
                            sharedPrefs.edit().putString("backup_email", it).apply()
                        },
                        label = { Text("Gmail fiók a biztonsági mentéshez") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Az egész alkalmazás tartalmát elmentheti és elküldheti a saját Gmail fiókjára.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val jsonString = viewModel.getBackupJsonString()
                                    val cacheDir = context.cacheDir
                                    val backupFile = File(cacheDir, "mancskiskonyv_backup.json")
                                    FileOutputStream(backupFile).use { fos ->
                                        fos.write(jsonString.toByteArray())
                                    }
                                    
                                    val backupUri: Uri = FileProvider.getUriForFile(
                                        context,
                                        "com.aistudio.mancskiskonyv.petcare.fileprovider",
                                        backupFile
                                    )
                                    
                                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf(backupEmail))
                                        putExtra(Intent.EXTRA_SUBJECT, "MancsKiskönyv Biztonsági Mentés - " + SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date()))
                                        putExtra(Intent.EXTRA_TEXT, "Kedves MancsKiskönyv Felhasználó!\n\nMellékelten megtalálja a kutyusai/cicái adatait tartalmazó biztonsági mentést. Kérjük, őrizze meg ezt a levelet, mert az app újratelepítése esetén ebből a mellékletből tudja majd visszaállítani az adatait.\n\nÜdvözlettel,\nMancsKiskönyv")
                                        putExtra(Intent.EXTRA_STREAM, backupUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(emailIntent, "Mentés küldése:"))
                                 } catch (e: Exception) {
                                    Toast.makeText(context, "Hiba az exportálás során: ${e.message}", Toast.LENGTH_SHORT).show()
                                 }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("backup_export_gmail_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mentés küldése Gmail-re")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                backupFilePickerLauncher.launch("*/*")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Nem sikerült megnyitni a fájlválasztót: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("backup_import_file_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gmail biztonsági mentés visszaállítása")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupRestoreDialog = false }) {
                    Text("Bezárás")
                }
            }
        )
    }

    if (showAddPetDialog) {
        PetFormDialog(
            title = "Új Kedvenc Felvétele",
            onDismiss = { showAddPetDialog = false },
            onSubmit = { name, type, breed, color, gender, age, neutered, chip, photoUri, allergies, diseases, vetName, vetPhone, insCompany, insPolicy, birthDate, chipImplant, chipExpiry ->
                viewModel.addPet(name, type, breed, color, gender, age, neutered, chip, photoUri, allergies, diseases, vetName, vetPhone, insCompany, insPolicy, birthDate, chipImplant, chipExpiry)
                showAddPetDialog = false
            },
            onImportZipClick = {
                showAddPetDialog = false
                try {
                    zipImportFilePickerLauncher.launch("application/zip")
                } catch (e: Exception) {
                    try {
                        zipImportFilePickerLauncher.launch("*/*")
                    } catch (ex: Exception) {
                        Toast.makeText(context, "Nem sikerült megnyitni a fájlválasztót: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showEditPetDialog && currentPet != null) {
        PetFormDialog(
            title = "Kedvenc Szerkesztése",
            pet = currentPet,
            onDismiss = { showEditPetDialog = false },
            onSubmit = { name, type, breed, color, gender, age, neutered, chip, photoUri, allergies, diseases, vetName, vetPhone, insCompany, insPolicy, birthDate, chipImplant, chipExpiry ->
                viewModel.updatePet(
                    currentPet!!.copy(
                        name = name,
                        type = type,
                        breed = breed,
                        color = color,
                        gender = gender,
                        age = age,
                        isNeutered = neutered,
                        chipNumber = chip,
                        photoUri = photoUri,
                        allergies = allergies,
                        chronicDiseases = diseases,
                        vetName = vetName,
                        vetPhone = vetPhone,
                        insuranceCompany = insCompany,
                        insurancePolicyNumber = insPolicy,
                        birthDate = birthDate,
                        chipImplantDate = chipImplant,
                        chipExpiryDate = chipExpiry
                    )
                )
                showEditPetDialog = false
            }
        )
    }

    if (showAddWeightDialog || weightToEdit != null) {
        AddWeightDialog(
            weightToEdit = weightToEdit,
            petType = currentPet?.type,
            onDismiss = {
                showAddWeightDialog = false
                weightToEdit = null
            },
            onSubmit = { weight, shoulderHeight, bodyLength, chestCircumference, neckCircumference ->
                if (weightToEdit != null) {
                    viewModel.updateWeight(
                        weightToEdit!!.copy(
                            weightKg = weight,
                            shoulderHeightCm = shoulderHeight,
                            bodyLengthCm = bodyLength,
                            chestCircumferenceCm = chestCircumference,
                            neckCircumferenceCm = neckCircumference
                        )
                    )
                } else {
                    viewModel.addWeight(
                        weightKg = weight,
                        shoulderHeightCm = shoulderHeight,
                        bodyLengthCm = bodyLength,
                        chestCircumferenceCm = chestCircumference,
                        neckCircumferenceCm = neckCircumference
                    )
                }
                showAddWeightDialog = false
                weightToEdit = null
            }
        )
    }

    if (largeImageViewUri != null) {
        Dialog(
            onDismissRequest = { largeImageViewUri = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { largeImageViewUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = largeImageViewUri,
                    contentDescription = "Bizonylat nagy méretben",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
                
                IconButton(
                    onClick = { largeImageViewUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Bezárás",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showAddExpenseDialog || expenseToEdit != null) {
        AddExpenseDialog(
            pets = pets,
            selectedPetId = currentPet?.id ?: 0,
            expenseToEdit = expenseToEdit,
            viewModel = viewModel,
            prefilledImageUri = prefilledExpenseImageUri,
            onDismiss = {
                showAddExpenseDialog = false
                expenseToEdit = null
                prefilledExpenseImageUri = null
            },
            onSubmit = { petId, amount, category, subCategory, desc, isRec, recInterval, imageUri, targetPetIds ->
                if (petId > 0) {
                    viewModel.selectPet(petId)
                }
                if (expenseToEdit != null) {
                    viewModel.updateExpense(
                        expenseToEdit!!.copy(
                            petId = petId,
                            amount = amount,
                            category = category,
                            subCategory = subCategory,
                            description = desc,
                            isRecurring = isRec,
                            recurringIntervalMonths = recInterval,
                            imageUri = imageUri,
                            targetPetIds = targetPetIds
                        )
                    )
                } else {
                    viewModel.addExpense(amount, category, subCategory, desc, System.currentTimeMillis(), isRec, recInterval, imageUri, targetPetIds)
                }
                showAddExpenseDialog = false
                expenseToEdit = null
                prefilledExpenseImageUri = null
            }
        )
    }

    if (showAddLimitDialog || budgetLimitToEdit != null) {
        AddLimitDialog(
            limitToEdit = budgetLimitToEdit,
            viewModel = viewModel,
            onDismiss = {
                showAddLimitDialog = false
                budgetLimitToEdit = null
            },
            onSubmit = { category, limit ->
                viewModel.addBudgetLimit(category, limit)
                showAddLimitDialog = false
                budgetLimitToEdit = null
            }
        )
    }

    if (showManageMembersDialog) {
        val familyMembersList by viewModel.familyMembers.collectAsStateWithLifecycle()
        ManageFamilyMembersDialog(
            members = familyMembersList,
            onAdd = { name -> viewModel.addFamilyMember(context, name) },
            onRemove = { name -> viewModel.removeFamilyMember(context, name) },
            onDismiss = { showManageMembersDialog = false }
        )
    }

    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            viewModel = viewModel,
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    if (showScanReceiptOptions) {
        AlertDialog(
            onDismissRequest = { showScanReceiptOptions = false },
            title = { Text("AI Blokk Beolvasása", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text("Készíts egy képet a bizonylatról a kamerával, vagy válassz egyet a galériából!") },
            confirmButton = {
                Button(
                    onClick = {
                        showScanReceiptOptions = false
                        val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasCameraPermission) {
                            try {
                                val tempFile = java.io.File(context.cacheDir, "receipt_temp.jpg")
                                if (tempFile.exists()) tempFile.delete()
                                tempFile.createNewFile()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "com.aistudio.mancskiskonyv.petcare.fileprovider",
                                    tempFile
                                )
                                tempReceiptPhotoUri = uri
                                receiptCameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                android.util.Log.e("MainAppScreen", "Error launching receipt camera: ${e.message}")
                                Toast.makeText(context, "Nem sikerült elindítani a kamerát!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kamera")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showScanReceiptOptions = false
                        receiptPickerLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galéria")
                }
            }
        )
    }

    if (showAddRoutineTemplateDialog || routineTemplateToEdit != null) {
        AddRoutineTemplateDialog(
            templateToEdit = routineTemplateToEdit,
            onDismiss = {
                showAddRoutineTemplateDialog = false
                routineTemplateToEdit = null
            },
            onSubmit = { name, desc ->
                if (routineTemplateToEdit != null) {
                    viewModel.updateRoutineTemplate(routineTemplateToEdit!!.copy(name = name, description = desc))
                } else {
                    viewModel.addRoutineTemplate(name, desc)
                }
                showAddRoutineTemplateDialog = false
                routineTemplateToEdit = null
            }
        )
    }

    routineToComplete?.let { routine ->
        val familyMembersList by viewModel.familyMembers.collectAsStateWithLifecycle()
        LogRoutineCompletionDialog(
            actionName = routine.name,
            familyMembers = familyMembersList,
            defaultMember = selectedFamilyMember,
            onDismiss = { routineToComplete = null },
            onSubmit = { member, hour, minute ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                viewModel.logDailyRoutine(routine.name, member, cal.timeInMillis)
                routineToComplete = null
            }
        )
    }

    if (dailyRoutineToEdit != null) {
        val familyMembersList by viewModel.familyMembers.collectAsStateWithLifecycle()
        LogRoutineCompletionDialog(
            actionName = dailyRoutineToEdit!!.actionType,
            familyMembers = familyMembersList,
            defaultMember = selectedFamilyMember,
            routineToEdit = dailyRoutineToEdit,
            onDismiss = { dailyRoutineToEdit = null },
            onSubmit = { member, hour, minute ->
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dailyRoutineToEdit!!.timestamp
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }
                viewModel.updateDailyRoutine(
                    dailyRoutineToEdit!!.copy(
                        loggedBy = member,
                        timestamp = cal.timeInMillis
                    )
                )
                dailyRoutineToEdit = null
            }
        )
    }

    if ((showAddVaccineDialog || vaccinationToEdit != null) && currentPet != null) {
        AddVaccineDialog(
            viewModel = viewModel,
            vaccinationToEdit = vaccinationToEdit,
            onDismiss = {
                showAddVaccineDialog = false
                vaccinationToEdit = null
            },
            onSubmit = { name, date, serial, vet, notes, nextDue, isMandatory, diseasePrevention ->
                if (vaccinationToEdit != null) {
                    viewModel.updateVaccination(
                        vaccinationToEdit!!.copy(
                            name = name,
                            timestamp = date,
                            serialNumber = serial,
                            veterinarian = vet,
                            notes = notes,
                            nextDueTimestamp = nextDue,
                            isMandatory = isMandatory,
                            diseasePrevention = diseasePrevention
                        )
                    )
                } else {
                    viewModel.addVaccination(name, date, serial, vet, notes, nextDue, isMandatory, diseasePrevention)
                }
                if (nextDue != null && nextDue > System.currentTimeMillis()) {
                    val eventName = if (!diseasePrevention.isNullOrBlank()) {
                        if (name.isNotBlank()) "$diseasePrevention - $name" else diseasePrevention
                    } else {
                        name
                    }
                    calendarEventTitle = "[Oltás emlékeztető] $eventName - ${currentPet?.name ?: ""}"
                    calendarEventDescription = "Oltóanyag: $name\nMire való: ${diseasePrevention ?: ""}\nÁllat: ${currentPet?.name ?: ""}\n\nMancskiskönyv automatikus oltási emlékeztető."
                    calendarEventTime = nextDue
                    showCalendarIntegrationDialog = true
                }
                showAddVaccineDialog = false
                vaccinationToEdit = null
            }
        )
    }

    if ((showAddParasiteDialog || parasiteToEdit != null) && currentPet != null) {
        AddParasiteDialog(
            parasiteToEdit = parasiteToEdit,
            onDismiss = {
                showAddParasiteDialog = false
                parasiteToEdit = null
            },
            onSubmit = { type, method, name, duration ->
                val nextDueTimestamp = if (parasiteToEdit != null) {
                    parasiteToEdit!!.timestamp + duration * 24 * 60 * 60 * 1000L
                } else {
                    System.currentTimeMillis() + duration * 24 * 60 * 60 * 1000L
                }

                if (parasiteToEdit != null) {
                    viewModel.updateParasiteProtection(
                        parasiteToEdit!!.copy(
                            protectionType = type,
                            treatmentMethod = method,
                            productName = name,
                            durationDays = duration,
                            nextDueTimestamp = nextDueTimestamp
                        )
                    )
                } else {
                    viewModel.addParasiteProtection(type, method, name, System.currentTimeMillis(), duration)
                }

                if (nextDueTimestamp > System.currentTimeMillis()) {
                    val eventName = if (type.isNotBlank()) {
                        if (name.isNotBlank()) "$type - $name" else type
                    } else {
                        name
                    }
                    calendarEventTitle = "[Parazita elleni kezelés] $eventName - ${currentPet?.name ?: ""}"
                    calendarEventDescription = "Kezelés típusa: $type\nKészítmény: $name\nIdőtartam: $duration nap\n\nMancskiskönyv élősködő elleni ismétlő kezelés emlékeztető."
                    calendarEventTime = nextDueTimestamp
                    showCalendarIntegrationDialog = true
                }
                showAddParasiteDialog = false
                parasiteToEdit = null
            }
        )
    }

    if (showCalendarIntegrationDialog) {
        AlertDialog(
            onDismissRequest = { showCalendarIntegrationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Összekötés a naptárral")
                }
            },
            text = {
                Text("A beállított emlékeztető dátuma a jövőben van. Szeretnéd elmenteni a telefonod vagy a Google naptárába?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = android.provider.CalendarContract.Events.CONTENT_URI
                                putExtra(android.provider.CalendarContract.Events.TITLE, calendarEventTitle)
                                putExtra(android.provider.CalendarContract.Events.DESCRIPTION, calendarEventDescription)
                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendarEventTime)
                                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, calendarEventTime + 60 * 60 * 1000L) // 1 hour duration
                                putExtra(android.provider.CalendarContract.Events.ALL_DAY, true)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Nem sikerült megnyitni a naptárt: \${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                        showCalendarIntegrationDialog = false
                    }
                ) {
                    Text("Igen, mentés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendarIntegrationDialog = false }) {
                    Text("Mégse")
                }
            }
        )
    }

    if ((showAddMedicationReminderDialog || medicationReminderToEdit != null) && currentPet != null) {
        AddMedicationReminderDialog(
            reminderToEdit = medicationReminderToEdit,
            onDismiss = {
                showAddMedicationReminderDialog = false
                medicationReminderToEdit = null
            },
            onSubmit = { name, dosage, freq, time, notes, photoUri ->
                if (medicationReminderToEdit != null) {
                    viewModel.updateMedicationReminder(
                        medicationReminderToEdit!!.copy(
                            medicationName = name,
                            dosage = dosage,
                            frequency = freq,
                            reminderTime = time,
                            notes = notes,
                            prescriptionPhotoUri = photoUri
                        )
                    )
                } else {
                    viewModel.addMedicationReminder(
                        medicationName = name,
                        dosage = dosage,
                        frequency = freq,
                        reminderTime = time,
                        notes = notes,
                        prescriptionPhotoUri = photoUri
                    )
                }
                showAddMedicationReminderDialog = false
                medicationReminderToEdit = null
            }
        )
    }

    // --- RECEIPT SCANNING STATUS & DIALOGS ---
    if (isReceiptScanning) {
        Dialog(onDismissRequest = { /* Don't dismiss during scan */ }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Blokk beolvasása AI-val...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "A Gemini épp elemzi a számlát, felismeri az összeget, a helyet és a megvásárolt tételeket.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    receiptError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearReceiptState() },
            title = { Text("Hiba a beolvasáskor", fontWeight = FontWeight.Bold) },
            text = { Text("$err\n\nSzeretnéd inkább manuálisan rögzíteni a kiadást a lefényképezett bizonylattal?") },
            confirmButton = {
                Button(
                    onClick = {
                        tempReceiptPhotoUri?.let { uri ->
                            try {
                                val savedPath = copyUriToInternalStorage(context, uri.toString(), "receipt")
                                prefilledExpenseImageUri = savedPath
                            } catch (e: Exception) {
                                android.util.Log.e("MainAppScreen", "Error prefilling image on scan error: ${e.message}")
                            }
                        }
                        showAddExpenseDialog = true
                        viewModel.clearReceiptState()
                    }
                ) {
                    Text("Kitöltés manuálisan")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearReceiptState() }) {
                    Text("Mégse")
                }
            }
        )
    }

    receiptScanResult?.let { scanResult ->
        ReceiptSplitDialog(
            scanResult = scanResult,
            pets = pets,
            defaultPetId = currentPet?.id,
            viewModel = viewModel,
            onDismiss = { viewModel.clearReceiptState() },
            onSubmit = { expenses ->
                viewModel.saveScannedExpenses(expenses)
                viewModel.clearReceiptState()
                Toast.makeText(context, "${expenses.size} tétel sikeresen rögzítve a kiadásokhoz!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

// --- TAB CONTENT 1: PET PROFILE ---
@Composable
fun PetTabContent(
    pet: PetEntity?,
    weights: List<WeightEntity>,
    viewModel: PetViewModel,
    onAddWeightClick: () -> Unit,
    onDeletePet: (PetEntity) -> Unit,
    onEditWeightClick: (WeightEntity) -> Unit,
    onDeleteWeightClick: (WeightEntity) -> Unit
) {
    if (pet == null) return
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("mancskiskonyv_prefs", Context.MODE_PRIVATE) }
    var showCallConfirmation by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val breedAiInfo by viewModel.breedAiInfo.collectAsStateWithLifecycle()
    val isBreedAiLoading by viewModel.isBreedAiLoading.collectAsStateWithLifecycle()
    val breedAiError by viewModel.breedAiError.collectAsStateWithLifecycle()

    if (showExportDialog) {
        PetExportDialog(
            initialPet = pet,
            viewModel = viewModel,
            onDismiss = { showExportDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Banner at top of profile for Allergies and Chronic diseases
        if (pet.allergies.isNotEmpty() && pet.allergies.lowercase() != "nincs" ||
            pet.chronicDiseases.isNotEmpty() && pet.chronicDiseases.lowercase() != "nincs"
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RedWarning.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.2.dp, RedWarning.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RedWarning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Figyelem", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "KRITIKUS EGÉSZSÉGÜGYI INFORMÁCIÓ!",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RedWarning
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (pet.allergies.isNotEmpty() && pet.allergies.lowercase() != "nincs") {
                                Text(
                                    "Allergiák: ${pet.allergies}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            if (pet.chronicDiseases.isNotEmpty() && pet.chronicDiseases.lowercase() != "nincs") {
                                Text(
                                    "Krónikus betegségek: ${pet.chronicDiseases}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Basic Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar Placeholder or Real Photo
                        if (pet.photoUri != null) {
                            AsyncImage(
                                model = pet.photoUri,
                                contentDescription = "${pet.name} profilképe",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pet.name.take(1).uppercase(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                pet.name,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "${pet.type} | ${pet.breed}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Detail list
                    val displayedAge = if (!pet.birthDate.isNullOrBlank()) calculateAge(pet.birthDate) else pet.age
                    val details = mutableListOf(
                        Triple(Icons.Default.Palette, "Szín:", pet.color),
                        Triple(Icons.Default.Transgender, "Nem:", pet.gender)
                    )
                    if (!pet.birthDate.isNullOrBlank()) {
                        details.add(Triple(Icons.Default.CalendarToday, "Születési idő:", pet.birthDate))
                    }
                    details.add(Triple(Icons.Default.CalendarToday, "Kor:", displayedAge))
                    details.add(Triple(Icons.Default.CheckCircle, "Ivartalanítva:", if (pet.isNeutered) "Igen" else "Nem"))
                    details.add(Triple(Icons.Default.QrCode, "Chip szám:", pet.chipNumber.ifEmpty { "Nincs megadva" }))

                    details.forEach { (icon, label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = if (label == "Chip szám:") Alignment.Top else Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = if (label == "Chip szám:") 2.dp else 0.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(100.dp))
                            if (label == "Chip szám:") {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(value)
                                    val implant = pet.chipImplantDate
                                    val expiry = pet.chipExpiryDate
                                    if (!implant.isNullOrBlank()) {
                                        Text(
                                            text = "Beültetve: $implant",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (!expiry.isNullOrBlank()) {
                                        Text(
                                            text = "Lejár: $expiry",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            } else {
                                Text(value, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // --- Pet Age Calculator & Milestones ---
        item {
            PetAgeMilestonesCard(pet = pet)
        }

        // --- AI Breed & Care Recommendations ---
        item {
            val expandKey = "breed_ai_expanded_pet_${pet.id}"
            var isExpanded by remember(pet.id) {
                mutableStateOf<Boolean>(sharedPrefs.getBoolean(expandKey, true))
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextExpanded = !isExpanded
                                isExpanded = nextExpanded
                                sharedPrefs.edit().putBoolean(expandKey, nextExpanded).apply()
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AI Fajta & Gondozási Asszisztens",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Automatikus, személyre szabott javaslatok a(z) ${pet.type} (${pet.breed}) fajtához",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Összecsukás" else "Lenyitás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))

                        if (isBreedAiLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                                Text(
                                    "AI javaslatok betöltése a(z) ${pet.type} (${pet.breed}) fajtához...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (breedAiError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = RedWarning,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    breedAiError ?: "Ismeretlen hiba történt a lekérés során.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RedWarning,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.loadBreedAiInfo(context, pet.id, pet.type, pet.breed, force = true) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Újrapróbálkozás")
                                }
                            }
                        } else if (breedAiInfo != null) {
                            val info = breedAiInfo!!
                            
                            // Characteristics section
                            if (info.breedCharacteristics.isNotEmpty()) {
                                Text(
                                    if (pet.breed.isBlank()) "AI Asszisztens:" else "Fajta jellemzői:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    info.breedCharacteristics,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Vaccinations section
                            if (info.recommendedVaccinations.isNotEmpty()) {
                                Text(
                                    "Ajánlott Védőoltások:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                info.recommendedVaccinations.forEach { vaccination ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MedicalServices,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                        )
                                        Text(
                                            text = vaccination,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Toxic Foods section
                            if (info.toxicFoods.isNotEmpty()) {
                                Text(
                                    "Mérgező anyagok & etetés:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RedWarning
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                info.toxicFoods.forEach { food ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = RedWarning,
                                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                        )
                                        Text(
                                            text = food,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Care Tips section
                            if (info.careTips.isNotEmpty()) {
                                Text(
                                    "Ápolás & Gondozási tanácsok:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                info.careTips.forEach { tip ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                        )
                                        Text(
                                            text = tip,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Empty/Fallback state (e.g. if API Key is not set or not loaded yet)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Nincs betöltve AI információ. Kattints a lekéréshez!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Button(
                                    onClick = { viewModel.loadBreedAiInfo(context, pet.id, pet.type, pet.breed, force = true) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Lekérés AI-val")
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Smart Vaccination & Parasite protection schedule ---
        item {
            val vaccinations by viewModel.currentPetVaccinations.collectAsStateWithLifecycle()
            val parasiteProtections by viewModel.currentPetParasiteProtections.collectAsStateWithLifecycle()
            PetHealthScheduleCard(
                vaccinations = vaccinations,
                parasiteProtections = parasiteProtections
            )
        }

        // Emergency Vet & Insurance
        if (pet.vetName.isNotEmpty() || pet.vetPhone.isNotEmpty() || pet.insuranceCompany.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Orvosi & Biztosítási adatok",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        if (pet.vetName.isNotEmpty() || pet.vetPhone.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Kezelő állatorvos:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(pet.vetName.ifEmpty { "Nincs megadva" }, style = MaterialTheme.typography.bodyLarge)
                                    if (pet.vetPhone.isNotEmpty()) {
                                        Text(pet.vetPhone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                                if (pet.vetPhone.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            showCallConfirmation = true
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("call_vet_button")
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Hívás")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hívás")
                                    }
                                }
                            }
                        }

                        if ((pet.vetName.isNotEmpty() || pet.vetPhone.isNotEmpty()) && pet.insuranceCompany.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (pet.insuranceCompany.isNotEmpty()) {
                            Column {
                                Text("Kisállat biztosítás:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = if (pet.insurancePolicyNumber.isNotEmpty()) "${pet.insuranceCompany} (${pet.insurancePolicyNumber})" else pet.insuranceCompany,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Weight tracker & Graph
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Súly- és mérettörténet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val lastRecord = weights.lastOrNull()
                            val currentWeight = lastRecord?.weightKg ?: 0.0
                            Text(
                                if (currentWeight > 0.0) "$currentWeight kg" else "Nincs bejegyezve",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (lastRecord != null) {
                                val measures = mutableListOf<String>()
                                if (lastRecord.shoulderHeightCm != null && lastRecord.shoulderHeightCm > 0.0) measures.add("Marmagasság: ${lastRecord.shoulderHeightCm} cm")
                                if (lastRecord.bodyLengthCm != null && lastRecord.bodyLengthCm > 0.0) measures.add("Testhossz: ${lastRecord.bodyLengthCm} cm")
                                if (lastRecord.chestCircumferenceCm != null && lastRecord.chestCircumferenceCm > 0.0) measures.add("Mellbőség: ${lastRecord.chestCircumferenceCm} cm")
                                if (lastRecord.neckCircumferenceCm != null && lastRecord.neckCircumferenceCm > 0.0) measures.add("Nyakbőség: ${lastRecord.neckCircumferenceCm} cm")
                                if (measures.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        measures.joinToString(" | "),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onAddWeightClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("add_weight_button")
                        ) {
                            Icon(Icons.Default.Scale, contentDescription = "Mérés")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mérés", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (weights.size >= 2) {
                        // Drawing Custom Weight Line Graph using Compose Canvas!
                        WeightLineGraph(weights = weights)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = 0.03f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Adj meg legalább 2 súlymérést a grafikon kirajzolásához!",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (weights.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        var showWeightsList by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showWeightsList = !showWeightsList }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Súlymérések előzményei (${weights.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (showWeightsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showWeightsList) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val df = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
                            weights.reversed().forEach { w ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f), shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${w.weightKg} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        val details = mutableListOf<String>()
                                        if (w.shoulderHeightCm != null && w.shoulderHeightCm > 0.0) details.add("Marmagasság: ${w.shoulderHeightCm} cm")
                                        if (w.bodyLengthCm != null && w.bodyLengthCm > 0.0) details.add("Testhossz: ${w.bodyLengthCm} cm")
                                        if (w.chestCircumferenceCm != null && w.chestCircumferenceCm > 0.0) details.add("Mellbőség: ${w.chestCircumferenceCm} cm")
                                        if (w.neckCircumferenceCm != null && w.neckCircumferenceCm > 0.0) details.add("Nyakbőség: ${w.neckCircumferenceCm} cm")
                                        if (details.isNotEmpty()) {
                                            Text(details.joinToString(" | "), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(df.format(Date(w.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { onEditWeightClick(w) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { onDeleteWeightClick(w) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = RedWarning.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feeding & Hydration Calculator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var isExpanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.padding(16.dp)) {
                    // Clickable Header to Expand/Collapse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    "Táplálási & Hidratációs Kalkulátor",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Számold ki kedvenced napi kalória- és vízszükségletét.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Inputs
                        val currentWeight = weights.lastOrNull()?.weightKg ?: 5.0
                        var weightInput by remember { mutableStateOf(currentWeight.toString()) }
                        
                        val activityLevels = listOf("Alacsony (Lusta/Benti)", "Normál (Aktív benti)", "Magas (Nagyon aktív)", "Szuper aktív (Munkakutya)")
                        var selectedActivityIndex by remember { mutableStateOf(1) }
                        var showActivityMenu by remember { mutableStateOf(false) }

                        val lifeStages = listOf("Ivartalanított felnőtt", "Ivartalanítatlan felnőtt", "Idős / Szenior", "Kölyök (Növekedésben)")
                        var selectedStageIndex by remember { mutableStateOf(0) }
                        var showStageMenu by remember { mutableStateOf(false) }

                        Text("Testsúly (kg):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aktivitási szint:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showActivityMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(activityLevels[selectedActivityIndex], fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(expanded = showActivityMenu, onDismissRequest = { showActivityMenu = false }) {
                                        activityLevels.forEachIndexed { idx, level ->
                                            DropdownMenuItem(
                                                text = { Text(level) },
                                                onClick = {
                                                    selectedActivityIndex = idx
                                                    showActivityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Életkor / Állapot:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showStageMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(lifeStages[selectedStageIndex], fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(expanded = showStageMenu, onDismissRequest = { showStageMenu = false }) {
                                        lifeStages.forEachIndexed { idx, stage ->
                                            DropdownMenuItem(
                                                text = { Text(stage) },
                                                onClick = {
                                                    selectedStageIndex = idx
                                                    showStageMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Calculations
                        val parsedWeight = weightInput.toDoubleOrNull() ?: currentWeight
                        if (parsedWeight > 0) {
                            // RER = 70 * (weight ^ 0.75)
                            val rer = 70 * Math.pow(parsedWeight, 0.75)
                            
                            // Multiplier selection
                            val isCat = pet.type.lowercase().contains("macska")
                            val multiplier = if (isCat) {
                                when (selectedStageIndex) {
                                    0 -> 1.2 // Ivartalanított felnőtt
                                    1 -> 1.4 // Ivartalanítatlan
                                    2 -> 1.1 // Szenior
                                    3 -> 2.5 // Kölyök
                                    else -> 1.2
                                } * when (selectedActivityIndex) {
                                    0 -> 0.8
                                    1 -> 1.0
                                    2 -> 1.2
                                    3 -> 1.4
                                    else -> 1.0
                                }
                            } else {
                                // Dog multiplier
                                when (selectedStageIndex) {
                                    0 -> 1.6
                                    1 -> 1.8
                                    2 -> 1.4
                                    3 -> 3.0
                                    else -> 1.6
                                } * when (selectedActivityIndex) {
                                    0 -> 0.8
                                    1 -> 1.0
                                    2 -> 1.4
                                    3 -> 2.0
                                    else -> 1.0
                                }
                            }

                            val der = rer * multiplier
                            val recommendedWaterMl = parsedWeight * 60 // 60 ml/kg/day is standard pet hydration rate

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Calorie Result Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Napi kalória", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("${der.toInt()} kcal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("DER szükséglet", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }

                                // Hydration Result Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Napi folyadék", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text("${recommendedWaterMl.toInt()} ml", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Friss tiszta víz", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Guidelines text block
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), shape = RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("💡 Szakértői etetési tippek:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("• Oszd el ezt a mennyiséget napi 2 vagy 3 egyenlő adagra.", fontSize = 11.sp)
                                    Text("• Nedves eledel etetése esetén a vízigény egy része abból is fedeződik.", fontSize = 11.sp)
                                    Text("• Folyamatosan ellenőrizd a testsúlyát a fenti grafikonon az elhízás elkerülésére!", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Share & Send Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Kisállat adatainak megosztása",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Oszd meg kedvenced összes orvosi adatát, oltási könyvét, bizonylatait és költségvetését ZIP formátumban. A másik személy ezt sajátjaként be tudja importálni a saját alkalmazásába!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("download_all_data_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Megosztás")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Megosztás")
                    }
                }
            }
        }

        // Danger zone: Delete pet profile
        item {
            var showConfirmDelete by remember { mutableStateOf(false) }
            if (showConfirmDelete) {
                AlertDialog(
                    onDismissRequest = { showConfirmDelete = false },
                    title = { Text("Profil Törlése") },
                    text = { Text("Biztosan törölni szeretnéd ${pet.name} teljes profilját? Ez a művelet nem vonható vissza!") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeletePet(pet)
                                showConfirmDelete = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = RedWarning)
                        ) {
                            Text("Igen, Törlés")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDelete = false }) {
                            Text("Mégse")
                        }
                    }
                )
            }

            OutlinedButton(
                onClick = { showConfirmDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedWarning),
                border = BorderStroke(1.dp, RedWarning),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_pet_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Törlés")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Profil és adatok törlése")
            }
        }
    }

    if (showCallConfirmation) {
        AlertDialog(
            onDismissRequest = { showCallConfirmation = false },
            title = { Text("Biztonsági megerősítés", fontWeight = FontWeight.Bold) },
            text = { Text("Biztos, hogy hívást akar indítani: ${pet.vetName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${pet.vetPhone}"))
                        context.startActivity(dialIntent)
                        showCallConfirmation = false
                    }
                ) {
                    Text("Igen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallConfirmation = false }) {
                    Text("Mégse")
                }
            }
        )
    }
}

// Custom line graph for weight records
@Composable
fun WeightLineGraph(weights: List<WeightEntity>) {
    val maxWeight = weights.maxOfOrNull { it.weightKg } ?: 30.0
    val minWeight = weights.minOfOrNull { it.weightKg } ?: 0.0
    val weightRange = (maxWeight - minWeight).coerceAtLeast(1.0)
    
    val graphColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val spacing = width / (weights.size - 1)

        val points = weights.mapIndexed { idx, w ->
            val x = idx * spacing
            val yNormalized = (w.weightKg - minWeight) / weightRange
            val y = height - (yNormalized * height).toFloat()
            Offset(x, y)
        }

        // Draw background grid lines (horizontal)
        val gridLines = 3
        for (i in 0..gridLines) {
            val gridY = height * i / gridLines
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1f
            )
        }

        // Draw connections
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
        }
        drawPath(
            path = path,
            color = graphColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw dots and weight texts
        points.forEachIndexed { index, point ->
            drawCircle(
                color = graphColor,
                radius = 6.dp.toPx(),
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = point
            )
        }
    }

    // Horizontal timeline labels
    val df = SimpleDateFormat("MM.dd.", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weights.forEach { w ->
            Text(
                text = "${df.format(Date(w.timestamp))}\n(${w.weightKg}kg)",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// --- TAB CONTENT 2: BUDGET & EXPENSES ---
enum class BudgetPeriod(val displayName: String) {
    HAVI("Havi"),
    EVI("Évi"),
    OSSZES("Összes")
}

@Composable
fun BudgetTabContent(
    viewModel: PetViewModel,
    allExpenses: List<ExpenseEntity>,
    budgetLimits: List<BudgetLimitEntity>,
    pets: List<PetEntity>,
    selectedPet: PetEntity?,
    onAddExpenseClick: () -> Unit,
    onAddLimitClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onEditLimit: (BudgetLimitEntity) -> Unit,
    onManageCategoriesClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("mancskiskonyv_prefs", Context.MODE_PRIVATE) }
    var showBudgetLimits by remember { mutableStateOf(sharedPrefs.getBoolean("show_budget_limits", true)) }
    
    var filterBySelectedPet by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(BudgetPeriod.HAVI) }

    var searchItemName by remember { mutableStateOf("") }
    var searchLocation by remember { mutableStateOf("") }
    var searchCategory by remember { mutableStateOf("Mindegyik") }

    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()

    // Start of current month timestamp
    val monthStartTime = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Start of current year timestamp
    val yearStartTime = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // First filter by selected pet if active
    val petFilteredExpenses = if (filterBySelectedPet && selectedPet != null) {
        allExpenses.filter { exp ->
            val target = exp.targetPetIds
            when {
                target == "none" -> false
                target == "all" -> true
                target != null && target.isNotBlank() -> {
                    target.split(",").mapNotNull { it.toIntOrNull() }.contains(selectedPet.id)
                }
                else -> exp.petId == selectedPet.id
            }
        }
    } else {
        allExpenses
    }

    // Then filter by selected time period
    val periodFilteredExpenses = when (selectedPeriod) {
        BudgetPeriod.HAVI -> petFilteredExpenses.filter { it.timestamp >= monthStartTime }
        BudgetPeriod.EVI -> petFilteredExpenses.filter { it.timestamp >= yearStartTime }
        BudgetPeriod.OSSZES -> petFilteredExpenses
    }

    // Finally apply search filters (Item name, Location, Category)
    val filteredExpenses = periodFilteredExpenses.filter { exp ->
        val matchesCategory = if (searchCategory == "Mindegyik") true else exp.category == searchCategory
        
        val (prodName, locName) = if (exp.description.contains("|||")) {
            val parts = exp.description.split("|||")
            Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
        } else {
            Pair(exp.description, "")
        }
        
        val matchesItemName = if (searchItemName.isBlank()) true else prodName.contains(searchItemName, ignoreCase = true)
        val matchesLocation = if (searchLocation.isBlank()) true else locName.contains(searchLocation, ignoreCase = true)
        
        matchesCategory && matchesItemName && matchesLocation
    }

    // Group spending by category
    val spendingByCategory = filteredExpenses.groupBy { it.category }.mapValues { entry ->
        entry.value.sumOf { it.amount }
    }

    // Group spending by pet for comparative pie chart
    val spendingByPet = filteredExpenses.groupBy { it.petId }.mapValues { entry ->
        entry.value.sumOf { it.amount }
    }

    val totalSpent = filteredExpenses.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats and filter Row
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val spendingTitle = when (selectedPeriod) {
                                BudgetPeriod.HAVI -> "Havi kiadások"
                                BudgetPeriod.EVI -> "Éves kiadások"
                                BudgetPeriod.OSSZES -> "Összes kiadás"
                            }
                            Text(
                                spendingTitle,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                formatHungarianAmount(totalSpent),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedPet != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .clickable { filterBySelectedPet = !filterBySelectedPet }
                                .padding(8.dp)
                        ) {
                            Checkbox(
                                checked = filterBySelectedPet,
                                onCheckedChange = { filterBySelectedPet = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.onPrimary,
                                    checkmarkColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                "Szűrés csak ${selectedPet.name} kiadásaira",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Period selector chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(BudgetPeriod.HAVI, BudgetPeriod.EVI, BudgetPeriod.OSSZES).forEach { period ->
                    val isSelected = selectedPeriod == period
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.displayName, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }

        // Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddExpenseClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manuális beírás", fontSize = 12.sp, maxLines = 1)
                }

                Button(
                    onClick = onScanReceiptClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Blokk beolvasása AI-val", fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        // Kategóriánkénti költségek és keretek
        if (categories.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Költségek kategóriánként",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Egyedi keretek és aktuális költések",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Limit vizualizáció", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Switch(
                                    checked = showBudgetLimits,
                                    onCheckedChange = {
                                        showBudgetLimits = it
                                        sharedPrefs.edit().putBoolean("show_budget_limits", it).apply()
                                    },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onManageCategoriesClick) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kategóriák és limitek kezelése", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        categories.forEach { cat ->
                            val spent = spendingByCategory[cat] ?: 0.0
                            val limit = budgetLimits.find { it.category == cat }
                            val showLimitDetails = showBudgetLimits && limit != null && limit.isEnabled && selectedPeriod != BudgetPeriod.OSSZES

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(cat, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (showLimitDetails && limit != null) {
                                            val multiplier = if (selectedPeriod == BudgetPeriod.EVI) 12.0 else 1.0
                                            val limitAmount = limit.limitAmount * multiplier
                                            val isExceeded = spent >= limitAmount
                                            val isNear = spent >= limitAmount * 0.9 && !isExceeded
                                            if (isExceeded) {
                                                Icon(Icons.Default.Error, contentDescription = "Túllépve", tint = RedWarning, modifier = Modifier.size(16.dp))
                                            } else if (isNear) {
                                                Icon(Icons.Default.Warning, contentDescription = "Közeledik a limit", tint = OrangeWarning, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (showLimitDetails && limit != null) {
                                        val multiplier = if (selectedPeriod == BudgetPeriod.EVI) 12.0 else 1.0
                                        val limitAmount = limit.limitAmount * multiplier
                                        val isExceeded = spent >= limitAmount
                                        val isNear = spent >= limitAmount * 0.9 && !isExceeded
                                        Text(
                                            "${formatHungarianAmount(spent)} / ${formatHungarianAmount(limitAmount)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExceeded) RedWarning else if (isNear) OrangeWarning else MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Text(
                                            formatHungarianAmount(spent),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (showLimitDetails && limit != null) {
                                    val multiplier = if (selectedPeriod == BudgetPeriod.EVI) 12.0 else 1.0
                                    val limitAmount = limit.limitAmount * multiplier
                                    val ratio = (spent / limitAmount).coerceIn(0.0, 1.0)
                                    val isExceeded = spent >= limitAmount
                                    val isNear = spent >= limitAmount * 0.9 && !isExceeded

                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { ratio.toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isExceeded) RedWarning else if (isNear) OrangeWarning else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Comparative Chart (Összehasonlító grafikonok)
        val showChart = (!filterBySelectedPet && spendingByPet.size >= 2) || (filterBySelectedPet && selectedPet != null && spendingByCategory.isNotEmpty())
        if (showChart) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val chartTitle = if (filterBySelectedPet && selectedPet != null) {
                            "${selectedPet.name} költségei kategóriánként"
                        } else {
                            "Költségmegoszlás kedvencenként"
                        }
                        
                        Text(
                            chartTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Draw Pie Chart
                        val dataPoints = if (filterBySelectedPet && selectedPet != null) {
                            spendingByCategory.map { (category, spent) ->
                                category to spent
                            }
                        } else {
                            spendingByPet.map { (petId, spent) ->
                                val petName = pets.find { it.id == petId }?.name ?: "Egyéb"
                                petName to spent
                            }
                        }
                        
                        PetPieChart(dataPoints = dataPoints, total = totalSpent)
                    }
                }
            }
        }

        // Recurring expenses list (Ismétlődő költségek)
        val recurringExpenses = filteredExpenses.filter { it.isRecurring }
        if (recurringExpenses.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Ismétlődő kiadások",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        recurringExpenses.forEach { exp ->
                            val petDisplayText = formatExpensePetName(exp, pets)
                            val labelText = if (petDisplayText != null) {
                                "Kedvenc: $petDisplayText | Időköz: Havonta"
                            } else {
                                "Időköz: Havonta"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(exp.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(labelText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Text(formatHungarianAmount(exp.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Expenses details catalog
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Kiadások tételes listája",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Keresés és Szűrés",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (searchItemName.isNotEmpty() || searchLocation.isNotEmpty() || searchCategory != "Mindegyik") {
                                TextButton(
                                    onClick = {
                                        searchItemName = ""
                                        searchLocation = ""
                                        searchCategory = "Mindegyik"
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.testTag("clear_all_filters_button")
                                ) {
                                    Text("Alaphelyzet", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tétel neve kereső (kisebb méret)
                            OutlinedTextField(
                                value = searchItemName,
                                onValueChange = { searchItemName = it },
                                label = { Text("Tétel", fontSize = 11.sp) },
                                placeholder = { Text("Pl. Táp", fontSize = 11.sp) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (searchItemName.isNotEmpty()) {
                                        IconButton(onClick = { searchItemName = "" }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("search_item_name_input")
                            )
                            
                            // Üzlet kereső (nagyobb méret)
                            OutlinedTextField(
                                value = searchLocation,
                                onValueChange = { searchLocation = it },
                                label = { Text("Üzlet", fontSize = 11.sp) },
                                placeholder = { Text("Pl. Fressnapf, Állatpatika", fontSize = 11.sp) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (searchLocation.isNotEmpty()) {
                                        IconButton(onClick = { searchLocation = "" }, modifier = Modifier.size(16.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("search_location_input")
                            )
                        }
                        
                        // Kategória szűrő (Scrollable Row of chips)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Kategória szűrése:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            
                            val categoryOptions = listOf("Mindegyik") + categories
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categoryOptions) { cat ->
                                    val isSelected = searchCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { searchCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("filter_chip_$cat")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nincsenek bejegyzett kiadások ebben a szűrésben.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(filteredExpenses) { expense ->
                val petDisplayText = formatExpensePetName(expense, pets)
                val df = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
                val (prodName, locName) = if (expense.description.contains("|||")) {
                    val parts = expense.description.split("|||")
                    Pair(parts.getOrNull(0) ?: "", parts.getOrNull(1) ?: "")
                } else {
                    Pair(expense.description, "")
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        expense.category,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    if (expense.subCategory != null) {
                                        Text(
                                            "› ${expense.subCategory}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = prodName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (locName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Vásárlási hely: $locName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val detailLabelText = if (petDisplayText != null) {
                                    "Kedvenc: $petDisplayText | Dátum: ${df.format(Date(expense.timestamp))}"
                                } else {
                                    "Dátum: ${df.format(Date(expense.timestamp))}"
                                }
                                Text(
                                    detailLabelText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            if (expense.imageUri != null) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            onImageClick(expense.imageUri)
                                        }
                                ) {
                                    AsyncImage(
                                        model = expense.imageUri,
                                        contentDescription = "Bizonylat nagyítása",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp))
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatHungarianAmount(expense.amount) + " Ft",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onEditExpense(expense) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Szerkesztés", fontSize = 13.sp)
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.requestDeletion("Kiadás törlése", prodName) {
                                            viewModel.deleteExpense(expense.id)
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = RedWarning.copy(alpha = 0.8f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Törlés", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Törlés", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Pie chart
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetPieChart(dataPoints: List<Pair<String, Double>>, total: Double) {
    val colors = listOf(
        Color(0xFF1B4965), // Deep ocean blue
        Color(0xFFBA1A1A), // Red Warning (solid deep red)
        Color(0xFF6A040F), // Deep maroon
        Color(0xFF3F5E4D), // Dark forest green
        Color(0xFF7209B7), // Royal Purple
        Color(0xFFD28E52), // Warm Amber
        Color(0xFF0077B6), // Vivid blue
        Color(0xFF4D382B)  // Deep brown
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Canvas(
        modifier = Modifier
            .size(150.dp)
            .padding(12.dp)
    ) {
        var startAngle = 0f
        dataPoints.forEachIndexed { index, pair ->
            val sweepAngle = ((pair.second / total) * 360f).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true
            )
            startAngle += sweepAngle
        }

        // Draw inner circle matching the background surface to make it a elegant donut chart!
        drawCircle(
            color = surfaceColor,
            radius = 45.dp.toPx()
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Legend
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        maxItemsInEachRow = 3
    ) {
        dataPoints.forEachIndexed { index, pair ->
            val percentage = (pair.second / total) * 100
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(colors[index % colors.size], shape = CircleShape)
                )
                Text(
                    "${pair.first}: ${formatHungarianAmount(pair.second)} (${String.format("%.0f", percentage)}%)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// --- TAB CONTENT 3: HEALTH & VACCINES & OCR ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthTabContent(
    viewModel: PetViewModel,
    pet: PetEntity,
    vaccinations: List<VaccinationEntity>,
    parasiteProtections: List<ParasiteEntity>,
    medicationReminders: List<MedicationReminderEntity>,
    onAddVaccineClick: () -> Unit,
    onEditVaccine: (VaccinationEntity) -> Unit,
    onAddMedicationClick: () -> Unit,
    onEditMedication: (MedicationReminderEntity) -> Unit
) {
    val context = LocalContext.current
    val df = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
    var prescriptionPhotoToView by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timeline schedule recommendations card (Kölyök és felnőttkori ajánlások)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Oltási ütemterv & Ajánlások",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Ajánlott időrend ${if (pet.type.lowercase() == "macska") "macskáknak" else "kutyáknak"}:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pet.type.lowercase() == "macska") {
                        // Cat schedule
                        ScheduleStepItem(pet.id, "8-9. hét", "Kombinált oltás (fertőző gyomor- és bélgyulladás, macskanátha)", true)
                        ScheduleStepItem(pet.id, "11-12. hét", "Kombinált oltás ismétlése", true)
                        ScheduleStepItem(pet.id, "15-16. hét", "Veszettség elleni oltás", false)
                        ScheduleStepItem(pet.id, "1 éves kor", "Éves kombinált + veszettség ismétlés", true)
                    } else {
                        // Dog schedule
                        ScheduleStepItem(pet.id, "6-8. hét", "Parvo elleni oltás (kölyökkori alap)", true)
                        ScheduleStepItem(pet.id, "8-10. hét", "Kombinált oltás (szopornyica, hepatitis, parvo, leptospira)", true)
                        ScheduleStepItem(pet.id, "10-12. hét", "Parvo ismétlés", true)
                        ScheduleStepItem(pet.id, "12-14. hét", "Kombinált ismétlés", true)
                        ScheduleStepItem(pet.id, "3 hó után", "Veszettség elleni oltás (Kötelező!)", true)
                        ScheduleStepItem(pet.id, "6 hónap", "Veszettség elleni ismétlés (Kötelező!)", true)
                    }
                }
            }
        }

        // --- MEDICATION REMINDERS SECTION ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gyógyszerek és Emlékeztetők",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onAddMedicationClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("add_medication_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add, 
                        contentDescription = "Új gyógyszer rögzítése",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Új gyógyszer", 
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        if (medicationReminders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Healing, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                            Text(
                                "Nincs aktív gyógyszeres kezelés rögzítve.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(medicationReminders) { r ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (r.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (r.isActive) 2.dp else 0.dp),
                    border = if (r.isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (r.isActive) MaterialTheme.colorScheme.primaryContainer 
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Healing,
                                        contentDescription = null,
                                        tint = if (r.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = r.medicationName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (r.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "${r.dosage} • ${r.frequency}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            
                            // Active toggle switch
                            Switch(
                                checked = r.isActive,
                                onCheckedChange = { active ->
                                    viewModel.updateMedicationReminder(r.copy(isActive = active))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Napi időpont: ${r.reminderTime}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (r.notes.isNotEmpty()) {
                                    Text(
                                        text = r.notes,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (r.prescriptionPhotoUri != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                            .clickable { prescriptionPhotoToView = r.prescriptionPhotoUri }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Recept megtekintése",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (r.isActive) {
                                Button(
                                    onClick = {
                                        viewModel.administerMedication(r)
                                        Toast.makeText(context, "${r.medicationName} beadása naplózva a rutinok közé!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Beadva", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onEditMedication(r) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    viewModel.requestDeletion("Emlékeztető törlése", r.medicationName) {
                                        viewModel.deleteMedicationReminder(r.id)
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Törlés", modifier = Modifier.size(16.dp), tint = RedWarning.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }

        // Vaccination List Header & Action Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Oltások és Dokumentumok",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = onAddVaccineClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("add_vaccination_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Új oltás / AI Szkennelés")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Új / AI Szken")
                }
            }
        }

        // Passport Export Button (Állat Egészségügyi Adatok exportálása PDF)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { viewModel.exportPassportPdf(context, pet, vaccinations, parasiteProtections) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Állategészségügyi Adatok Exportálása (PDF)", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Figyelem: Ezek az adatok nem hivatalosak, így semmit nem bizonyítanak és nem helyettesítik a hivatalos állatorvosi okmányokat!",
                    color = RedWarning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (vaccinations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nincsenek még oltások bejegyezve.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(vaccinations) { v ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(v.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (v.isMandatory) {
                                Badge(containerColor = RedWarning, contentColor = Color.White) {
                                    Text("KÖTELEZŐ", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Dátum: ${df.format(Date(v.timestamp))}", fontSize = 13.sp)
                        if (v.diseasePrevention != null && v.diseasePrevention.isNotEmpty()) {
                            Text("Mire való: ${v.diseasePrevention}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (v.serialNumber != null && v.serialNumber.isNotEmpty()) {
                            Text("Gyártási szám: ${v.serialNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        if (v.veterinarian != null && v.veterinarian.isNotEmpty()) {
                            Text("Állatorvos: ${v.veterinarian}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        if (v.notes != null && v.notes.isNotEmpty()) {
                            Text("Megjegyzések: ${v.notes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        if (v.nextDueTimestamp != null) {
                            val nextDueStr = df.format(Date(v.nextDueTimestamp))
                            val remainingDays = ((v.nextDueTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Következő esedékesség: $nextDueStr (${if (remainingDays < 0) "LEJÁRT!" else "még $remainingDays nap"})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingDays < 0) RedWarning else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onEditVaccine(v) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Szerkesztés", fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.requestDeletion("Oltás törlése", v.name) {
                                        viewModel.deleteVaccination(v.id)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = RedWarning.copy(alpha = 0.8f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Törlés", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Törlés", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (prescriptionPhotoToView != null) {
        Dialog(onDismissRequest = { prescriptionPhotoToView = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recept fotó",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { prescriptionPhotoToView = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Bezárás")
                        }
                    }

                    AsyncImage(
                        model = prescriptionPhotoToView,
                        contentDescription = "Recept",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )

                    Button(
                        onClick = { prescriptionPhotoToView = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Bezárás")
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleStepItem(petId: Int, age: String, text: String, defaultChecked: Boolean) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("mancskiskonyv_prefs", Context.MODE_PRIVATE) }
    val cacheKey = "vax_schedule_checked_pet_${petId}_${text.hashCode()}"
    var isChecked by remember(petId, text) {
        mutableStateOf(sharedPrefs.getBoolean(cacheKey, defaultChecked))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val nextValue = !isChecked
                isChecked = nextValue
                sharedPrefs.edit().putBoolean(cacheKey, nextValue).apply()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$age: ",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// --- TAB CONTENT 4: PROTECTION & DAILY ROUTINES ---
@Composable
fun RoutineTabContent(
    viewModel: PetViewModel,
    pet: PetEntity,
    parasites: List<ParasiteEntity>,
    routinesToday: List<DailyRoutineEntity>,
    routinesAll: List<DailyRoutineEntity>,
    selectedFamilyMember: String,
    onAddParasiteClick: () -> Unit,
    onManageMembersClick: () -> Unit,
    onAddRoutineTemplateClick: () -> Unit,
    onLogRoutineTemplateClick: (RoutineTemplateEntity) -> Unit,
    onEditParasite: (ParasiteEntity) -> Unit,
    onEditRoutineTemplate: (RoutineTemplateEntity) -> Unit,
    onEditDailyRoutine: (DailyRoutineEntity) -> Unit
) {
    var showMemberMenu by remember { mutableStateOf(false) }
    val familyMembersList by viewModel.familyMembers.collectAsStateWithLifecycle()
    val routineTemplates by viewModel.currentPetRoutineTemplates.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Parasite protection section (Parazitavédelem visszaszámláló)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Parazitavédelem",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onAddParasiteClick,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_parasite_button")
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Kezelés")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Új kezelés")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (parasites.isEmpty()) {
                        Text(
                            "Nincs bejegyzett védelem. Adj hozzá, hogy követni tudd a lejárati napokat!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        parasites.forEach { p ->
                            val remainingDays = ((p.nextDueTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
                            val df = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${p.productName} (${p.treatmentMethod})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Védelem: ${p.protectionType}", fontSize = 12.sp)
                                    Text("Beadva: ${df.format(Date(p.timestamp))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (remainingDays <= 0) "LEJÁRT!" else "még $remainingDays nap",
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingDays <= 0) RedWarning else if (remainingDays < 7) OrangeWarning else MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { onEditParasite(p) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.requestDeletion("Parazita elleni kezelés törlése", p.productName) {
                                                    viewModel.deleteParasiteProtection(p.id)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Törlés", tint = RedWarning.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        // Daily Routine and Teamwork section
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Napi Rutin & Csapatmunka",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Oszd meg a feladatokat és kezeld a családtagokat!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        
                        Button(
                            onClick = onAddRoutineTemplateClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Új Rutin")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Új Rutin")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Family member management row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Kijelölt családtag:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            TextButton(
                                onClick = onManageMembersClick,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Családtagok kezelése ›", fontSize = 12.sp)
                            }
                        }
                        
                        Box {
                            OutlinedButton(
                                onClick = { showMemberMenu = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(selectedFamilyMember)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showMemberMenu,
                                onDismissRequest = { showMemberMenu = false }
                            ) {
                                familyMembersList.forEach { member ->
                                    DropdownMenuItem(
                                        text = { Text(member) },
                                        onClick = {
                                            viewModel.selectedFamilyMember.value = member
                                            showMemberMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Napi rutin feladatok:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (routineTemplates.isEmpty()) {
                        Text(
                            "Nincsenek beállított rutinok ehhez a kedvenchez. Kattints az 'Új Rutin' gombra!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        routineTemplates.forEach { template ->
                            val logToday = routinesToday.find { it.actionType == template.name }
                            val isDone = logToday != null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(
                                        if (isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                        else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (!isDone) {
                                            onLogRoutineTemplateClick(template)
                                        } else {
                                            viewModel.requestDeletion("Napi teendő visszavonása", template.name) {
                                                viewModel.deleteDailyRoutine(logToday!!.id)
                                            }
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val icon = when {
                                        template.name.contains("Etetés", true) || template.name.contains("Enni", true) -> Icons.Default.Restaurant
                                        template.name.contains("Séta", true) -> Icons.Default.DirectionsWalk
                                        template.name.contains("Gyógyszer", true) || template.name.contains("Oltás", true) -> Icons.Default.MedicalInformation
                                        template.name.contains("Fésülés", true) || template.name.contains("Kozmetika", true) || template.name.contains("Fésül", true) -> Icons.Default.Brush
                                        template.name.contains("Játék", true) || template.name.contains("Tanulás", true) -> Icons.Default.Toys
                                        else -> Icons.Default.TaskAlt
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                template.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            // Edit button for this routine template
                                            IconButton(
                                                onClick = { onEditRoutineTemplate(template) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Rutin szerkesztése",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            // Delete button for this routine template
                                            IconButton(
                                                onClick = {
                                                    viewModel.requestDeletion("Rutin teendő törlése", template.name) {
                                                        viewModel.deleteRoutineTemplate(template.id)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Rutin törlése",
                                                    tint = RedWarning.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (isDone) {
                                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(logToday!!.timestamp))
                                            Text(
                                                "Elvégezte: ${logToday.loggedBy} ($timeStr)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(template.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action logs timeline
        if (routinesAll.isNotEmpty()) {
            item {
                Text("Korábbi tevékenységek naplója", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }

            items(routinesAll.take(15)) { log ->
                val df = SimpleDateFormat("MM.dd. HH:mm", Locale.getDefault())
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(log.actionType, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Végezte: ${log.loggedBy} | ${df.format(Date(log.timestamp))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onEditDailyRoutine(log) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = {
                                viewModel.requestDeletion("Tevékenységnapló törlése", log.actionType) {
                                    viewModel.deleteDailyRoutine(log.id)
                                }
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Törlés", tint = RedWarning.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB CONTENT 5: EMERGENCY & TOXICITY LOOKUP ---
@Composable
fun EmergencyTabContent(viewModel: PetViewModel) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // State from ViewModel for AI Symptom Checker
    val isSymptomAnalyzing by viewModel.isSymptomAnalyzing.collectAsStateWithLifecycle()
    val symptomAnalysisResult by viewModel.symptomAnalysisResult.collectAsStateWithLifecycle()
    val symptomAnalysisError by viewModel.symptomAnalysisError.collectAsStateWithLifecycle()
    val pets by viewModel.allPets.collectAsStateWithLifecycle(initialValue = emptyList())

    // Selection states for custom symptom analysis
    var selectedPetIndex by remember { mutableStateOf(0) }
    var symptomText by remember { mutableStateOf("") }
    
    // Generic fallback if there are no registered pets
    val petTypes = listOf("Kutya", "Macska", "Nyúl", "Madár", "Egyéb")
    var selectedGenericPetTypeIndex by remember { mutableStateOf(0) }
    var customGenericPetType by remember { mutableStateOf("") }
    var genericPetAge by remember { mutableStateOf("Felnőtt") }

    // Map to hold checked states for the triage checklist
    var checkedItems by remember(symptomAnalysisResult) { mutableStateOf(mapOf<Int, Boolean>()) }

    val toxicityDatabase = listOf(
        Triple("Xilit / Xylitol (Édesítőszer)", "Rendkívül mérgező! Gyors inzulinfelszabadulást, vércukorszint-esést és súlyos májkárosodást okoz kutyákban és macskákban.", "AZONNALI TEENDŐ: Ha megette, haladéktalanul keresd fel az állatorvost vagy ügyeletet! Ne várd meg a tüneteket (hányás, levertség, koordináció hiánya)."),
        Triple("Szőlő és Mazsola", "Kutyáknál visszafordíthatatlan, hirtelen fellépő veseelégtelenséget okozhat. Már pár szem is végzetes lehet.", "AZONNALI TEENDŐ: Hánytatás javasolt a fogyasztást követő 2 órán belül állatorvos által. Azonnal irány a rendelő!"),
        Triple("Csokoládé (Teobromin)", "A kakaóban lévő teobromint a kedvencek szervezete nem tudja lebontani. Szapora szívverést, remegést, görcsöket okoz. A sötét/étcsokoládé a legveszélyesebb.", "TEENDŐ: Mennyiségtől és kedvenc súlyától függ. Hívj orvost, mondd be a csoki típusát és a pet súlyát hánytatás vagy aktív szén beadásához."),
        Triple("Mikulásvirág és egyéb szobanövények", "Közepesen mérgező növény. Tejnedve irritálja a nyálkahártyát, nyálzást, hányást, hasmenést idéz elő.", "TEENDŐ: Öblítsd ki a száját vízzel. Biztosíts sok friss vizet. Súlyos vagy tartós hányás esetén kérj állatorvosi segítséget."),
        Triple("Vöröshagyma és Fokhagyma", "Rombolja a vörösvértesteket, ami súlyos vérszegénységhez (anémia) vezet. A főtt és porított forma is mérgező.", "TEENDŐ: Figyeld a vizelet színét (ha sötét/barna, az vérszegénység jele). Konzultálj orvossal, ha sokat evett belőle."),
        Triple("Koffein (Kávé, Tea, Energiaital)", "Rendkívül megterheli a szív- és idegrendszert, szívritmuszavart, hiperaktivitást és remegést okoz.", "AZONNALI TEENDŐ: Ha nagyobb mennyiségű kávéport vagy teát evett, azonnal orvoshoz kell vinni!"),
        Triple("Avokádó", "Perzsin nevű toxint tartalmaz, ami hányást és hasmenést okozhat kutyáknál. A magja ráadásul fulladás- és bélelzáródás-veszélyes.", "TEENDŐ: Ne engedd játszani a maggal, és kerüld az avokádó etetését.")
    )

    val filteredToxicity = toxicityDatabase.filter {
        it.first.lowercase().contains(searchQuery.lowercase()) ||
        it.second.lowercase().contains(searchQuery.lowercase())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedWarning),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Column {
                        Text(
                            "Sürgősségi Elsősegély & Segédlet",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Pánikhelyzetben hívd azonnal az orvosodat! Az alábbiak alapvető elsősegély-nyújtási információk.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- GEMINI AI SYMPTOM CHECKER CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                "Gemini AI Sürgősségi Tünetellenőrző",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Kérdezd a mesterséges intelligenciát elsősegély lépésekért vészhelyzetben.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isSymptomAnalyzing) {
                        // Loading State
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Tünetek elemzése folyamatban...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "A Gemini épp felméri az életveszély kockázatát és összeállítja az elsősegély lépéseket.",
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else if (symptomAnalysisResult != null) {
                        // Display Analysis Results
                        val result = symptomAnalysisResult!!
                        
                        // Urgency Level Badge Card
                        val (badgeColor, textColor, urgencyText) = when {
                            result.urgency.lowercase().contains("kritikus") || result.urgency.lowercase().contains("critical") -> 
                                Triple(RedWarning.copy(alpha = 0.15f), RedWarning, "🚨 KRITIKUS VESZÉLY - Azonnali orvos vagy ügyelet!")
                            result.urgency.lowercase().contains("magas") || result.urgency.lowercase().contains("high") -> 
                                Triple(Color(0xFFFFF2E6), Color(0xFFFF8000), "⚠️ MAGAS KOCKÁZAT - Sürgős orvosi konzultáció javasolt!")
                            result.urgency.lowercase().contains("közepes") || result.urgency.lowercase().contains("medium") -> 
                                Triple(Color(0xFFFFF9E6), Color(0xFFD4AF37), "🔔 KÖZEPES KOCKÁZAT - Figyeld szorosan, 24 órán belül orvos!")
                            else -> 
                                Triple(Color(0xFFEAFaf1), Color(0xFF27AE60), "✅ ALACSONY KOCKÁZAT - Otthoni ápolás és megfigyelés elegendő.")
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = badgeColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = urgencyText,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Explanation
                        Text(
                            "Elemzés:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = result.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Checklist (Interactive Triage Check)
                        Text(
                            "Teendő Triage vizsgálólista (Ellenőrizd le ezeket):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        result.checklist.forEachIndexed { index, checkText ->
                            val isChecked = checkedItems[index] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checkedItems = checkedItems.toMutableMap().apply {
                                            put(index, !isChecked)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        checkedItems = checkedItems.toMutableMap().apply {
                                            put(index, it)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = checkText,
                                    fontSize = 13.sp,
                                    color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // DOs
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Ezeket tedd meg azonnal:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                result.doActions.forEach { action ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(action, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // DONTs
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RedWarning.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RedWarning.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = RedWarning, modifier = Modifier.size(18.dp))
                                    Text("Amit SOHA ne tegyél:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = RedWarning)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                result.dontActions.forEach { action ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("•", fontWeight = FontWeight.Bold, color = RedWarning)
                                        Text(action, fontSize = 12.sp, color = RedWarning, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearSymptomState() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Új tünetelemzés")
                        }

                    } else {
                        // Input Form
                        Text(
                            "Melyik állatnál tapasztalod?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (pets.isNotEmpty()) {
                            // Horizontally scrollable pets list
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(pets.size) { index ->
                                    val pet = pets[index]
                                    val isSelected = selectedPetIndex == index
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedPetIndex = index },
                                        label = { Text(pet.name) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        } else {
                            // Generic Fallback
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showPetTypeMenu by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showPetTypeMenu = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(petTypes[selectedGenericPetTypeIndex], fontSize = 12.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    DropdownMenu(
                                        expanded = showPetTypeMenu,
                                        onDismissRequest = { showPetTypeMenu = false }
                                    ) {
                                        petTypes.forEachIndexed { idx, type ->
                                            DropdownMenuItem(
                                                text = { Text(type) },
                                                onClick = {
                                                    selectedGenericPetTypeIndex = idx
                                                    showPetTypeMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = genericPetAge,
                                    onValueChange = { genericPetAge = it },
                                    label = { Text("Életkor") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (petTypes[selectedGenericPetTypeIndex] == "Egyéb") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customGenericPetType,
                                    onValueChange = { customGenericPetType = it },
                                    label = { Text("Milyen állat? (pl. hörcsög, hüllő) *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("custom_symptom_pet_type")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Írd le a tüneteket részletesen:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = symptomText,
                            onValueChange = { symptomText = it },
                            placeholder = { Text("Pl. hirtelen sántít a jobb lábára és nyalogatja, vagy órák óta hány és levert...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (symptomText.trim().isEmpty()) {
                                    Toast.makeText(context, "Kérlek, írd le a tünetet!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val petType = if (pets.isNotEmpty()) {
                                    pets[selectedPetIndex].type
                                } else {
                                    if (petTypes[selectedGenericPetTypeIndex] == "Egyéb") {
                                        if (customGenericPetType.isBlank()) "Egyéb" else customGenericPetType.trim()
                                    } else {
                                        petTypes[selectedGenericPetTypeIndex]
                                    }
                                }
                                val petAge = if (pets.isNotEmpty()) pets[selectedPetIndex].age else genericPetAge
                                viewModel.analyzePetSymptom(petType, petAge, symptomText.trim())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sürgősségi AI elemzés indítása")
                        }
                    }

                    symptomAnalysisError?.let { err ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            err,
                            color = RedWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Search Bar for toxicity (Toxicitás kereső)
        item {
            Text(
                "„Eheti-e?” Toxicitás Kereső",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Keresés: pl. csoki, xilit, növény...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Searched results
        if (filteredToxicity.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nincs találat az adatbázisban a keresett kifejezésre.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(filteredToxicity) { (food, hazard, action) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, RedWarning.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = RedWarning, modifier = Modifier.size(18.dp))
                            Text(food, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = RedWarning)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hazard, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RedWarning.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(action, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RedWarning)
                        }
                    }
                }
            }
        }

        // Mancs AI Sürgősségi Asszisztens Chat Card
        item {
            val chatMessages by viewModel.emergencyChatMessages.collectAsStateWithLifecycle()
            val isChatLoading by viewModel.isEmergencyChatLoading.collectAsStateWithLifecycle()
            var chatInputText by remember { mutableStateOf("") }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    "Mancs AI Sürgősségi Asszisztens",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Kérdezz bátran elsősegélyről, mérgezésekről...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        IconButton(onClick = { viewModel.clearEmergencyChat() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Beszélgetés törlése", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Message list container
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chatMessages) { message ->
                                val isUser = message.sender == "user"
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 0.dp,
                                            bottomEnd = if (isUser) 0.dp else 12.dp
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    ) {
                                        Text(
                                            text = message.text,
                                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                            if (isChatLoading) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Asszisztens válaszol...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Írj üzenetet...") },
                            textStyle = TextStyle(fontSize = 13.sp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (chatInputText.isNotBlank()) {
                                    viewModel.sendEmergencyChatMessage(chatInputText.trim())
                                    chatInputText = ""
                                }
                            })
                        )
                        
                        IconButton(
                            onClick = {
                                if (chatInputText.isNotBlank()) {
                                    viewModel.sendEmergencyChatMessage(chatInputText.trim())
                                    chatInputText = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = chatInputText.isNotBlank() && !isChatLoading,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Küldés", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Symptom Diagnostics Guide (Tünetellenőrző alapozó)
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tünetellenőrző Elsősegély Alapok",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        val symptoms = listOf(
            Triple("Darázs- vagy méhcsípés", "Helyi duzzanatot, fájdalmat okoz. Veszélyes, ha a szájüregbe csípett, mert elzárhatja a légutakat!", "TEENDŐ: Ha külső helyen van, tegyél rá jégzselét vagy hideg borogatást. Ha a torkát/száját érte, vagy nehezen lélegzik, AZONNAL rohanj az orvoshoz duzzadáscsökkentőért!"),
            Triple("Hirtelen sántítás", "Okozhatja tüske, kavics, vágás, ficam vagy rándulás.", "TEENDŐ: Vizsgáld meg óvatosan a talppárnákat idegen tárgyak után. Tisztítsd meg, fertőtlenítsd a sebeket. Ha nem terheli a lábát, vagy lógatja, korlátozd a mozgását és mutasd meg orvosnak!"),
            Triple("Puffadás / Gyomorgörcs", "Különösen nagytestű kutyáknál rendkívül veszélyes a gyomorcsavarodás, ami órákon belül végzetes lehet!", "SÜRGŐS JELEK: Ha öklendezik de nem tud hányni, a hasa feszes, kemény, nyálzik és nyugtalan. AZONNAL INDULJ az állatügyeletre! Ne várj egy percet sem, ez élet-halál kérdés!"),
            Triple("Hőguta / Túlhevülés", "Nyáron autóban hagyott vagy melegben túlmozgatott állatoknál lép fel. Eszméletvesztést, halált okoz.", "TEENDŐ: Vidd hűvös, árnyékos helyre. Kezdd el fokozatosan hűteni a testét langyos-nedves törölközőkkel (SOHA ne jéghideg vízzel, mert sokkot kap!). Kínáld vízzel, de ne kényszerítsd. Irány az orvos!")
        )

        items(symptoms) { (symptom, details, action) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(symptom, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(details, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(action, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// --- SUB COMPONENTS & DIALOGS ---

@Composable
fun MancsKiskonyvWelcomeScreen(
    pets: List<PetEntity>,
    onAddPetClick: () -> Unit,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Load generated decorative image illustration
        Image(
            painter = painterResource(id = R.drawable.img_pet_banner_1782383054561),
            contentDescription = "Boldog kiskedvencek",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Üdvözöl a MancsKiskönyv!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (pets.isEmpty()) {
            Button(
                onClick = onAddPetClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .testTag("welcome_add_first_pet_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kedvenc hozzáadása")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Válassz ki egy kedvencet a fenti listából a folytatáshoz!",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateScreen(onAddPet: () -> Unit, onImportZip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Load generated decorative image illustration
        Image(
            painter = painterResource(id = R.drawable.img_pet_banner_1782383054561),
            contentDescription = "Boldog kiskedvencek",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Üdvözöl a MancsKiskönyv!",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Kövesd kedvenceid egészségügyi adatait, oltásait, napi rutinját és a pénzügyi kiadásokat egyetlen rendezett helyen. Kezdésként add hozzá az első háziállatodat!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onAddPet,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .testTag("add_first_pet_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Első Kedvenc Hozzáadása", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onImportZip,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .testTag("import_zip_empty_state_button")
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Importálás ZIP fájlból", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

fun calculateAge(birthDateStr: String?): String {
    if (birthDateStr.isNullOrBlank()) return ""
    return try {
        val parts = birthDateStr.split("-")
        if (parts.size != 3) return birthDateStr
        val year = parts[0].toIntOrNull() ?: return birthDateStr
        val month = parts[1].toIntOrNull()?.minus(1) ?: return birthDateStr
        val day = parts[2].toIntOrNull() ?: return birthDateStr

        val birthCalendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month)
            set(java.util.Calendar.DAY_OF_MONTH, day)
        }
        val today = java.util.Calendar.getInstance()

        var years = today.get(java.util.Calendar.YEAR) - birthCalendar.get(java.util.Calendar.YEAR)
        var months = today.get(java.util.Calendar.MONTH) - birthCalendar.get(java.util.Calendar.MONTH)

        if (today.get(java.util.Calendar.DAY_OF_MONTH) < birthCalendar.get(java.util.Calendar.DAY_OF_MONTH)) {
            months--
        }
        if (months < 0) {
            years--
            months += 12
        }

        when {
            years > 0 && months > 0 -> "$years éves, $months hónapos"
            years > 0 -> "$years éves"
            months > 0 -> "$months hónapos"
            else -> "Pár napos"
        }
    } catch (e: Exception) {
        birthDateStr
    }
}

// --- NEW HEALTH & SAFETY FEATURES ---

data class FoodSafetyItem(
    val name: String,
    val category: String, // Hús, Zöldség, Gyümölcs, Ital, Édesség, Egyéb
    val safetyLevel: SafetyLevel,
    val explanation: String
)

enum class SafetyLevel {
    SAFE, // Biztonságos (Zöld)
    CAUTION, // Figyelem / Óvatosság (Sárga)
    TOXIC // Veszélyes / Mérgező (Piros)
}

@Composable
fun PetAgeMilestonesCard(pet: PetEntity) {
    val (humanAge, phase, tip) = remember(pet.birthDate, pet.age, pet.type) {
        calculateHumanAgeAndPhase(pet.birthDate, pet.age, pet.type)
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Életkor kalkulátor & Mérföldkövek",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Human age display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$humanAge",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Column {
                    Text(
                        "${pet.name} emberi években kifejezve kb.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "$humanAge éves!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Timeline progress
            val phases = listOf("Kölyök", "Kamasz", "Felnőtt", "Senior")
            val currentPhaseIndex = when (phase) {
                "Kölyökkor" -> 0
                "Kamaszkor" -> 1
                "Felnőttkor" -> 2
                else -> 3
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                phases.forEachIndexed { index, pName ->
                    val isCurrent = index == currentPhaseIndex
                    val isPassed = index < currentPhaseIndex
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else if (isPassed) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPassed) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            } else if (isCurrent) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            pName,
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Care advice for the current milestone
            Column {
                Text(
                    "Aktuális mérföldkő: $phase",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    tip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun PetFoodSafetyCheckerCard() {
    val foods = remember {
        listOf(
            FoodSafetyItem("Csokoládé", "Édesség", SafetyLevel.TOXIC, "Teobromint tartalmaz, ami súlyos mérgezést, hányást, görcsöket vagy akár szívelégtelenséget okozhat kisállatoknál."),
            FoodSafetyItem("Szőlő & Mazsola", "Gyümölcs", SafetyLevel.TOXIC, "Akár kis mennyiségben is akut veseelégtelenséget okozhat kutyáknál és macskáknál."),
            FoodSafetyItem("Hagyma & Fokhagyma", "Zöldség", SafetyLevel.TOXIC, "Rombolja a vörösvértesteket, vérszegénységet (anémiát) okozhat mind a kutyáknál, mind a macskáknál."),
            FoodSafetyItem("Xilit / Nyírfacukor", "Édesség", SafetyLevel.TOXIC, "Rendkívül veszélyes! Hirtelen inzulinrezisztenciát, életveszélyes vércukorszint-esést és májelégtelenséget okoz."),
            FoodSafetyItem("Avokádó", "Zöldség", SafetyLevel.TOXIC, "Persin nevű gombaölő toxint tartalmaz, ami hányást, hasmenést és légzési nehézséget okozhat."),
            FoodSafetyItem("Tej & Tejtermékek", "Ital", SafetyLevel.CAUTION, "Sok felnőtt kutya és macska laktózérzékeny, így hasmenést és emésztési zavarokat okozhat."),
            FoodSafetyItem("Főtt csontok", "Hús", SafetyLevel.CAUTION, "A főtt csontok szilánkosan törnek, ami felsértheti vagy elzárhatja a nyelőcsövet és a beleket."),
            FoodSafetyItem("Alma (mag nélkül)", "Gyümölcs", SafetyLevel.SAFE, "Kiváló rost- és vitaminforrás. Fontos: a magokat távolítsd el, mert cianidot tartalmaznak!"),
            FoodSafetyItem("Sárgarépa", "Zöldség", SafetyLevel.SAFE, "Nagyon egészséges rágcsálnivaló, gazdag béta-karotinban és rostokban, tisztítja a fogakat."),
            FoodSafetyItem("Főtt lazac", "Hús", SafetyLevel.SAFE, "Főzve kitűnő omega-3 zsírsav- és fehérjeforrás, támogatja az immunrendszert és a szőrzet egészségét."),
            FoodSafetyItem("Főtt csirkehús", "Hús", SafetyLevel.SAFE, "Kiváló sovány fehérjeforrás. Mindig csont nélkül és fűszerek (főleg só, hagyma) nélkül tálald!"),
            FoodSafetyItem("Sütőtök", "Zöldség", SafetyLevel.SAFE, "Kiváló az emésztésre, segít mind a székrekedés, mind a hasmenés enyhítésében.")
        )
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Mind") }
    var selectedFood by remember { mutableStateOf<FoodSafetyItem?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    
    val filteredFoods = remember(searchQuery, selectedCategory) {
        foods.filter { item ->
            val matchesSearch = item.name.lowercase().contains(searchQuery.lowercase()) ||
                                item.explanation.lowercase().contains(searchQuery.lowercase())
            val matchesCategory = selectedCategory == "Mind" || item.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Mérgező & Biztonságos Ételek",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Ellenőrizd azonnal, hogy mit ehet a kedvenced!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Keress pl. csoki, hagyma, alma...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Category filter chips
                val categories = listOf("Mind", "Hús", "Zöldség", "Gyümölcs", "Édesség")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(containerColor)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = contentColor)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Food list or detail card
                if (selectedFood != null) {
                    // Detail card for the selected food
                    val item = selectedFood!!
                    val safetyBgColor = when (item.safetyLevel) {
                        SafetyLevel.SAFE -> Color(0xFFE8F5E9)
                        SafetyLevel.CAUTION -> Color(0xFFFFF3E0)
                        SafetyLevel.TOXIC -> Color(0xFFFFEBEE)
                    }
                    val safetyTextColor = when (item.safetyLevel) {
                        SafetyLevel.SAFE -> Color(0xFF2E7D32)
                        SafetyLevel.CAUTION -> Color(0xFFE65100)
                        SafetyLevel.TOXIC -> Color(0xFFC62828)
                    }
                    val safetyText = when (item.safetyLevel) {
                        SafetyLevel.SAFE -> "BIZTONSÁGOS"
                        SafetyLevel.CAUTION -> "FIGYELEM / ÓVATOSSÁG"
                        SafetyLevel.TOXIC -> "VESZÉLYES / MÉRGEZŐ"
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = safetyBgColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = safetyTextColor
                                )
                                Text(
                                    safetyText,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = safetyTextColor,
                                    modifier = Modifier
                                        .background(safetyTextColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                item.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = safetyTextColor.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = { selectedFood = null },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Vissza a listához", fontSize = 12.sp, color = safetyTextColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Show scrollable results
                    if (filteredFoods.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nincs találat erre az ételre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredFoods.take(5).forEach { item ->
                                val badgeColor = when (item.safetyLevel) {
                                    SafetyLevel.SAFE -> Color(0xFF4CAF50)
                                    SafetyLevel.CAUTION -> Color(0xFFFF9800)
                                    SafetyLevel.TOXIC -> Color(0xFFF44336)
                                }
                                val badgeText = when (item.safetyLevel) {
                                    SafetyLevel.SAFE -> "Eheti"
                                    SafetyLevel.CAUTION -> "Óvatosan"
                                    SafetyLevel.TOXIC -> "Mérgező"
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                                        .clickable { selectedFood = item }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor)
                                        )
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            badgeText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                            if (filteredFoods.size > 5) {
                                Text(
                                    "További ${filteredFoods.size - 5} találat a keresés finomításával...",
                                    fontSize = 11.sp,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class TimelineEvent(
    val title: String,
    val date: Long,
    val type: String, // "Oltás" vagy "Parazitavédelem"
    val badge: String, // "Késésben", "Aktuális", "Közelgő"
    val badgeColor: Color,
    val subtitle: String
)

@Composable
fun PetHealthScheduleCard(
    vaccinations: List<VaccinationEntity>,
    parasiteProtections: List<ParasiteEntity>
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val timelineEvents = remember(vaccinations, parasiteProtections) {
        val now = System.currentTimeMillis()
        val events = mutableListOf<TimelineEvent>()
        
        // Add vaccinations with future due dates
        vaccinations.forEach { vac ->
            val due = vac.nextDueTimestamp
            if (due != null && due > 0) {
                val delayMs = due - now
                val (badge, color) = when {
                    delayMs < 0 -> Pair("Késésben", Color(0xFFF44336))
                    delayMs < 14 * 24 * 60 * 60 * 1000L -> Pair("Aktuális", Color(0xFFFF9800))
                    else -> Pair("Közelgő", Color(0xFF4CAF50))
                }
                val hasPrevention = !vac.diseasePrevention.isNullOrBlank()
                val displayTitle = if (hasPrevention) vac.diseasePrevention!! else vac.name
                val displaySubtitle = if (hasPrevention) "Oltóanyag: ${vac.name}" else "Következő oltás esedékessége"
                events.add(
                    TimelineEvent(
                        title = displayTitle,
                        date = due,
                        type = "Oltás",
                        badge = badge,
                        badgeColor = color,
                        subtitle = displaySubtitle
                    )
                )
            }
        }
        
        // Add parasite protections
        parasiteProtections.forEach { par ->
            val due = par.nextDueTimestamp
            if (due > 0) {
                val delayMs = due - now
                val (badge, color) = when {
                    delayMs < 0 -> Pair("Késésben", Color(0xFFF44336))
                    delayMs < 14 * 24 * 60 * 60 * 1000L -> Pair("Aktuális", Color(0xFFFF9800))
                    else -> Pair("Közelgő", Color(0xFF4CAF50))
                }
                events.add(
                    TimelineEvent(
                        title = "${par.protectionType} elleni védekezés",
                        date = due,
                        type = "Parazitavédelem",
                        badge = badge,
                        badgeColor = color,
                        subtitle = "Készítmény: ${par.productName}"
                    )
                )
            }
        }
        
        // Sort chronologically
        events.sortBy { it.date }
        events
    }
    
    val df = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Egészségügyi ütemterv",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Oltások és parazitavédelem naptára",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                
                if (timelineEvents.isEmpty()) {
                    // Fallback to recommended general schedule
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Nincs rögzített emlékeztető", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Még nem rögzítettél olyan oltást vagy parazitavédelmet, amelynek jövőbeli esedékessége lenne. Rögzíts egy újat az 'Oltások' vagy 'Védekezés' gombbal, és a rendszer automatikusan felveszi ide az emlékeztetőt!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        timelineEvents.forEach { event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            event.type.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                        Text(
                                            event.badge,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            color = event.badgeColor,
                                            modifier = Modifier
                                                .background(event.badgeColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        event.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        event.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Text(
                                        df.format(Date(event.date)),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (event.badge == "Késésben") event.badgeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    val daysDiff = ((event.date - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt()
                                    Text(
                                        text = when {
                                            daysDiff < 0 -> "${-daysDiff} napja lejárt"
                                            daysDiff == 0 -> "Ma esedékes!"
                                            daysDiff == 1 -> "Holnap esedékes"
                                            else -> "$daysDiff nap múlva"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (daysDiff <= 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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

fun calculateHumanAgeAndPhase(birthDateStr: String?, petAgeStr: String, petTypeStr: String): Triple<Int, String, String> {
    val type = petTypeStr.trim().lowercase()
    val isCat = type.contains("macska") || type.contains("cat") || type.contains("cica")
    val isDog = type.contains("kutya") || type.contains("dog") || type.contains("eb")
    
    // Parse years and months
    var years = 0
    var months = 0
    
    if (!birthDateStr.isNullOrBlank()) {
        try {
            val parts = birthDateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull() ?: 0
                val month = parts[1].toIntOrNull()?.minus(1) ?: 0
                val day = parts[2].toIntOrNull() ?: 0
                
                val birthCalendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, day)
                }
                val today = java.util.Calendar.getInstance()
                
                years = today.get(java.util.Calendar.YEAR) - birthCalendar.get(java.util.Calendar.YEAR)
                months = today.get(java.util.Calendar.MONTH) - birthCalendar.get(java.util.Calendar.MONTH)
                if (today.get(java.util.Calendar.DAY_OF_MONTH) < birthCalendar.get(java.util.Calendar.DAY_OF_MONTH)) {
                    months--
                }
                if (months < 0) {
                    years--
                    months += 12
                }
            }
        } catch (e: Exception) {
            // fallback
        }
    }
    
    if (years == 0 && months == 0 && petAgeStr.isNotBlank()) {
        val cleanAge = petAgeStr.lowercase()
        val yearsMatch = java.util.regex.Pattern.compile("(\\d+)\\s*év").matcher(cleanAge)
        val monthsMatch = java.util.regex.Pattern.compile("(\\d+)\\s*hónap").matcher(cleanAge)
        
        if (yearsMatch.find()) {
            years = yearsMatch.group(1)?.toIntOrNull() ?: 0
        }
        if (monthsMatch.find()) {
            months = monthsMatch.group(1)?.toIntOrNull() ?: 0
        }
        
        if (years == 0 && months == 0) {
            years = petAgeStr.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
    
    val totalMonths = years * 12 + months
    val humanAge: Int
    val phase: String
    val tip: String
    
    if (isCat) {
        humanAge = when {
            totalMonths <= 1 -> 1
            totalMonths <= 3 -> 4
            totalMonths <= 6 -> 10
            totalMonths <= 12 -> 15
            totalMonths <= 24 -> 24
            else -> {
                val remYears = (totalMonths - 24) / 12
                24 + remYears * 4
            }
        }
        
        when {
            totalMonths < 6 -> {
                phase = "Kölyökkor"
                tip = "Ebben a szakaszban a legfontosabb az alapvető oltások beadatása, a szocializáció és a kiváló minőségű kölyöktáp biztosítása."
            }
            totalMonths < 18 -> {
                phase = "Kamaszkor"
                tip = "A kamaszkor során megnő a játékosság és a határok feszegetése. Ajánlott az ivartalanítás elvégzése és az aktív lemozgatás."
            }
            totalMonths < 84 -> {
                phase = "Felnőttkor"
                tip = "Kedvenced elérte teljes fizikai és szellemi érettségét. Figyelj a rendszeres évenkénti oltásokra, parazitavédelemre és a fegyelem megtartására."
            }
            else -> {
                phase = "Időskor (Szenior)"
                tip = "Az idősödő kedvenceknél kiemelten fontos a féléves orvosi szűrés, a kímélőbb étrend, valamint az ízületvédők adása a vitalitás megőrzéséhez."
            }
        }
    } else {
        humanAge = when {
            totalMonths <= 1 -> 1
            totalMonths <= 3 -> 5
            totalMonths <= 6 -> 9
            totalMonths <= 12 -> 15
            totalMonths <= 24 -> 24
            else -> {
                val remYears = (totalMonths - 24) / 12
                24 + remYears * 4
            }
        }
        
        when {
            totalMonths < 6 -> {
                phase = "Kölyökkor"
                tip = "Ebben a szakaszban a legfontosabb az alapvető oltások beadatása, a szocializáció és a kiváló minőségű kölyöktáp biztosítása."
            }
            totalMonths < 18 -> {
                phase = "Kamaszkor"
                tip = "A kamaszkor során megnő a játékosság és a határok feszegetése. Ajánlott az ivartalanítás elvégzése és az aktív lemozgatás."
            }
            totalMonths < 84 -> {
                phase = "Felnőttkor"
                tip = "Kedvenced elérte teljes fizikai és szellemi érettségét. Figyelj a rendszeres évenkénti oltásokra, parazitavédelemre és a fegyelem megtartására."
            }
            else -> {
                phase = "Időskor (Szenior)"
                tip = "Az idősödő kedvenceknél kiemelten fontos a féléves orvosi szűrés, a kímélőbb étrend, valamint az ízületvédők adása a vitalitás megőrzéséhez."
            }
        }
    }
    
    return Triple(humanAge, phase, tip)
}

// Dialog for Add/Edit Pet
@Composable
fun PetFormDialog(
    title: String,
    pet: PetEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String, type: String, breed: String, color: String, gender: String, age: String,
        isNeutered: Boolean, chipNumber: String, photoUri: String?, allergies: String, chronicDiseases: String,
        vetName: String, vetPhone: String, insuranceCompany: String, insurancePolicyNumber: String,
        birthDate: String?, chipImplantDate: String?, chipExpiryDate: String?
    ) -> Unit,
    onImportZipClick: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(pet?.name ?: "") }
    var type by remember { mutableStateOf(pet?.type ?: "Kutya") }
    var breed by remember { mutableStateOf(pet?.breed ?: "") }
    var color by remember { mutableStateOf(pet?.color ?: "") }
    var gender by remember { mutableStateOf(pet?.gender ?: "Szuka") }
    var age by remember { mutableStateOf(pet?.age ?: "") }
    var birthDate by remember { mutableStateOf(pet?.birthDate ?: "") }
    var isNeutered by remember { mutableStateOf(pet?.isNeutered ?: false) }
    var chipNumber by remember { mutableStateOf(pet?.chipNumber ?: "") }
    var chipImplantDate by remember { mutableStateOf(pet?.chipImplantDate ?: "") }
    var chipExpiryDate by remember { mutableStateOf(pet?.chipExpiryDate ?: "") }
    var allergies by remember { mutableStateOf(pet?.allergies ?: "") }
    var diseases by remember { mutableStateOf(pet?.chronicDiseases ?: "") }
    var vetName by remember { mutableStateOf(pet?.vetName ?: "") }
    var vetPhone by remember { mutableStateOf(pet?.vetPhone ?: "") }
    var insuranceCompany by remember { mutableStateOf(pet?.insuranceCompany ?: "") }
    var insurancePolicyNumber by remember { mutableStateOf(pet?.insurancePolicyNumber ?: "") }

    var photoUri by remember { mutableStateOf(pet?.photoUri) }

    val standardPetTypes = listOf("Kutya", "Macska", "Nyúl", "Madár", "Hüllő", "Ló", "Hal", "Kétéltű", "Tengerimalac", "Hörcsög")
    val petTypes = standardPetTypes + "Egyéb"
    var isCustomType by remember { mutableStateOf(pet != null && !standardPetTypes.contains(pet.type)) }
    var customTypeText by remember { mutableStateOf(if (isCustomType) pet?.type ?: "" else "") }
    var showTypeMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var showPhotoPickerOptions by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { photoUri = copyUriToInternalStorage(context, it.toString(), "pet_avatar") }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val dir = java.io.File(context.filesDir, "pet_media")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "pet_photo_${System.currentTimeMillis()}.jpg")
                val fos = java.io.FileOutputStream(file)
                it.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()
                photoUri = Uri.fromFile(file).toString()
            } catch (e: Exception) {
                android.util.Log.e("PetFormDialog", "Error saving camera thumbnail: ${e.message}")
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                if (pet == null && onImportZipClick != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onImportZipClick() }
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "ZIP importálás",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Kisállat importálása ZIP-ből",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Oltások, súlyok, kiadások és minden korábbi adat automatikus beolvasása",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Profile image selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (photoUri != null) {
                        Box(modifier = Modifier.size(80.dp)) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Profilkép előnézet",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { photoUri = null },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Törlés",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { showPhotoPickerOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Kép",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Kedvenc profilképe",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Készíts egy fotót vagy válassz ki egyet a galériából!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kedvenc Neve *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pet_name_input")
                )

                // Pet Type Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isCustomType) "Egyéb" else type,
                        onValueChange = {},
                        label = { Text("Állat Faja") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showTypeMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showTypeMenu = true }
                    )
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        petTypes.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    if (t == "Egyéb") {
                                        isCustomType = true
                                        type = customTypeText.ifEmpty { "Egyéb" }
                                    } else {
                                        isCustomType = false
                                        type = t
                                    }
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                if (isCustomType) {
                    OutlinedTextField(
                        value = customTypeText,
                        onValueChange = {
                            customTypeText = it
                            type = it.ifEmpty { "Egyéb" }
                        },
                        label = { Text("Egyedi állatfaj megnevezése (pl. Görény, Süni)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = breed,
                    onValueChange = { breed = it },
                    label = { Text("Fajta (pl. Golden Retriever, Sziámi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Szín (pl. zsemle, cirmos, fekete)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Gender selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).clickable { gender = "Kan" }
                    ) {
                        RadioButton(selected = gender == "Kan" || gender == "Hím", onClick = { gender = "Kan" })
                        Text("Kan / Hím", fontSize = 13.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).clickable { gender = "Szuka" }
                    ) {
                        RadioButton(selected = gender == "Szuka" || gender == "Nőstény", onClick = { gender = "Szuka" })
                        Text("Szuka / Nőstény", fontSize = 13.sp)
                    }
                }

                // Age / Birthdate input with automatic calculation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = birthDate,
                        onValueChange = { 
                            birthDate = it
                            val computed = calculateAge(it)
                            if (computed.isNotEmpty()) {
                                age = computed
                            }
                        },
                        label = { Text("Születési idő (ÉÉÉÉ-HH-NN)") },
                        placeholder = { Text("Pl. 2024-05-12") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val calendar = java.util.Calendar.getInstance()
                            if (birthDate.isNotBlank()) {
                                val parts = birthDate.split("-")
                                if (parts.size == 3) {
                                    val y = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    val d = parts[2].toIntOrNull()
                                    if (y != null && m != null && d != null) {
                                        calendar.set(java.util.Calendar.YEAR, y)
                                        calendar.set(java.util.Calendar.MONTH, m - 1)
                                        calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    }
                                }
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val formattedMonth = String.format("%02d", month + 1)
                                    val formattedDay = String.format("%02d", dayOfMonth)
                                    val dateStr = "$year-$formattedMonth-$formattedDay"
                                    birthDate = dateStr
                                    age = calculateAge(dateStr)
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Naptár")
                    }
                }

                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it },
                    label = { Text("Kor *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNeutered = !isNeutered }
                ) {
                    Checkbox(checked = isNeutered, onCheckedChange = { isNeutered = it })
                    Text("Ivartalanítva van", fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = chipNumber,
                    onValueChange = { chipNumber = it },
                    label = { Text("Mikrochip szám (15 számjegy)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chipImplantDate,
                        onValueChange = { chipImplantDate = it },
                        label = { Text("Chip beültetés dátuma (ÉÉÉÉ-HH-NN)") },
                        placeholder = { Text("pl. 2026-07-04") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val calendar = java.util.Calendar.getInstance()
                            if (chipImplantDate.isNotBlank()) {
                                val parts = chipImplantDate.split("-")
                                if (parts.size == 3) {
                                    val y = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    val d = parts[2].toIntOrNull()
                                    if (y != null && m != null && d != null) {
                                        calendar.set(java.util.Calendar.YEAR, y)
                                        calendar.set(java.util.Calendar.MONTH, m - 1)
                                        calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    }
                                }
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val formattedMonth = String.format("%02d", month + 1)
                                    val formattedDay = String.format("%02d", dayOfMonth)
                                    chipImplantDate = "$year-$formattedMonth-$formattedDay"
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Naptár")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chipExpiryDate,
                        onValueChange = { chipExpiryDate = it },
                        label = { Text("Chip lejárati dátuma (ÉÉÉÉ-HH-NN)") },
                        placeholder = { Text("pl. 2036-07-04") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val calendar = java.util.Calendar.getInstance()
                            if (chipExpiryDate.isNotBlank()) {
                                val parts = chipExpiryDate.split("-")
                                if (parts.size == 3) {
                                    val y = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    val d = parts[2].toIntOrNull()
                                    if (y != null && m != null && d != null) {
                                        calendar.set(java.util.Calendar.YEAR, y)
                                        calendar.set(java.util.Calendar.MONTH, m - 1)
                                        calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    }
                                }
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val formattedMonth = String.format("%02d", month + 1)
                                    val formattedDay = String.format("%02d", dayOfMonth)
                                    chipExpiryDate = "$year-$formattedMonth-$formattedDay"
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Naptár")
                    }
                }

                // Health additions
                Text("Különleges Egészségügyi Megjegyzések:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Allergiák (pl. Csirke, poratka, nincs)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = diseases,
                    onValueChange = { diseases = it },
                    label = { Text("Krónikus betegségek / Kezelések") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Vet details
                Text("Állatorvos Elérhetősége:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

                OutlinedTextField(
                    value = vetName,
                    onValueChange = { vetName = it },
                    label = { Text("Állatorvos neve") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vetPhone,
                    onValueChange = { vetPhone = it },
                    label = { Text("Orvos telefonszáma (hívható)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                // Insurance
                Text("Állatbiztosítás:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

                OutlinedTextField(
                    value = insuranceCompany,
                    onValueChange = { insuranceCompany = it },
                    label = { Text("Biztosító neve") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = insurancePolicyNumber,
                    onValueChange = { insurancePolicyNumber = it },
                    label = { Text("Kötvényszám / Szerződésszám") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                val finalType = if (isCustomType) customTypeText.ifEmpty { "Egyéb" } else type
                                onSubmit(
                                    name, finalType, breed, color, gender, age, isNeutered, chipNumber, photoUri,
                                    allergies, diseases, vetName, vetPhone, insuranceCompany, insurancePolicyNumber,
                                    birthDate, chipImplantDate, chipExpiryDate
                                )
                            }
                        },
                        enabled = name.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("save_pet_button")
                    ) {
                        Text("Mentés")
                    }
                }
            }
        }
    }

    if (showPhotoPickerOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerOptions = false },
            title = { Text("Profilkép Hozzáadása", fontWeight = FontWeight.Bold) },
            text = { Text("Hogyan szeretnéd hozzáadni a képet?") },
            confirmButton = {
                Button(onClick = {
                    showPhotoPickerOptions = false
                    cameraLauncher.launch(null)
                }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kamera")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showPhotoPickerOptions = false
                        galleryLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galéria")
                }
            }
        )
    }
}

// Dialog for Weight Measurement
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWeightDialog(
    weightToEdit: WeightEntity? = null,
    petType: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        weight: Double,
        shoulderHeight: Double?,
        bodyLength: Double?,
        chestCircumference: Double?,
        neckCircumference: Double?
    ) -> Unit
) {
    var weightStr by remember { mutableStateOf(weightToEdit?.weightKg?.toString() ?: "") }
    var shoulderHeightStr by remember { mutableStateOf(weightToEdit?.shoulderHeightCm?.toString() ?: "") }
    var bodyLengthStr by remember { mutableStateOf(weightToEdit?.bodyLengthCm?.toString() ?: "") }
    var chestCircumferenceStr by remember { mutableStateOf(weightToEdit?.chestCircumferenceCm?.toString() ?: "") }
    var neckCircumferenceStr by remember { mutableStateOf(weightToEdit?.neckCircumferenceCm?.toString() ?: "") }

    var showGuide by remember { mutableStateOf(false) }
    var activeGuideType by remember {
        mutableStateOf(
            when (petType?.lowercase() ?: "kutya") {
                "kutya" -> "Kutya"
                "macska" -> "Macska"
                "ló", "lo" -> "Ló"
                else -> "Kutya"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (weightToEdit != null) "Mérések Szerkesztése" else "Új Mérés (Súly és Méretek)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Add meg a kedvenced súlyát és testméreteit a növekedés követéséhez:")
                
                // Measurement Guide
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGuide = !showGuide }
                        ) {
                            Icon(
                                imageVector = if (showGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showGuide) "Elrejtés" else "Mutatás",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Mérési segédlet megtekintése",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (showGuide) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Kutya", "Macska", "Ló").forEach { t ->
                                    val isSelected = activeGuideType == t
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { activeGuideType = t },
                                        label = { Text(t) }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val imageRes = when (activeGuideType) {
                                "Macska" -> R.drawable.img_measure_cat
                                "Ló" -> R.drawable.img_measure_horse
                                else -> R.drawable.img_measure_dog
                            }
                            
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = imageRes),
                                    contentDescription = "$activeGuideType mérési segédlet kép",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val helpTexts = when (activeGuideType) {
                                    "Macska" -> listOf(
                                        "📏 Marmagasság: A talajtól a lapockák közötti legmagasabb pontig (a marig).",
                                        "📏 Testhossz: A szegycsonttól/mellkas elejétől a farok tövéig.",
                                        "📏 Mellbőség: A mellkas legszélesebb kerülete, a mellső lábak mögött.",
                                        "📏 Nyakbőség: A nyak töve, kényelmesen mérve."
                                    )
                                    "Ló" -> listOf(
                                        "📏 Marmagasság: A talajtól a mar legmagasabb pontjáig függőlegesen.",
                                        "📏 Testhossz: A vállbúbtól az ülőgumóig terjedő hosszirányú távolság.",
                                        "📏 Mellbőség: A heveder helyén, a mar mögött mért teljes körméret.",
                                        "📏 Nyakbőség: A nyak tövétől a váll búbjáig terjedő kerület."
                                    )
                                    else -> listOf(
                                        "📏 Marmagasság: A talajtól a mar legmagasabb pontjáig (a vállak fölött).",
                                        "📏 Testhossz: A szegycsont elejétől a farok tövéig (ülőgumóig).",
                                        "📏 Mellbőség: A mellkas legszélesebb kerülete, a mellső lábak mögött.",
                                        "📏 Nyakbőség: A nyak legalsó része a vállak felett."
                                    )
                                }
                                
                                helpTexts.forEach { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = weightStr,
                    onValueChange = { weightStr = it },
                    label = { Text("Testsúly (kg) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("weight_input")
                )
                
                Text("Opcionális testméretek a növekedés követéséhez:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                OutlinedTextField(
                    value = shoulderHeightStr,
                    onValueChange = { shoulderHeightStr = it },
                    label = { Text("Marmagasság (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("shoulder_height_input")
                )

                OutlinedTextField(
                    value = bodyLengthStr,
                    onValueChange = { bodyLengthStr = it },
                    label = { Text("Testhossz (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("body_length_input")
                )

                OutlinedTextField(
                    value = chestCircumferenceStr,
                    onValueChange = { chestCircumferenceStr = it },
                    label = { Text("Mellbőség (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("chest_circumference_input")
                )

                OutlinedTextField(
                    value = neckCircumferenceStr,
                    onValueChange = { neckCircumferenceStr = it },
                    label = { Text("Nyakbőség (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("neck_circumference_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightStr.replace(",", ".").toDoubleOrNull()
                    if (w != null && w > 0.0) {
                        val sh = shoulderHeightStr.replace(",", ".").toDoubleOrNull()
                        val bl = bodyLengthStr.replace(",", ".").toDoubleOrNull()
                        val cc = chestCircumferenceStr.replace(",", ".").toDoubleOrNull()
                        val nc = neckCircumferenceStr.replace(",", ".").toDoubleOrNull()
                        onSubmit(w, sh, bl, cc, nc)
                    }
                },
                enabled = weightStr.isNotEmpty(),
                modifier = Modifier.testTag("save_weight_button")
            ) {
                Text("Mentés")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse")
            }
        }
    )
}

fun formatExpensePetName(expense: ExpenseEntity, pets: List<PetEntity>): String? {
    val target = expense.targetPetIds
    return when {
        target == "none" -> null
        target == "all" -> "Összes"
        target != null && target.isNotBlank() -> {
            val ids = target.split(",").mapNotNull { it.toIntOrNull() }
            if (ids.isEmpty()) {
                null
            } else {
                val names = ids.mapNotNull { id -> pets.find { it.id == id }?.name }
                if (names.isEmpty()) null else names.joinToString(", ")
            }
        }
        else -> {
            if (expense.petId > 0) {
                pets.find { it.id == expense.petId }?.name
            } else {
                null
            }
        }
    }
}

// Dialog for Add Expense (with deep categories)
@Composable
fun AddExpenseDialog(
    pets: List<PetEntity>,
    selectedPetId: Int,
    expenseToEdit: ExpenseEntity? = null,
    viewModel: PetViewModel,
    prefilledImageUri: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        petId: Int, amount: Double, category: String, subCategory: String?, description: String,
        isRecurring: Boolean, recurringIntervalMonths: Int, imageUri: String?, targetPetIds: String?
    ) -> Unit
) {
    var selectedPetIds by remember {
        mutableStateOf<Set<Int>>(
            if (expenseToEdit != null) {
                val target = expenseToEdit.targetPetIds
                when {
                    target == "none" -> emptySet()
                    target == "all" -> pets.map { it.id }.toSet()
                    target != null && target.isNotBlank() -> {
                        target.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                    }
                    else -> if (expenseToEdit.petId > 0) setOf(expenseToEdit.petId) else emptySet()
                }
            } else {
                if (pets.any { it.id == selectedPetId }) {
                    setOf(selectedPetId)
                } else {
                    pets.firstOrNull()?.id?.let { setOf(it) } ?: emptySet()
                }
            }
        )
    }
    var amountStr by remember { mutableStateOf(expenseToEdit?.amount?.toInt()?.toString() ?: "") }
    var category by remember { mutableStateOf(expenseToEdit?.category ?: "Étel") }
    var subCategory by remember { mutableStateOf<String?>(expenseToEdit?.subCategory) }
    var productName by remember {
        mutableStateOf(
            if (expenseToEdit?.description?.contains("|||") == true) {
                expenseToEdit.description.substringBefore("|||")
            } else {
                expenseToEdit?.description ?: ""
            }
        )
    }
    var purchaseLocation by remember {
        mutableStateOf(
            if (expenseToEdit?.description?.contains("|||") == true) {
                expenseToEdit.description.substringAfter("|||")
            } else {
                ""
            }
        )
    }
    var isRecurring by remember { mutableStateOf(expenseToEdit?.isRecurring ?: false) }
    var imageUri by remember {
        mutableStateOf<Uri?>(
            expenseToEdit?.imageUri?.let { Uri.parse(it) }
                ?: prefilledImageUri?.let { Uri.parse(it) }
        )
    }
    val context = LocalContext.current
    val suggestedCategory = remember(productName) {
        if (productName.isNotBlank()) viewModel.getSimilarAssociatedCategory(context, productName) else null
    }
    val suggestedLocation = remember(productName) {
        if (productName.isNotBlank()) viewModel.getSimilarAssociatedLocation(context, productName) else null
    }

    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val knownShops = remember(allExpenses) {
        val shops = mutableSetOf<String>()
        allExpenses.forEach { exp ->
            if (exp.description.contains("|||")) {
                val loc = exp.description.substringAfter("|||").trim()
                if (loc.isNotEmpty()) {
                    shops.add(loc)
                }
            }
        }
        val prefs = context.getSharedPreferences("item_location_associations", Context.MODE_PRIVATE)
        val allAssoc = prefs.all as? Map<String, *>
        allAssoc?.values?.forEach { v ->
            val loc = (v as? String)?.trim()
            if (loc != null && loc.isNotEmpty()) {
                shops.add(loc)
            }
        }
        shops.toList()
    }

    val matchedShop = remember(productName, knownShops) {
        val cleanInput = productName.trim().lowercase()
        if (cleanInput.isEmpty()) null
        else {
            knownShops.find { shop ->
                val cleanShop = shop.trim().lowercase()
                cleanShop.length >= 3 && (cleanInput == cleanShop || cleanInput.contains(cleanShop) || cleanShop.contains(cleanInput))
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val permanentPath = copyUriToInternalStorage(context, it.toString(), "expense_receipt")
            imageUri = Uri.parse(permanentPath)
        }
    }

    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    
    // Sub-category list matching health & services requirements
    val healthSubCategories = listOf("Orvosi vizitdíj", "Gyógyszer/Parazitavédelem", "Műtét")
    val servicesSubCategories = listOf("Kutyakozmetikus", "Panzió/Sétáltatás", "Kiképzés/Suli")

    var showPetMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showSubCategoryMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (expenseToEdit != null) "Kiadás Szerkesztése" else "Új Kiadás Rögzítése",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // Pet selection
                Box(modifier = Modifier.fillMaxWidth()) {
                    val petDisplayText = when {
                        selectedPetIds.isEmpty() -> "Üres"
                        selectedPetIds.size == pets.size && pets.isNotEmpty() -> "Összes kedvenc"
                        else -> {
                            selectedPetIds.mapNotNull { id -> pets.find { it.id == id }?.name }.joinToString(", ")
                        }
                    }
                    OutlinedTextField(
                        value = petDisplayText,
                        onValueChange = {},
                        label = { Text("Kedvenc") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showPetMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { showPetMenu = true }
                    )
                    DropdownMenu(
                        expanded = showPetMenu,
                        onDismissRequest = { showPetMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedPetIds.size == pets.size && pets.isNotEmpty(),
                                        onCheckedChange = null // Click is handled by parent DropdownMenuItem
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Összes kedvenc")
                                }
                            },
                            onClick = {
                                selectedPetIds = if (selectedPetIds.size == pets.size) emptySet() else pets.map { it.id }.toSet()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedPetIds.isEmpty(),
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Egyik sem / Üres")
                                }
                            },
                            onClick = {
                                selectedPetIds = emptySet()
                            }
                        )
                        HorizontalDivider()
                        pets.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selectedPetIds.contains(p.id),
                                            onCheckedChange = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(p.name)
                                    }
                                },
                                onClick = {
                                    selectedPetIds = if (selectedPetIds.contains(p.id)) {
                                        selectedPetIds - p.id
                                    } else {
                                        selectedPetIds + p.id
                                    }
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Összeg (Ft) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                )

                // Main Category dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Kategória") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showCategoryMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    subCategory = null // Reset sub category when parent shifts
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                var showAddCategoryLocalDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showAddCategoryLocalDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kategóriák szerkesztése / hozzáadása", fontSize = 12.sp)
                    }
                }

                if (showAddCategoryLocalDialog) {
                    ManageCategoriesDialog(
                        viewModel = viewModel,
                        onDismiss = { showAddCategoryLocalDialog = false }
                    )
                }

                // Deep sub-categories if Egészségügy or Szolgáltatások (Kategóriák mélyebb bontása)
                if (category == "Egészségügy" || category == "Szolgáltatások") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val subText = subCategory ?: "Válassz alkategóriát..."
                        OutlinedTextField(
                            value = subText,
                            onValueChange = {},
                            label = { Text("Alkategória") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showSubCategoryMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = showSubCategoryMenu,
                            onDismissRequest = { showSubCategoryMenu = false }
                        ) {
                            val subList = if (category == "Egészségügy") healthSubCategories else servicesSubCategories
                            subList.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        subCategory = sub
                                        showSubCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Description / Product Name
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Termék / szolgáltatás neve *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_product_name_input")
                )

                // Purchase Location
                OutlinedTextField(
                    value = purchaseLocation,
                    onValueChange = { purchaseLocation = it },
                    label = { Text("Vásárlási hely (pl. Fressnapf, állatpatika)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("expense_purchase_location_input")
                )

                if (matchedShop != null && purchaseLocation.trim().lowercase() != matchedShop.lowercase()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Korábbi üzlet találat!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "A leírásban szereplő név ($matchedShop) korábban már üzletként/helyszínként volt használva. Szeretnéd beállítani vásárlási helyként?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        purchaseLocation = matchedShop
                                        if (productName.trim().equals(matchedShop, ignoreCase = true)) {
                                            productName = ""
                                        }
                                        Toast.makeText(context, "Helyszín beállítva!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Igen, oda teszem", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }

                if ((suggestedCategory != null && suggestedCategory != category) || (suggestedLocation != null && suggestedLocation != purchaseLocation)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Javasolt adatok korábbi vásárlás alapján:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (suggestedCategory != null && suggestedCategory != category) {
                                Text("- Kategória: $suggestedCategory", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            if (suggestedLocation != null && suggestedLocation != purchaseLocation) {
                                Text("- Vásárlási hely: $suggestedLocation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        if (suggestedCategory != null) category = suggestedCategory
                                        if (suggestedLocation != null) purchaseLocation = suggestedLocation
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Alkalmazás", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }

                // Is Recurring (Ismétlődő költségek rögzítése)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecurring = !isRecurring }
                ) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Column {
                        Text("Ismétlődő költség", fontWeight = FontWeight.Bold)
                        Text("Minden hónapban automatikusan hozzáadódik", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Image selector for Expense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kép feltöltése")
                    }
                    
                    imageUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Bizonylat/Kép",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { imageUri = null },
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Eltávolítás",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull()
                            if (amt != null && amt > 0.0) {
                                viewModel.saveItemCategoryAssociation(context, productName, category)
                                viewModel.saveItemLocationAssociation(context, productName, purchaseLocation)
                                val desc = if (purchaseLocation.isNotBlank()) "$productName|||$purchaseLocation" else productName
                                val firstPetId = selectedPetIds.firstOrNull() ?: 0
                                val targetIdsStr = if (selectedPetIds.isEmpty()) "none" else if (selectedPetIds.size == pets.size && pets.isNotEmpty()) "all" else selectedPetIds.joinToString(",")
                                onSubmit(firstPetId, amt, category, subCategory, desc, isRecurring, if (isRecurring) 1 else 0, imageUri?.toString(), targetIdsStr)
                            }
                        },
                        enabled = productName.isNotEmpty() && amountStr.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("save_expense_button")
                    ) {
                        Text("Mentés")
                    }
                }
            }
        }
    }
}

// Dialog for Add Category Budget Limit
@Composable
fun AddLimitDialog(
    limitToEdit: BudgetLimitEntity? = null,
    viewModel: PetViewModel,
    onDismiss: () -> Unit,
    onSubmit: (category: String, limit: Double) -> Unit
) {
    var category by remember { mutableStateOf(limitToEdit?.category ?: "Jutifali") }
    var limitStr by remember { mutableStateOf(limitToEdit?.limitAmount?.toInt()?.toString() ?: "") }
    
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    var showCatMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (limitToEdit != null) "Keret Szerkesztése" else "Havi Keret Beállítása") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Állíts be egy maximális havi limitet kategóriánként:")
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        label = { Text("Kategória") },
                        readOnly = true,
                        trailingIcon = {
                            if (limitToEdit == null) {
                                IconButton(onClick = { showCatMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (limitToEdit == null) {
                        DropdownMenu(
                            expanded = showCatMenu,
                            onDismissRequest = { showCatMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        showCatMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                var showAddCategoryLocalDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showAddCategoryLocalDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kategóriák kezelése", fontSize = 12.sp)
                    }
                }

                if (showAddCategoryLocalDialog) {
                    ManageCategoriesDialog(
                        viewModel = viewModel,
                        onDismiss = { showAddCategoryLocalDialog = false }
                    )
                }

                OutlinedTextField(
                    value = limitStr,
                    onValueChange = { limitStr = it },
                    label = { Text("Havi limit (Ft)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = limitStr.toDoubleOrNull()
                    if (limit != null && limit > 0.0) {
                        onSubmit(category, limit)
                    }
                },
                enabled = limitStr.isNotEmpty()
            ) {
                Text(if (limitToEdit != null) "Mentés" else "Beállítás")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse")
            }
        }
    )
}

// Dialog for Add Vaccine with AI OCR
@Composable
fun AddVaccineDialog(
    viewModel: PetViewModel,
    vaccinationToEdit: VaccinationEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String, timestamp: Long, serialNumber: String?, veterinarian: String?,
        notes: String?, nextDueTimestamp: Long?, isMandatory: Boolean, diseasePrevention: String?
    ) -> Unit
) {
    val context = LocalContext.current

    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    var dateStr by remember {
        val initialTimestamp = vaccinationToEdit?.timestamp ?: System.currentTimeMillis()
        mutableStateOf(sdf.format(java.util.Date(initialTimestamp)))
    }

    var name by remember { mutableStateOf(vaccinationToEdit?.name ?: "") }
    var serialNumber by remember { mutableStateOf(vaccinationToEdit?.serialNumber ?: "") }
    var veterinarian by remember { mutableStateOf(vaccinationToEdit?.veterinarian ?: "") }
    var notes by remember { mutableStateOf(vaccinationToEdit?.notes ?: "") }
    var isMandatory by remember { mutableStateOf(vaccinationToEdit?.isMandatory ?: false) }
    var diseasePrevention by remember { mutableStateOf(vaccinationToEdit?.diseasePrevention ?: "") }

    val initialNextDueInMonths = remember(vaccinationToEdit) {
        if (vaccinationToEdit != null) {
            if (vaccinationToEdit.nextDueTimestamp != null) {
                val diffMs = vaccinationToEdit.nextDueTimestamp - vaccinationToEdit.timestamp
                val diffMonths = (diffMs / (30 * 24 * 60 * 60 * 1000L)).toInt()
                when {
                    diffMonths in 2..4 -> 3
                    diffMonths in 5..7 -> 6
                    diffMonths in 10..14 -> 12
                    else -> 0
                }
            } else {
                0
            }
        } else {
            0
        }
    }
    var nextDueInMonths by remember { mutableStateOf(initialNextDueInMonths) }
    var hasUserOverriddenRecurrence by remember { mutableStateOf(vaccinationToEdit != null) }

    LaunchedEffect(name, diseasePrevention) {
        if (vaccinationToEdit == null && !hasUserOverriddenRecurrence) {
            val isRabies = name.contains("veszettség", ignoreCase = true) ||
                           name.contains("veszett", ignoreCase = true) ||
                           diseasePrevention.contains("veszettség", ignoreCase = true) ||
                           diseasePrevention.contains("veszett", ignoreCase = true)
            if (isRabies) {
                nextDueInMonths = 6
            } else {
                nextDueInMonths = 0
            }
        }
    }

    val isOcrScanning by viewModel.isOcrScanning.collectAsStateWithLifecycle()
    val ocrResult by viewModel.ocrScanResult.collectAsStateWithLifecycle()
    val ocrError by viewModel.ocrError.collectAsStateWithLifecycle()

    var showScanVaccineOptions by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempPhotoUri?.let { uri ->
                try {
                    val bitmap = decodeAndRotateBitmap(context, uri)
                    if (bitmap != null) {
                        if (!isNetworkAvailable(context)) {
                            Toast.makeText(context, "Nincs internetkapcsolat. Az AI szkenneléshez internet szükséges. Kérjük, töltsd ki manuálisan az adatokat!", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.scanVaccinationBooklet(bitmap)
                        }
                    } else {
                        Toast.makeText(context, "Hiba a kép betöltésekor!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainAppScreen", "Error reading vaccine camera: ${e.message}")
                    Toast.makeText(context, "Hiba a kép beolvasása közben!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = decodeAndRotateBitmap(context, it)
                if (bitmap != null) {
                    if (!isNetworkAvailable(context)) {
                        Toast.makeText(context, "Nincs internetkapcsolat. Az AI szkenneléshez internet szükséges. Kérjük, töltsd ki manuálisan az adatokat!", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.scanVaccinationBooklet(bitmap)
                    }
                } else {
                    Toast.makeText(context, "Hiba a kép betöltésekor!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainAppScreen", "Error reading vaccine gallery: ${e.message}")
                Toast.makeText(context, "Hiba a kép beolvasása közben!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            try {
                val tempFile = java.io.File(context.cacheDir, "vaccine_temp.jpg")
                if (tempFile.exists()) tempFile.delete()
                tempFile.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "com.aistudio.mancskiskonyv.petcare.fileprovider",
                    tempFile
                )
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                android.util.Log.e("MainAppScreen", "Error launching vaccine camera after permission: ${e.message}")
                Toast.makeText(context, "Nem sikerült elindítani a kamerát!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "A fényképezéshez kamera engedély szükséges!", Toast.LENGTH_SHORT).show()
        }
    }

    // When OCR extracts data, populate our form fields!
    LaunchedEffect(ocrResult) {
        ocrResult?.let {
            name = it.name
            serialNumber = it.serialNumber
            if (it.diseasePrevention != null && it.diseasePrevention.isNotEmpty()) {
                diseasePrevention = it.diseasePrevention
            }
            if (it.date.isNotEmpty()) {
                dateStr = it.date
            }
            Toast.makeText(context, "Sikeres AI adatolvasás!", Toast.LENGTH_LONG).show()
            viewModel.clearOcrState()
        }
    }

    LaunchedEffect(ocrError) {
        ocrError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearOcrState()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (vaccinationToEdit != null) "Oltás Szerkesztése" else "Új Oltás Rögzítése",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // AI OCR SCANNER TRIGGER BUTTON (Szövegfelismerő szkenner)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Oltási könyv fotó beolvasása AI segítségével",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Fotózd le a matricát vagy a pecsétet, és a rendszer kitölti az űrlapot!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        if (isOcrScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("AI szövegfelismerés folyamatban...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Button(
                                onClick = {
                                    showScanVaccineOptions = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("ai_ocr_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Szkennelés")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Matrica Beolvasása (AI)")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Date selection field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        label = { Text("Oltás dátuma * (ÉÉÉÉ-HH-NN)") },
                        placeholder = { Text("pl. 2026-07-04") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val calendar = java.util.Calendar.getInstance()
                            if (dateStr.isNotEmpty()) {
                                val parts = dateStr.split("-")
                                if (parts.size == 3) {
                                    val y = parts[0].toIntOrNull()
                                    val m = parts[1].toIntOrNull()
                                    val d = parts[2].toIntOrNull()
                                    if (y != null && m != null && d != null) {
                                        calendar.set(java.util.Calendar.YEAR, y)
                                        calendar.set(java.util.Calendar.MONTH, m - 1)
                                        calendar.set(java.util.Calendar.DAY_OF_MONTH, d)
                                    }
                                }
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val formattedMonth = String.format("%02d", month + 1)
                                    val formattedDay = String.format("%02d", dayOfMonth)
                                    dateStr = "$year-$formattedMonth-$formattedDay"
                                },
                                calendar.get(java.util.Calendar.YEAR),
                                calendar.get(java.util.Calendar.MONTH),
                                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp).padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Dátum választása")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Oltóanyag neve *") },
                    placeholder = { Text("pl. Nobivac Parvo-C") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vaccine_name_input")
                )

                OutlinedTextField(
                    value = diseasePrevention,
                    onValueChange = { diseasePrevention = it },
                    label = { Text("Mire való? (Célbetegségek)") },
                    placeholder = { Text("pl. Veszettség vagy Kombinált (parvo, szopornyica)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vaccine_prevention_input")
                )

                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("Gyártási szám") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = veterinarian,
                    onValueChange = { veterinarian = it },
                    label = { Text("Állatorvos neve") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Megjegyzések") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isMandatory = !isMandatory }
                ) {
                    Checkbox(checked = isMandatory, onCheckedChange = { isMandatory = it })
                    Text("Kötelező oltás (pl. Veszettség)", fontWeight = FontWeight.Bold)
                }

                // Next due interval selection
                Text("Ismétlés esedékessége:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Kihagyás" to 0,
                        "3 hó" to 3,
                        "6 hó" to 6,
                        "12 hó" to 12
                    ).forEach { (label, months) ->
                        FilterChip(
                            selected = nextDueInMonths == months,
                            onClick = {
                                nextDueInMonths = months
                                hasUserOverriddenRecurrence = true
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                val parsedTimestamp = try {
                                    sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    System.currentTimeMillis()
                                }
                                val nextDue = if (nextDueInMonths > 0) {
                                    parsedTimestamp + nextDueInMonths * 30 * 24 * 60 * 60 * 1000L
                                } else vaccinationToEdit?.nextDueTimestamp
                                onSubmit(name, parsedTimestamp, serialNumber, veterinarian, notes, nextDue, isMandatory, diseasePrevention)
                            }
                        },
                        enabled = name.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("save_vaccination_button")
                    ) {
                        Text("Mentés")
                    }
                }
            }
        }
    }

    if (showScanVaccineOptions) {
        AlertDialog(
            onDismissRequest = { showScanVaccineOptions = false },
            title = { Text("Oltási Matrica Beolvasása", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text("Válaszd ki, hogyan szeretnéd beolvasni az oltási matrica adatait!") },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showScanVaccineOptions = false
                            val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasCameraPermission) {
                                try {
                                    val tempFile = java.io.File(context.cacheDir, "vaccine_temp.jpg")
                                    if (tempFile.exists()) tempFile.delete()
                                    tempFile.createNewFile()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "com.aistudio.mancskiskonyv.petcare.fileprovider",
                                        tempFile
                                    )
                                    tempPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    android.util.Log.e("MainAppScreen", "Error launching vaccine camera: ${e.message}")
                                    Toast.makeText(context, "Nem sikerült elindítani a kamerát!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamera használata")
                    }

                    Button(
                        onClick = {
                            showScanVaccineOptions = false
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiválasztás galériából")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showScanVaccineOptions = false }
                ) {
                    Text("Mégse")
                }
            }
        )
    }
}

// Dialog for Add Parasite Protection
@Composable
fun AddParasiteDialog(
    parasiteToEdit: ParasiteEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (protectionType: String, treatmentMethod: String, productName: String, durationDays: Int) -> Unit
) {
    val predefinedTypes = listOf("Kullancs", "Bolha", "Szívféreg", "Féreghajtás")
    
    var selectedTypeOption by remember {
        mutableStateOf(
            if (parasiteToEdit == null) "Kullancs"
            else if (parasiteToEdit.protectionType in predefinedTypes) parasiteToEdit.protectionType
            else "Egyéb"
        )
    }
    
    var customProtectionType by remember {
        mutableStateOf(
            if (parasiteToEdit != null && parasiteToEdit.protectionType !in predefinedTypes) parasiteToEdit.protectionType
            else ""
        )
    }

    var treatmentMethod by remember { mutableStateOf(parasiteToEdit?.treatmentMethod ?: "Tabletta") }
    var productName by remember { mutableStateOf(parasiteToEdit?.productName ?: "") }

    val predefinedDurations = listOf(30, 84, 180, 240)
    var selectedDurationOption by remember {
        mutableStateOf(
            if (parasiteToEdit == null) 30
            else if (parasiteToEdit.durationDays in predefinedDurations) parasiteToEdit.durationDays
            else -1 // represents "Egyéb"
        )
    }
    
    var customDurationDaysText by remember {
        mutableStateOf(
            if (parasiteToEdit != null && parasiteToEdit.durationDays !in predefinedDurations) parasiteToEdit.durationDays.toString()
            else ""
        )
    }

    val methods = listOf("Tabletta", "Csepp (Spot-on)", "Nyakörv")
    val durations = listOf(
        "4 hét (30 nap)" to 30,
        "12 hét (84 nap)" to 84,
        "6 hónap (180 nap)" to 180,
        "8 hónap (240 nap)" to 240
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (parasiteToEdit != null) "Kezelés Szerkesztése" else "Parazitavédelmi Kezelés",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // Type selector
                Text("Kezelés Típusa:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val typeOptions = predefinedTypes + "Egyéb"
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(typeOptions) { t ->
                        FilterChip(
                            selected = selectedTypeOption == t,
                            onClick = { selectedTypeOption = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }

                if (selectedTypeOption == "Egyéb") {
                    OutlinedTextField(
                        value = customProtectionType,
                        onValueChange = { customProtectionType = it },
                        label = { Text("Egyedi kezelés típusa *") },
                        placeholder = { Text("Pl. Atka, szúnyog elleni védelem") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Method selector
                Text("Alkalmazás Módja:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { m ->
                        FilterChip(
                            selected = treatmentMethod == m,
                            onClick = { treatmentMethod = m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }

                // Product Name
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Készítmény neve *") },
                    placeholder = { Text("pl. Nexgard, Milprazon, Foresto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("parasite_name_input")
                )

                // Protection Duration
                Text("Védettség Időtartama:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    durations.forEach { (label, days) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDurationOption = days }
                        ) {
                            RadioButton(
                                selected = selectedDurationOption == days, 
                                onClick = { selectedDurationOption = days }
                            )
                            Text(label, fontSize = 13.sp)
                        }
                    }
                    
                    // Egyéb duration option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDurationOption = -1 }
                    ) {
                        RadioButton(
                            selected = selectedDurationOption == -1, 
                            onClick = { selectedDurationOption = -1 }
                        )
                        Text("Egyéb (egyedi nap megadása)", fontSize = 13.sp)
                    }
                }

                if (selectedDurationOption == -1) {
                    OutlinedTextField(
                        value = customDurationDaysText,
                        onValueChange = { customDurationDaysText = it },
                        label = { Text("Védettség ideje (napokban) *") },
                        placeholder = { Text("Pl. 45") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val isTypeValid = selectedTypeOption != "Egyéb" || customProtectionType.isNotBlank()
                val isDurationValid = selectedDurationOption != -1 || (customDurationDaysText.toIntOrNull() != null && (customDurationDaysText.toIntOrNull() ?: 0) > 0)
                val isFormValid = productName.isNotEmpty() && isTypeValid && isDurationValid

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val finalType = if (selectedTypeOption == "Egyéb") customProtectionType else selectedTypeOption
                                val finalDays = if (selectedDurationOption == -1) (customDurationDaysText.toIntOrNull() ?: 30) else selectedDurationOption
                                onSubmit(finalType, treatmentMethod, productName, finalDays)
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier.weight(1f).testTag("save_parasite_button")
                    ) {
                        Text("Rögzítés")
                    }
                }
            }
        }
    }
}

@Composable
fun ManageFamilyMembersDialog(
    members: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newMemberName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Családtagok Kezelése",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // Add member input field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("Új családtag neve") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newMemberName.trim().isNotEmpty()) {
                                onAdd(newMemberName.trim())
                                newMemberName = ""
                            }
                        },
                        enabled = newMemberName.trim().isNotEmpty()
                    ) {
                        Text("Hozzáadás")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                Text("Regisztrált tagok:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(member, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(
                                onClick = { onRemove(member) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eltávolítás", tint = RedWarning.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kész")
                }
            }
        }
    }
}

@Composable
fun AddRoutineTemplateDialog(
    templateToEdit: RoutineTemplateEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf(templateToEdit?.name ?: "") }
    var description by remember { mutableStateOf(templateToEdit?.description ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (templateToEdit != null) "Rutin Szerkesztése" else "Új Rutin Létrehozása",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feladat neve *") },
                    placeholder = { Text("pl. Délutáni séta, Reggeli vitamin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Rövid leírás *") },
                    placeholder = { Text("pl. 30 perces séta a parkban") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && description.isNotEmpty()) {
                                onSubmit(name, description)
                            }
                        },
                        enabled = name.isNotEmpty() && description.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (templateToEdit != null) "Mentés" else "Létrehozás")
                    }
                }
            }
        }
    }
}

@Composable
fun LogRoutineCompletionDialog(
    actionName: String,
    familyMembers: List<String>,
    defaultMember: String,
    routineToEdit: DailyRoutineEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (member: String, hour: Int, minute: Int) -> Unit
) {
    var selectedMember by remember { mutableStateOf(routineToEdit?.loggedBy ?: (if (familyMembers.contains(defaultMember)) defaultMember else familyMembers.firstOrNull() ?: "Anya")) }
    var showDropdown by remember { mutableStateOf(false) }

    val cal = Calendar.getInstance().apply {
        if (routineToEdit != null) {
            timeInMillis = routineToEdit.timestamp
        }
    }
    var hour by remember { mutableStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(cal.get(Calendar.MINUTE)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (routineToEdit != null) "Rögzítés Szerkesztése" else "Feladat rögzítése",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Rutin: $actionName",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Performed by
                Text("Ki végezte el?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null)
                                Text(selectedMember)
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        familyMembers.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member) },
                                onClick = {
                                    selectedMember = member
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                // Time performed
                Text("Mikor végezte el?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour picker wheel/buttons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { hour = (hour + 1) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Óra növelése")
                        }
                        Text(
                            text = String.format("%02d", hour),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { hour = if (hour == 0) 23 else hour - 1 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Óra csökkentése")
                        }
                    }

                    Text(" : ", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))

                    // Minute picker wheel/buttons
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minute = (minute + 5) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Perc növelése")
                        }
                        Text(
                            text = String.format("%02d", minute),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { minute = if (minute < 5) 55 else minute - 5 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Perc csökkentése")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = { onSubmit(selectedMember, hour, minute) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rögzítés")
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptSplitDialog(
    scanResult: ReceiptScanResult,
    pets: List<PetEntity>,
    defaultPetId: Int?,
    viewModel: PetViewModel,
    onDismiss: () -> Unit,
    onSubmit: (List<ExpenseEntity>) -> Unit
) {
    val context = LocalContext.current
    var shopName by remember { mutableStateOf(scanResult.shopName) }
    var dateStr by remember { mutableStateOf(scanResult.date.ifEmpty { 
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }) }

    data class SplitItemState(
        val originalName: String,
        val name: String,
        val price: Double,
        val petId: Int,
        val category: String,
        val suggestion: String? = null
    )

    val initialItems = remember(scanResult) {
        scanResult.items.map { item ->
            val suggestion = viewModel.getSimilarAssociatedCategory(context, item.name)
            SplitItemState(
                originalName = item.name,
                name = item.name,
                price = item.price,
                petId = defaultPetId ?: (pets.firstOrNull()?.id ?: 0),
                category = "Egyéb",
                suggestion = suggestion
            )
        }
    }

    var itemsState by remember { mutableStateOf(initialItems) }
    var showSuggestionsBanner by remember { mutableStateOf(initialItems.any { it.suggestion != null }) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    "Blokk tételeinek szétosztása",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Az AI felismerte az üzlet nevét, dátumát és tételeit. Alább pontosíthatod az adatokat és szétoszthatod a kategóriákat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Üzlet") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        label = { Text("Dátum") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (showSuggestionsBanner) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "Korábbi társítások találhatók!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Egyes tételek hasonlítanak korábbi vásárlásokra. Szeretnéd, hogy automatikusan beállítsuk hozzájuk a korábban mentett kategóriát?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        itemsState = itemsState.map { item ->
                                            if (item.suggestion != null) {
                                                item.copy(category = item.suggestion)
                                            } else {
                                                item
                                            }
                                        }
                                        showSuggestionsBanner = false
                                        Toast.makeText(context, "Kategóriák automatikusan beállítva!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Igen, társítás", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { showSuggestionsBanner = false },
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text("Mégse", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(itemsState.size) { index ->
                        val item = itemsState[index]
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var isEditingName by remember { mutableStateOf(false) }
                                    var editedName by remember(item.name) { mutableStateOf(item.name) }

                                    if (isEditingName) {
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = editedName,
                                            onValueChange = {
                                                editedName = it
                                                itemsState = itemsState.toMutableList().apply {
                                                    this[index] = item.copy(name = it)
                                                }
                                            },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            singleLine = true
                                        )
                                        IconButton(
                                            onClick = { isEditingName = false },
                                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Mentés", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.weight(1f).clickable { isEditingName = true },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Szerkesztés",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        formatHungarianAmount(item.price),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (item.suggestion != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "Korábbi társítás alapján javasolt: ${item.suggestion}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    var showPetMenu by remember { mutableStateOf(false) }
                                    val currentPetName = pets.find { it.id == item.petId }?.name ?: "Nincs"
                                    
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { showPetMenu = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(currentPetName, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showPetMenu,
                                            onDismissRequest = { showPetMenu = false }
                                        ) {
                                            pets.forEach { pet ->
                                                DropdownMenuItem(
                                                    text = { Text(pet.name, fontSize = 12.sp) },
                                                    onClick = {
                                                        itemsState = itemsState.toMutableList().apply {
                                                            this[index] = item.copy(petId = pet.id)
                                                        }
                                                        showPetMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    var showCategoryMenu by remember { mutableStateOf(false) }
                                    val categories = listOf("Étel", "Jutifali", "Egészségügy", "Szolgáltatások", "Kiegészítők", "Egyéb")
                                    
                                    Box(modifier = Modifier.weight(1f)) {
                                        Button(
                                            onClick = { showCategoryMenu = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(item.category, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showCategoryMenu,
                                            onDismissRequest = { showCategoryMenu = false }
                                        ) {
                                            categories.forEach { cat ->
                                                DropdownMenuItem(
                                                    text = { Text(cat, fontSize = 12.sp) },
                                                    onClick = {
                                                        itemsState = itemsState.toMutableList().apply {
                                                            this[index] = item.copy(category = cat)
                                                        }
                                                        showCategoryMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mégse")
                    }
                    Button(
                        onClick = {
                            val parsedDate = try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val d = sdf.parse(dateStr)
                                d?.time ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }

                            val expenses = itemsState.map { item ->
                                val desc = if (shopName.trim().isNotEmpty()) {
                                    "${item.name.trim()}|||${shopName.trim()}"
                                } else {
                                    item.name.trim()
                                }

                                ExpenseEntity(
                                    petId = item.petId,
                                    amount = item.price,
                                    category = item.category,
                                    description = desc,
                                    timestamp = parsedDate,
                                    isRecurring = false,
                                    recurringIntervalMonths = 0
                                )
                            }

                            itemsState.forEach { item ->
                                viewModel.saveItemCategoryAssociation(context, item.name, item.category)
                                if (shopName.trim().isNotEmpty()) {
                                    viewModel.saveItemLocationAssociation(context, item.name.trim(), shopName.trim())
                                }
                            }

                            onSubmit(expenses)
                        },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Kiadások mentése")
                    }
                }
            }
        }
    }
}

@Composable
fun ManageCategoriesDialog(
    viewModel: PetViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val categories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val budgetLimits by viewModel.budgetLimits.collectAsStateWithLifecycle()
    var newCategoryName by remember { mutableStateOf("") }
    
    // Track category currently being renamed
    var categoryToRename by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategóriák és Limitek", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Kategóriák módosítása és egyedi havi limitek beállítása:", fontSize = 13.sp)

                // List of existing categories
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val limitForCat = budgetLimits.find { it.category == cat }
                        val isLimitEnabled = limitForCat != null && limitForCat.isEnabled

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (categoryToRename == cat) {
                                        OutlinedTextField(
                                            value = renameValue,
                                            onValueChange = { renameValue = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                                            label = { Text("Új név") }
                                        )
                                        IconButton(onClick = {
                                            if (renameValue.isNotBlank()) {
                                                viewModel.renameExpenseCategory(context, cat, renameValue)
                                                categoryToRename = null
                                            }
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Mentés", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { categoryToRename = null }) {
                                            Icon(Icons.Default.Close, contentDescription = "Mégse")
                                        }
                                    } else {
                                        Text(
                                            text = cat,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            categoryToRename = cat
                                            renameValue = cat
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Átnevezés", modifier = Modifier.size(18.dp))
                                        }
                                        // Protect having at least 1 category
                                        if (categories.size > 1) {
                                            IconButton(onClick = {
                                                viewModel.removeExpenseCategory(context, cat)
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Törlés",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(6.dp))

                                // Limit management row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Checkbox(
                                            checked = isLimitEnabled,
                                            onCheckedChange = { checked ->
                                                val amt = limitForCat?.limitAmount ?: 10000.0
                                                viewModel.addBudgetLimit(cat, amt, checked)
                                            }
                                        )
                                        Text("Limit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    if (limitForCat != null) {
                                        var localLimitStr by remember(limitForCat.limitAmount) {
                                            mutableStateOf(limitForCat.limitAmount.toInt().toString())
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = localLimitStr,
                                                onValueChange = { localLimitStr = it },
                                                placeholder = { Text("Összeg") },
                                                singleLine = true,
                                                enabled = limitForCat.isEnabled,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(110.dp),
                                                textStyle = MaterialTheme.typography.bodyMedium,
                                                trailingIcon = {
                                                    Text("Ft", fontSize = 11.sp, color = if (limitForCat.isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                                }
                                            )
                                            IconButton(
                                                onClick = {
                                                    val limitVal = localLimitStr.toDoubleOrNull()
                                                    if (limitVal != null && limitVal >= 0) {
                                                        viewModel.addBudgetLimit(cat, limitVal, limitForCat.isEnabled)
                                                    }
                                                },
                                                enabled = limitForCat.isEnabled && localLimitStr.toDoubleOrNull() != null,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Mentés",
                                                    tint = if (limitForCat.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Limit kikapcsolva",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add new category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Új kategória") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newCategoryName.isNotBlank()) {
                                viewModel.addExpenseCategory(context, newCategoryName)
                                newCategoryName = ""
                             }
                        },
                        enabled = newCategoryName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Új")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Kész")
            }
        }
    )
}

@Composable
fun AddMedicationReminderDialog(
    reminderToEdit: MedicationReminderEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (name: String, dosage: String, frequency: String, reminderTime: String, notes: String, prescriptionPhotoUri: String?) -> Unit
) {
    val context = LocalContext.current
    var medicationName by remember { mutableStateOf(reminderToEdit?.medicationName ?: "") }
    var dosage by remember { mutableStateOf(reminderToEdit?.dosage ?: "") }
    var frequency by remember { mutableStateOf(reminderToEdit?.frequency ?: "Naponta egyszer") }
    var reminderTime by remember { mutableStateOf(reminderToEdit?.reminderTime ?: "08:00") }
    var notes by remember { mutableStateOf(reminderToEdit?.notes ?: "") }
    var prescriptionPhotoUri by remember { mutableStateOf(reminderToEdit?.prescriptionPhotoUri) }

    val showTimePicker = {
        val parts = reminderTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(context, { _, hour, minute ->
            reminderTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
        }, h, m, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (reminderToEdit != null) "Emlékeztető szerkesztése" else "Új gyógyszer rögzítése",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = medicationName,
                    onValueChange = { medicationName = it },
                    label = { Text("Gyógyszer neve *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("medication_name_input")
                )

                // Dosage with custom dosage helper
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Adagolás * (pl. 1 tabletta, 0.5 ml)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dosage_input")
                )

                // Quick dosage chips
                Text("Gyors adagok:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1 tabletta", "0.5 tabletta", "1.5 tabletta", "1 ml", "2 csepp", "5 ml").forEach { d ->
                        FilterChip(
                            selected = dosage == d,
                            onClick = { dosage = d },
                            label = { Text(d, fontSize = 11.sp) }
                        )
                    }
                }

                // Frequency selection
                Text("Gyakoriság:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val freqOptions = listOf("Naponta egyszer", "Naponta kétszer", "Minden másnap", "Hetente egyszer")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    freqOptions.forEach { opt ->
                        FilterChip(
                            selected = frequency == opt,
                            onClick = { frequency = opt },
                            label = { Text(opt, fontSize = 11.sp) }
                        )
                    }
                }

                // Time picker button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Emlékeztető időpontja:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(reminderTime, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = showTimePicker,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = "Idő kiválasztása")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Időpont")
                    }
                }

                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap: Bitmap? ->
                    bitmap?.let {
                        try {
                            val dir = java.io.File(context.filesDir, "pet_media")
                            if (!dir.exists()) dir.mkdirs()
                            val file = java.io.File(dir, "prescription_${System.currentTimeMillis()}.jpg")
                            val fos = java.io.FileOutputStream(file)
                            it.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                            fos.close()
                            prescriptionPhotoUri = Uri.fromFile(file).toString()
                        } catch (e: Exception) {
                            android.util.Log.e("AddMedicationReminder", "Error saving prescription photo: ${e.message}")
                        }
                    }
                }

                // --- Prescription Photo Section ---
                Text("Állatorvosi recept:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                
                if (prescriptionPhotoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = prescriptionPhotoUri,
                            contentDescription = "Recept",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Delete overlay button
                        IconButton(
                            onClick = { prescriptionPhotoUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Fotó törlése", tint = RedWarning, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth().testTag("snap_prescription_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recept lefényképezése")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Megjegyzések, utasítások (pl. étkezés után)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Mégse")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (medicationName.isNotBlank() && dosage.isNotBlank()) {
                                onSubmit(medicationName, dosage, frequency, reminderTime, notes, prescriptionPhotoUri)
                            }
                        },
                        enabled = medicationName.isNotBlank() && dosage.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mentés")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetExportDialog(
    initialPet: PetEntity,
    viewModel: PetViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pets by viewModel.allPets.collectAsStateWithLifecycle()
    var selectedTargetPetId by remember { mutableStateOf<Int?>(initialPet.id) }
    
    var includeVaccinations by remember { mutableStateOf(true) }
    var includeReminders by remember { mutableStateOf(true) }
    var includeParasites by remember { mutableStateOf(true) }
    var includeWeights by remember { mutableStateOf(true) }
    var includeExpenses by remember { mutableStateOf(true) }
    
    val defaultCategories = listOf("Étel", "Egészségügy", "Szolgáltatások", "Játék", "Kiegészítők", "Jutifali", "Egyéb")
    val selectedCategories = remember { mutableStateListOf<String>().apply { addAll(defaultCategories) } }
    
    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportPetDataZip(
                context = context,
                targetPetId = selectedTargetPetId,
                includeVaccinations = includeVaccinations,
                includeReminders = includeReminders,
                includeParasites = includeParasites,
                includeExpenses = includeExpenses,
                selectedExpenseCategories = selectedCategories.toList(),
                includeWeights = includeWeights,
                customTargetUri = uri
            )
            onDismiss()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kisállat megosztása & Exportálása",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Válaszd ki, hogy mely adatokat és képeket szeretnéd megosztani egyetlen ZIP fájlban. A megosztott fájlt a másik személy is be tudja importálni a saját alkalmazásába!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Célállat kiválasztása
                Text(
                    text = "Megosztani kívánt kedvenc kiválasztása",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                var expandedDropdown by remember { mutableStateOf(false) }
                val currentTargetName = if (selectedTargetPetId == null) "Összes kedvenc" else pets.find { it.id == selectedTargetPetId }?.name ?: ""
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentTargetName, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Összes kedvenc") },
                            onClick = {
                                selectedTargetPetId = null
                                expandedDropdown = false
                            }
                        )
                        pets.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    selectedTargetPetId = p.id
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // 1. Orvosi adatok
                Text(
                    text = "Egészségügyi adatok (Riport és Képek)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeVaccinations,
                        onCheckedChange = { includeVaccinations = it },
                        modifier = Modifier.testTag("export_vaccinations_checkbox")
                    )
                    Text("Oltások listája", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeReminders,
                        onCheckedChange = { includeReminders = it },
                        modifier = Modifier.testTag("export_reminders_checkbox")
                    )
                    Text("Gyógyszerek és Receptek (képekkel)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeParasites,
                        onCheckedChange = { includeParasites = it },
                        modifier = Modifier.testTag("export_parasites_checkbox")
                    )
                    Text("Parazita elleni védekezések", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // 2. Súly adatok
                Text(
                    text = "Fizikai adatok (CSV táblázat)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeWeights,
                        onCheckedChange = { includeWeights = it },
                        modifier = Modifier.testTag("export_weights_checkbox")
                    )
                    Text("Súly- és mérettörténet (.csv)", style = MaterialTheme.typography.bodyMedium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // 3. Költségvetés
                Text(
                    text = "Pénzügyi adatok & Költségvetés",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeExpenses,
                        onCheckedChange = { includeExpenses = it },
                        modifier = Modifier.testTag("export_expenses_checkbox")
                    )
                    Text("Kiadások (.csv + bizonylat képek)", style = MaterialTheme.typography.bodyMedium)
                }

                if (includeExpenses) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Kategóriák szűrése:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                defaultCategories.forEach { cat ->
                                    val isSelected = selectedCategories.contains(cat)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                selectedCategories.remove(cat)
                                            } else {
                                                selectedCategories.add(cat)
                                            }
                                        },
                                        label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportPetDataZip(
                                context = context,
                                targetPetId = selectedTargetPetId,
                                includeVaccinations = includeVaccinations,
                                includeReminders = includeReminders,
                                includeParasites = includeParasites,
                                includeExpenses = includeExpenses,
                                selectedExpenseCategories = selectedCategories.toList(),
                                includeWeights = includeWeights,
                                customTargetUri = null
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        enabled = includeVaccinations || includeReminders || includeParasites || includeWeights || includeExpenses,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Azonnali megosztás & Átküldés")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val defaultFileName = if (selectedTargetPetId == null) {
                                    "MancsKiskonyv_osszes_kedvenc.zip"
                                } else {
                                    val selectedPetName = pets.find { it.id == selectedTargetPetId }?.name ?: "kedvenc"
                                    "${selectedPetName}_dokumentumok_es_adatok.zip"
                                }
                                zipExportLauncher.launch(defaultFileName)
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = includeVaccinations || includeReminders || includeParasites || includeWeights || includeExpenses,
                            modifier = Modifier.weight(1f).testTag("confirm_export_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mentés fájlként", fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.5f)
                        ) {
                            Text("Mégse")
                        }
                    }
                }
            }
        }
    }
}

