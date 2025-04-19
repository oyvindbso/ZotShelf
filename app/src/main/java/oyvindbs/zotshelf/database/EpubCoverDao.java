package oyvindbs.zotshelf.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for the EpubCoverEntity
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
}
