package oyvindbs.zotshelf;

public class EpubCoverItem {
    private final String id;
    private final String title;
    private final String coverPath;
    private final String authors;
    private final String zoteroUsername;
    private final String year; 
    
    public EpubCoverItem(String id, String title, String coverPath, String authors, String zoteroUsername) {
        this.id = id;
        this.title = title;
        this.coverPath = coverPath;
        this.authors = authors;
        this.zoteroUsername = zoteroUsername;
        this.year = year;  
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getCoverPath() {
        return coverPath;
    }
    
    public String getAuthors() {
        return authors;
    }
    
    public String getZoteroUsername() {
        return zoteroUsername;
    }
    
    public String getYear() {  
        return year;
    }

    public int getYearAsInt() { 
        if (year == null || year.isEmpty()) {
            return 9999;
        }
        try {
            String yearDigits = year.replaceAll("[^0-9]", "");
            if (yearDigits.length() >= 4) {
                return Integer.parseInt(yearDigits.substring(0, 4));
            }
            return 9999;
        } catch (Exception e) {
            return 9999;
        }
    }
}

