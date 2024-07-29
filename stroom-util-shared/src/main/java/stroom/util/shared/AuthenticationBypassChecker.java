package stroom.util.shared;

public interface AuthenticationBypassChecker {

    /**
     * @return True if servletPath matches a path spec for a servlet marked with {@link Unauthenticated}
     */
    boolean isUnauthenticated(final String servletName, final String servletPath, final String fullPath);
}
