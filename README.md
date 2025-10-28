# ZotShelf: Your Digital Bookshelf for Zotero 

ZotShelf extracts covers from the EPUBs in your [Zotero](https://www.zotero.org) library and displays them in a beautiful, browsable grid. Quickly see your entire digital book collection at a glance and access your books with a single tap.

Zotshelf allows you to select a collection, a set of tags or a combination of a collection and tag(s).

The app is currently in closed beta testing. Contact me if you would like to test it at appbugge@gmail.com. 

## How It Works

1. **Set up your Zotero credentials** - Choose between:
   - **Easy OAuth Login**: Click "Login with Zotero" and authenticate through Zotero's website (recommended)
   - **Manual Entry**: Enter your Zotero username, user ID, and API key manually
2. **Connect to your library** - ZotShelf securely connects to your Zotero account and finds all your EPUB books.
3. **Select a collection, tag(s) or a combination** - Filter your library by collection and/or tags.
4. **Extract covers** - The app automatically extracts cover art from each EPUB file in your library.
5. **Browse your covers** - Explore your digital bookshelf, and tap any cover to start reading.
6. **Multiple tabs** - ZotShelf supports tabs, each with a different combination of collection and tags.
7. **Stay updated** - Pull down to refresh whenever you add new books to your Zotero library.

## Getting Started

To use ZotShelf, you'll need:

- An Android device running Android 6.0 (Marshmallow) or newer
- A Zotero account with EPUB books in your library
- Authentication credentials:
  - **For OAuth Login (recommended)**: Just your Zotero username and password
  - **For Manual Entry**: Your Zotero API key, username and User ID (available at https://www.zotero.org/settings/keys - please select read only)

### For Developers: Setting Up OAuth

If you're building the app from source, you'll need to register your own OAuth application with Zotero. See [OAUTH_SETUP.md](OAUTH_SETUP.md) for detailed instructions.

## Technical Details

ZotShelf is built using native Android technologies with a focus on performance and reliability:

- Java-based Android application with modern architecture patterns
- **OAuth 1.0a authentication** for secure, user-friendly login via Zotero
- Retrofit and OkHttp for efficient API communication with Zotero
- Room Database for local caching of book covers and metadata
- RecyclerView with GridLayoutManager for smooth scrolling performance
- SwipeRefreshLayout for intuitive pull-to-refresh functionality
- Glide for efficient image loading and caching
- Material Design components for a clean, modern UI
- Home screen widget for quick access to your books
- Offline mode for viewing your collection without an internet connection

## Open Source

ZotShelf is completely open source under the MIT License. You can find the source code, contribute, or report issues here.

## Support the Project

If you find ZotShelf useful, consider

- Starring the project on GitHub
- Reporting bugs or suggesting features
- Contributing code improvements
- [Buying me a coffee](https://buymeacoffee.com/oyvindbs)
