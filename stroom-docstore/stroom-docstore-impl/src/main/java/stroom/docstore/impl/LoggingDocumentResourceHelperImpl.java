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

package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocRefUtil;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

public class LoggingDocumentResourceHelperImpl implements DocumentResourceHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoggingDocumentResourceHelperImpl.class);

    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    public LoggingDocumentResourceHelperImpl(final DocumentEventLog documentEventLog,
                                             final SecurityContext securityContext) {
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public <D extends AbstractDoc> D read(final DocumentActionHandler<D> documentActionHandler,
                                          final DocRef docRef) {
        return securityContext.secureResult(() ->
                securityContext.useAsReadResult(() -> {
                    try {
                        final D doc = documentActionHandler.readDocument(docRef);
                        if (doc == null) {
                            documentEventLog.view(docRef, new RuntimeException("Unable to find document"));
                        } else {
                            documentEventLog.view(doc, null);
                        }
                        return doc;
                    } catch (final PermissionException e) {
                        documentEventLog.view(docRef, e);
                        throw new PermissionException(
                                e.getUser(),
                                e.getMessage().replaceAll("permission to read", "permission to use"));
                    } catch (final RuntimeException e) {
                        documentEventLog.view(docRef, e);
                        throw e;
                    }
                }));
    }

    @Override
    public <D extends AbstractDoc> D update(final DocumentActionHandler<D> documentActionHandler, final D doc) {
        return securityContext.secureResult(() -> {
            final DocRef docRef = DocRefUtil.create(doc);
            try {
                final D before = documentActionHandler.readDocument(docRef);
                final D after = documentActionHandler.writeDocument(doc);
                documentEventLog.update(before, after, null);
                return after;
            } catch (final RuntimeException e) {
                documentEventLog.update(null, doc, e);
                throw e;
            }
        });
    }
}
