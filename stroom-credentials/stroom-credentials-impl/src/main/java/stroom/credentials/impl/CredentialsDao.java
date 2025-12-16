package stroom.credentials.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.util.shared.ResultPage;

import java.util.function.Function;
import java.util.function.Predicate;

public interface CredentialsDao {

    /**
     * Returns a list of all the credentials in the database meeting the specified criteria and decorated with
     * permissions.
     */
    @SuppressWarnings("checkstyle:LineLength")
    ResultPage<CredentialWithPerms> findCredentialsWithPermissions(FindCredentialRequest request,
                                                                   Function<Credential, CredentialWithPerms> permissionDecorator);

    /**
     * Returns a list of all the credentials in the database meeting the specified criteria.
     */
    ResultPage<Credential> findCredentials(FindCredentialRequest request,
                                           Predicate<Credential> permissionFilter);

    /**
     * Returns the credential matching the UUID.
     *
     * @return The credential matching the UUID.
     */
    Credential getCredentialByUuid(String uuid);

    /**
     * Returns the credential matching the name.
     *
     * @return The credential matching the name.
     */
    Credential getCredentialByName(String name);

    /**
     * Deletes the credentials and secrets matching the UUID.
     */
    void deleteCredentialsAndSecret(String uuid);

    /**
     * Returns the credential matching the name.
     *
     * @return The credential and secret matching the name.
     */
    StoredSecret getStoredSecretByName(String name);

    /**
     * Stores the given secret to the database.
     *
     * @param secret The secret to store.
     */
    void putStoredSecret(StoredSecret secret, boolean update);
}
