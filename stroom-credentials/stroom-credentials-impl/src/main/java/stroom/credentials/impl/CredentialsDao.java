package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsType;

import java.io.IOException;
import java.util.List;

public interface CredentialsDao {

    /**
     * Returns a list of all the credentials in the database.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> list() throws IOException;

    /**
     * Returns a list of all the credentials of a given type.
     * @param type The type of credentials to select, or all credentials if null.
     * @return The credentials that match the type.
     * @throws IOException if something goes wrong.
     */
    List<Credentials> list(CredentialsType type) throws IOException;

    /**
     * Stores the given credential to the database.
     * @param credentials The credentials to store in the database.
     * @throws IOException if something goes wrong.
     */
    void store(Credentials credentials) throws IOException;

    /**
     * Returns the credential matching the UUID.
     * @return The credential matching the UUID.
     */
    Credentials get(String uuid) throws IOException;

    /**
     * Deletes the credentials matchting the UUID.
     */
    void delete(String uuid) throws IOException;

}
