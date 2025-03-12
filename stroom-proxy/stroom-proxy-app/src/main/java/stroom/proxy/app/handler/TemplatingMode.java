package stroom.proxy.app.handler;

/**
 * The mode for handling unknown parameters in subPathTemplate, e.g.
 * <pre>{@code
 * 'cat/${unknownparam}/dog'
 * }</pre>
 */
public enum TemplatingMode {
    /**
     * Ignore any unknown parameters, e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/${unknownparam}/dog'}
     */
    IGNORE_UNKNOWN,
    /**
     * Remove any unknown parameters, e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/dog'}
     */
    REMOVE_UNKNOWN,
    /**
     * Replace any unknown parameters with '{@code XXX}', e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/XXX/dog'}
     */
    REPLACE_UNKNOWN,
    ;
}
