package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    // --- PETS ---
    @Query("SELECT * FROM pets ORDER BY name ASC")
    fun getAllPets(): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE id = :id LIMIT 1")
    fun getPetById(id: Int): Flow<PetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPet(pet: PetEntity): Long

    @Update
    suspend fun updatePet(pet: PetEntity)

    @Delete
    suspend fun deletePet(pet: PetEntity)

    @Query("DELETE FROM pets WHERE name IN ('Bodza', 'Cirmi')")
    suspend fun deleteLegacyPets()

    // --- WEIGHTS ---
    @Query("SELECT * FROM weight_records WHERE petId = :petId ORDER BY timestamp ASC")
    fun getWeightsForPet(petId: Int): Flow<List<WeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: WeightEntity)

    @Query("DELETE FROM weight_records WHERE id = :id")
    suspend fun deleteWeightById(id: Int)

    // --- EXPENSES ---
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE petId = :petId ORDER BY timestamp DESC")
    fun getExpensesForPet(petId: Int): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    // --- VACCINATIONS ---
    @Query("SELECT * FROM vaccinations WHERE petId = :petId ORDER BY timestamp DESC")
    fun getVaccinationsForPet(petId: Int): Flow<List<VaccinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(vaccination: VaccinationEntity)

    @Query("DELETE FROM vaccinations WHERE id = :id")
    suspend fun deleteVaccinationById(id: Int)

    // --- PARASITES ---
    @Query("SELECT * FROM parasite_protections WHERE petId = :petId ORDER BY timestamp DESC")
    fun getParasiteProtectionsForPet(petId: Int): Flow<List<ParasiteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParasite(parasite: ParasiteEntity)

    @Query("DELETE FROM parasite_protections WHERE id = :id")
    suspend fun deleteParasiteById(id: Int)

    // --- DAILY ROUTINES ---
    @Query("SELECT * FROM daily_routines WHERE petId = :petId ORDER BY timestamp DESC")
    fun getDailyRoutinesForPet(petId: Int): Flow<List<DailyRoutineEntity>>

    @Query("SELECT * FROM daily_routines WHERE petId = :petId AND timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getDailyRoutinesToday(petId: Int, startOfDay: Long): Flow<List<DailyRoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyRoutine(routine: DailyRoutineEntity)

    @Query("DELETE FROM daily_routines WHERE id = :id")
    suspend fun deleteDailyRoutineById(id: Int)

    // --- BUDGET LIMITS ---
    @Query("SELECT * FROM budget_limits")
    fun getAllBudgetLimits(): Flow<List<BudgetLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(limit: BudgetLimitEntity)

    @Query("DELETE FROM budget_limits WHERE category = :category")
    suspend fun deleteBudgetLimitByCategory(category: String)

    // --- ROUTINE TEMPLATES ---
    @Query("SELECT * FROM routine_templates WHERE petId = :petId")
    fun getRoutineTemplatesForPet(petId: Int): Flow<List<RoutineTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineTemplate(template: RoutineTemplateEntity)

    @Query("DELETE FROM routine_templates WHERE id = :id")
    suspend fun deleteRoutineTemplateById(id: Int)

    // --- MEDICATION REMINDERS ---
    @Query("SELECT * FROM medication_reminders WHERE petId = :petId ORDER BY id DESC")
    fun getMedicationRemindersForPet(petId: Int): Flow<List<MedicationReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationReminder(reminder: MedicationReminderEntity)

    @Query("DELETE FROM medication_reminders WHERE id = :id")
    suspend fun deleteMedicationReminderById(id: Int)

    // --- CLEAR TABLES FOR RESTORE ---
    @Query("DELETE FROM pets")
    suspend fun deleteAllPets()

    @Query("DELETE FROM weight_records")
    suspend fun deleteAllWeights()

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM vaccinations")
    suspend fun deleteAllVaccinations()

    @Query("DELETE FROM parasite_protections")
    suspend fun deleteAllParasites()

    @Query("DELETE FROM daily_routines")
    suspend fun deleteAllDailyRoutines()

    @Query("DELETE FROM budget_limits")
    suspend fun deleteAllBudgetLimits()

    @Query("DELETE FROM routine_templates")
    suspend fun deleteAllRoutineTemplates()

    @Query("DELETE FROM medication_reminders")
    suspend fun deleteAllMedications()
}
