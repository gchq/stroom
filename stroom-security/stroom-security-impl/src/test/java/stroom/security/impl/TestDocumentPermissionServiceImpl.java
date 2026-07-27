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

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDocumentPermissionServiceImpl {

    private static final DocRef DEST = new DocRef("Dictionary", "dest-uuid", "dest");
    private static final DocRef SOURCE = new DocRef("Dictionary", "source-uuid", "source");

    private final DocumentPermissionDao documentPermissionDao = Mockito.mock(DocumentPermissionDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final DocumentPermissionServiceImpl service = new DocumentPermissionServiceImpl(
            documentPermissionDao,
            Mockito.mock(UserGroupsCache.class),
            Mockito.mock(PermissionChangeEventBus.class),
            securityContext);

    @Test
    void copyingPermissionsFromAnUnreadableSourceIsRejected() {
        // The caller owns the destination (so may change its permissions) but not the source, so it must not
        // be able to copy - and thereby disclose - the source's permission rows.
        when(securityContext.hasDocumentPermission(DEST, DocumentPermission.OWNER)).thenReturn(true);
        when(securityContext.hasDocumentPermission(SOURCE, DocumentPermission.OWNER)).thenReturn(false);

        final SingleDocumentPermissionChangeRequest request = new SingleDocumentPermissionChangeRequest(
                DEST, new AddAllPermissionsFrom(SOURCE));

        assertThatThrownBy(() -> service.changeDocumentPermissions(request))
                .isInstanceOf(PermissionException.class);
        verify(documentPermissionDao, never()).addDocumentPermissions(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void copyingPermissionsFromAnOwnedSourceIsAllowed() {
        when(securityContext.hasDocumentPermission(DEST, DocumentPermission.OWNER)).thenReturn(true);
        when(securityContext.hasDocumentPermission(SOURCE, DocumentPermission.OWNER)).thenReturn(true);

        final SingleDocumentPermissionChangeRequest request = new SingleDocumentPermissionChangeRequest(
                DEST, new SetAllPermissionsFrom(SOURCE));

        service.changeDocumentPermissions(request);

        verify(documentPermissionDao).setDocumentPermissions(SOURCE.getUuid(), DEST.getUuid());
    }
}
