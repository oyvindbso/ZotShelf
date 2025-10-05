package oyvindbs.zotshelf.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**

- Enhanced Data Access Object for the EpubCoverEntity with offline support
  */
  @Dao
  public interface EpubCoverDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insert(EpubCoverEntity cover);
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void insertAll(List<EpubCoverEntity> covers);
  
  @Update
  void update(EpubCoverEntity cover);
  
  @Query("SELECT * FROM epub_covers")
  List<EpubCoverEntity> getAllCovers();
  
  @Query("SELECT * FROM epub_covers WHERE id = :id")
  EpubCoverEntity getCoverById(String id);
  
  @Query("DELETE FROM epub_covers WHERE id = :id")
  void deleteById(String id);
  
  @Query("DELETE FROM epub_covers")
  void deleteAll();
  
  // Get the number of covers in the database
  @Query("SELECT COUNT(id) FROM epub_covers")
  int getCount();
  
  // Check if a cover exists by id
  @Query("SELECT EXISTS(SELECT 1 FROM epub_covers WHERE id = :id)")
  boolean exists(String id);
  
  // Get covers for a specific username
  @Query("SELECT * FROM epub_covers WHERE zoteroUsername = :username")
  List<EpubCoverEntity> getCoversByUsername(String username);
  
  // Enhanced queries for offline functionality
  
  
@Query("SELECT * FROM epub_covers WHERE " +
       "(:showEpubs = 1 AND mimeType = 'application/epub+zip') OR " +
       "(:showPdfs = 1 AND mimeType = 'application/pdf')")
List<EpubCoverEntity> getCoversByFileTypes(boolean showEpubs, boolean showPdfs);

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
  
  // Get covers modified since a certain timestamp (for incremental sync)
  @Query("SELECT * FROM epub_covers WHERE lastUpdated > :timestamp")
  List<EpubCoverEntity> getCoversModifiedSince(long timestamp);
  
  // Clean up old entries that are older than a certain age
  @Query("DELETE FROM epub_covers WHERE lastUpdated < :cutoffTime")
  int deleteOldEntries(long cutoffTime);
  }