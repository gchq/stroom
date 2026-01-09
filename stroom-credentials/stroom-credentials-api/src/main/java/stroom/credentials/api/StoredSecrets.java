package stroom.credentials.api;

public interface StoredSecrets {

    /**
     * Get a stored secret by name.
     *
     * @param name The name of the stored secret.
     * @return The stored secret or null if none can be found.
     */
    StoredSecret get(String name);

    KeyStore getKeyStore(String name);
}
