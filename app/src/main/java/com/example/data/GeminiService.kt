package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitInstance {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }
}

fun Bitmap.resizeWithLimit(maxDimension: Int = 1024): Bitmap {
    if (this.width <= maxDimension && this.height <= maxDimension) {
        return this
    }
    val aspectRatio = this.width.toFloat() / this.height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (this.width > this.height) {
        newWidth = maxDimension
        newHeight = (maxDimension / aspectRatio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * aspectRatio).toInt()
    }
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

fun extractJsonFromResponse(rawText: String): String {
    val trimmed = rawText.trim()
    
    // Try to strip markdown code block wrapper if present
    var clean = trimmed
    if (clean.startsWith("```json")) {
        clean = clean.removePrefix("```json").trim()
    } else if (clean.startsWith("```")) {
        clean = clean.removePrefix("```").trim()
    }
    if (clean.endsWith("```")) {
        clean = clean.removeSuffix("```").trim()
    }
    
    val firstCurly = clean.indexOf('{')
    val lastCurly = clean.lastIndexOf('}')
    val firstSquare = clean.indexOf('[')
    val lastSquare = clean.lastIndexOf(']')
    
    val hasCurly = firstCurly != -1 && lastCurly != -1 && lastCurly > firstCurly
    val hasSquare = firstSquare != -1 && lastSquare != -1 && lastSquare > firstSquare
    
    return when {
        hasCurly && hasSquare -> {
            if (firstCurly < firstSquare) {
                clean.substring(firstCurly, lastCurly + 1)
            } else {
                clean.substring(firstSquare, lastSquare + 1)
            }
        }
        hasCurly -> clean.substring(firstCurly, lastCurly + 1)
        hasSquare -> clean.substring(firstSquare, lastSquare + 1)
        else -> clean
    }
}

suspend fun <T> retryWithDelay(
    times: Int = 3,
    initialDelayMillis: Long = 1000,
    maxDelayMillis: Long = 4000,
    factor: Double = 2.0,
    block: suspend () -> T?
): T? {
    var currentDelay = initialDelayMillis
    repeat(times - 1) { attempt ->
        try {
            val result = block()
            if (result != null) return result
        } catch (e: Exception) {
            Log.e("GeminiRetry", "Attempt ${attempt + 1} failed: ${e.message}", e)
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
    }
    return try {
        block()
    } catch (e: Exception) {
        Log.e("GeminiRetry", "Final attempt failed: ${e.message}", e)
        null
    }
}

@JsonClass(generateAdapter = true)
data class OcrResult(
    val name: String,
    val date: String,
    val serialNumber: String,
    val diseasePrevention: String? = null
)

@JsonClass(generateAdapter = true)
data class ReceiptItem(
    val name: String,
    val price: Double
)

@JsonClass(generateAdapter = true)
data class ReceiptScanResult(
    val shopName: String,
    val date: String,
    val items: List<ReceiptItem>
)

class GeminiReceiptScanner {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val receiptResultAdapter = moshi.adapter(ReceiptScanResult::class.java)

    suspend fun scanReceiptImage(bitmap: Bitmap): ReceiptScanResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiReceiptScanner", "API Key is missing or default placeholder!")
            return null
        }

        // Downscale image to limit payload size and memory usage, making uploads fast even on slow mobile data
        val resizedBitmap = bitmap.resizeWithLimit(1024)

        // Convert Bitmap to Base64
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Kérlek, elemezd ezt a fényképet, ami egy vásárlási nyugtát (blokkot) ábrázol. A kép lehet álló, fekvő (fektetve) vagy bármilyen irányba elforgatott helyzetű is.
            Ha a kép fekvő vagy elforgatott, virtuálisan forgasd el (90, 180 vagy 270 fokkal), vagy olvasd le a szöveget és az adatokat oldalra fordítva, fejjel lefelé vagy fektetve is! Mindenképp fejtsd meg és értelmezd a szöveget, bármilyen tájolású is a kép!
            Keresd meg az alábbi adatokat a blokkon:
            1. Az üzlet/bolt nevét (pl. Fressnapf, Spar, Lidl, Tesco, DM, Rossmann, állatpatika stb.) -> "shopName"
            2. A vásárlás dátumát (pl. 2026.06.25 vagy 2026-06-25, formázd meg YYYY-MM-DD alakba) -> "date"
            3. A vásárolt tételeket és azok árait (csak a termékek nevét és az árukat számként, pl. 14500) -> "items"

            A válaszodat KIZÁRÓLAG egy érvényes JSON formátumban add meg, az alábbi struktúrával (ha valamit nem találsz, üres stringet vagy üres listát adj meg):
            {
              "shopName": "üzlet neve",
              "date": "YYYY-MM-DD",
              "items": [
                {
                  "name": "tétel vagy termék neve",
                  "price": 1250.0
                }
              ]
            }
            Ne írj semmilyen egyéb bevezetőt vagy magyarázatot, csak a tiszta JSON-t!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a specialized receipt OCR assistant that returns only valid JSON matching the requested structure."))
            )
        )

        // Retry with exponential delay to survive transient network issues or cell network handovers
        return retryWithDelay(times = 3, initialDelayMillis = 1000) {
            try {
                val response = RetrofitInstance.api.generateContent(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText != null) {
                    Log.d("GeminiReceiptScanner", "Raw response: $rawText")
                    val cleanJson = extractJsonFromResponse(rawText)
                    receiptResultAdapter.fromJson(cleanJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("GeminiReceiptScanner", "Attempt failed: ${e.message}", e)
                null
            }
        }
    }
}

class GeminiOcrScanner {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val ocrResultAdapter = moshi.adapter(OcrResult::class.java)

    suspend fun scanVaccinationImage(bitmap: Bitmap): OcrResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiOcrScanner", "API Key is missing or default placeholder!")
            return null
        }

        // Downscale image to limit payload size and memory usage
        val resizedBitmap = bitmap.resizeWithLimit(1024)

        // Convert Bitmap to Base64
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val prompt = """
            Kérlek, elemezd ezt a képet, ami egy háziállat oltási könyvének matricáját vagy pecsétjét tartalmazza. A kép lehet álló, fekvő (fektetve) vagy bármilyen irányba elforgatott helyzetű is.
            Ha a kép fekvő vagy elforgatott, virtuálisan forgasd el (90, 180 vagy 270 fokkal), vagy olvasd le a szöveget és az adatokat oldalra fordítva, fejjel lefelé vagy fektetve is! Mindenképp fejtsd meg és értelmezd a szöveget, bármilyen tájolású is a kép!
            Keresd meg az oltóanyag nevét (pl. Nobivac, Rabisin, Eurican, Versican, Vanguard), az oltás dátumát (pl. 2026.06.25 vagy 2026-06-25), a gyártási számot (batch / tétel / serial number), és határozd meg vagy keresd meg, hogy MIRE VALÓ az oltás (pl. veszettség, kombinált (parvovírus, szopornyica, adenovírus), macskanátha, leukózis, Lyme-kór, stb. - amit a matrica vagy a szöveg jelez).
            A válaszodat KIZÁRÓLAG egy érvényes JSON formátumban add meg, az alábbi kulcsokkal (ha valamit nem találsz, üres stringgel térj vissza):
            {
              "name": "oltóanyag neve",
              "date": "YYYY-MM-DD formátumú dátum",
              "serialNumber": "gyártási vagy tétel szám",
              "diseasePrevention": "Mire való (pl. Veszettség elleni védőoltás vagy Kombinált védőoltás macskanátha és herpesvírus ellen)"
            }
            Ne írj semmilyen egyéb bevezetőt vagy magyarázatot, csak a tiszta JSON-t!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Data))
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a specialized medical OCR assistant that returns only valid JSON matching the requested structure."))
            )
        )

        // Retry with exponential delay to survive transient network issues
        return retryWithDelay(times = 3, initialDelayMillis = 1000) {
            try {
                val response = RetrofitInstance.api.generateContent(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText != null) {
                    Log.d("GeminiOcrScanner", "Raw response: $rawText")
                    val cleanJson = extractJsonFromResponse(rawText)
                    ocrResultAdapter.fromJson(cleanJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("GeminiOcrScanner", "Attempt failed: ${e.message}", e)
                null
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class SymptomAnalysisResult(
    val urgency: String, // "Kritikus" | "Magas" | "Közepes" | "Alacsony"
    val explanation: String, // Magyar nyelvű részletes magyarázat
    val checklist: List<String>, // Miket ellenőrizz le (pl. íny színe, testhőmérséklet, légzés)
    val doActions: List<String>, // Mit tegyél meg azonnal
    val dontActions: List<String> // Mit NE tegyél semmiképp
)

class GeminiSymptomChecker {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val symptomAdapter = moshi.adapter(SymptomAnalysisResult::class.java)

    suspend fun analyzeSymptom(petType: String, petAge: String, symptomDescription: String): SymptomAnalysisResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiSymptomChecker", "API Key is missing or default placeholder!")
            return null
        }

        val prompt = """
            Elemezd az alábbi háziállat tüneteit és adj sürgősségi/elsősegély tanácsot:
            Állat fajtája: ${petType}
            Állat kora: ${petAge}
            Tünetek leírása: ${symptomDescription}

            Kérlek, határozd meg a veszélyességi szintet az alábbiak közül (magyarul):
            - "Kritikus" (Életveszély, azonnali ügyelet szükséges)
            - "Magas" (Sürgős, állatorvoshoz kell fordulni mielőbb)
            - "Közepes" (Nem azonnal életveszélyes, de orvosi vizsgálatot igényel 24-48 órán belül)
            - "Alacsony" (Otthon is megfigyelhető, enyhe tünet)

            Add meg a válaszodat kizárólag érvényes JSON formátumban, az alábbi struktúrával (magyar nyelven):
            {
              "urgency": "Kritikus" vagy "Magas" vagy "Közepes" vagy "Alacsony",
              "explanation": "Rövid, megnyugtató, de tárgyilagos magyarázat a lehetséges okokról.",
              "checklist": [
                "Ezt ellenőrizd rajta: pl. ínyének színe",
                "Ezt is ellenőrizd..."
              ],
              "doActions": [
                "Teendő 1...",
                "Teendő 2..."
              ],
              "dontActions": [
                "Tiltott dolog 1...",
                "Tiltott dolog 2..."
              ]
            }

            Ne írj bevezetőt, markdown magyarázatot, csak a tiszta JSON kódot küldd vissza!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are an expert veterinary triage assistant that analyzes pet symptoms and returns structured first aid triage JSON."))
            )
        )

        return retryWithDelay(times = 3, initialDelayMillis = 1000) {
            try {
                val response = RetrofitInstance.api.generateContent(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText != null) {
                    Log.d("GeminiSymptomChecker", "Raw response: $rawText")
                    val cleanJson = extractJsonFromResponse(rawText)
                    symptomAdapter.fromJson(cleanJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("GeminiSymptomChecker", "Attempt failed: ${e.message}", e)
                null
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class PetBreedAiInfo(
    val recommendedVaccinations: List<String>,
    val toxicFoods: List<String>,
    val careTips: List<String>,
    val breedCharacteristics: String
)

class GeminiBreedAdvisor {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(PetBreedAiInfo::class.java)

    suspend fun getBreedInfo(petType: String, breed: String): PetBreedAiInfo? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiBreedAdvisor", "API Key is missing or default placeholder!")
            return null
        }

        val prompt = """
            Kérlek, adj szakértő állatorvosi tanácsot és információkat az alábbi háziállathoz:
            Állat faja/típusa: $petType
            Fajtája: $breed

            Kérlek, gyűjtsd össze az alábbi adatokat magyar nyelven:
            1. Ajánlott védőoltások listája a fajnak/fajtának megfelelően -> "recommendedVaccinations"
            2. Etetésnél kiemelten mérgező vagy kerülendő anyagok, élelmiszerek listája -> "toxicFoods"
            3. Általános gondozási tippek (pl. mozgásigény, fésülés, speciális odafigyelést igénylő dolgok) -> "careTips"
            4. Rövid összefoglaló a fajta/faj főbb jellemzőiről, személyiségéről -> "breedCharacteristics"

            A válaszodat KIZÁRÓLAG érvényes JSON formátumban add meg, az alábbi struktúrával:
            {
              "recommendedVaccinations": [
                "Oltás neve (mi ellen véd, mikor ajánlott)",
                "Másik oltás..."
              ],
              "toxicFoods": [
                "Étel neve (miért veszélyes)",
                "Másik étel..."
              ],
              "careTips": [
                "Gondozási tanács...",
                "Másik gondozási tanács..."
              ],
              "breedCharacteristics": "Rövid, barátságos összefoglaló a fajtáról magyarul."
            }

            Ne írj semmilyen egyéb szöveget, markdown kódblokkokon kívüli bevezetőt, csak a tiszta JSON formátumot küldd vissza!
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a specialized veterinary research assistant that provides pet-specific and breed-specific health, vaccination, feeding, and care advice in JSON."))
            )
        )

        return retryWithDelay(times = 3, initialDelayMillis = 1000) {
            try {
                val response = RetrofitInstance.api.generateContent(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (rawText != null) {
                    Log.d("GeminiBreedAdvisor", "Raw response: $rawText")
                    val cleanJson = extractJsonFromResponse(rawText)
                    adapter.fromJson(cleanJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("GeminiBreedAdvisor", "Attempt failed: ${e.message}", e)
                null
            }
        }
    }
}


