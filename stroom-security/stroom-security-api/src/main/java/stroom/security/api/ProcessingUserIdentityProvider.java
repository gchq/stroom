package stroom.security.api;

public interface ProcessingUserIdentityProvider {

    /**
     * @return The identity of the processing user.
     */
    UserIdentity get();

    /**
     * @return True if the provided uses matches the processing user.
     */
    boolean isProcessingUser(final UserIdentity userIdentity);

    /**
     * @param subject The unique identifier
     * @param issuer
     * @return True if the provided Open ID Connect subject and issuer match that of the
     * processing user.
     */
    boolean isProcessingUser(final String subject,
                             final String issuer);
}
