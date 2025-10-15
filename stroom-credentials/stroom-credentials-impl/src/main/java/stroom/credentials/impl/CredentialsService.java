package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CredentialsService {

    /** Talks to database */
    private final CredentialsDao credentialsDao;

    /** Security checks */
    private final SecurityContext securityContext;

    /** Logger */
    @SuppressWarnings("unused")
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsService.class);

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsService(final CredentialsDao credentialsDao,
                              final SecurityContext securityContext) {
        this.credentialsDao = credentialsDao;
        this.securityContext = securityContext;
    }

    /**
     * Filters the credentials so that only those with VIEW, EDIT or OWNER permissions are returned.
     * @param inputCredentials The credentials to filter
     * @return The filtered credentials.
     */
    private List<Credentials> permissionFilterCredentials(final List<Credentials> inputCredentials) {

        final List<Credentials> filteredCredentials = new ArrayList<>(inputCredentials.size());
        for (final Credentials credentials : inputCredentials) {
            if (hasViewPermission(credentials)) {
                filteredCredentials.add(credentials);
            } else {
                LOGGER.info("User does not have permission to see credentials: {}", credentials);
            }
        }
        return filteredCredentials;
    }

    /**
     * Returns a list of all the credentials in the database.
     * Permissions: App, View|Edit|Owner (for all individual permissions)
     * @throws IOException if something goes wrong.
     */
    public List<Credentials> listCredentials() throws IOException {
        checkAppPermission();
        return permissionFilterCredentials(credentialsDao.listCredentials());
    }

    /**
     * Returns a list of all the credentials of a given type.
     * Permissions: App, View|Edit|Owner (for all individual permissions)
     * @param type The type of credentials to select, or all credentials if null.
     * @return The credentials that match the type.
     * @throws IOException if something goes wrong.
     */
    public List<Credentials> listCredentials(final CredentialsType type) throws IOException {
        checkAppPermission();
        return permissionFilterCredentials(credentialsDao.listCredentials(type));
    }

    /**
     * Stores the given credential to the database.
     * Permissions: App, Edit|Owner
     * @param credentials The credentials to store in the database.
     * @throws IOException if something goes wrong.
     */
    public void storeCredentials(final Credentials credentials) throws IOException {
        checkAppPermission();
        checkEditPermission(credentials.getUuid());
        credentialsDao.storeCredentials(credentials);
    }

    /**
     * Returns the credential matching the UUID.
     * Permissions: App, View|Edit|Owner
     * @return The credential matching the UUID.
     */
    public Credentials getCredentials(final String uuid) throws IOException {
        checkAppPermission();
        checkViewPermission(uuid);
        return credentialsDao.getCredentials(uuid);
    }

    /**
     * Deletes the credentials and secrets matching the UUID.
     * Permissions: App, Delete|Owner
     */
    public void deleteCredentialsAndSecret(final String uuid) throws IOException {
        checkAppPermission();
        checkDeletePermission(uuid);
        credentialsDao.deleteCredentialsAndSecret(uuid);
    }

    /**
     * Stores the given secret to the database.
     * Permissions: App, View|Edit|Owner
     * @param secret The secret to store.
     * @throws IOException if something goes wrong.
     */
    public void storeSecret(final CredentialsSecret secret) throws IOException {
        checkAppPermission();
        checkEditPermission(secret.getUuid());
        credentialsDao.storeSecret(secret);
    }

    /**
     * Returns the secret for the given UUID.
     * Permissions: App, View|Edit|Owner
     * @param uuid The UUID of the corresponding Credentials object.
     * @return The secrets for the credentials.
     * @throws IOException if something goes wrong.
     */
    public CredentialsSecret getSecret(final String uuid) throws IOException {
        checkAppPermission();
        checkViewPermission(uuid);
        return credentialsDao.getSecret(uuid);
    }

    /**
     * Throws exception if the user does not have permission to access credentials.
     */
    private void checkAppPermission() {
        if (!securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to use credentials");
        }
    }

    /**
     * Returns false if the user does not have permission to view the given credentials.
     * @param credentials The credentials to check.
     */
    private boolean hasViewPermission(final Credentials credentials) {
        if (credentials == null) {
            return false;
        } else {
            final DocRef docRef = new DocRef(Credentials.TYPE, credentials.getUuid());
            return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)
                   || securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
                   || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
        }
    }

    /**
     * Throws exception if the user does not have permission to view the given credentials.
     * @param uuid ID of the credentials to check.
     */
    private void checkViewPermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credentials.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to edit the given credentials.
     * @param uuid ID of the credentials to check.
     */
    private void checkEditPermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credentials.TYPE, uuid);
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to delete the given credentials.
     * @param uuid ID of the credentials to check.
     */
    private void checkDeletePermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credentials.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete these credentials");
        }
    }

}
