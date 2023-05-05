package stroom.util.jersey;

/**
 * A set of names for distinct jersey clients. Each one should map to a server (or cluster of servers backed
 * by a DNS name) such that the connection to each 'server' can be configured separately depending on the
 * nature of the communication or the capabilities of the 'server'. The {@link JerseyClientName#DEFAULT}
 * name provides the means for clients to use a common default config if the named one does not exist.
 */
public enum JerseyClientName {

    /**
     * Client for getting AWS public keys from <pre>https://public-keys.auth.elb.{}.amazonaws.com/{}</pre>
     */
    AWS_PUBLIC_KEYS,

    /**
     * Client for Proxy to get content from a downstream stroom/proxy.
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
    @Deprecated
    HTTP_POST_FILTER // HttpPostFilter is deprecated, use HttpAppender which has content controlled config
    ,

    /**
     * Client for communications with an external Open ID Connect provider,
     * e.g. Cognito, KeyCloak, Azure AD, etc.
     */
    OPEN_ID,

    /**
     * Client for inter-node stroom communications
     */
    STROOM,
}
