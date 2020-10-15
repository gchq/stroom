package stroom.security.api;

public interface ProcessingUserIdentityProvider {

    UserIdentity get();

    boolean isProcessingUser(final UserIdentity userIdentity);
}
