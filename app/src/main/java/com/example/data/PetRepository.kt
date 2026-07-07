package com.example.data

import kotlinx.coroutines.flow.Flow

class PetRepository(private val petDao: PetDao) {

    // --- PETS ---
    val allPets: Flow<List<PetEntity>> = petDao.getAllPets()

    fun getPetById(id: Int): Flow<PetEntity?> = petDao.getPetById(id)

    suspend fun insertPet(pet: PetEntity): Long = petDao.insertPet(pet)

    suspend fun updatePet(pet: PetEntity) = petDao.updatePet(pet)

    suspend fun deletePet(pet: PetEntity) = petDao.deletePet(pet)

    // --- WEIGHTS ---
    fun getWeightsForPet(petId: Int): Flow<List<WeightEntity>> = petDao.getWeightsForPet(petId)

    suspend fun insertWeight(weight: WeightEntity) = petDao.insertWeight(weight)

    suspend fun deleteWeightById(id: Int) = petDao.deleteWeightById(id)

    // --- EXPENSES ---
    val allExpenses: Flow<List<ExpenseEntity>> = petDao.getAllExpenses()

    fun getExpensesForPet(petId: Int): Flow<List<ExpenseEntity>> = petDao.getExpensesForPet(petId)

    suspend fun insertExpense(expense: ExpenseEntity) = petDao.insertExpense(expense)

    suspend fun deleteExpenseById(id: Int) = petDao.deleteExpenseById(id)

    // --- VACCINATIONS ---
    fun getVaccinationsForPet(petId: Int): Flow<List<VaccinationEntity>> = petDao.getVaccinationsForPet(petId)

    suspend fun insertVaccination(vaccination: VaccinationEntity) = petDao.insertVaccination(vaccination)

    suspend fun deleteVaccinationById(id: Int) = petDao.deleteVaccinationById(id)

    // --- PARASITES ---
    fun getParasiteProtectionsForPet(petId: Int): Flow<List<ParasiteEntity>> = petDao.getParasiteProtectionsForPet(petId)

    suspend fun insertParasite(parasite: ParasiteEntity) = petDao.insertParasite(parasite)

    suspend fun deleteParasiteById(id: Int) = petDao.deleteParasiteById(id)

    // --- DAILY ROUTINES ---
    fun getDailyRoutinesForPet(petId: Int): Flow<List<DailyRoutineEntity>> = petDao.getDailyRoutinesForPet(petId)

    fun getDailyRoutinesToday(petId: Int, startOfDay: Long): Flow<List<DailyRoutineEntity>> =
        petDao.getDailyRoutinesToday(petId, startOfDay)

    suspend fun insertDailyRoutine(routine: DailyRoutineEntity) = petDao.insertDailyRoutine(routine)

    suspend fun deleteDailyRoutineById(id: Int) = petDao.deleteDailyRoutineById(id)

    // --- BUDGET LIMITS ---
    val allBudgetLimits: Flow<List<BudgetLimitEntity>> = petDao.getAllBudgetLimits()

    suspend fun insertBudgetLimit(limit: BudgetLimitEntity) = petDao.insertBudgetLimit(limit)

    suspend fun deleteBudgetLimitByCategory(category: String) = petDao.deleteBudgetLimitByCategory(category)

    // --- ROUTINE TEMPLATES ---
    fun getRoutineTemplatesForPet(petId: Int): Flow<List<RoutineTemplateEntity>> = petDao.getRoutineTemplatesForPet(petId)

    suspend fun insertRoutineTemplate(template: RoutineTemplateEntity) = petDao.insertRoutineTemplate(template)

    suspend fun deleteRoutineTemplateById(id: Int) = petDao.deleteRoutineTemplateById(id)

    // --- MEDICATION REMINDERS ---
    fun getMedicationRemindersForPet(petId: Int): Flow<List<MedicationReminderEntity>> =
        petDao.getMedicationRemindersForPet(petId)

    suspend fun insertMedicationReminder(reminder: MedicationReminderEntity) =
        petDao.insertMedicationReminder(reminder)

    suspend fun deleteMedicationReminderById(id: Int) =
        petDao.deleteMedicationReminderById(id)

    // --- CLEAR TABLES FOR RESTORE ---
    suspend fun clearAllData() {
        petDao.deleteAllPets()
        petDao.deleteAllWeights()
        petDao.deleteAllExpenses()
        petDao.deleteAllVaccinations()
        petDao.deleteAllParasites()
        petDao.deleteAllDailyRoutines()
        petDao.deleteAllBudgetLimits()
        petDao.deleteAllRoutineTemplates()
        petDao.deleteAllMedications()
    }
}
