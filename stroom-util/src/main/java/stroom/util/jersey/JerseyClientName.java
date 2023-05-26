package stroom.util.jersey;

public enum JerseyClientName {

    /**
     * Client for Proxy to get content from a downstream stroom.
     */
    CONTENT_SYNC,

    /**
     * The default client. Used if a named client has not been configured or where
     * you want multiple named clients to share similar config.
     */
    DEFAULT,

    /**
     * Client for proxy to get the feed status from a downstream stroom/proxy.
     */
    FEED_STATUS,

    /**
     * Client for the HttpPostFilter.
     */
    @Deprecated // HttpPostFilter is deprecated
    HTTP_POST_FILTER,

    /**
     * Client for communications with an external Open ID Connect provider,
     * e.g. Cognito, KeyCloak, Azure AD, etc.
     */
    OPEN_ID,

    /**
     * Client for inter-node and proxy => stroom communications
     */
    STROOM,
}
