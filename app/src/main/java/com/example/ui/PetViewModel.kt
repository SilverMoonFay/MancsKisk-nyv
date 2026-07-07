package com.example.ui

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.BuildConfig
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class EmergencyChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PetViewModel(private val repository: PetRepository) : ViewModel() {

    var showDeleteConfirmation = androidx.compose.runtime.mutableStateOf(false)
    var deleteConfirmationTitle = androidx.compose.runtime.mutableStateOf("")
    var deleteConfirmationText = androidx.compose.runtime.mutableStateOf("")
    var onDeleteConfirmAction: () -> Unit = {}

    fun requestDeletion(title: String, itemLabel: String, action: () -> Unit) {
        deleteConfirmationTitle.value = title
        deleteConfirmationText.value = "Biztos el akarja távolítani ezt: $itemLabel?"
        onDeleteConfirmAction = action
        showDeleteConfirmation.value = true
    }

    // --- SEED MOCK DATA ---
    fun seedInitialDataIfEmpty() {
        viewModelScope.launch {
            repository.allPets.first().let { pets ->
                // Delete legacy/demo pets Bodza and Cirmi if they exist
                var deletedAny = false
                pets.forEach { pet ->
                    if (pet.name == "Bodza" || pet.name == "Cirmi") {
                        repository.deletePet(pet)
                        deletedAny = true
                    }
                }
                
                val remainingPets = repository.allPets.first()
                if (deletedAny || remainingPets.isEmpty()) {
                    if (remainingPets.isNotEmpty()) {
                        selectedPetId.value = remainingPets.first().id
                    } else {
                        selectedPetId.value = null
                    }
                } else {
                    if (selectedPetId.value == null && remainingPets.isNotEmpty()) {
                        selectedPetId.value = remainingPets.first().id
                    }
                }

                // Seed Budget limits if none exist
                repository.allBudgetLimits.first().let { limits ->
                    if (limits.isEmpty()) {
                        repository.insertBudgetLimit(BudgetLimitEntity("Étel", 25000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Jutifali", 8000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Egészségügy", 30000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Szolgáltatások", 15000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Kiegészítők", 10000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Oltás", 20000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Játék", 5000.0))
                        repository.insertBudgetLimit(BudgetLimitEntity("Egyéb", 10000.0))
                    }
                }
            }
        }
    }

    // --- STATE VARIABLES ---
    val allPets: StateFlow<List<PetEntity>> = repository.allPets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPetId = MutableStateFlow<Int?>(null)

    val currentPet: StateFlow<PetEntity?> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) repository.getPetById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPetWeights: StateFlow<List<WeightEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) repository.getWeightsForPet(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPetVaccinations: StateFlow<List<VaccinationEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) repository.getVaccinationsForPet(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPetParasiteProtections: StateFlow<List<ParasiteEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) repository.getParasiteProtectionsForPet(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPetDailyRoutines: StateFlow<List<DailyRoutineEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) repository.getDailyRoutinesForPet(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered daily routines logged today
    val currentPetDailyRoutinesToday: StateFlow<List<DailyRoutineEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                repository.getDailyRoutinesToday(id, calendar.timeInMillis)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPetRoutineTemplates: StateFlow<List<RoutineTemplateEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getRoutineTemplatesForPet(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPetMedications: StateFlow<List<MedicationReminderEntity>> = selectedPetId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMedicationRemindersForPet(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun checkAndSeedRoutineTemplates(context: Context, petId: Int) {
        viewModelScope.launch {
            val list = repository.getRoutineTemplatesForPet(petId).first()
            val prefs = context.getSharedPreferences("mancs_prefs", Context.MODE_PRIVATE)
            val wasSeeded = prefs.getBoolean("routine_templates_seeded_$petId", false)
            if (list.isEmpty() && !wasSeeded) {
                prefs.edit().putBoolean("routine_templates_seeded_$petId", true).apply()
                seedDefaultRoutineTemplates(petId)
            }
        }
    }

    private fun seedDefaultRoutineTemplates(petId: Int) {
        viewModelScope.launch {
            val defaults = listOf(
                Pair("Etetés (Reggeli)", "Megkapta a reggeli eledelét"),
                Pair("Etetés (Ebéd)", "Megkapta a déli eledelét"),
                Pair("Etetés (Vacsora)", "Megkapta az esti vacsorát"),
                Pair("Gyógyszer", "Beadva a napi gyógyszer/vitamin"),
                Pair("Séta", "Délutáni / napi séta elvégezve"),
                Pair("Fésülés / Kozmetika", "Szőrápolás, kefélés megvolt")
            )
            defaults.forEach { (name, desc) ->
                repository.insertRoutineTemplate(
                    RoutineTemplateEntity(petId = petId, name = name, description = desc)
                )
            }
        }
    }

    // --- EXPENSES STATE & CALCULATIONS ---
    val allExpenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetLimits: StateFlow<List<BudgetLimitEntity>> = repository.allBudgetLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- OCR SCANNING STATE ---
    var isOcrScanning = MutableStateFlow(false)
        private set
    var ocrScanResult = MutableStateFlow<OcrResult?>(null)
        private set
    var ocrError = MutableStateFlow<String?>(null)
        private set

    // --- RECEIPT SCANNING STATE ---
    var isReceiptScanning = MutableStateFlow(false)
        private set
    var receiptScanResult = MutableStateFlow<ReceiptScanResult?>(null)
        private set
    var receiptError = MutableStateFlow<String?>(null)
        private set

    // --- GEMINI SYMPTOM CHECKER STATE ---
    var isSymptomAnalyzing = MutableStateFlow(false)
        private set
    var symptomAnalysisResult = MutableStateFlow<SymptomAnalysisResult?>(null)
        private set
    var symptomAnalysisError = MutableStateFlow<String?>(null)
        private set

    // --- EMERGENCY AI ASSISTANT CHAT ---
    val emergencyChatMessages = MutableStateFlow<List<EmergencyChatMessage>>(listOf(
        EmergencyChatMessage("ai", "Szia! Én az AI MancsKiskönyv sürgősségi asszisztense vagyok. Örömmel segítek az elsősegéllyel vagy sürgős teendőkkel kapcsolatos kérdésekben, de kérlek vedd figyelembe, hogy egy AI asszisztens vagyok, így előfordulhatnak hibák. Súlyos esetben mindig fordulj állatorvoshoz!")
    ))
    val isEmergencyChatLoading = MutableStateFlow(false)

    // --- BREED AI RECOMMENDATIONS ---
    val breedAiInfo = MutableStateFlow<PetBreedAiInfo?>(null)
    val isBreedAiLoading = MutableStateFlow(false)
    val breedAiError = MutableStateFlow<String?>(null)

    private val breedAdvisor = GeminiBreedAdvisor()
    private val moshiInstance = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val breedAdapter = moshiInstance.adapter(PetBreedAiInfo::class.java)

    fun loadBreedAiInfo(context: Context, petId: Int, petType: String, breed: String, force: Boolean = false) {
        if (breed.trim().isBlank()) {
            breedAiInfo.value = PetBreedAiInfo(
                recommendedVaccinations = emptyList(),
                toxicFoods = emptyList(),
                careTips = emptyList(),
                breedCharacteristics = "Az állat fajtájának meghatározása nélkül nem tudok segítséget nyújtani, hiszen minden fajtának megvan a saját különlegessége."
            )
            isBreedAiLoading.value = false
            breedAiError.value = null
            return
        }
        val prefs = context.getSharedPreferences("mancskiskonyv_prefs", Context.MODE_PRIVATE)
        val cacheKey = "breed_ai_info_pet_$petId"

        if (!force) {
            val cachedJson = prefs.getString(cacheKey, null)
            if (cachedJson != null) {
                try {
                    val cachedInfo = breedAdapter.fromJson(cachedJson)
                    if (cachedInfo != null) {
                        breedAiInfo.value = cachedInfo
                        isBreedAiLoading.value = false
                        breedAiError.value = null
                        return
                    }
                } catch (e: Exception) {
                    Log.e("PetViewModel", "Error parsing cached AI breed info: ${e.message}")
                }
            }
        }

        viewModelScope.launch {
            isBreedAiLoading.value = true
            breedAiError.value = null
            try {
                val result = breedAdvisor.getBreedInfo(petType, breed)
                if (result != null) {
                    breedAiInfo.value = result
                    // Save to cache
                    val jsonStr = breedAdapter.toJson(result)
                    prefs.edit().putString(cacheKey, jsonStr).apply()
                } else {
                    breedAiError.value = "Nem sikerült lekérni az AI ajánlásokat. Ellenőrizd az API kulcsot!"
                }
            } catch (e: Exception) {
                breedAiError.value = "Hiba történt az AI lekérés közben: ${e.message}"
            } finally {
                isBreedAiLoading.value = false
            }
        }
    }

    val selectedFamilyMember = MutableStateFlow("Anya") // Default family member for routine tracking

    val familyMembers = MutableStateFlow<List<String>>(listOf("Anya", "Apa", "Gyerek", "Kutyaszitter"))

    val expenseCategories = MutableStateFlow<List<String>>(listOf("Étel", "Jutifali", "Egészségügy", "Szolgáltatások", "Kiegészítők", "Egyéb"))

    fun loadExpenseCategories(context: Context) {
        val prefs = context.getSharedPreferences("mancs_prefs", Context.MODE_PRIVATE)
        val categoriesSet = prefs.getStringSet("expense_categories", null)
        if (categoriesSet != null && categoriesSet.isNotEmpty()) {
            expenseCategories.value = categoriesSet.toList().sorted()
        }
    }

    private fun saveExpenseCategories(context: Context, categories: List<String>) {
        val prefs = context.getSharedPreferences("mancs_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("expense_categories", categories.toSet()).apply()
    }

    fun addExpenseCategory(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !expenseCategories.value.contains(trimmed)) {
            val newList = expenseCategories.value + trimmed
            expenseCategories.value = newList
            saveExpenseCategories(context, newList)
        }
    }

    fun renameExpenseCategory(context: Context, oldName: String, newName: String) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isNotEmpty() && trimmedNew != oldName && !expenseCategories.value.contains(trimmedNew)) {
            val newList = expenseCategories.value.map { if (it == oldName) trimmedNew else it }
            expenseCategories.value = newList
            saveExpenseCategories(context, newList)
            
            // Sync database entities carrying the old category name!
            viewModelScope.launch {
                val expenses = repository.allExpenses.first()
                expenses.forEach { exp ->
                    if (exp.category == oldName) {
                        repository.insertExpense(exp.copy(category = trimmedNew))
                    }
                }
                
                val limits = repository.allBudgetLimits.first()
                limits.forEach { lim ->
                    if (lim.category == oldName) {
                        repository.deleteBudgetLimitByCategory(oldName)
                        repository.insertBudgetLimit(BudgetLimitEntity(category = trimmedNew, limitAmount = lim.limitAmount, isEnabled = lim.isEnabled))
                    }
                }
            }
        }
    }

    fun removeExpenseCategory(context: Context, name: String) {
        if (expenseCategories.value.size > 1) {
            val newList = expenseCategories.value - name
            expenseCategories.value = newList
            saveExpenseCategories(context, newList)
        }
    }

    fun loadFamilyMembers(context: Context) {
        val prefs = context.getSharedPreferences("mancs_prefs", Context.MODE_PRIVATE)
        val membersSet = prefs.getStringSet("family_members", null)
        if (membersSet != null && membersSet.isNotEmpty()) {
            familyMembers.value = membersSet.toList().sorted()
        }
    }

    private fun saveFamilyMembers(context: Context, members: List<String>) {
        val prefs = context.getSharedPreferences("mancs_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("family_members", members.toSet()).apply()
    }

    fun addFamilyMember(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !familyMembers.value.contains(trimmed)) {
            val newList = familyMembers.value + trimmed
            familyMembers.value = newList
            saveFamilyMembers(context, newList)
        }
    }

    fun removeFamilyMember(context: Context, name: String) {
        if (familyMembers.value.size > 1) {
            val newList = familyMembers.value - name
            familyMembers.value = newList
            saveFamilyMembers(context, newList)
            if (selectedFamilyMember.value == name) {
                selectedFamilyMember.value = newList.first()
            }
        }
    }

    // --- OPERATIONS ---

    fun selectPet(id: Int) {
        selectedPetId.value = id
    }

    fun addPet(
        name: String,
        type: String,
        breed: String,
        color: String,
        gender: String,
        age: String,
        isNeutered: Boolean,
        chipNumber: String,
        photoUri: String? = null,
        allergies: String,
        chronicDiseases: String,
        vetName: String,
        vetPhone: String,
        insuranceCompany: String,
        insurancePolicyNumber: String,
        birthDate: String? = "",
        chipImplantDate: String? = null,
        chipExpiryDate: String? = null
    ) {
        viewModelScope.launch {
            val pet = PetEntity(
                name = name,
                type = type,
                breed = breed,
                color = color,
                gender = gender,
                age = age,
                isNeutered = isNeutered,
                chipNumber = chipNumber,
                photoUri = photoUri,
                allergies = allergies,
                chronicDiseases = chronicDiseases,
                vetName = vetName,
                vetPhone = vetPhone,
                insuranceCompany = insuranceCompany,
                insurancePolicyNumber = insurancePolicyNumber,
                birthDate = birthDate,
                chipImplantDate = chipImplantDate,
                chipExpiryDate = chipExpiryDate
            )
            val insertedId = repository.insertPet(pet).toInt()
            selectedPetId.value = insertedId
        }
    }

    fun updatePet(pet: PetEntity) {
        viewModelScope.launch {
            repository.updatePet(pet)
        }
    }

    fun deletePet(pet: PetEntity) {
        viewModelScope.launch {
            repository.deletePet(pet)
            val currentPets = allPets.value.filter { it.id != pet.id }
            if (currentPets.isNotEmpty()) {
                selectedPetId.value = currentPets.first().id
            } else {
                selectedPetId.value = null
            }
        }
    }

    fun addWeight(
        weightKg: Double,
        timestamp: Long = System.currentTimeMillis(),
        shoulderHeightCm: Double? = null,
        bodyLengthCm: Double? = null,
        chestCircumferenceCm: Double? = null,
        neckCircumferenceCm: Double? = null
    ) {
        val petId = selectedPetId.value ?: return
        viewModelScope.launch {
            repository.insertWeight(
                WeightEntity(
                    petId = petId,
                    weightKg = weightKg,
                    timestamp = timestamp,
                    shoulderHeightCm = shoulderHeightCm,
                    bodyLengthCm = bodyLengthCm,
                    chestCircumferenceCm = chestCircumferenceCm,
                    neckCircumferenceCm = neckCircumferenceCm
                )
            )
        }
    }

    fun addExpense(
        amount: Double,
        category: String,
        subCategory: String? = null,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
        isRecurring: Boolean = false,
        recurringIntervalMonths: Int = 0,
        imageUri: String? = null,
        targetPetIds: String? = null
    ) {
        val petId = selectedPetId.value ?: 0
        viewModelScope.launch {
            repository.insertExpense(
                ExpenseEntity(
                    petId = petId,
                    amount = amount,
                    category = category,
                    subCategory = subCategory,
                    description = description,
                    timestamp = timestamp,
                    isRecurring = isRecurring,
                    recurringIntervalMonths = recurringIntervalMonths,
                    imageUri = imageUri,
                    targetPetIds = targetPetIds
                )
            )
        }
    }

    fun saveScannedExpenses(expenses: List<ExpenseEntity>) {
        viewModelScope.launch {
            expenses.forEach { expense ->
                repository.insertExpense(expense)
            }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            repository.deleteExpenseById(expenseId)
        }
    }

    fun addBudgetLimit(category: String, limitAmount: Double, isEnabled: Boolean = true) {
        viewModelScope.launch {
            repository.insertBudgetLimit(BudgetLimitEntity(category, limitAmount, isEnabled))
        }
    }

    fun deleteBudgetLimit(category: String) {
        viewModelScope.launch {
            repository.deleteBudgetLimitByCategory(category)
        }
    }

    fun addVaccination(
        name: String,
        timestamp: Long = System.currentTimeMillis(),
        serialNumber: String? = null,
        veterinarian: String? = null,
        notes: String? = null,
        nextDueTimestamp: Long? = null,
        isMandatory: Boolean = false,
        diseasePrevention: String? = null
    ) {
        val petId = selectedPetId.value ?: return
        viewModelScope.launch {
            repository.insertVaccination(
                VaccinationEntity(
                    petId = petId,
                    name = name,
                    timestamp = timestamp,
                    serialNumber = serialNumber,
                    veterinarian = veterinarian,
                    notes = notes,
                    nextDueTimestamp = nextDueTimestamp,
                    isMandatory = isMandatory,
                    diseasePrevention = diseasePrevention
                )
            )
        }
    }

    fun deleteVaccination(id: Int) {
        viewModelScope.launch {
            repository.deleteVaccinationById(id)
        }
    }

    fun addParasiteProtection(
        protectionType: String,
        treatmentMethod: String,
        productName: String,
        timestamp: Long = System.currentTimeMillis(),
        durationDays: Int
    ) {
        val petId = selectedPetId.value ?: return
        val nextDueTimestamp = timestamp + durationDays * 24 * 60 * 60 * 1000L
        viewModelScope.launch {
            repository.insertParasite(
                ParasiteEntity(
                    petId = petId,
                    protectionType = protectionType,
                    treatmentMethod = treatmentMethod,
                    productName = productName,
                    timestamp = timestamp,
                    durationDays = durationDays,
                    nextDueTimestamp = nextDueTimestamp
                )
            )
        }
    }

    fun deleteParasiteProtection(id: Int) {
        viewModelScope.launch {
            repository.deleteParasiteById(id)
        }
    }

    // --- MEDICATION REMINDERS ---
    fun addMedicationReminder(
        medicationName: String,
        dosage: String,
        frequency: String,
        reminderTime: String,
        startDate: Long = System.currentTimeMillis(),
        isActive: Boolean = true,
        notes: String = "",
        prescriptionPhotoUri: String? = null
    ) {
        val petId = selectedPetId.value ?: return
        viewModelScope.launch {
            repository.insertMedicationReminder(
                MedicationReminderEntity(
                    petId = petId,
                    medicationName = medicationName,
                    dosage = dosage,
                    frequency = frequency,
                    reminderTime = reminderTime,
                    startDate = startDate,
                    isActive = isActive,
                    notes = notes,
                    prescriptionPhotoUri = prescriptionPhotoUri
                )
            )
        }
    }

    fun updateMedicationReminder(reminder: MedicationReminderEntity) {
        viewModelScope.launch {
            repository.insertMedicationReminder(reminder)
        }
    }

    fun deleteMedicationReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteMedicationReminderById(id)
        }
    }

    fun administerMedication(reminder: MedicationReminderEntity, familyMember: String = selectedFamilyMember.value) {
        val petId = selectedPetId.value ?: return
        viewModelScope.launch {
            val routineText = "Gyógyszer: ${reminder.medicationName} (${reminder.dosage})"
            repository.insertDailyRoutine(
                DailyRoutineEntity(
                    petId = petId,
                    actionType = routineText,
                    loggedBy = familyMember,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun logDailyRoutine(actionType: String, member: String = selectedFamilyMember.value, timestamp: Long = System.currentTimeMillis()) {
        val petId = selectedPetId.value ?: return
        viewModelScope.launch {
            repository.insertDailyRoutine(
                DailyRoutineEntity(
                    petId = petId,
                    actionType = actionType,
                    loggedBy = member,
                    timestamp = timestamp
                )
            )
        }
    }

    fun deleteDailyRoutine(id: Int) {
        viewModelScope.launch {
            repository.deleteDailyRoutineById(id)
        }
    }

    fun addRoutineTemplate(name: String, description: String) {
        val petId = selectedPetId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertRoutineTemplate(
                RoutineTemplateEntity(petId = petId, name = name, description = description)
            )
        }
    }

    fun deleteRoutineTemplate(id: Int) {
        viewModelScope.launch {
            repository.deleteRoutineTemplateById(id)
        }
    }

    // --- GENERIC UPDATE FUNCTIONS FOR DATA ENTITIES ---
    fun updateWeight(weight: WeightEntity) {
        viewModelScope.launch {
            repository.insertWeight(weight)
        }
    }

    fun deleteWeight(id: Int) {
        viewModelScope.launch {
            repository.deleteWeightById(id)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.insertExpense(expense)
        }
    }

    fun updateVaccination(vaccination: VaccinationEntity) {
        viewModelScope.launch {
            repository.insertVaccination(vaccination)
        }
    }

    fun updateParasiteProtection(parasite: ParasiteEntity) {
        viewModelScope.launch {
            repository.insertParasite(parasite)
        }
    }

    fun updateDailyRoutine(routine: DailyRoutineEntity) {
        viewModelScope.launch {
            repository.insertDailyRoutine(routine)
        }
    }

    fun updateRoutineTemplate(template: RoutineTemplateEntity) {
        viewModelScope.launch {
            repository.insertRoutineTemplate(template)
        }
    }

    // --- GEMINI OCR INTEGRATION ---
    fun scanVaccinationBooklet(bitmap: Bitmap) {
        viewModelScope.launch {
            isOcrScanning.value = true
            ocrScanResult.value = null
            ocrError.value = null

            val scanner = GeminiOcrScanner()
            val result = scanner.scanVaccinationImage(bitmap)
            if (result != null) {
                ocrScanResult.value = result
            } else {
                ocrError.value = "Nem sikerült az adatok beolvasása. Ellenőrizd a hálózati kapcsolatot vagy az API kulcsot!"
            }
            isOcrScanning.value = false
        }
    }

    fun clearOcrState() {
        ocrScanResult.value = null
        ocrError.value = null
    }

    // --- GEMINI RECEIPT SCANNER ---
    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            isReceiptScanning.value = true
            receiptScanResult.value = null
            receiptError.value = null

            val scanner = GeminiReceiptScanner()
            val result = scanner.scanReceiptImage(bitmap)
            if (result != null) {
                receiptScanResult.value = result
            } else {
                receiptError.value = "Nem sikerült a nyugta beolvasása. Ellenőrizd a hálózati kapcsolatot vagy az API kulcsot!"
            }
            isReceiptScanning.value = false
        }
    }

    fun clearReceiptState() {
        receiptScanResult.value = null
        receiptError.value = null
    }

    fun saveItemCategoryAssociation(context: Context, itemName: String, category: String) {
        val trimmedItem = itemName.trim()
        if (trimmedItem.isEmpty()) return
        val prefs = context.getSharedPreferences("item_category_associations", Context.MODE_PRIVATE)
        prefs.edit().putString(trimmedItem, category).apply()
    }

    fun getSimilarAssociatedCategory(context: Context, itemName: String): String? {
        val prefs = context.getSharedPreferences("item_category_associations", Context.MODE_PRIVATE)
        val allAssociations = prefs.all as? Map<String, *> ?: return null
        
        val cleanInput = itemName.lowercase().trim()
        if (cleanInput.isEmpty()) return null

        // 1. Try exact match
        val exactMatch = allAssociations[itemName] as? String
        if (exactMatch != null) return exactMatch
        
        // 2. Try case-insensitive exact match
        for ((key, value) in allAssociations) {
            if (key.lowercase().trim() == cleanInput) {
                return value as? String
            }
        }
        
        // 3. Try containment / similar words match
        for ((key, value) in allAssociations) {
            val cleanKey = key.lowercase().trim()
            if (cleanKey.contains(cleanInput) || cleanInput.contains(cleanKey)) {
                return value as? String
            }
            
            val keyWords = cleanKey.split("\\s+".toRegex()).filter { it.length > 3 }
            val inputWords = cleanInput.split("\\s+".toRegex()).filter { it.length > 3 }
            val commonWords = keyWords.intersect(inputWords.toSet())
            if (commonWords.isNotEmpty()) {
                return value as? String
            }
        }
        
        return null
    }

    fun saveItemLocationAssociation(context: Context, itemName: String, location: String) {
        val trimmedItem = itemName.trim()
        val trimmedLoc = location.trim()
        if (trimmedItem.isEmpty() || trimmedLoc.isEmpty()) return
        val prefs = context.getSharedPreferences("item_location_associations", Context.MODE_PRIVATE)
        prefs.edit().putString(trimmedItem, trimmedLoc).apply()
    }

    fun getSimilarAssociatedLocation(context: Context, itemName: String): String? {
        val prefs = context.getSharedPreferences("item_location_associations", Context.MODE_PRIVATE)
        val allAssociations = prefs.all as? Map<String, *> ?: return null
        
        val cleanInput = itemName.lowercase().trim()
        if (cleanInput.isEmpty()) return null

        // 1. Try exact match
        val exactMatch = allAssociations[itemName] as? String
        if (exactMatch != null) return exactMatch
        
        // 2. Try case-insensitive exact match
        for ((key, value) in allAssociations) {
            if (key.lowercase().trim() == cleanInput) {
                return value as? String
            }
        }
        
        // 3. Try containment / similar words match
        for ((key, value) in allAssociations) {
            val cleanKey = key.lowercase().trim()
            if (cleanKey.contains(cleanInput) || cleanInput.contains(cleanKey)) {
                return value as? String
            }
            
            val keyWords = cleanKey.split("\\s+".toRegex()).filter { it.length > 3 }
            val inputWords = cleanInput.split("\\s+".toRegex()).filter { it.length > 3 }
            val commonWords = keyWords.intersect(inputWords.toSet())
            if (commonWords.isNotEmpty()) {
                return value as? String
            }
        }
        return null
    }

    // --- GEMINI SYMPTOM CHECKER ---
    fun analyzePetSymptom(petType: String, petAge: String, symptomDescription: String) {
        viewModelScope.launch {
            isSymptomAnalyzing.value = true
            symptomAnalysisResult.value = null
            symptomAnalysisError.value = null

            val checker = GeminiSymptomChecker()
            val result = checker.analyzeSymptom(petType, petAge, symptomDescription)
            if (result != null) {
                symptomAnalysisResult.value = result
            } else {
                symptomAnalysisError.value = "Nem sikerült a tünetelemzés lekérése. Kérlek, próbáld újra!"
            }
            isSymptomAnalyzing.value = false
        }
    }

    fun clearSymptomState() {
        symptomAnalysisResult.value = null
        symptomAnalysisError.value = null
    }

    // --- EMERGENCY AI ASSISTANT CHAT METHODS ---
    fun sendEmergencyChatMessage(messageText: String) {
        if (messageText.isBlank()) return
        
        viewModelScope.launch {
            val currentList = emergencyChatMessages.value.toMutableList()
            currentList.add(EmergencyChatMessage("user", messageText))
            emergencyChatMessages.value = currentList
            
            isEmergencyChatLoading.value = true
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                kotlinx.coroutines.delay(1000)
                val newList = emergencyChatMessages.value.toMutableList()
                newList.add(EmergencyChatMessage("ai", "Demo Mód: Az API kulcs nincs beállítva. Elsősegély lépésként az érintett területet mossuk le hideg vízzel, helyezzünk rá hideg borogatást, és ha a tünetek súlyosbodnak, haladéktalanul keressük fel a legközelebbi állatorvosi ügyeletet!"))
                emergencyChatMessages.value = newList
                isEmergencyChatLoading.value = false
                return@launch
            }

            try {
                val chatHistoryString = currentList.joinToString("\n") { 
                    val senderName = if (it.sender == "user") "Felhasználó" else "Állatorvos Asszisztens"
                    "$senderName: ${it.text}"
                }
                
                val prompt = """
                    Te egy tapasztalt sürgősségi állatorvos és elsősegély asszisztens vagy.
                    Elemezd az alábbi beszélgetési előzményt és válaszolj a felhasználó legutóbbi kérdésére vagy üzenetére.
                    
                    Beszélgetési előzmények:
                    $chatHistoryString
                    
                    Válaszolj tömören, gyakorlatiasan, megnyugtató, de határozott hangnemben. Csak és kizárólag magyar nyelven! Ha a probléma súlyosnak tűnik, mindig javasold az azonnali állatorvosi vizsgálatot!
                """.trimIndent()
                
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = prompt))
                        )
                    ),
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = "You are an emergency veterinary first-aid assistant. You only respond in Hungarian."))
                    )
                )
                
                val response = RetrofitInstance.api.generateContent(apiKey, request)
                val replyText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                    ?: "Sajnos nem kaptam választ a modelltől, kérlek próbáld újra!"
                
                val newList = emergencyChatMessages.value.toMutableList()
                newList.add(EmergencyChatMessage("ai", replyText))
                emergencyChatMessages.value = newList
            } catch (e: Exception) {
                val newList = emergencyChatMessages.value.toMutableList()
                newList.add(EmergencyChatMessage("ai", "Hiba történt a kapcsolat során: ${e.localizedMessage}. Kérlek, próbáld újra!"))
                emergencyChatMessages.value = newList
            } finally {
                isEmergencyChatLoading.value = false
            }
        }
    }
    
    fun clearEmergencyChat() {
        emergencyChatMessages.value = listOf(
            EmergencyChatMessage("ai", "Szia! Én az AI MancsKiskönyv sürgősségi asszisztense vagyok. Örömmel segítek az elsősegéllyel vagy sürgős teendőkkel kapcsolatos kérdésekben, de kérlek vedd figyelembe, hogy egy AI asszisztens vagyok, így előfordulhatnak hibák. Súlyos esetben mindig fordulj állatorvoshoz!")
        )
    }

    // --- PDF PASSPORT EXPORT ---
    fun exportPassportPdf(context: Context, pet: PetEntity, vaccinations: List<VaccinationEntity>, parasiteProtections: List<ParasiteEntity>) {
        viewModelScope.launch {
            try {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = Color.rgb(46, 117, 89) // Forest Green Theme
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                val headerPaint = Paint().apply {
                    color = Color.rgb(33, 33, 33)
                    textSize = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                val textPaint = Paint().apply {
                    color = Color.rgb(66, 66, 66)
                    textSize = 11f
                    isAntiAlias = true
                }

                val subTextPaint = Paint().apply {
                    color = Color.rgb(117, 117, 117)
                    textSize = 10f
                    isAntiAlias = true
                }

                val linePaint = Paint().apply {
                    color = Color.rgb(200, 200, 200)
                    strokeWidth = 1f
                }

                var y = 50f

                // Header
                canvas.drawText("ÁLLATEGÉSZSÉGÜGYI KISKÖNYV & ÚTLEVÉL", 50f, y, titlePaint)
                y += 15f
                canvas.drawLine(50f, y, 545f, y, linePaint)
                y += 30f

                // Pet details
                canvas.drawText("Kedvenc Adatai:", 50f, y, headerPaint)
                y += 20f

                val details = listOf(
                    "Név: ${pet.name}",
                    "Faj: ${pet.type} | Fajta: ${pet.breed}",
                    "Szín: ${pet.color} | Nem: ${pet.gender}",
                    "Kor: ${pet.age}",
                    "Ivartalanítva: ${if (pet.isNeutered) "Igen" else "Nem"}",
                    "Mikrochip szám: ${pet.chipNumber.ifEmpty { "Nincs megadva" }}",
                    "Allergiák: ${pet.allergies.ifEmpty { "Nincs" }}",
                    "Krónikus betegségek: ${pet.chronicDiseases.ifEmpty { "Nincs" }}"
                )

                for (detail in details) {
                    canvas.drawText(detail, 70f, y, textPaint)
                    y += 18f
                }
                y += 15f

                // Insurances / Vet details
                canvas.drawText("Állatorvos & Biztosítás:", 50f, y, headerPaint)
                y += 20f
                canvas.drawText("Kezelő állatorvos: ${pet.vetName} (${pet.vetPhone})", 70f, y, textPaint)
                y += 18f
                canvas.drawText("Biztosítás: ${pet.insuranceCompany} | Kötvényszám: ${pet.insurancePolicyNumber.ifEmpty { "Nincs" }}", 70f, y, textPaint)
                y += 25f

                canvas.drawLine(50f, y, 545f, y, linePaint)
                y += 25f

                // Vaccinations
                canvas.drawText("Oltási bejegyzések (Utolsó 5):", 50f, y, headerPaint)
                y += 20f

                val dateFormat = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())

                if (vaccinations.isEmpty()) {
                    canvas.drawText("Nincs bejegyzett oltás.", 70f, y, textPaint)
                    y += 20f
                } else {
                    val listToPrint = vaccinations.take(5)
                    for (v in listToPrint) {
                        val dateStr = dateFormat.format(Date(v.timestamp))
                        val mandatoryText = if (v.isMandatory) "[KÖTELEZŐ]" else "[AJÁNLOTT]"
                        canvas.drawText("$dateStr - ${v.name} $mandatoryText", 70f, y, textPaint)
                        y += 15f
                        val subText = "Gyártási szám: ${v.serialNumber ?: "Nincs"} | Orvos: ${v.veterinarian ?: "Nincs"}"
                        canvas.drawText(subText, 90f, y, subTextPaint)
                        y += 20f
                    }
                }
                y += 10f

                // Parasite treatment
                canvas.drawText("Parazita elleni kezelések (Utolsó 3):", 50f, y, headerPaint)
                y += 20f

                if (parasiteProtections.isEmpty()) {
                    canvas.drawText("Nincs bejegyzett kezelés.", 70f, y, textPaint)
                    y += 20f
                } else {
                    val listToPrint = parasiteProtections.take(3)
                    for (p in listToPrint) {
                        val dateStr = dateFormat.format(Date(p.timestamp))
                        val nextDateStr = dateFormat.format(Date(p.nextDueTimestamp))
                        canvas.drawText("$dateStr - ${p.protectionType} (${p.treatmentMethod})", 70f, y, textPaint)
                        y += 15f
                        val subText = "Készítmény: ${p.productName} | Következő esedékesség: $nextDateStr"
                        canvas.drawText(subText, 90f, y, subTextPaint)
                        y += 20f
                    }
                }

                // Page end note
                y = 770f
                val footerWarningPaint = Paint().apply {
                    color = Color.rgb(180, 50, 50) // Subtle reddish color for the warning
                    textSize = 8f
                    isAntiAlias = true
                }
                canvas.drawText("Figyelem: Ezek az adatok nem hivatalosak, így semmit nem bizonyítanak és nem", 50f, y, footerWarningPaint)
                y += 11f
                canvas.drawText("helyettesítik a hivatalos állatorvosi okmányokat!", 50f, y, footerWarningPaint)
                y += 14f
                canvas.drawText("Generálva a MancsKiskönyv alkalmazás által - " + dateFormat.format(Date()), 50f, y, subTextPaint)

                pdfDocument.finishPage(page)

                // Save PDF to cache dir to share it
                val pdfFile = File(context.cacheDir, "${pet.name}_egeszsegugyi_utlevel.pdf")
                val outputStream = FileOutputStream(pdfFile)
                pdfDocument.writeTo(outputStream)
                pdfDocument.close()
                outputStream.close()

                // Share intent
                val pdfUri: Uri = FileProvider.getUriForFile(
                    context,
                    "com.aistudio.mancskiskonyv.petcare.fileprovider",
                    pdfFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, "${pet.name} állategészségügyi kiskönyve")
                    putExtra(Intent.EXTRA_TEXT, "Mellékelten küldöm ${pet.name} állategészségügyi útlevelét.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "Küldés ezzel:"))

            } catch (e: Exception) {
                Log.e("PetViewModel", "Error generating PDF: ${e.message}", e)
            }
        }
    }

    suspend fun getBackupJsonString(): String {
        val root = org.json.JSONObject()
        root.put("version", 1)

        // Retrieve all data
        val pets = repository.allPets.first()
        val budgetLimits = repository.allBudgetLimits.first()
        val expenses = repository.allExpenses.first()

        // Gather other data per pet
        val weights = mutableListOf<WeightEntity>()
        val vaccinations = mutableListOf<VaccinationEntity>()
        val parasites = mutableListOf<ParasiteEntity>()
        val routines = mutableListOf<DailyRoutineEntity>()
        val templates = mutableListOf<RoutineTemplateEntity>()
        val medications = mutableListOf<MedicationReminderEntity>()

        pets.forEach { pet ->
            weights.addAll(repository.getWeightsForPet(pet.id).first())
            vaccinations.addAll(repository.getVaccinationsForPet(pet.id).first())
            parasites.addAll(repository.getParasiteProtectionsForPet(pet.id).first())
            routines.addAll(repository.getDailyRoutinesForPet(pet.id).first())
            templates.addAll(repository.getRoutineTemplatesForPet(pet.id).first())
            medications.addAll(repository.getMedicationRemindersForPet(pet.id).first())
        }

        // Serialize Pets
        val petsArray = org.json.JSONArray()
        pets.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("type", p.type)
            obj.put("breed", p.breed)
            obj.put("color", p.color)
            obj.put("gender", p.gender)
            obj.put("age", p.age)
            obj.put("isNeutered", p.isNeutered)
            obj.put("chipNumber", p.chipNumber)
            obj.put("photoUri", p.photoUri ?: org.json.JSONObject.NULL)
            obj.put("allergies", p.allergies)
            obj.put("chronicDiseases", p.chronicDiseases)
            obj.put("vetName", p.vetName)
            obj.put("vetPhone", p.vetPhone)
            obj.put("insuranceCompany", p.insuranceCompany)
            obj.put("insurancePolicyNumber", p.insurancePolicyNumber)
            obj.put("birthDate", p.birthDate ?: "")
            obj.put("chipImplantDate", p.chipImplantDate ?: "")
            obj.put("chipExpiryDate", p.chipExpiryDate ?: "")
            petsArray.put(obj)
        }
        root.put("pets", petsArray)

        // Serialize Weights
        val weightsArray = org.json.JSONArray()
        weights.forEach { w ->
            val obj = org.json.JSONObject()
            obj.put("id", w.id)
            obj.put("petId", w.petId)
            obj.put("weightKg", w.weightKg)
            obj.put("timestamp", w.timestamp)
            obj.put("shoulderHeightCm", w.shoulderHeightCm ?: org.json.JSONObject.NULL)
            obj.put("bodyLengthCm", w.bodyLengthCm ?: org.json.JSONObject.NULL)
            obj.put("chestCircumferenceCm", w.chestCircumferenceCm ?: org.json.JSONObject.NULL)
            obj.put("neckCircumferenceCm", w.neckCircumferenceCm ?: org.json.JSONObject.NULL)
            weightsArray.put(obj)
        }
        root.put("weights", weightsArray)

        // Serialize Budget Limits
        val limitsArray = org.json.JSONArray()
        budgetLimits.forEach { bl ->
            val obj = org.json.JSONObject()
            obj.put("category", bl.category)
            obj.put("limitAmount", bl.limitAmount)
            limitsArray.put(obj)
        }
        root.put("budgetLimits", limitsArray)

        // Serialize Expenses
        val expensesArray = org.json.JSONArray()
        expenses.forEach { e ->
            val obj = org.json.JSONObject()
            obj.put("id", e.id)
            obj.put("petId", e.petId)
            obj.put("amount", e.amount)
            obj.put("category", e.category)
            obj.put("subCategory", e.subCategory ?: org.json.JSONObject.NULL)
            obj.put("description", e.description)
            obj.put("timestamp", e.timestamp)
            obj.put("isRecurring", e.isRecurring)
            obj.put("recurringIntervalMonths", e.recurringIntervalMonths)
            obj.put("imageUri", e.imageUri ?: org.json.JSONObject.NULL)
            obj.put("targetPetIds", e.targetPetIds ?: org.json.JSONObject.NULL)
            expensesArray.put(obj)
        }
        root.put("expenses", expensesArray)

        // Serialize Vaccinations
        val vacArray = org.json.JSONArray()
        vaccinations.forEach { v ->
            val obj = org.json.JSONObject()
            obj.put("id", v.id)
            obj.put("petId", v.petId)
            obj.put("name", v.name)
            obj.put("timestamp", v.timestamp)
            obj.put("serialNumber", v.serialNumber ?: org.json.JSONObject.NULL)
            obj.put("veterinarian", v.veterinarian ?: org.json.JSONObject.NULL)
            obj.put("notes", v.notes ?: org.json.JSONObject.NULL)
            obj.put("nextDueTimestamp", v.nextDueTimestamp ?: org.json.JSONObject.NULL)
            obj.put("isMandatory", v.isMandatory)
            vacArray.put(obj)
        }
        root.put("vaccinations", vacArray)

        // Serialize Parasites
        val parArray = org.json.JSONArray()
        parasites.forEach { p ->
            val obj = org.json.JSONObject()
            obj.put("id", p.id)
            obj.put("petId", p.petId)
            obj.put("protectionType", p.protectionType)
            obj.put("treatmentMethod", p.treatmentMethod)
            obj.put("productName", p.productName)
            obj.put("timestamp", p.timestamp)
            obj.put("durationDays", p.durationDays)
            obj.put("nextDueTimestamp", p.nextDueTimestamp)
            parArray.put(obj)
        }
        root.put("parasites", parArray)

        // Serialize Daily Routines
        val routArray = org.json.JSONArray()
        routines.forEach { r ->
            val obj = org.json.JSONObject()
            obj.put("id", r.id)
            obj.put("petId", r.petId)
            obj.put("actionType", r.actionType)
            obj.put("loggedBy", r.loggedBy)
            obj.put("timestamp", r.timestamp)
            routArray.put(obj)
        }
        root.put("routines", routArray)

        // Serialize Routine Templates
        val tempArray = org.json.JSONArray()
        templates.forEach { t ->
            val obj = org.json.JSONObject()
            obj.put("id", t.id)
            obj.put("petId", t.petId)
            obj.put("name", t.name)
            obj.put("description", t.description)
            tempArray.put(obj)
        }
        root.put("templates", tempArray)

        // Serialize Medication Reminders
        val medArray = org.json.JSONArray()
        medications.forEach { m ->
            val obj = org.json.JSONObject()
            obj.put("id", m.id)
            obj.put("petId", m.petId)
            obj.put("medicationName", m.medicationName)
            obj.put("dosage", m.dosage)
            obj.put("frequency", m.frequency)
            obj.put("reminderTime", m.reminderTime)
            obj.put("startDate", m.startDate)
            obj.put("isActive", m.isActive)
            obj.put("notes", m.notes)
            obj.put("prescriptionPhotoUri", m.prescriptionPhotoUri ?: org.json.JSONObject.NULL)
            medArray.put(obj)
        }
        root.put("medications", medArray)

        return root.toString(2)
    }

    suspend fun restoreFromJsonString(jsonString: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonString)
            
            // Clear all data first
            repository.clearAllData()

            // 1. Pets
            val petsArray = root.optJSONArray("pets")
            if (petsArray != null) {
                for (i in 0 until petsArray.length()) {
                    val obj = petsArray.getJSONObject(i)
                    repository.insertPet(
                        PetEntity(
                            id = obj.optInt("id", 0),
                            name = obj.getString("name"),
                            type = obj.getString("type"),
                            breed = obj.getString("breed"),
                            color = obj.getString("color"),
                            gender = obj.getString("gender"),
                            age = obj.getString("age"),
                            isNeutered = obj.getBoolean("isNeutered"),
                            chipNumber = obj.getString("chipNumber"),
                            photoUri = if (obj.isNull("photoUri")) null else obj.getString("photoUri"),
                            allergies = obj.getString("allergies"),
                            chronicDiseases = obj.getString("chronicDiseases"),
                            vetName = obj.getString("vetName"),
                            vetPhone = obj.getString("vetPhone"),
                            insuranceCompany = obj.getString("insuranceCompany"),
                            insurancePolicyNumber = obj.getString("insurancePolicyNumber"),
                            birthDate = if (obj.has("birthDate")) obj.optString("birthDate", "") else "",
                            chipImplantDate = if (obj.has("chipImplantDate")) obj.optString("chipImplantDate", null) else null,
                            chipExpiryDate = if (obj.has("chipExpiryDate")) obj.optString("chipExpiryDate", null) else null
                        )
                    )
                }
            }

            // 2. Weights
            val weightsArray = root.optJSONArray("weights")
            if (weightsArray != null) {
                for (i in 0 until weightsArray.length()) {
                    val obj = weightsArray.getJSONObject(i)
                    repository.insertWeight(
                        WeightEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            weightKg = obj.getDouble("weightKg"),
                            timestamp = obj.getLong("timestamp"),
                            shoulderHeightCm = if (obj.isNull("shoulderHeightCm") || !obj.has("shoulderHeightCm")) null else obj.getDouble("shoulderHeightCm"),
                            bodyLengthCm = if (obj.isNull("bodyLengthCm") || !obj.has("bodyLengthCm")) null else obj.getDouble("bodyLengthCm"),
                            chestCircumferenceCm = if (obj.isNull("chestCircumferenceCm") || !obj.has("chestCircumferenceCm")) null else obj.getDouble("chestCircumferenceCm"),
                            neckCircumferenceCm = if (obj.isNull("neckCircumferenceCm") || !obj.has("neckCircumferenceCm")) null else obj.getDouble("neckCircumferenceCm")
                        )
                    )
                }
            }

            // 3. Budget Limits
            val limitsArray = root.optJSONArray("budgetLimits")
            if (limitsArray != null) {
                for (i in 0 until limitsArray.length()) {
                    val obj = limitsArray.getJSONObject(i)
                    repository.insertBudgetLimit(
                        BudgetLimitEntity(
                            category = obj.getString("category"),
                            limitAmount = obj.getDouble("limitAmount")
                        )
                    )
                }
            }

            // 4. Expenses
            val expensesArray = root.optJSONArray("expenses")
            if (expensesArray != null) {
                for (i in 0 until expensesArray.length()) {
                    val obj = expensesArray.getJSONObject(i)
                    repository.insertExpense(
                        ExpenseEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            amount = obj.getDouble("amount"),
                            category = obj.getString("category"),
                            subCategory = if (obj.isNull("subCategory")) null else obj.getString("subCategory"),
                            description = obj.getString("description"),
                            timestamp = obj.getLong("timestamp"),
                            isRecurring = obj.optBoolean("isRecurring", false),
                            recurringIntervalMonths = obj.optInt("recurringIntervalMonths", 0),
                            imageUri = if (obj.isNull("imageUri")) null else obj.getString("imageUri"),
                            targetPetIds = if (obj.isNull("targetPetIds") || !obj.has("targetPetIds")) null else obj.getString("targetPetIds")
                        )
                    )
                }
            }

            // 5. Vaccinations
            val vacArray = root.optJSONArray("vaccinations")
            if (vacArray != null) {
                for (i in 0 until vacArray.length()) {
                    val obj = vacArray.getJSONObject(i)
                    repository.insertVaccination(
                        VaccinationEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            name = obj.getString("name"),
                            timestamp = obj.getLong("timestamp"),
                            serialNumber = if (obj.isNull("serialNumber")) null else obj.getString("serialNumber"),
                            veterinarian = if (obj.isNull("veterinarian")) null else obj.getString("veterinarian"),
                            notes = if (obj.isNull("notes")) null else obj.getString("notes"),
                            nextDueTimestamp = if (obj.isNull("nextDueTimestamp")) null else obj.getLong("nextDueTimestamp"),
                            isMandatory = obj.optBoolean("isMandatory", false)
                        )
                    )
                }
            }

            // 6. Parasites
            val parArray = root.optJSONArray("parasites")
            if (parArray != null) {
                for (i in 0 until parArray.length()) {
                    val obj = parArray.getJSONObject(i)
                    repository.insertParasite(
                        ParasiteEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            protectionType = obj.getString("protectionType"),
                            treatmentMethod = obj.getString("treatmentMethod"),
                            productName = obj.getString("productName"),
                            timestamp = obj.getLong("timestamp"),
                            durationDays = obj.getInt("durationDays"),
                            nextDueTimestamp = obj.getLong("nextDueTimestamp")
                        )
                    )
                }
            }

            // 7. Routines
            val routArray = root.optJSONArray("routines")
            if (routArray != null) {
                for (i in 0 until routArray.length()) {
                    val obj = routArray.getJSONObject(i)
                    repository.insertDailyRoutine(
                        DailyRoutineEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            actionType = obj.getString("actionType"),
                            loggedBy = obj.getString("loggedBy"),
                            timestamp = obj.getLong("timestamp")
                        )
                    )
                }
            }

            // 8. Templates
            val tempArray = root.optJSONArray("templates")
            if (tempArray != null) {
                for (i in 0 until tempArray.length()) {
                    val obj = tempArray.getJSONObject(i)
                    repository.insertRoutineTemplate(
                        RoutineTemplateEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            name = obj.getString("name"),
                            description = obj.getString("description")
                        )
                    )
                }
            }

            // 9. Medications
            val medArray = root.optJSONArray("medications")
            if (medArray != null) {
                for (i in 0 until medArray.length()) {
                    val obj = medArray.getJSONObject(i)
                    repository.insertMedicationReminder(
                        MedicationReminderEntity(
                            id = obj.optInt("id", 0),
                            petId = obj.getInt("petId"),
                            medicationName = obj.getString("medicationName"),
                            dosage = obj.getString("dosage"),
                            frequency = obj.getString("frequency"),
                            reminderTime = obj.getString("reminderTime"),
                            startDate = obj.getLong("startDate"),
                            isActive = obj.optBoolean("isActive", true),
                            notes = obj.optString("notes", ""),
                            prescriptionPhotoUri = if (obj.isNull("prescriptionPhotoUri")) null else obj.getString("prescriptionPhotoUri")
                        )
                    )
                }
            }

            true
         } catch (e: Exception) {
             e.printStackTrace()
             false
         }
     }

    fun exportPetDataZip(
        context: Context,
        targetPetId: Int?, // if null, export all pets in separate folders
        includeVaccinations: Boolean,
        includeReminders: Boolean,
        includeParasites: Boolean,
        includeExpenses: Boolean,
        selectedExpenseCategories: List<String>,
        includeWeights: Boolean,
        customTargetUri: Uri? = null
    ) {
        viewModelScope.launch {
            try {
                val petsToExport = if (targetPetId == null) {
                    allPets.value
                } else {
                    allPets.value.filter { it.id == targetPetId }
                }
                
                if (petsToExport.isEmpty()) return@launch
                
                val zipFileName = if (targetPetId == null) {
                    "MancsKiskonyv_osszes_kedvenc.zip"
                } else {
                    "${petsToExport.first().name}_dokumentumok_es_adatok.zip"
                }
                
                val zipFile = java.io.File(context.cacheDir, zipFileName)
                val fos = java.io.FileOutputStream(zipFile)
                val zos = java.util.zip.ZipOutputStream(fos)
                
                val dateFormat = SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault())
                
                for (pet in petsToExport) {
                    // Prepend folder path if exporting all pets
                    val folderPrefix = if (targetPetId == null) "${pet.name}/" else ""
                    
                    // 1. Pet Details Text
                    val detailsSb = StringBuilder()
                    detailsSb.append("=== KEDVENC ALAPADATAI ===\n")
                    detailsSb.append("Név: ${pet.name}\n")
                    detailsSb.append("Faj: ${pet.type}\n")
                    detailsSb.append("Fajta: ${pet.breed}\n")
                    detailsSb.append("Szín: ${pet.color}\n")
                    detailsSb.append("Nem: ${pet.gender}\n")
                    detailsSb.append("Kor / Születési idő: ${pet.age}\n")
                    detailsSb.append("Születési dátum: ${pet.birthDate ?: ""}\n")
                    detailsSb.append("Ivartalanítva: ${if (pet.isNeutered) "Igen" else "Nem"}\n")
                    detailsSb.append("Mikrochip szám: ${pet.chipNumber.ifEmpty { "Nincs megadva" }}\n")
                    detailsSb.append("Allergiák: ${pet.allergies.ifEmpty { "Nincs" }}\n")
                    detailsSb.append("Krónikus betegségek: ${pet.chronicDiseases.ifEmpty { "Nincs" }}\n")
                    detailsSb.append("Kezelő állatorvos: ${pet.vetName} (${pet.vetPhone})\n")
                    detailsSb.append("Biztosítás: ${pet.insuranceCompany} (${pet.insurancePolicyNumber})\n\n")
                    
                    if (includeVaccinations) {
                        val vacs = repository.getVaccinationsForPet(pet.id).first()
                        detailsSb.append("=== VÉDŐOLTÁSOK ===\n")
                        if (vacs.isEmpty()) {
                            detailsSb.append("Nincs bejegyzett oltás.\n\n")
                        } else {
                            vacs.forEach { v ->
                                val dateStr = dateFormat.format(Date(v.timestamp))
                                val nextDueStr = v.nextDueTimestamp?.let { dateFormat.format(Date(it)) } ?: "Nincs"
                                detailsSb.append("- Dátum: $dateStr | Oltóanyag: ${v.name} | Típus: ${if (v.isMandatory) "Kötelező" else "Ajánlott"}\n")
                                detailsSb.append("  Gyártási szám: ${v.serialNumber ?: "Nincs"} | Orvos: ${v.veterinarian ?: "Nincs"}\n")
                                detailsSb.append("  Következő esedékesség: $nextDueStr | Megjegyzés: ${v.notes ?: "Nincs"}\n\n")
                            }
                        }
                    }
                    
                    if (includeReminders) {
                        val meds = repository.getMedicationRemindersForPet(pet.id).first()
                        detailsSb.append("=== GYÓGYSZERES REMINDER-EK / RECEPTEK ===\n")
                        if (meds.isEmpty()) {
                            detailsSb.append("Nincs gyógyszeres emlékeztető.\n\n")
                        } else {
                            meds.forEach { m ->
                                val startStr = dateFormat.format(Date(m.startDate))
                                detailsSb.append("- Gyógyszer: ${m.medicationName} | Adagolás: ${m.dosage}\n")
                                detailsSb.append("  Gyakoriság: ${m.frequency} | Időpont: ${m.reminderTime} | Kezdés: $startStr\n")
                                detailsSb.append("  Aktív: ${if (m.isActive) "Igen" else "Nem"} | Megjegyzés: ${m.notes}\n")
                                if (m.prescriptionPhotoUri != null) {
                                    val imgFileName = "recept_${m.id}.jpg"
                                    detailsSb.append("  Csatolt receptkép: $imgFileName\n")
                                    try {
                                        val uri = Uri.parse(m.prescriptionPhotoUri)
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        if (inputStream != null) {
                                            zos.putNextEntry(java.util.zip.ZipEntry("$folderPrefix$imgFileName"))
                                            inputStream.use { it.copyTo(zos) }
                                            zos.closeEntry()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ExportZip", "Error adding prescription image to zip: ${e.message}")
                                    }
                                }
                                detailsSb.append("\n")
                            }
                        }
                    }
                    
                    if (includeParasites) {
                        val paras = repository.getParasiteProtectionsForPet(pet.id).first()
                        detailsSb.append("=== PARAZITA ELLENI KEZELÉSEK ===\n")
                        if (paras.isEmpty()) {
                            detailsSb.append("Nincs bejegyzett kezelés.\n\n")
                        } else {
                            paras.forEach { p ->
                                val dateStr = dateFormat.format(Date(p.timestamp))
                                val nextDueStr = dateFormat.format(Date(p.nextDueTimestamp))
                                detailsSb.append("- Dátum: $dateStr | Típus: ${p.protectionType} (${p.treatmentMethod})\n")
                                detailsSb.append("  Készítmény: ${p.productName} | Tartósság: ${p.durationDays} nap\n")
                                detailsSb.append("  Következő kezelés esedékessége: $nextDueStr\n\n")
                            }
                        }
                    }
                    
                    zos.putNextEntry(java.util.zip.ZipEntry("${folderPrefix}orvosi_es_alapadatok_riport.txt"))
                    zos.write(detailsSb.toString().toByteArray())
                    zos.closeEntry()
                    
                    if (includeExpenses) {
                        val expenses = repository.getExpensesForPet(pet.id).first()
                        val filteredExpenses = expenses.filter { selectedExpenseCategories.isEmpty() || selectedExpenseCategories.contains(it.category) }
                        
                        val csvSb = java.lang.StringBuilder()
                        csvSb.append("Dátum,Kategória,Alkategória,Összeg,Leírás,Vásárlási hely,Ismétlődő,Csatolt bizonylat\n")
                        filteredExpenses.forEach { e ->
                            val dateStr = dateFormat.format(Date(e.timestamp))
                            val hasLocation = e.description.contains("|||")
                            val cleanDesc = if (hasLocation) {
                                e.description.substringBefore("|||").replace("\"", "\"\"").replace("\n", " ")
                            } else {
                                e.description.replace("\"", "\"\"").replace("\n", " ")
                            }
                            val purchaseLoc = if (hasLocation) {
                                e.description.substringAfter("|||").replace("\"", "\"\"").replace("\n", " ")
                            } else {
                                ""
                            }
                            val imageFileInZip = if (e.imageUri != null) "bizonylat_${e.id}.jpg" else "Nincs"
                            csvSb.append("\"$dateStr\",\"${e.category}\",\"${e.subCategory ?: ""}\",${e.amount},\"$cleanDesc\",\"$purchaseLoc\",\"${if (e.isRecurring) "Igen" else "Nem"}\",\"$imageFileInZip\"\n")
                            
                            if (e.imageUri != null) {
                                try {
                                    val uri = Uri.parse(e.imageUri)
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    if (inputStream != null) {
                                        zos.putNextEntry(java.util.zip.ZipEntry("${folderPrefix}bizonylat_${e.id}.jpg"))
                                        inputStream.use { it.copyTo(zos) }
                                        zos.closeEntry()
                                    }
                                } catch (ex: Exception) {
                                    Log.e("ExportZip", "Error adding receipt image to zip: ${ex.message}")
                                }
                            }
                        }
                        
                        zos.putNextEntry(java.util.zip.ZipEntry("${folderPrefix}koltsegvetes_es_kiadasok.csv"))
                        zos.write(csvSb.toString().toByteArray())
                        zos.closeEntry()
                    }
                    
                    if (includeWeights) {
                        val weights = repository.getWeightsForPet(pet.id).first()
                        val csvSb = java.lang.StringBuilder()
                        csvSb.append("Dátum,Súly (kg)\n")
                        weights.forEach { w ->
                            val dateStr = dateFormat.format(Date(w.timestamp))
                            csvSb.append("\"$dateStr\",${w.weightKg}\n")
                        }
                        zos.putNextEntry(java.util.zip.ZipEntry("${folderPrefix}suly_elozmenyek.csv"))
                        zos.write(csvSb.toString().toByteArray())
                        zos.closeEntry()
                    }
                    
                    if (pet.photoUri != null) {
                        try {
                            val uri = Uri.parse(pet.photoUri)
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                zos.putNextEntry(java.util.zip.ZipEntry("${folderPrefix}profilkep_${pet.name}.jpg"))
                                inputStream.use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        } catch (e: Exception) {
                            Log.e("ExportZip", "Error adding pet profile picture to zip: ${e.message}")
                        }
                    }
                }
                
                zos.close()
                fos.close()
                
                if (customTargetUri != null) {
                    context.contentResolver.openOutputStream(customTargetUri)?.use { outputStream ->
                        zipFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Sikeresen mentve a kiválasztott helyre: $zipFileName", android.widget.Toast.LENGTH_LONG).show()
                    }
                } else {
                    val zipUri: Uri = FileProvider.getUriForFile(
                        context,
                        "com.aistudio.mancskiskonyv.petcare.fileprovider",
                        zipFile
                    )
                    
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, zipUri)
                        if (targetPetId == null) {
                            putExtra(Intent.EXTRA_SUBJECT, "MancsKiskönyv Export - Összes kedvenc")
                            putExtra(Intent.EXTRA_TEXT, "Mellékelve találod az összes kedvenced kiválasztott dokumentumait, képeit és táblázatait, állatonként külön mappákba rendezve egy tömörített ZIP fájlban.")
                        } else {
                            val singlePet = petsToExport.first()
                            putExtra(Intent.EXTRA_SUBJECT, "MancsKiskönyv Export - ${singlePet.name}")
                            putExtra(Intent.EXTRA_TEXT, "Mellékelve találod ${singlePet.name} kiválasztott dokumentumait, képeit és táblázatait egy tömörített ZIP fájlban.")
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // Save directly to the public Downloads folder
                    val savedToDownloads = saveFileToPublicDownloads(context, zipFile, zipFileName)
                    withContext(Dispatchers.Main) {
                        if (savedToDownloads) {
                            android.widget.Toast.makeText(context, "Sikeresen elmentve a Letöltések mappába: $zipFileName", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Mentve ideiglenesen, megosztás megnyitása...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    val chooserIntent = Intent.createChooser(shareIntent, "Küldés / Megosztás:")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                }
                
            } catch (e: Exception) {
                Log.e("PetViewModel", "Hiba az adatok letöltésekor: ${e.message}", e)
            }
        }
    }

    private fun saveFileToPublicDownloads(context: Context, sourceFile: java.io.File, fileName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    true
                } else {
                    false
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val targetFile = java.io.File(downloadsDir, fileName)
                sourceFile.inputStream().use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e("SaveToDownloads", "Error saving file to Downloads folder: ${e.message}", e)
            false
        }
    }

    suspend fun importPetDataZip(context: Context, uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            val tempDir = java.io.File(context.cacheDir, "zip_import_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
                val zis = java.util.zip.ZipInputStream(inputStream)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = java.io.File(tempDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                zis.close()
                
                val reportFiles = findReportFiles(tempDir)
                if (reportFiles.isEmpty()) {
                    tempDir.deleteRecursively()
                    return@withContext false
                }
                
                for (reportFile in reportFiles) {
                    val petFolder = reportFile.parentFile ?: tempDir
                    importSinglePetFolder(context, petFolder, reportFile)
                }
                
                tempDir.deleteRecursively()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                tempDir.deleteRecursively()
                false
            }
        }
    }

    private fun findReportFiles(dir: File): List<File> {
        val results = mutableListOf<File>()
        val files = dir.listFiles() ?: return results
        for (f in files) {
            if (f.isDirectory) {
                results.addAll(findReportFiles(f))
            } else if (f.name == "orvosi_es_alapadatok_riport.txt") {
                results.add(f)
            }
        }
        return results
    }

    private suspend fun importSinglePetFolder(context: Context, folder: File, reportFile: File) {
        val lines = reportFile.readLines()
        
        var name = ""
        var type = ""
        var breed = ""
        var color = ""
        var gender = "Szuka"
        var age = ""
        var birthDate = ""
        var isNeutered = false
        var chipNumber = ""
        var allergies = ""
        var chronicDiseases = ""
        var vetName = ""
        var vetPhone = ""
        var insuranceCompany = ""
        var insurancePolicyNumber = ""

        var currentSection = ""
        val vaccinationsList = mutableListOf<VaccinationEntity>()
        val medicationsList = mutableListOf<MedicationReminderEntity>()
        val parasitesList = mutableListOf<ParasiteEntity>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            if (line.startsWith("===")) {
                currentSection = line
                i++
                continue
            }

            when (currentSection) {
                "=== KEDVENC ALAPADATAI ===" -> {
                    when {
                        line.startsWith("Név:") -> name = line.substringAfter("Név:").trim()
                        line.startsWith("Faj:") -> type = line.substringAfter("Faj:").trim()
                        line.startsWith("Fajta:") -> breed = line.substringAfter("Fajta:").trim()
                        line.startsWith("Szín:") -> color = line.substringAfter("Szín:").trim()
                        line.startsWith("Nem:") -> gender = line.substringAfter("Nem:").trim()
                        line.startsWith("Kor / Születési idő:") -> age = line.substringAfter("Kor / Születési idő:").trim()
                        line.startsWith("Születési dátum:") -> birthDate = line.substringAfter("Születési dátum:").trim()
                        line.startsWith("Ivartalanítva:") -> isNeutered = line.substringAfter("Ivartalanítva:").trim().lowercase() == "igen"
                        line.startsWith("Mikrochip szám:") -> chipNumber = line.substringAfter("Mikrochip szám:").trim().let { if (it == "Nincs megadva") "" else it }
                        line.startsWith("Allergiák:") -> allergies = line.substringAfter("Allergiák:").trim().let { if (it == "Nincs") "" else it }
                        line.startsWith("Krónikus betegségek:") -> chronicDiseases = line.substringAfter("Krónikus betegségek:").trim().let { if (it == "Nincs") "" else it }
                        line.startsWith("Kezelő állatorvos:") -> {
                            val doctorPart = line.substringAfter("Kezelő állatorvos:").trim()
                            vetName = doctorPart.substringBefore("(").trim()
                            vetPhone = doctorPart.substringAfter("(").substringBefore(")").trim()
                        }
                        line.startsWith("Biztosítás:") -> {
                            val insurancePart = line.substringAfter("Biztosítás:").trim()
                            insuranceCompany = insurancePart.substringBefore("(").trim()
                            insurancePolicyNumber = insurancePart.substringAfter("(").substringBefore(")").trim()
                        }
                    }
                }
                "=== VÉDŐOLTÁSOK ===" -> {
                    if (line.startsWith("- Dátum:")) {
                        try {
                            val dateStr = line.substringAfter("Dátum:").substringBefore("|").trim()
                            val vName = line.substringAfter("Oltóanyag:").substringBefore("|").trim()
                            val vType = line.substringAfter("Típus:").trim()
                            val isMandatory = vType == "Kötelező"
                            
                            var serialNumber: String? = null
                            var veterinarian: String? = null
                            var nextDueTimestamp: Long? = null
                            var notes: String? = null
                            
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Gyártási szám:")) {
                                i++
                                val l2 = lines[i].trim()
                                serialNumber = l2.substringAfter("Gyártási szám:").substringBefore("|").trim().let { if (it == "Nincs") null else it }
                                veterinarian = l2.substringAfter("Orvos:").trim().let { if (it == "Nincs") null else it }
                            }
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Következő esedékesség:")) {
                                i++
                                val l3 = lines[i].trim()
                                val nextDueStr = l3.substringAfter("Következő esedékesség:").substringBefore("|").trim()
                                nextDueTimestamp = if (nextDueStr == "Nincs") null else parseDate(nextDueStr)
                                notes = l3.substringAfter("Megjegyzés:").trim().let { if (it == "Nincs") null else it }
                            }
                            
                            vaccinationsList.add(
                                VaccinationEntity(
                                    id = 0,
                                    petId = 0,
                                    name = vName,
                                    timestamp = parseDate(dateStr),
                                    serialNumber = serialNumber,
                                    veterinarian = veterinarian,
                                    notes = notes,
                                    nextDueTimestamp = nextDueTimestamp,
                                    isMandatory = isMandatory
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "=== GYÓGYSZERES REMINDER-EK / RECEPTEK ===" -> {
                    if (line.startsWith("- Gyógyszer:")) {
                        try {
                            val mName = line.substringAfter("Gyógyszer:").substringBefore("|").trim()
                            val mDosage = line.substringAfter("Adagolás:").trim()
                            
                            var freq = ""
                            var time = ""
                            var startStr = ""
                            var isActive = true
                            var notes = ""
                            var prescriptionPhotoName: String? = null
                            
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Gyakoriság:")) {
                                i++
                                val l2 = lines[i].trim()
                                freq = l2.substringAfter("Gyakoriság:").substringBefore("|").trim()
                                time = l2.substringAfter("Időpont:").substringBefore("|").trim()
                                startStr = l2.substringAfter("Kezdés:").trim()
                            }
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Aktív:")) {
                                i++
                                val l3 = lines[i].trim()
                                isActive = l3.substringAfter("Aktív:").substringBefore("|").trim() == "Igen"
                                notes = l3.substringAfter("Megjegyzés:").trim()
                            }
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Csatolt receptkép:")) {
                                i++
                                val l4 = lines[i].trim()
                                prescriptionPhotoName = l4.substringAfter("Csatolt receptkép:").trim().let { if (it == "Nincs" || it.isEmpty()) null else it }
                            }
                            
                            medicationsList.add(
                                MedicationReminderEntity(
                                    id = 0,
                                    petId = 0,
                                    medicationName = mName,
                                    dosage = mDosage,
                                    frequency = freq,
                                    reminderTime = time,
                                    startDate = parseDate(startStr),
                                    isActive = isActive,
                                    notes = notes,
                                    prescriptionPhotoUri = prescriptionPhotoName
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "=== PARAZITA ELLENI KEZELÉSEK ===" -> {
                    if (line.startsWith("- Dátum:")) {
                        try {
                            val dateStr = line.substringAfter("Dátum:").substringBefore("|").trim()
                            val typePart = line.substringAfter("Típus:").trim()
                            val protectionType = typePart.substringBefore("(").trim()
                            val treatmentMethod = typePart.substringAfter("(").substringBefore(")").trim()
                            
                            var pName = ""
                            var durationDays = 30
                            var nextDueTimestamp = 0L
                            
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Készítmény:")) {
                                i++
                                val l2 = lines[i].trim()
                                pName = l2.substringAfter("Készítmény:").substringBefore("|").trim()
                                durationDays = l2.substringAfter("Tartósság:").substringBefore("nap").trim().toIntOrNull() ?: 30
                            }
                            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("Következő kezelés esedékessége:")) {
                                i++
                                val l3 = lines[i].trim()
                                val nextDueStr = l3.substringAfter("Következő kezelés esedékessége:").trim()
                                nextDueTimestamp = parseDate(nextDueStr)
                            }
                            
                            parasitesList.add(
                                ParasiteEntity(
                                    id = 0,
                                    petId = 0,
                                    protectionType = protectionType,
                                    treatmentMethod = treatmentMethod,
                                    productName = pName,
                                    timestamp = parseDate(dateStr),
                                    durationDays = durationDays,
                                    nextDueTimestamp = nextDueTimestamp
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            i++
        }

        if (name.isEmpty()) return

        var photoUri: String? = null
        val profilePicFile = folder.listFiles()?.find { it.name == "profilkep_$name.jpg" }
        if (profilePicFile != null && profilePicFile.exists()) {
            photoUri = copyFileToInternalStorage(context, profilePicFile, "profile")
        }

        val petEntity = PetEntity(
            id = 0,
            name = name,
            type = type,
            breed = breed,
            color = color,
            gender = gender,
            age = age,
            birthDate = birthDate,
            isNeutered = isNeutered,
            chipNumber = chipNumber,
            photoUri = photoUri,
            allergies = allergies,
            chronicDiseases = chronicDiseases,
            vetName = vetName,
            vetPhone = vetPhone,
            insuranceCompany = insuranceCompany,
            insurancePolicyNumber = insurancePolicyNumber
        )
        
        val newPetId = repository.insertPet(petEntity).toInt()
        if (newPetId <= 0) return

        vaccinationsList.forEach { v ->
            repository.insertVaccination(v.copy(petId = newPetId))
        }

        medicationsList.forEach { m ->
            val originalImageName = m.prescriptionPhotoUri
            var finalPrescriptionUri: String? = null
            if (originalImageName != null) {
                val imageFile = File(folder, originalImageName)
                if (imageFile.exists()) {
                    finalPrescriptionUri = copyFileToInternalStorage(context, imageFile, "prescription")
                }
            }
            repository.insertMedicationReminder(m.copy(petId = newPetId, prescriptionPhotoUri = finalPrescriptionUri))
        }

        parasitesList.forEach { p ->
            repository.insertParasite(p.copy(petId = newPetId))
        }

        val weightsFile = File(folder, "suly_elozmenyek.csv")
        if (weightsFile.exists()) {
            try {
                val weightLines = weightsFile.readLines()
                if (weightLines.size > 1) {
                    for (idx in 1 until weightLines.size) {
                        val wLine = weightLines[idx].trim()
                        if (wLine.isEmpty()) continue
                        val parts = parseCsvLine(wLine)
                        if (parts.size >= 2) {
                            val dateStr = parts[0].trim()
                            val weightKg = parts[1].trim().toDoubleOrNull() ?: 0.0
                            repository.insertWeight(
                                WeightEntity(
                                    id = 0,
                                    petId = newPetId,
                                    weightKg = weightKg,
                                    timestamp = parseDate(dateStr)
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val expensesFile = File(folder, "koltsegvetes_es_kiadasok.csv")
        if (expensesFile.exists()) {
            try {
                val expLines = expensesFile.readLines()
                if (expLines.size > 1) {
                    for (idx in 1 until expLines.size) {
                        val eLine = expLines[idx].trim()
                        if (eLine.isEmpty()) continue
                        val parts = parseCsvLine(eLine)
                        if (parts.size >= 8) {
                            val dateStr = parts[0].trim()
                            val category = parts[1].trim()
                            val subCategory = parts[2].trim().let { if (it.isEmpty() || it == "null") null else it }
                            val amount = parts[3].trim().toDoubleOrNull() ?: 0.0
                            val productName = parts[4].trim()
                            val purchaseLocation = parts[5].trim()
                            val isRecurring = parts[6].trim() == "Igen"
                            val receiptFileName = parts[7].trim().let { if (it == "Nincs" || it.isEmpty()) null else it }
                            
                            val desc = if (purchaseLocation.isNotBlank()) "$productName|||$purchaseLocation" else productName
                            
                            var finalReceiptUri: String? = null
                            if (receiptFileName != null) {
                                val receiptImgFile = File(folder, receiptFileName)
                                if (receiptImgFile.exists()) {
                                    finalReceiptUri = copyFileToInternalStorage(context, receiptImgFile, "receipt")
                                }
                            }
                            
                            repository.insertExpense(
                                ExpenseEntity(
                                    id = 0,
                                    petId = newPetId,
                                    amount = amount,
                                    category = category,
                                    subCategory = subCategory,
                                    description = desc,
                                    timestamp = parseDate(dateStr),
                                    isRecurring = isRecurring,
                                    recurringIntervalMonths = if (isRecurring) 1 else 0,
                                    imageUri = finalReceiptUri
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun copyFileToInternalStorage(context: Context, srcFile: File, prefix: String): String {
        return try {
            val dir = File(context.filesDir, "pet_media")
            if (!dir.exists()) dir.mkdirs()
            val extension = srcFile.extension.ifEmpty { "jpg" }
            val destFile = File(dir, "${prefix}_${System.currentTimeMillis()}_${(1000..9999).random()}.$extension")
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        val clean = dateStr.trim().removeSuffix(".")
        val formats = listOf("yyyy.MM.dd", "yyyy-MM-dd", "yyyy.MM.dd.")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.getDefault())
                return sdf.parse(clean)?.time ?: continue
            } catch (e: Exception) {}
        }
        return System.currentTimeMillis()
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

class PetViewModelFactory(private val repository: PetRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PetViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
