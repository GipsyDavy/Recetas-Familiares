package org.gipsybuho.recetasfamiliares.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(AppDatabase.Schema, "recetas.db")
}
