package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.credentials.shared.CredentialsWithPerms;
import stroom.docref.DocRef;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CredentialsService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialsService.class);

    /** Talks to database */
    private final CredentialsDao credentialsDao;

    /** Security checks */
    private final SecurityContext securityContext;

    /** Permission service */
    private final Provider<DocumentPermissionService> permissionServiceProvider;

    /** Allows us to find the parent groups of the current group */
    private final Provider<UserGroupsService> userGroupsServiceProvider;


    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public CredentialsService(final CredentialsDao credentialsDao,
                              final SecurityContext securityContext,
                              final Provider<DocumentPermissionService> permissionServiceProvider,
                              final Provider<UserGroupsService> userGroupsServiceProvider) {
        this.credentialsDao = credentialsDao;
        this.securityContext = securityContext;
        this.permissionServiceProvider = permissionServiceProvider;
        this.userGroupsServiceProvider = userGroupsServiceProvider;
    }

    /**
     * Filters the credentials so that only those with VIEW, EDIT or OWNER permissions are returned.
     * @param inputCredentials The credentials to filter
     * @return The filtered credentials.
     */
    private List<CredentialsWithPerms> permissionFilterCredentials(final List<Credentials> inputCredentials) {

        final List<CredentialsWithPerms> filteredCredentials = new ArrayList<>(inputCredentials.size());
        for (final Credentials credentials : inputCredentials) {
            if (credentials != null) {
                final DocRef docRef = new DocRef(Credentials.TYPE, credentials.getUuid());
                if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                    final boolean canEdit = securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
                    final boolean canDelete = securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE);
                    final boolean owner = securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
                    filteredCredentials.add(new CredentialsWithPerms(credentials,
                            canEdit || owner,
                            canDelete || owner));
                } else {
                    LOGGER.debug("User does not have permission to see credentials: {}", credentials);
                }
            }
        }
        return filteredCredentials;
    }

    /**
     * Wraps the given credentials object with UI permissions.
     * @param credentials The credentials to wrap. Can be null in which case null is returned.
     * @return Credentials with permissions. Null if credentials are null or user doesn't have
     * permission to view these credentials.
     */
    private CredentialsWithPerms permissionWrap(final Credentials credentials) {
        CredentialsWithPerms retval = null;
        if (credentials != null) {

            final DocRef docRef = new DocRef(Credentials.TYPE, credentials.getUuid());
            if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                final boolean canEdit = securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
                final boolean canDelete = securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE);
                final boolean owner = securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
                retval = new CredentialsWithPerms(credentials,
                        canEdit || owner,
                        canDelete || owner);
            }
        }

        return retval;
    }

    /**
     * Returns a list of all the credentials in the database.
     * Permissions: App, View|Edit|Owner (for all individual permissions)
     * @throws IOException if something goes wrong.
     */
    public List<CredentialsWithPerms> listCredentials() throws IOException {
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
    public List<CredentialsWithPerms> listCredentials(final CredentialsType type) throws IOException {
        checkAppPermission();
        return permissionFilterCredentials(credentialsDao.listCredentials(type));
    }

    /**
     * Creates new credentials given some credentials from the client.
     * Note that the credentials WILL NOT have the same UUID as that given by the client
     * as this could lead to security issues. Instead, we create a new UUID on the server
     * and copy data across.
     * @param clientCredentials The new credentials to create from the client
     * @throws IOException if something goes wrong.
     */
    public CredentialsWithPerms createCredentials(final Credentials clientCredentials,
                                                  final CredentialsSecret clientSecret) throws IOException {
        LOGGER.debug("Creating credentials: {}", clientCredentials);
        checkAppPermission();

        final Credentials dbCredentials = credentialsDao.createCredentials(clientCredentials);
        final CredentialsSecret dbSecret = clientSecret.copyWithUuid(dbCredentials.getUuid());
        credentialsDao.storeSecret(dbSecret);

        // Create a fake docRef for the security system
        final DocRef docRef = new DocRef(dbCredentials.getName(),
                dbCredentials.getUuid(),
                Credentials.TYPE);
        final UserRef userRef = securityContext.getUserRef();

        securityContext.asProcessingUser(() -> {
            final DocumentPermissionService permissionService = permissionServiceProvider.get();

            // Add owner permission
            permissionService.setPermission(docRef, userRef, DocumentPermission.OWNER);

            // Add ownership perms to parent groups
            final Set<UserRef> parentGroups = userGroupsServiceProvider.get().getGroups(userRef);
            if (NullSafe.hasItems(parentGroups)) {
                parentGroups.forEach(group ->
                        permissionService.setPermission(docRef, group, DocumentPermission.OWNER));
            }
        });

        return permissionWrap(dbCredentials);
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
    public CredentialsWithPerms getCredentials(final String uuid) throws IOException {
        checkAppPermission();
        checkViewPermission(uuid);
        return permissionWrap(credentialsDao.getCredentials(uuid));
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
     * Throws exception if the user does not have permission to view the given credentials.
     * @param uuid ID of the credentials to check.
     */
    @SuppressWarnings("StatementWithEmptyBody")
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
    @SuppressWarnings("StatementWithEmptyBody")
    private void checkEditPermission(final String uuid) {
        if (uuid == null) {
            throw new RuntimeException("Credentials not found");
        }
        final DocRef docRef = new DocRef(Credentials.TYPE, uuid);
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)
            || securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            // OK
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit these credentials");
        }
    }

    /**
     * Throws exception if the user does not have permission to delete the given credentials.
     * @param uuid ID of the credentials to check.
     */
    @SuppressWarnings("StatementWithEmptyBody")
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
