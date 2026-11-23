package com.pace.app.data.local

import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.json.JSONArray

/** Writes a portable JSON snapshot without logging or retaining route data in memory. */
object LocalDataExporter {
    private val exportedTables = listOf(
        "activities", "track_points", "splits", "sensor_samples", "workout_templates",
        "workout_intervals", "planned_sessions", "goals", "personal_records",
        "saved_routes", "saved_route_points", "activity_aggregate_cache", "map_pack_metadata",
        "home_workout_drafts",
    )

    fun write(
        database: SupportSQLiteDatabase,
        output: OutputStream,
        exportedAtEpochMillis: Long = System.currentTimeMillis(),
        settings: JSONObject = JSONObject(),
        ownerId: String = "local",
        cloudAiUsage: JSONArray = JSONArray(),
    ) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
            writer.append("{\"format\":\"pace-local-export\",\"version\":1,\"exported_at_epoch_millis\":")
                .append(exportedAtEpochMillis.toString()).append(",\"settings\":")
                .append(settings.toString()).append(",\"ai_request_usage\":")
                .append(cloudAiUsage.toString()).append(",\"tables\":{")
            exportedTables.forEachIndexed { tableIndex, table ->
                if (tableIndex > 0) writer.append(',')
                writer.append(JSONObject.quote(table)).append(":")
                val (sql, args) = queryForOwner(table)
                database.query(sql, args(ownerId)).use { cursor -> writeRows(cursor, writer) }
            }
            writer.append("}}")
        }
    }

    private fun queryForOwner(table: String): Pair<String, (String) -> Array<Any?>> = when (table) {
        "activities", "workout_templates", "planned_sessions", "goals", "personal_records", "saved_routes" ->
            "SELECT * FROM `$table` WHERE owner_id = ?" to { owner -> arrayOf(owner) }
        "activity_aggregate_cache" ->
            "SELECT * FROM `activity_aggregate_cache` WHERE owner_id = ?" to { owner -> arrayOf(owner) }
        "home_workout_drafts" ->
            "SELECT * FROM `home_workout_drafts` WHERE owner_id = ?" to { owner -> arrayOf(owner) }
        "track_points", "splits", "sensor_samples" ->
            "SELECT * FROM `$table` WHERE activity_id IN (SELECT id FROM activities WHERE owner_id = ?)" to { owner -> arrayOf(owner) }
        "workout_intervals" ->
            "SELECT * FROM `workout_intervals` WHERE template_id IN (SELECT id FROM workout_templates WHERE owner_id = ?)" to { owner -> arrayOf(owner) }
        "saved_route_points" ->
            "SELECT * FROM `saved_route_points` WHERE route_id IN (SELECT id FROM saved_routes WHERE owner_id = ?)" to { owner -> arrayOf(owner) }
        "map_pack_metadata" -> "SELECT * FROM `map_pack_metadata`" to { arrayOf<Any?>() }
        else -> error("Unsupported local export table.")
    }

    private fun writeRows(cursor: Cursor, writer: Appendable) {
        writer.append('[')
        var firstRow = true
        while (cursor.moveToNext()) {
            if (!firstRow) writer.append(',')
            firstRow = false
            writer.append('{')
            for (column in 0 until cursor.columnCount) {
                if (column > 0) writer.append(',')
                writer.append(JSONObject.quote(cursor.getColumnName(column))).append(':')
                when (cursor.getType(column)) {
                    Cursor.FIELD_TYPE_NULL -> writer.append("null")
                    Cursor.FIELD_TYPE_INTEGER, Cursor.FIELD_TYPE_FLOAT -> writer.append(cursor.getString(column))
                    Cursor.FIELD_TYPE_BLOB -> writer.append(JSONObject.quote(Base64.encodeToString(cursor.getBlob(column), Base64.NO_WRAP)))
                    else -> writer.append(JSONObject.quote(cursor.getString(column)))
                }
            }
            writer.append('}')
        }
        writer.append(']')
    }
}
