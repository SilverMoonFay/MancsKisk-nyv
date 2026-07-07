package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // Kutya, Macska, Nyúl, Madár, Egyéb
    val breed: String,
    val color: String,
    val gender: String, // Kan / Szuka or Hím / Nőstény
    val age: String,
    val isNeutered: Boolean,
    val chipNumber: String,
    val photoUri: String? = null,
    val allergies: String, // Csirke, por, etc.
    val chronicDiseases: String, // Szívférgesség, etc.
    val vetName: String,
    val vetPhone: String,
    val insuranceCompany: String,
    val insurancePolicyNumber: String,
    val birthDate: String? = "",
    val chipImplantDate: String? = null,
    val chipExpiryDate: String? = null
)

@Entity(tableName = "weight_records")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val weightKg: Double,
    val timestamp: Long,
    val shoulderHeightCm: Double? = null,     // marmagasság
    val bodyLengthCm: Double? = null,         // testhossz
    val chestCircumferenceCm: Double? = null,  // mellbőség
    val neckCircumferenceCm: Double? = null    // nyakbőség
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val amount: Double,
    val category: String, // Egészségügy, Szolgáltatások, Oltás, Játék, Étel, Jutifali, Kiegészítők, Egyéb
    val subCategory: String? = null, // Orvosi vizitdíj, Gyógyszer/Parazitavédelem, Műtét, Kutyakozmetikus, Panzió/Sétáltatás, Kiképzés/Suli
    val description: String,
    val timestamp: Long,
    val isRecurring: Boolean = false,
    val recurringIntervalMonths: Int = 0, // Pl. 1 = havonta
    val imageUri: String? = null,
    val targetPetIds: String? = null
)

@Entity(tableName = "vaccinations")
data class VaccinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val name: String,
    val timestamp: Long,
    val serialNumber: String? = null,
    val veterinarian: String? = null,
    val notes: String? = null,
    val nextDueTimestamp: Long? = null,
    val isMandatory: Boolean = false,
    val diseasePrevention: String? = null
)

@Entity(tableName = "parasite_protections")
data class ParasiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val protectionType: String, // Kullancs/Bolha/Szívféreg, Féreghajtás
    val treatmentMethod: String, // Tabletta, Csepp (Spot-on), Nyakörv
    val productName: String,
    val timestamp: Long,
    val durationDays: Int, // 28, 84, 180, etc.
    val nextDueTimestamp: Long
)

@Entity(tableName = "daily_routines")
data class DailyRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val actionType: String, // Reggeli, Ebéd, Vacsora, Séta, Gyógyszer, Játék, Fésülés, Egyéb
    val loggedBy: String, // Anya, Apa, Gyerek, Kutyaszitter
    val timestamp: Long
)

@Entity(tableName = "budget_limits")
data class BudgetLimitEntity(
    @PrimaryKey val category: String, // Kategória név (pl. Jutifali, Étel, Szolgáltatások)
    val limitAmount: Double,
    val isEnabled: Boolean = true
)

@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val name: String,
    val description: String
)

@Entity(tableName = "medication_reminders")
data class MedicationReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val petId: Int,
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val reminderTime: String,
    val startDate: Long,
    val isActive: Boolean = true,
    val notes: String = "",
    val prescriptionPhotoUri: String? = null
)

