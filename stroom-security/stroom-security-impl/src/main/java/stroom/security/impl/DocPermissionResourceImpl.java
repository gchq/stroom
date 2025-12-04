/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
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
            logPermCheck(request, hasPerm, null);
        } catch (final Exception e) {
            try {
                logPermCheck(request, false, e);
            } catch (final Exception ex) {
                LOGGER.error("Error logging event: {}", e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }

        return hasPerm;
    }

    private void logPermCheck(final CheckDocumentPermissionRequest request,
                              final boolean hasPerm,
                              final Throwable e) {
        final DocRef docRef = request.getDocRef();

        final String msg;
        final Outcome outcome;
        if (e == null) {
            if (hasPerm) {
                msg = LogUtil.message("User has permission {} on document {}",
                        request.getPermission(),
                        docRef);
                outcome = null;
            } else {
                msg = LogUtil.message("User does not have permission {} on document {}",
                        request.getPermission(),
                        docRef);
                outcome = Outcome.builder()
                        .withSuccess(false)
                        .withDescription(msg)
                        .build();
            }
        } else {
            msg = LogUtil.message("User failed permissions check for permission {} on document {}",
                    request.getPermission(),
                    docRef);
            outcome = Outcome.builder()
                    .withSuccess(false)
                    .withDescription(NullSafe.getOrElse(e, Throwable::getMessage, msg))
                    .build();
        }

        stroomEventLoggingServiceProvider.get().log(
                StroomEventLoggingUtil.buildTypeId(this, "checkDocumentPermission"),
                msg,
                AuthoriseEventAction.builder()
                        .withAction(AuthorisationActionType.REQUEST)
                        .addObject(StroomEventLoggingUtil.createOtherObject(docRef))
                        .withOutcome(outcome)
                        .build());
    }
}
