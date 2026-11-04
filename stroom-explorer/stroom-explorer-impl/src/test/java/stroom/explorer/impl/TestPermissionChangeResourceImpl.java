/*
 * Copyright 2026 Crown Copyright
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

package stroom.explorer.impl;

import stroom.credentials.shared.Credential;
import stroom.docref.DocRef;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestPermissionChangeResourceImpl {

    @Mock
    private PermissionChangeService mockPermissionChangeService;
    @Mock
    private EntityEventBus mockEntityEventBus;

    @Test
    void testChangeDocumentPermissions_firesEventForChangedDoc() {
        // Credentials are not explorer docs so go through this resource. Previously it fired an
        // event with a type-only DocRef, which threw an NPE as a DocRef must have a UUID.
        final DocRef docRef = DocRef.builder()
                .randomUuid()
                .type(Credential.TYPE)
                .name("My Credential")
                .build();
        final SingleDocumentPermissionChangeRequest request = new SingleDocumentPermissionChangeRequest(
                docRef,
                new SetPermission(UserRef.forUserUuid("user-uuid"), DocumentPermission.VIEW));

        final PermissionChangeResourceImpl resource = new PermissionChangeResourceImpl(
                () -> mockPermissionChangeService,
                () -> mockEntityEventBus);

        final Boolean result = resource.changeDocumentPermissions(request);

        assertThat(result).isTrue();
        Mockito.verify(mockPermissionChangeService).changeDocumentPermissions(request);

        final ArgumentCaptor<EntityEvent> eventCaptor = ArgumentCaptor.forClass(EntityEvent.class);
        Mockito.verify(mockEntityEventBus).fire(eventCaptor.capture());
        final EntityEvent event = eventCaptor.getValue();
        assertThat(event.getDocRef())
                .isEqualTo(docRef);
        assertThat(event.getAction())
                .isEqualTo(EntityAction.UPDATE);
    }
}
