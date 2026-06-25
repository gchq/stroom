/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.docstore.api.DocAuditEntryService;
import stroom.docstore.shared.DocAuditEntry;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

class DocAuditEntryServiceImpl implements DocAuditEntryService {

    private final Persistence persistence;
    private final SecurityContext securityContext;

    @Inject
    public DocAuditEntryServiceImpl(final Persistence persistence,
                                    final SecurityContext securityContext) {
        this.persistence = persistence;
        this.securityContext = securityContext;
    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        if (canView(docRef)) {
            return persistence.getAuditInfo(docRef);
        }
        return ResultPage.empty();
    }

    private boolean canView(final DocRef docRef) {
        return securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
    }
}
