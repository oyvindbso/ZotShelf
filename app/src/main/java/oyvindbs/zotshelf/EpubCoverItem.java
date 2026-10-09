package oyvindbs.zotshelf;

public class EpubCoverItem {
    private final String id;
    private final String title;
    private final String coverPath;
    private final String authors;
    private final String zoteroUsername;
    private final String year;

    public EpubCoverItem(String id, String title, String coverPath, String authors, String zoteroUsername, String year) {
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
}

