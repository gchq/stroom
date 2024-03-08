package stroom.security.api;

public interface ServiceUserFactory {

    UserIdentity createServiceUserIdentity();

    boolean isServiceUser(final UserIdentity userIdentity,
                          final UserIdentity serviceUserIdentity);
}
