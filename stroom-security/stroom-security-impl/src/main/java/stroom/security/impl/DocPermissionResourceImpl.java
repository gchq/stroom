package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.DocumentUserPermissionsRequest;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.Outcome;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

@AutoLogged
class DocPermissionResourceImpl implements DocPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocPermissionResourceImpl.class);

    private final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    //Todo
    // Permission checking should be responsibility of underlying service rather than REST resource impl
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    DocPermissionResourceImpl(final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider,
                              final Provider<SecurityContext> securityContextProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(final FetchDocumentUserPermissionsRequest
                                                                                    request) {
        return documentPermissionServiceProvider.get().fetchDocumentUserPermissions(request);
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public DocumentUserPermissionsReport getDocUserPermissionsReport(final DocumentUserPermissionsRequest request) {
        return documentPermissionServiceProvider.get().getDocUserPermissionsReport(request);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED) // Only log failures
    public Boolean checkDocumentPermission(final CheckDocumentPermissionRequest request) {
        final boolean hasPerm;
        try {
            Objects.requireNonNull(request);
            hasPerm = securityContextProvider.get().hasDocumentPermission(
                    request.getDocRef(),
                    request.getPermission());
            if (!hasPerm) {
                // Only want to log a failure
                logPermCheckFailure(request, null);
            }
        } catch (Exception e) {
            try {
                logPermCheckFailure(request, e);
            } catch (Exception ex) {
                LOGGER.error("Error logging event: {}", e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }

        return hasPerm;
    }

    private void logPermCheckFailure(final CheckDocumentPermissionRequest request,
                                     final Throwable e) {
        final DocRef docRef = request.getDocRef();
        final String msg = LogUtil.message("User failed permissions check for permission {} document {}",
                request.getPermission(),
                docRef);

        stroomEventLoggingServiceProvider.get().log(
                StroomEventLoggingUtil.buildTypeId(this, "checkDocumentPermission"),
                msg,
                AuthoriseEventAction.builder()
                        .withAction(AuthorisationActionType.REQUEST)
                        .addObject(StroomEventLoggingUtil.createOtherObject(docRef))
                        .withOutcome(Outcome.builder()
                                .withSuccess(false)
                                .withDescription(NullSafe.getOrElse(e, Throwable::getMessage, msg))
                                .build())
                        .build());
    }
}
