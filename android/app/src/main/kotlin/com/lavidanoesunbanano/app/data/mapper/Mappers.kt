package com.lavidanoesunbanano.app.data.mapper

import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockRangeEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockedAppEntity
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.ProfileEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.remote.dto.ProfileDto
import com.lavidanoesunbanano.app.data.remote.dto.TemplateResponseDto
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.AttemptOutcome
import com.lavidanoesunbanano.app.domain.model.AttemptRecord
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.ExcuseRecord
import com.lavidanoesunbanano.app.domain.model.ExcuseType
import com.lavidanoesunbanano.app.domain.model.MessageContext
import com.lavidanoesunbanano.app.domain.model.Profile
import com.lavidanoesunbanano.app.domain.model.TemplateMessage
import com.lavidanoesunbanano.app.domain.model.Verdict
import com.lavidanoesunbanano.app.domain.model.VerdictReason
import com.lavidanoesunbanano.app.domain.schedule.ScheduleFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// Límites del backend (banano/schemas/profile.py): recortar aquí evita un 422.
private const val MAX_SCHEDULE_SUMMARY = 300
private const val MAX_BLOCKED_APPS = 60
private const val MAX_APP_LABEL = 120

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

fun TemplateMessageEntity.toDomain() = TemplateMessage(
    id = id,
    text = text,
    context = MessageContext.fromWire(context),
    intensity = intensity,
)

fun AttemptEntity.toRecord() = AttemptRecord(
    id = id,
    app = AppInfo(packageName, appLabel),
    startedAt = Instant.ofEpochMilli(startedAt),
    outcome = AttemptOutcome.fromWire(outcome),
)

fun ExcuseEntity.toRecord() = ExcuseRecord(
    id = id,
    attemptId = attemptId,
    app = AppInfo(packageName, appLabel),
    type = ExcuseType.fromWire(type),
    reason = reason,
    decidedAt = Instant.ofEpochMilli(decidedAt),
    verdict = Verdict(
        valid = valid,
        grantedMinutes = grantedMinutes,
        message = message,
        reason = VerdictReason.fromWire(reasonCode),
        confidence = confidence,
        offline = offline,
    ),
    unlockedUntil = unlockedUntil?.let(Instant::ofEpochMilli),
    needsReview = needsReview,
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
            context = MessageContext.fromWire(it.context).wire,
            intensity = it.intensity.coerceIn(1, 5),
        )
    },
    verdictLines = verdictLines.approved.map { VerdictLineEntity(kind = VerdictLineEntity.APPROVED, text = it) } +
        verdictLines.rejected.map { VerdictLineEntity(kind = VerdictLineEntity.REJECTED, text = it) },
    meta = TemplateMetaEntity(generatedAt = generatedAt, profileSummary = profileSummary),
)
