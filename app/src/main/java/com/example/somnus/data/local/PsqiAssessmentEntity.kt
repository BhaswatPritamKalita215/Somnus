package com.example.somnus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.somnus.domain.model.PsqiAssessment
import com.example.somnus.domain.model.PsqiComponentScores
import com.example.somnus.domain.model.PsqiResponses
import com.example.somnus.domain.model.PsqiStatus
import org.json.JSONObject
import java.time.Instant

/**
 * Room database entity representing a monthly PSQI assessment.
 */
@Entity(
    tableName = "psqi_assessments",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["status"])
    ]
)
data class PsqiAssessmentEntity(
    @PrimaryKey
    val id: String,
    val startedAt: Long,
    val completedAt: Long?,
    val version: String,
    val language: String,
    val reportingPeriod: String,
    val status: String,
    val scoringVersion: String,
    val globalScore: Int?,
    val component1: Int?,
    val component2: Int?,
    val component3: Int?,
    val component4: Int?,
    val component5: Int?,
    val component6: Int?,
    val component7: Int?,
    val responsesJson: String,
    val notes: String?
) {
    fun toDomain(): PsqiAssessment {
        val parsedResponses = deserializeResponses(responsesJson)
        val componentScores = if (
            globalScore != null &&
            component1 != null && component2 != null && component3 != null &&
            component4 != null && component5 != null && component6 != null && component7 != null
        ) {
            try {
                PsqiComponentScores(
                    c1SubjectiveQuality = component1,
                    c2SleepLatency = component2,
                    c3SleepDuration = component3,
                    c4SleepEfficiency = component4,
                    c5SleepDisturbance = component5,
                    c6UseOfMedication = component6,
                    c7DaytimeDysfunction = component7,
                    globalScore = globalScore
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return PsqiAssessment(
            id = id,
            startedAt = Instant.ofEpochMilli(startedAt),
            completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
            version = version,
            language = language,
            reportingPeriod = reportingPeriod,
            status = try {
                PsqiStatus.valueOf(status)
            } catch (e: Exception) {
                PsqiStatus.INCOMPLETE
            },
            scoringVersion = scoringVersion,
            responses = parsedResponses,
            componentScores = componentScores,
            notes = notes
        )
    }

    companion object {
        fun fromDomain(assessment: PsqiAssessment): PsqiAssessmentEntity {
            val scores = assessment.componentScores
            return PsqiAssessmentEntity(
                id = assessment.id,
                startedAt = assessment.startedAt.toEpochMilli(),
                completedAt = assessment.completedAt?.toEpochMilli(),
                version = assessment.version,
                language = assessment.language,
                reportingPeriod = assessment.reportingPeriod,
                status = assessment.status.name,
                scoringVersion = assessment.scoringVersion,
                globalScore = scores?.globalScore,
                component1 = scores?.c1SubjectiveQuality,
                component2 = scores?.c2SleepLatency,
                component3 = scores?.c3SleepDuration,
                component4 = scores?.c4SleepEfficiency,
                component5 = scores?.c5SleepDisturbance,
                component6 = scores?.c6UseOfMedication,
                component7 = scores?.c7DaytimeDysfunction,
                responsesJson = serializeResponses(assessment.responses),
                notes = assessment.notes
            )
        }

        fun serializeResponses(responses: PsqiResponses): String {
            val obj = JSONObject()
            responses.bedTime?.let { obj.put("bedTime", it) }
            responses.latencyMinutes?.let { obj.put("latencyMinutes", it) }
            responses.wakeTime?.let { obj.put("wakeTime", it) }
            responses.actualSleepHours?.let { obj.put("actualSleepHours", it) }
            responses.q5a?.let { obj.put("q5a", it) }
            responses.q5b?.let { obj.put("q5b", it) }
            responses.q5c?.let { obj.put("q5c", it) }
            responses.q5d?.let { obj.put("q5d", it) }
            responses.q5e?.let { obj.put("q5e", it) }
            responses.q5f?.let { obj.put("q5f", it) }
            responses.q5g?.let { obj.put("q5g", it) }
            responses.q5h?.let { obj.put("q5h", it) }
            responses.q5i?.let { obj.put("q5i", it) }
            responses.q5j?.let { obj.put("q5j", it) }
            responses.q6?.let { obj.put("q6", it) }
            responses.q7?.let { obj.put("q7", it) }
            responses.q8?.let { obj.put("q8", it) }
            responses.q9?.let { obj.put("q9", it) }
            return obj.toString()
        }

        fun deserializeResponses(jsonStr: String): PsqiResponses {
            if (jsonStr.isBlank()) return PsqiResponses()
            return try {
                val obj = JSONObject(jsonStr)
                PsqiResponses(
                    bedTime = if (obj.has("bedTime")) obj.getString("bedTime") else null,
                    latencyMinutes = if (obj.has("latencyMinutes")) obj.getInt("latencyMinutes") else null,
                    wakeTime = if (obj.has("wakeTime")) obj.getString("wakeTime") else null,
                    actualSleepHours = if (obj.has("actualSleepHours")) obj.getDouble("actualSleepHours") else null,
                    q5a = if (obj.has("q5a")) obj.getInt("q5a") else null,
                    q5b = if (obj.has("q5b")) obj.getInt("q5b") else null,
                    q5c = if (obj.has("q5c")) obj.getInt("q5c") else null,
                    q5d = if (obj.has("q5d")) obj.getInt("q5d") else null,
                    q5e = if (obj.has("q5e")) obj.getInt("q5e") else null,
                    q5f = if (obj.has("q5f")) obj.getInt("q5f") else null,
                    q5g = if (obj.has("q5g")) obj.getInt("q5g") else null,
                    q5h = if (obj.has("q5h")) obj.getInt("q5h") else null,
                    q5i = if (obj.has("q5i")) obj.getInt("q5i") else null,
                    q5j = if (obj.has("q5j")) obj.getInt("q5j") else null,
                    q6 = if (obj.has("q6")) obj.getInt("q6") else null,
                    q7 = if (obj.has("q7")) obj.getInt("q7") else null,
                    q8 = if (obj.has("q8")) obj.getInt("q8") else null,
                    q9 = if (obj.has("q9")) obj.getInt("q9") else null
                )
            } catch (e: Exception) {
                PsqiResponses()
            }
        }
    }
}
