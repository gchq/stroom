package stroom.util.shared;

public interface ServletAuthenticationChecker {

    /**
     * @param servletPath The servlet path for the servlet being requested
     * @return True if servletPath matches a path spec for a servlet marked with {@link Unauthenticated}
     */
    boolean isUnauthenticatedPath(final String servletPath);
}
