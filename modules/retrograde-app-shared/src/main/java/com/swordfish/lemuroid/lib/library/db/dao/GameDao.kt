/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games")
    suspend fun selectAll(): List<Game>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Query("SELECT * FROM games WHERE fileName = :fileName")
    suspend fun selectByFileName(fileName: String): List<Game>

    /** Games whose file name is [stem] plus an extension (LIKE wildcards in [stem] may add extra rows). */
    @Query("SELECT * FROM games WHERE fileName LIKE :stem || '.%'")
    suspend fun selectByFileStem(stem: String): List<Game>

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY COALESCE(customName, title) ASC")
    fun selectFavorites(): PagingSource<Int, Game>

    // Lemuroid-S: favorites are included, Recent is the full play history.
    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun asyncSelectFirstRecents(limit: Int): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    // Lemuroid-S: single-field updates, so a game object loaded earlier (e.g. in a list) can't
    // overwrite newer data such as play time or last played.
    @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(
        id: Int,
        isFavorite: Boolean,
    )

    @Query("UPDATE games SET customName = :customName WHERE id = :id")
    suspend fun updateCustomName(
        id: Int,
        customName: String?,
    )

    @Query("UPDATE games SET customCoverUri = :customCoverUri WHERE id = :id")
    suspend fun updateCustomCoverUri(
        id: Int,
        customCoverUri: String?,
    )

    /** Lemuroid-S: games with the most play time ("Frequently played"), favorites included. */
    @Query("SELECT * FROM games WHERE playTimeMillis > 0 ORDER BY playTimeMillis DESC LIMIT :limit")
    fun selectFirstMostPlayed(limit: Int): Flow<List<Game>>

    /** Records a finished session: last played now, plus its duration to the total play time. */
    @Query(
        """
        UPDATE games SET lastPlayedAt = :playedAt, playTimeMillis = playTimeMillis + :durationMillis WHERE id = :id
        """,
    )
    suspend fun recordSession(
        id: Int,
        playedAt: Long,
        durationMillis: Long,
    )

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL LIMIT :limit")
    fun selectFirstNotPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE systemId = :systemId ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystem(systemId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystems(systemIds: List<String>): PagingSource<Int, Game>

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(*) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)
