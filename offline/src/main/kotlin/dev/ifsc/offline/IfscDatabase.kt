package dev.ifsc.offline

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.Locale

/**
 * Read-only access to the bundled offline branch database.
 *
 * The database contains every IFSC code from the upstream release, including
 * branch name, address, city, district, state, MICR, SWIFT, and the
 * UPI/RTGS/NEFT/IMPS flags.
 *
 * The first call to [open] copies the asset database into the app's private
 * database directory (about 41 MiB). Later calls reuse it. Use [openAsync] from
 * a coroutine to avoid doing that work on the main thread.
 *
 * ```
 * val db = IfscDatabase.openAsync(context)
 * val branch = db.lookupAsync("HDFC0000001")
 * db.close()
 * ```
 *
 * Call [close] only when no queries are in flight.
 *
 * No `INTERNET` permission is required.
 */
public class IfscDatabase private constructor(
    private val database: SQLiteDatabase,
) : Closeable {

    /** Upstream dataset version bundled with this build. */
    public val dataVersion: String
        get() = IfscOfflineData.VERSION

    /** Looks up a branch by its exact IFSC code. */
    public fun lookup(ifsc: String): Branch? {
        val normalized = ifsc.trim().uppercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        return database.rawQuery(
            "SELECT * FROM branches WHERE ifsc = ? LIMIT 1",
            arrayOf(normalized),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toBranch() else null
        }
    }

    /** Returns branches for a four-letter bank code, ordered by branch name. */
    public fun branchesOfBank(
        bankCode: String,
        limit: Int = 100,
        offset: Int = 0,
    ): List<Branch> {
        val normalized = bankCode.trim().uppercase(Locale.ROOT)
        if (normalized.length != 4) return emptyList()
        return database.rawQuery(
            """
            SELECT * FROM branches
            WHERE bank_code = ?
            ORDER BY branch
            LIMIT ? OFFSET ?
            """.trimIndent(),
            arrayOf(normalized, limit.coerceIn(1, 1_000).toString(), offset.coerceAtLeast(0).toString()),
        ).use { cursor -> cursor.toBranches() }
    }

    /**
     * Searches IFSC code, branch name, city, district, and bank name.
     * Matching is case-insensitive for ASCII input.
     */
    public fun search(query: String, limit: Int = 20): List<Branch> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val pattern = "%${escapeLike(term)}%"
        return database.rawQuery(
            """
            SELECT * FROM branches
            WHERE ifsc LIKE ? ESCAPE '\'
               OR branch LIKE ? ESCAPE '\'
               OR city LIKE ? ESCAPE '\'
               OR district LIKE ? ESCAPE '\'
               OR bank LIKE ? ESCAPE '\'
            ORDER BY bank, branch
            LIMIT ?
            """.trimIndent(),
            arrayOf(
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                limit.coerceIn(1, 500).toString(),
            ),
        ).use { cursor -> cursor.toBranches() }
    }

    /** Number of branches in the database. */
    public fun countBranches(): Int {
        return database.rawQuery("SELECT COUNT(*) FROM branches", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Coroutine-friendly version of [lookup]. */
    public suspend fun lookupAsync(ifsc: String): Branch? =
        withContext(Dispatchers.IO) { lookup(ifsc) }

    /** Coroutine-friendly version of [branchesOfBank]. */
    public suspend fun branchesOfBankAsync(
        bankCode: String,
        limit: Int = 100,
        offset: Int = 0,
    ): List<Branch> = withContext(Dispatchers.IO) {
        branchesOfBank(bankCode, limit, offset)
    }

    /** Coroutine-friendly version of [search]. */
    public suspend fun searchAsync(query: String, limit: Int = 20): List<Branch> =
        withContext(Dispatchers.IO) { search(query, limit) }

    override fun close() {
        database.close()
    }

    private fun Cursor.toBranches(): List<Branch> {
        val result = ArrayList<Branch>(count)
        while (moveToNext()) result.add(toBranch())
        return result
    }

    private fun Cursor.toBranch(): Branch = Branch(
        ifsc = getString(getColumnIndexOrThrow("ifsc")),
        bankCode = getString(getColumnIndexOrThrow("bank_code")),
        bank = getString(getColumnIndexOrThrow("bank")),
        branch = getString(getColumnIndexOrThrow("branch")),
        address = getString(getColumnIndexOrThrow("address")),
        city = getString(getColumnIndexOrThrow("city")),
        district = getString(getColumnIndexOrThrow("district")),
        state = getString(getColumnIndexOrThrow("state")),
        centre = getString(getColumnIndexOrThrow("centre")),
        contact = getString(getColumnIndexOrThrow("contact")),
        micr = getString(getColumnIndexOrThrow("micr")),
        swift = getString(getColumnIndexOrThrow("swift")),
        iso3166 = getString(getColumnIndexOrThrow("iso3166")),
        upi = getInt(getColumnIndexOrThrow("upi")) != 0,
        rtgs = getInt(getColumnIndexOrThrow("rtgs")) != 0,
        neft = getInt(getColumnIndexOrThrow("neft")) != 0,
        imps = getInt(getColumnIndexOrThrow("imps")) != 0,
    )

    public companion object {
        private const val ASSET_NAME = "ifsc.db"
        private const val DATABASE_NAME = "ifsc.db"
        private const val PREFS_NAME = "dev.ifsc.offline"
        private const val PREF_DATA_VERSION = "data_version"
        private const val COPY_BUFFER_SIZE = 64 * 1024

        private val installLock = Any()

        /**
         * Opens the database, copying the bundled asset on first use or after
         * a dataset update. Call from a background thread.
         */
        public fun open(context: Context): IfscDatabase {
            val appContext = context.applicationContext
            val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            synchronized(installLock) {
                if (!databaseFile.exists() ||
                    prefs.getString(PREF_DATA_VERSION, null) != IfscOfflineData.VERSION
                ) {
                    installDatabase(appContext, databaseFile)
                }

                val database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                )
                prefs.edit().putString(PREF_DATA_VERSION, IfscOfflineData.VERSION).apply()
                return IfscDatabase(database)
            }
        }

        /** Coroutine-friendly version of [open]. */
        public suspend fun openAsync(context: Context): IfscDatabase =
            withContext(Dispatchers.IO) { open(context) }

        private fun installDatabase(context: Context, databaseFile: File) {
            databaseFile.parentFile?.mkdirs()
            val temporaryFile = File(databaseFile.parentFile, "${databaseFile.name}.tmp")
            if (temporaryFile.exists()) temporaryFile.delete()

            try {
                context.assets.open(ASSET_NAME).use { input ->
                    temporaryFile.outputStream().use { output ->
                        input.copyTo(output, COPY_BUFFER_SIZE)
                    }
                }

                if (!temporaryFile.renameTo(databaseFile)) {
                    temporaryFile.copyTo(databaseFile, overwrite = true)
                    temporaryFile.delete()
                }
            } catch (throwable: Throwable) {
                temporaryFile.delete()
                throw throwable
            }
        }

        private fun escapeLike(value: String): String = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
