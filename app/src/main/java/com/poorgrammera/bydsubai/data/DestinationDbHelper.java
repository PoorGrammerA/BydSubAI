package com.poorgrammera.bydsubai.data;
import com.poorgrammera.bydsubai.BuildConfig;
import com.poorgrammera.bydsubai.R;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DestinationDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "destinations.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "saved_destinations";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_ALIAS = "alias";
    private static final String COLUMN_ADDRESS = "address";
    private static final String COLUMN_LATITUDE = "latitude";
    private static final String COLUMN_LONGITUDE = "longitude";

    private static DestinationDbHelper instance;

    public static synchronized DestinationDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DestinationDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DestinationDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_ALIAS + " TEXT UNIQUE, " +
                COLUMN_ADDRESS + " TEXT, " +
                COLUMN_LATITUDE + " REAL, " +
                COLUMN_LONGITUDE + " REAL)";
        db.execSQL(createTableQuery);

        // Create table for storing LLM analyzed driver profile habits
        String createMemoryTable = "CREATE TABLE IF NOT EXISTS long_term_memory (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT, " +
                "timestamp INTEGER)";
        db.execSQL(createMemoryTable);
    }

    public synchronized void saveLongTermMemory(String key, String value) {
        if (key == null || key.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("key", key.trim());
        values.put("value", value != null ? value.trim() : "");
        values.put("timestamp", System.currentTimeMillis());
        db.insertWithOnConflict("long_term_memory", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<String> getRecentLongTermMemories() {
        List<String> memories = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("long_term_memory", new String[]{"key", "value"},
                "value IS NOT NULL AND value != '' AND LOWER(value) != 'none'", null, null, null, "timestamp DESC", "15");
        if (cursor != null) {
            try {
                int keyCol = cursor.getColumnIndexOrThrow("key");
                int valCol = cursor.getColumnIndexOrThrow("value");
                while (cursor.moveToNext()) {
                    memories.add(cursor.getString(keyCol) + ": " + cursor.getString(valCol));
                }
            } finally {
                cursor.close();
            }
        }
        return memories;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public synchronized long addDestination(String alias, String address, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ALIAS, alias);
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        return db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized int updateDestination(int id, String alias, String address, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ALIAS, alias);
        values.put(COLUMN_ADDRESS, address);
        values.put(COLUMN_LATITUDE, latitude);
        values.put(COLUMN_LONGITUDE, longitude);
        return db.update(TABLE_NAME, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void deleteDestination(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public synchronized List<SavedDestination> getAllDestinations() {
        List<SavedDestination> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, COLUMN_ALIAS + " ASC");
        if (cursor != null) {
            try {
                int idCol = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int aliasCol = cursor.getColumnIndexOrThrow(COLUMN_ALIAS);
                int addressCol = cursor.getColumnIndexOrThrow(COLUMN_ADDRESS);
                int latCol = cursor.getColumnIndexOrThrow(COLUMN_LATITUDE);
                int lngCol = cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE);

                while (cursor.moveToNext()) {
                    list.add(new SavedDestination(
                            cursor.getInt(idCol),
                            cursor.getString(aliasCol),
                            cursor.getString(addressCol),
                            cursor.getDouble(latCol),
                            cursor.getDouble(lngCol)
                    ));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public synchronized List<String> getAllAliases() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_ALIAS}, null, null, null, null, COLUMN_ALIAS + " ASC");
        if (cursor != null) {
            try {
                int aliasCol = cursor.getColumnIndexOrThrow(COLUMN_ALIAS);
                while (cursor.moveToNext()) {
                    list.add(cursor.getString(aliasCol));
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public synchronized SavedDestination getDestinationByAlias(String alias) {
        if (alias == null) return null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, "LOWER(" + COLUMN_ALIAS + ") = LOWER(?)", new String[]{alias.trim()}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return new SavedDestination(
                            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ALIAS)),
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ADDRESS)),
                            cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LATITUDE)),
                            cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LONGITUDE))
                    );
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    public synchronized SavedDestination findMatchingDestination(String query) {
        if (query == null) return null;
        String cleanQuery = query.trim().replaceAll("\\s+", "");
        
        List<SavedDestination> all = getAllDestinations();
        // 1. Try exact (ignoring whitespace and case)
        for (SavedDestination d : all) {
            String cleanAlias = d.getAlias().trim().replaceAll("\\s+", "");
            if (cleanAlias.equalsIgnoreCase(cleanQuery)) {
                return d;
            }
        }
        // 2. Try contains/starts/ends with match
        for (SavedDestination d : all) {
            String cleanAlias = d.getAlias().trim().replaceAll("\\s+", "");
            if (cleanQuery.contains(cleanAlias) || cleanAlias.contains(cleanQuery)) {
                return d;
            }
        }
        return null;
    }
}
