package com.lavidanoesunbanano.app.data.mapper

import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity
import com.lavidanoesunbanano.app.data.remote.dto.TemplateMessageDto
import com.lavidanoesunbanano.app.data.remote.dto.TemplateResponseDto
import com.lavidanoesunbanano.app.data.remote.dto.VerdictLinesDto
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.Profile
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `days mask round trips`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)

        assertEquals(0b1000101, days.toMask())
        assertEquals(days, days.toMask().toDays())
    }

    @Test
    fun `range entity round trips`() {
        val range = BlockRange(setOf(DayOfWeek.FRIDAY), LocalTime.of(22, 30), LocalTime.of(6, 15))

        assertEquals(range, range.toEntity().toDomain())
    }

    @Test
    fun `profile dto carries apps and schedule within backend limits`() {
        val profile = Profile("Alex", "programar", emptyList(), "", "", 3, false, "Colombia", "")
        val apps = (1..70).map { AppInfo("p$it", "App $it") }
        val ranges = List(30) { BlockRange(DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(6, 0)) }

        val dto = profile.toDto(apps, ranges)

        assertEquals(60, dto.blockedApps.size)
        assertEquals("App 1", dto.blockedApps.first())
        assertEquals(300, dto.scheduleSummary.length)
    }

    @Test
    fun `template rows keep known contexts and clamp intensity`() {
        val response = TemplateResponseDto(
            messages = listOf(
                TemplateMessageDto("m01", "uno", "noche", 9),
                TemplateMessageDto("m02", "dos", "inventado", 0),
            ),
            verdictLines = VerdictLinesDto(approved = listOf("sí"), rejected = listOf("no", "nunca")),
            profileSummary = "Resumen",
        )

        val rows = response.toRows(generatedAt = 1_000)

        assertEquals(listOf("noche", "general"), rows.messages.map { it.context })
        assertEquals(listOf(5, 1), rows.messages.map { it.intensity })
        assertEquals(1, rows.verdictLines.count { it.kind == VerdictLineEntity.APPROVED })
        assertEquals(2, rows.verdictLines.count { it.kind == VerdictLineEntity.REJECTED })
        assertEquals("Resumen", rows.meta.profileSummary)
        assertEquals(1_000, rows.meta.generatedAt)
    }
}
