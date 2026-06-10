package oyvindbs.zotshelf.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for cached ebook covers
 */
@Dao
public interface EpubCoverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(EpubCoverEntity cover);

    @Query("SELECT COUNT(id) FROM epub_covers")
    int getCount();

    @Query("SELECT * FROM epub_covers WHERE " +
           "((:booksOnly = 1 AND isBook = 1) OR (:booksOnly = 0)) AND " +
           "((:showEpubs = 1 AND mimeType = 'application/epub+zip') OR " +
           "(:showPdfs = 1 AND mimeType = 'application/pdf'))")
    List<EpubCoverEntity> getCoversByPreferences(boolean booksOnly, boolean showEpubs, boolean showPdfs);

    @Query("SELECT * FROM epub_covers WHERE " +
           "(:collectionKey = '' OR collectionKeys = '' OR collectionKeys LIKE '%' || :collectionKey || '%') AND " +
           "((:showEpubs = 1 AND mimeType = 'application/epub+zip') OR " +
           "(:showPdfs = 1 AND mimeType = 'application/pdf')) AND " +
           "((:booksOnly = 1 AND isBook = 1) OR (:booksOnly = 0))")
    List<EpubCoverEntity> getCoversByCollection(String collectionKey, boolean booksOnly, boolean showEpubs, boolean showPdfs);
}
