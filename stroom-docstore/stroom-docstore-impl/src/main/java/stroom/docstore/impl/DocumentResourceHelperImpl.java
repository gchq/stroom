/*
 * Copyright 2017 Crown Copyright
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
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

public class DocumentResourceHelperImpl implements DocumentResourceHelper {

    private final SecurityContext securityContext;

    @Inject
    public DocumentResourceHelperImpl(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public <D extends AbstractDoc> D read(final DocumentActionHandler<D> documentActionHandler,
                                          final DocRef docRef) {
        return securityContext.secureResult(() ->
                securityContext.useAsReadResult(() -> {
                    try {
                        return documentActionHandler.readDocument(docRef);
                    } catch (final PermissionException e) {
                        throw new PermissionException(
                                e.getUser(),
                                e.getMessage().replaceAll("permission to read", "permission to use"));
                    } catch (final RuntimeException e) {
                        throw e;
                    }
                }));
    }

    @Override
    public <D extends AbstractDoc> D update(final DocumentActionHandler<D> documentActionHandler, final D doc) {
        return securityContext.secureResult(() -> {
            try {
                return documentActionHandler.writeDocument(doc);
            } catch (final RuntimeException e) {
                throw e;
            }
        });
    }
}
