package stroom.util.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface ResourcePaths {

    /**
     * Used as the root path for all servlet and UI requests
     */
    String ROOT_PATH = "";

    /**
     * Used as the root path for all ui resources
     */
    String UI_PATH = "/ui";

    /**
     * Used as the root path for all script resources
     */
    String SCRIPT_PATH = "/ui/script";

    /**
     * Used as the path for internal IdP sign in
     */
    String SIGN_IN_PATH = "/signIn";

    /**
     * Used as the root path for all REST resources
     */
    String API_ROOT_PATH = "/api";

    /**
     * Used as the base path for all servlets. This is because in earlier versions
     * servlets were all under /stroom because the React UI was using /. We could move
     * them all down to / but that is a breaking API change.
     */
    String SERVLET_BASE_PATH = "/stroom";

    /**
     * Path part for unauthenticated servlets
     */
    String NO_AUTH = "/noauth";

    /**
     * Path part for the lucene based query service
     */
    String STROOM_INDEX = "/stroom-index";

    /**
     * Path part for the SQL Statistics query service
     */
    String SQL_STATISTICS = "/sqlstatistics";

    // Path parts for versioned API paths
    String V1 = "/v1";
    String V2 = "/v2";
    String V3 = "/v3";


    static String addLegacyUnauthenticatedServletPrefix(final String... parts) {
        return new Builder()
                .addPathPart(SERVLET_BASE_PATH)
                .addPathPart(NO_AUTH)
                .addPathParts(parts)
                .build();
    }

    static String addLegacyAuthenticatedServletPrefix(final String... parts) {
        return new Builder()
                .addPathPart(SERVLET_BASE_PATH)
                .addPathParts(parts)
                .build();
    }

    static String addUnauthenticatedUiPrefix(final String... parts) {
        return new Builder()
                .addPathPart(UI_PATH)
                .addPathPart(NO_AUTH)
                .addPathParts(parts)
                .build();
    }

    /**
     * Creates a full path (including root) to an unauthenticated servlet ending in parts
     */
    static String buildUnauthenticatedServletPath(final String... parts) {
        return new Builder()
                .addPathPart(ROOT_PATH)
                .addPathParts(parts)
                .build();
    }

    /**
     * Creates a full path (including root) to a servlet ending in parts
     */
    static String buildServletPath(final String... parts) {
        return new Builder()
                .addPathPart(ROOT_PATH)
                .addPathParts(parts)
                .build();
    }

    /**
     * @param parts The path or parts of a path to append onto the base path.
     * @return The full path to the authenticated resource, e.g. /api/node
     */
    static String buildAuthenticatedApiPath(final String... parts) {
        return new Builder()
                .addPathPart(API_ROOT_PATH)
                .addPathParts(parts)
                .build();
    }

    static String buildPath(final String... parts) {
        Objects.requireNonNull(parts);
        return String
                .join("/", parts)
                .replace("//", "/");
    }

    static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    final class Builder {

        private final List<String> pathParts = new ArrayList<>();
        private final List<String> queryParams = new ArrayList<>();

        private Builder() {
        }

        public Builder addPathPart(final String part) {
            if (part != null && !part.isEmpty()) {
                if (!part.startsWith("/")) {
                    pathParts.add("/");
                }
                pathParts.add(part);
            }
            return this;
        }

        public Builder addPathParts(final String... parts) {
            for (final String part : parts) {
                if (part != null && !part.isEmpty()) {
                    if (!part.startsWith("/")) {
                        pathParts.add("/");
                    }
                    pathParts.add(part);
                }
            }
            return this;
        }

        public Builder addQueryParam(final String paramName, final String value) {
            Objects.requireNonNull(paramName);
            if (value != null) {
                queryParams.add(paramName + "=" + value);
            }
            return this;
        }

        public String build() {
            final String pathStr = String.join("", pathParts).replace("//", "/");
            if (!queryParams.isEmpty()) {
                final String queryParamsStr = String.join("&", queryParams);
                return pathStr + "?" + queryParamsStr;
            } else {
                return pathStr;
            }
        }
    }
}
