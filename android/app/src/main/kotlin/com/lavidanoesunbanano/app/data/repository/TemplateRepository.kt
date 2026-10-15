package com.lavidanoesunbanano.app.data.repository

import com.lavidanoesunbanano.app.data.local.dao.TemplateMessageDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TemplateRepository @Inject constructor(
    private val messageDao: TemplateMessageDao,
) {
    fun observeMessageCount(): Flow<Int> = messageDao.observeCount()
}
