package stroom.cache.api;

public class CacheExistsException extends RuntimeException {

    public CacheExistsException(final String cacheName) {
        super("A cache called '" + cacheName + "' is already registered");
    }
}
