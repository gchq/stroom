package stroom.util.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface ResourcePaths {

    /**
     * Used as the root for all servlet and UI requests
     */
    String ROOT_PATH = "/stroom";

    /**
     * Used as the root for all REST resources
     */
    String API_ROOT_PATH = "/api";

    // TODO consider splitting all api resources into either stateful or stateless paths
    //   to make nginx routing easier
    String STATEFUL_PATH = "/stateful";
    String STATELESS_PATH = "/stateless";
    String NO_AUTH_PATH = "/noauth";

    String STROOM_INDEX = "/stroom-index";
    String SQL_STATISTICS = "/sqlstatistics";

    String DISPATCH_RPC_PATH = "/dispatch.rpc";
    String CLUSTER_CALL_RPC_PATH = "/clustercall.rpc";
    String XSRF_TOKEN_RPC_PATH = "/xsrf";

    String V1 = "/v1";
    String V2 = "/v2";
    String V3 = "/v3";


    static String buildUnauthenticatedServletPath(final String... parts) {
        return new Builder()
            .addPathPart(ROOT_PATH)
            .addPathPart(NO_AUTH_PATH)
            .addPathParts(parts)
            .build();
    }

    static String buildAuthenticatedServletPath(final String... parts) {
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

    /**
     * @param parts The path or parts of a path to append onto the base path.
     * @return The full path to the unauthenticated resource, e.g. /api/noauth/node
     */
    static String buildUnauthenticatedApiPath(final String... parts) {
        return new Builder()
            .addPathPart(API_ROOT_PATH)
            .addPathPart(NO_AUTH_PATH)
            .addPathParts(parts)
            .build();
    }

    static String buildPath(final String... parts) {
        Objects.requireNonNull(parts);
        return String
                .join("/", parts)
                .replace("//", "/");
    }



    class Builder {
        final List<String> pathParts = new ArrayList<>();
        final List<String> queryParams = new ArrayList<>();

        public Builder addPathPart(final String part) {
            if (!part.startsWith("/")) {
                pathParts.add("/");
            }
            pathParts.add(part);
            return this;
        }

        public Builder addPathParts(final String... parts) {
            for (String part : parts) {
                if (!part.startsWith("/")) {
                    pathParts.add("/");
                }
                pathParts.add(part);
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
            final String pathStr = String.join("", pathParts).replace("//","/");
            if (!queryParams.isEmpty()) {
                final String queryParamsStr = String.join("&", queryParams);
                return pathStr + "?" + queryParamsStr;
            } else {
                return pathStr;
            }
        }
    }
}
