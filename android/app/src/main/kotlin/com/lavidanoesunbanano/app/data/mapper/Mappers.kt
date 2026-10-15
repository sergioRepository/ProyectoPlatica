package com.lavidanoesunbanano.app.data.mapper

import com.lavidanoesunbanano.app.data.local.entity.BlockRangeEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockedAppEntity
import com.lavidanoesunbanano.app.data.local.entity.ProfileEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.remote.dto.ProfileDto
import com.lavidanoesunbanano.app.data.remote.dto.TemplateResponseDto
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.time.DayOfWeek
import java.time.LocalTime

// Límites del backend (banano/schemas/profile.py): recortar aquí evita un 422.
private const val MAX_SCHEDULE_SUMMARY = 300
private const val MAX_BLOCKED_APPS = 60
private const val MAX_APP_LABEL = 120

private val KNOWN_CONTEXTS = setOf("general", "reincidente", "noche", "mañana", "tras_rechazo")

fun ProfileEntity.toDomain() = Profile(
    name = name,
    mainGoal = mainGoal,
    secondaryGoals = secondaryGoals,
    goalReason = goalReason,
    typicalExcuses = typicalExcuses,
    sarcasmLevel = sarcasmLevel,
    profanityAllowed = profanityAllowed,
    country = country,
    forbiddenTopics = forbiddenTopics,
)

fun Profile.toEntity() = ProfileEntity(
    name = name,
    mainGoal = mainGoal,
    secondaryGoals = secondaryGoals,
    goalReason = goalReason,
    typicalExcuses = typicalExcuses,
    sarcasmLevel = sarcasmLevel,
    profanityAllowed = profanityAllowed,
    country = country,
    forbiddenTopics = forbiddenTopics,
)

fun BlockedAppEntity.toDomain() = AppInfo(packageName = packageName, label = label)

fun AppInfo.toEntity() = BlockedAppEntity(packageName = packageName, label = label)

fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

fun BlockRange.toEntity() = BlockRangeEntity(
    daysMask = days.toMask(),
    startMinute = start.hour * 60 + start.minute,
    endMinute = end.hour * 60 + end.minute,
)

fun BlockRangeEntity.toDomain() = BlockRange(
    days = daysMask.toDays(),
    start = LocalTime.of(startMinute / 60, startMinute % 60),
    end = LocalTime.of(endMinute / 60, endMinute % 60),
)

fun Profile.toDto(apps: List<AppInfo>, ranges: List<BlockRange>) = ProfileDto(
    name = name,
    mainGoal = mainGoal,
    secondaryGoals = secondaryGoals,
    goalReason = goalReason,
    blockedApps = apps.take(MAX_BLOCKED_APPS).map { it.label.take(MAX_APP_LABEL) },
    scheduleSummary = ScheduleFormatter.summary(ranges).take(MAX_SCHEDULE_SUMMARY),
    typicalExcuses = typicalExcuses,
    sarcasmLevel = sarcasmLevel,
    profanityAllowed = profanityAllowed,
    country = country,
    forbiddenTopics = forbiddenTopics,
)

data class TemplateRows(
    val messages: List<TemplateMessageEntity>,
    val verdictLines: List<VerdictLineEntity>,
    val meta: TemplateMetaEntity,
)

fun TemplateResponseDto.toRows(generatedAt: Long) = TemplateRows(
    messages = messages.map {
        TemplateMessageEntity(
            id = it.id,
            text = it.text,
            context = if (it.context in KNOWN_CONTEXTS) it.context else "general",
            intensity = it.intensity.coerceIn(1, 5),
        )
    },
    verdictLines = verdictLines.approved.map { VerdictLineEntity(kind = VerdictLineEntity.APPROVED, text = it) } +
        verdictLines.rejected.map { VerdictLineEntity(kind = VerdictLineEntity.REJECTED, text = it) },
    meta = TemplateMetaEntity(generatedAt = generatedAt, profileSummary = profileSummary),
)
