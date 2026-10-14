package app.bumboard

import android.database.sqlite.SQLiteDatabase
import app.bumboard.core.Sql

/** [Sql] over Android's SQLite, for app.bumboard.core.ReadingsDb. */
class AndroidSql(private val db: SQLiteDatabase) : Sql {
    override fun <T> query(sql: String, args: Array<String>, row: (Sql.Row) -> T): List<T> {
        db.rawQuery(sql, args).use { c ->
            val r = object : Sql.Row {
                override fun string(i: Int): String? = if (c.isNull(i)) null else c.getString(i)
                override fun long(i: Int): Long = c.getLong(i)
            }
            val out = ArrayList<T>(c.count)
            while (c.moveToNext()) out += row(r)
            return out
        }
    }
}
