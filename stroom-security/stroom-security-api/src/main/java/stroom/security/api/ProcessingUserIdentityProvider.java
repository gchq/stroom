package stroom.security.api;

public interface ProcessingUserIdentityProvider {

    UserIdentity get();

    boolean isProcessingUser(final UserIdentity userIdentity);

    boolean isProcessingUser(final String subject,
                             final String issuer);
}
