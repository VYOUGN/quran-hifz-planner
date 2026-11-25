package com.sallo.hifz.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY createdAt")
    fun plansFlow(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY createdAt")
    suspend fun plans(): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE id = :id")
    fun planFlow(id: Long): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun plan(id: Long): PlanEntity?

    @Query("SELECT * FROM sessions WHERE planId = :planId ORDER BY idx")
    fun sessionsFlow(planId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE planId = :planId ORDER BY idx")
    suspend fun sessions(planId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY planId, idx")
    fun allSessionsFlow(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Update
    suspend fun updatePlan(plan: PlanEntity)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE planId = :planId AND idx >= :fromIdx")
    suspend fun deleteSessionsFrom(planId: Long, fromIdx: Int)
}
