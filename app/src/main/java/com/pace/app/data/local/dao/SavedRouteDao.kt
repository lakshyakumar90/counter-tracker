package com.pace.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.pace.app.data.local.entity.SavedRouteEntity
import com.pace.app.data.local.entity.SavedRoutePointEntity
import kotlinx.coroutines.flow.Flow

data class SavedRouteWithPoints(
    @Embedded val route: SavedRouteEntity,
    @Relation(parentColumn = "id", entityColumn = "route_id") val points: List<SavedRoutePointEntity>,
)

@Dao
interface SavedRouteDao {
    @Transaction
    @Query("SELECT * FROM saved_routes WHERE owner_id = :ownerId AND deleted_at_epoch_millis IS NULL ORDER BY updated_at_epoch_millis DESC, id ASC")
    fun observeRoutes(ownerId: String = "local"): Flow<List<SavedRouteWithPoints>>

    @Transaction
    @Query("SELECT * FROM saved_routes WHERE id = :id AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun getRoute(id: String, ownerId: String = "local"): SavedRouteWithPoints?

    @Transaction
    @Query("SELECT * FROM saved_routes WHERE id = :id AND owner_id = :ownerId")
    suspend fun getRouteIncludingDeleted(id: String, ownerId: String = "local"): SavedRouteWithPoints?

    @Query("SELECT * FROM saved_routes WHERE id = :id AND owner_id = :ownerId")
    suspend fun getIncludingDeleted(id: String, ownerId: String = "local"): SavedRouteEntity?

    @Upsert suspend fun upsert(route: SavedRouteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPoints(points: List<SavedRoutePointEntity>)
    @Query("DELETE FROM saved_route_points WHERE route_id = :routeId") suspend fun clearPoints(routeId: String)
    @Query("UPDATE saved_routes SET deleted_at_epoch_millis = :deletedAt, updated_at_epoch_millis = :deletedAt WHERE id = :routeId AND owner_id = :ownerId AND deleted_at_epoch_millis IS NULL")
    suspend fun markDeleted(routeId: String, deletedAt: Long, ownerId: String = "local"): Int
}
