package stroom.query.api.v2;

public enum DestroyReason {
    /**
     * Manual destruction via the search result store management UI.
     */
    MANUAL,
    /**
     * A result store that is no longer needed as a new search has started that will replace previous results.
     */
    NO_LONGER_NEEDED,
    /**
     * A Stroom tab has been closed that might result in the destruction of search results depending on the result store
     * settings.
     */
    TAB_CLOSE,
    /**
     * A browser window or tab has been closed that might result in the destruction of search results depending on the
     * result store settings.
     */
    WINDOW_CLOSE
}
