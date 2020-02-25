package stroom.util.shared;

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
    String XSRF_TOKEN_RPC_PATH = "/xsrf";

    String V1 = "/v1";
    String V2 = "/v2";
    String V3 = "/v3";


    static String buildUnauthenticatedServletPath(final String... parts) {
        return new Builder()
            .addPart(ROOT_PATH)
            .addPart(NO_AUTH_PATH)
            .addParts(parts)
            .build();
    }

    static String buildAuthenticatedServletPath(final String... parts) {
        return new Builder()
            .addPart(ROOT_PATH)
            .addParts(parts)
            .build();
    }

    /**
     * @param parts The path or parts of a path to append onto the base path.
     * @return The full path to the authenticated resource, e.g. /api/node
     */
    static String buildAuthenticatedApiPath(final String... parts) {
        return new Builder()
            .addPart(API_ROOT_PATH)
            .addParts(parts)
            .build();
    }

    /**
     * @param parts The path or parts of a path to append onto the base path.
     * @return The full path to the unauthenticated resource, e.g. /api/noauth/node
     */
    static String buildUnauthenticatedApiPath(final String... parts) {
        return new Builder()
            .addPart(API_ROOT_PATH)
            .addPart(NO_AUTH_PATH)
            .addParts(parts)
            .build();
    }

    static String buildPath(final String... parts) {
        Objects.requireNonNull(parts);
        return String
                .join("/", parts)
                .replace("//", "/");
    }



    class Builder {
        private final StringBuilder stringBuilder = new StringBuilder();

        Builder addPart(final String part) {
            stringBuilder
                .append("/")
                .append(part);
            return this;
        }

        Builder addParts(final String... parts) {
            for (String part : parts) {
                stringBuilder
                    .append("/")
                    .append(part);
            }
            return this;
        }

        String build() {
            return stringBuilder.toString().replace("//","/");
        }
    }
}
