package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;

import java.io.IOException;
import java.util.List;

public interface CredentialsDao {

    /**
     * Returns a list of all the credentials in the database.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> listCredentials() throws IOException;

    /**
     * Returns a list of all the credentials of a given type.
     * @param type The type of credentials to select, or all credentials if null.
     * @return The credentials that match the type.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> listCredentials(CredentialsType type) throws IOException;

    /**
     * Stores the given credential to the database.
     * @param credentials The credentials to store in the database.
     * @throws IOException if something goes wrong.
     */
    void storeCredentials(Credentials credentials) throws IOException;

    /**
     * Returns the credential matching the UUID.
     * @return The credential matching the UUID.
     */
    Credentials getCredentials(String uuid) throws IOException;

    /**
     * Deletes the credentials and secrets matching the UUID.
     */
    void deleteCredentialsAndSecret(String uuid) throws IOException;

    /**
     * Stores the given secret to the database.
     * @param secret The secret to store.
     * @throws IOException if something goes wrong.
     */
    void storeSecret(CredentialsSecret secret) throws IOException;

    /**
     * Returns the secret for the given UUID.
     * @param uuid The UUID of the corresponding Credentials object.
     * @return The secrets for the credentials.
     * @throws IOException if something goes wrong.
     */
    CredentialsSecret getSecret(String uuid) throws IOException;

}
