package com.lavidanoesunbanano.app.data.repository

import androidx.room.withTransaction
import com.lavidanoesunbanano.app.data.local.LaVidaDatabase
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.RulesDao
import com.lavidanoesunbanano.app.data.mapper.toDomain
import com.lavidanoesunbanano.app.data.mapper.toEntity
import com.lavidanoesunbanano.app.domain.model.AppInfo
import com.lavidanoesunbanano.app.domain.model.BlockRange
import com.lavidanoesunbanano.app.domain.model.Profile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Perfil y reglas del onboarding (apps bloqueadas y horarios). */
@Singleton
class ProfileRepository @Inject constructor(
    private val database: LaVidaDatabase,
    private val profileDao: ProfileDao,
    private val rulesDao: RulesDao,
) {
    fun observeProfile(): Flow<Profile?> = profileDao.observe().map { it?.toDomain() }

    suspend fun getProfile(): Profile? = profileDao.get()?.toDomain()

    fun observeBlockedApps(): Flow<List<AppInfo>> =
        rulesDao.observeBlockedApps().map { rows -> rows.map { it.toDomain() } }

    suspend fun getBlockedApps(): List<AppInfo> = rulesDao.getBlockedApps().map { it.toDomain() }

    fun observeRanges(): Flow<List<BlockRange>> = rulesDao.observeRanges().map { rows -> rows.map { it.toDomain() } }

    suspend fun getRanges(): List<BlockRange> = rulesDao.getRanges().map { it.toDomain() }

    /**
     * Guarda perfil y reglas de una vez. Devuelve si cambió un perfil que ya existía: las
     * reglas no cuentan, porque los mensajes usan `{app}` y no dependen del horario.
     */
    suspend fun saveOnboarding(profile: Profile, apps: List<AppInfo>, ranges: List<BlockRange>): Boolean =
        database.withTransaction {
            val before = profileDao.get()?.toDomain()
            profileDao.upsert(profile.toEntity())
            rulesDao.replaceRules(apps.map { it.toEntity() }, ranges.map { it.toEntity() })
            before != null && before != profile
        }
}
