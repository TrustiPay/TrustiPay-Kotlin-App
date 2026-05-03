package app.trustipay.offline.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OfflinePaymentOpenHelper(context: Context) : SQLiteOpenHelper(
    context,
    OfflinePaymentSchema.DatabaseName,
    null,
    OfflinePaymentSchema.Version,
) {
    override fun onCreate(db: SQLiteDatabase) {
        OfflinePaymentSchema.CreateStatements.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            OfflinePaymentSchema.CreateStatements.forEach(db::execSQL)
        }
    }
}
