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

package stroom.security.client.presenter;

import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class TestDocumentUserPermissionsEditPresenter {

    @Test
    void testCreatePermissionChangesNotifyParent() {
        try (final MockedStatic<GWT> ignored = mockStatic(GWT.class)) {
            final DocRef document = new DocRef("Folder", "document", "Folder");
            final UserRef user = UserRef.forUserUuid("user");
            final DocumentUserCreatePermissionsEditPresenter createEditor =
                    mock(DocumentUserCreatePermissionsEditPresenter.class);
            final DocumentUserPermissionsEditPresenter editor = new DocumentUserPermissionsEditPresenter(
                    mock(EventBus.class), mock(DocumentUserPermissionsEditView.class),
                    mock(DocPermissionRestClient.class), mock(ExplorerClient.class),
                    mock(PermissionChangeClient.class), () -> createEditor);
            final Runnable onChange = mock(Runnable.class);
            editor.show(document, new DocumentUserPermissions(user, DocumentPermission.USE, Set.of()),
                    onChange, editor);

            editor.onEditCreatePermissions(editor);
            final ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
            verify(createEditor).show(eq(document), eq(user), callback.capture(), eq(editor));
            callback.getValue().run();

            verify(onChange).run();
        }
    }
}
