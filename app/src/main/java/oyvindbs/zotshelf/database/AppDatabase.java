package oyvindbs.zotshelf.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**

- Enhanced main database for the application with migration support
  */
  @Database(entities = {EpubCoverEntity.class}, version = 3, exportSchema = false)
  public abstract class AppDatabase extends RoomDatabase {
  
  private static final String DATABASE_NAME = "zotero_epub_covers_db";
  private static AppDatabase instance;
  
  public abstract EpubCoverDao epubCoverDao();
  
  // Migration from version 1 to 2 (adding new fields for offline support)
 private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        // Add new columns with default values
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN fileName TEXT");
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN mimeType TEXT");
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN downloadUrl TEXT");
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN parentItemType TEXT");
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN isBook INTEGER DEFAULT 1");
        database.execSQL("ALTER TABLE epub_covers ADD COLUMN collectionKeys TEXT DEFAULT ''");
    }
};

private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE epub_covers ADD COLUMN year TEXT");
        }
    };

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // <-- ADD MIGRATION_2_3
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}

