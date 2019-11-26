package stroom.util.guice;

import java.util.Objects;

public interface ResourcePaths {

    /**
     * Used as the root for all servlet and UI requests
     */
    String ROOT_PATH = "/stroom";
    /**
     * Used as the root for all servlet and UI requests
     */
    String API_PATH = "/api";
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


    static String buildUnauthenticatedServletPath(final String value) {
        return buildPath(
                ROOT_PATH,
                NO_AUTH_PATH,
                value);
    }

    static String buildAuthenticatedServletPath(final String value) {
        return buildPath(
                ROOT_PATH,
                value);
    }

    static String buildPath(final String... parts) {
        Objects.requireNonNull(parts);
        return String
                .join("/", parts)
                .replace("//", "/");
    }
}
