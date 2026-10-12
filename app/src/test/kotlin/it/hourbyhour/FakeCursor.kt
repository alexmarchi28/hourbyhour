package it.hourbyhour

import android.database.Cursor
import java.lang.reflect.Proxy

/**
 * A cursor over [rows], for tests on the JVM. Like a query on the provider, it has only the
 * [columns] asked for: reading another column fails. Columns missing from a row are NULL.
 */
fun fakeCursor(columns: Array<String>, rows: List<Map<String, Any?>>): Cursor {
    for (row in rows) require(columns.toSet().containsAll(row.keys)) { "Not a column: ${row.keys - columns.toSet()}" }
    var position = -1
    return Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, args ->
        fun value() = rows[position][columns[args[0] as Int]]
        when (method.name) {
            "moveToNext" -> ++position < rows.size
            "getCount" -> rows.size
            "getColumnIndexOrThrow" -> columns.indexOf(args[0]).also { require(it >= 0) { "No column ${args[0]}" } }
            "isNull" -> value() == null
            // Like SQLite, NULL reads as 0 from number getters.
            "getString" -> value() as String?
            "getInt" -> (value() as Number?)?.toInt() ?: 0
            "getLong" -> (value() as Number?)?.toLong() ?: 0L
            "close" -> null
            else -> throw UnsupportedOperationException("Cursor.${method.name}")
        }
    } as Cursor
}
