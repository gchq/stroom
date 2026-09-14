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
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.client.Preset;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDocumentUserPermissionsPresenter {

    private static final DocRef DOCUMENT = new DocRef("Folder", "document", "Folder");
    private static final UserRef FIRST_USER = UserRef.forUserUuid("first");
    private static final UserRef SECOND_USER = UserRef.forUserUuid("second");

    private MockedStatic<GWT> gwt;
    private MockedStatic<SafeHtmlUtil> html;
    private DocPermissionRestClient client;
    private DocumentUserPermissionsView view;
    private DocumentUserPermissionsListPresenter list;
    private DocumentUserPermissionsEditPresenter editor;
    private MultiSelectionModel<DocumentUserPermissions> selection;
    private MultiSelectEvent.Handler handler;
    private DocumentUserPermissionsPresenter presenter;
    private final List<Consumer<DocumentUserPermissionsReport>> responses = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gwt = mockStatic(GWT.class);
        html = mockStatic(SafeHtmlUtil.class, CALLS_REAL_METHODS);
        final SafeHtmlUtil.Template template = mock(SafeHtmlUtil.Template.class);
        html.when(SafeHtmlUtil::getTemplate).thenReturn(template);
        when(template.spanWithClass(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        client = mock(DocPermissionRestClient.class);
        view = mock(DocumentUserPermissionsView.class);
        list = mock(DocumentUserPermissionsListPresenter.class);
        editor = mock(DocumentUserPermissionsEditPresenter.class);
        selection = mock(MultiSelectionModel.class);
        final SelectionBox<PermissionShowLevel> visibility = mock(SelectionBox.class);
        when(view.getPermissionVisibility()).thenReturn(visibility);
        when(list.getSelectionModel()).thenReturn(selection);
        when(list.addButton(any(Preset.class))).thenReturn(mock(ButtonView.class));
        doAnswer(invocation -> {
            responses.add(invocation.getArgument(2));
            return null;
        }).when(client).getDocUserPermissionsReport(any(), any(), any(), any());
        presenter = new DocumentUserPermissionsPresenter(
                mock(EventBus.class), client, view, list, () -> editor);
        presenter.onBind();
        final ArgumentCaptor<MultiSelectEvent.Handler> captor = ArgumentCaptor.forClass(MultiSelectEvent.Handler.class);
        verify(selection).addSelectionHandler(captor.capture());
        handler = captor.getValue();
        presenter.setDocRef(DOCUMENT);
        clearInvocations(view, list);
    }

    @AfterEach
    void tearDown() {
        html.close();
        gwt.close();
    }

    @Test
    void testEditRefreshesListAndDetails() {
        select(FIRST_USER, true);
        final ArgumentCaptor<Runnable> onChange = ArgumentCaptor.forClass(Runnable.class);
        verify(editor).show(eq(DOCUMENT), any(), onChange.capture(), eq(presenter));

        onChange.getValue().run();

        verify(list).refresh();
        verify(client, times(2)).getDocUserPermissionsReport(eq(DOCUMENT), eq(FIRST_USER), any(), eq(presenter));
    }

    @Test
    void testEditDiscardsOlderReportForSameUser() {
        select(FIRST_USER, true);
        final ArgumentCaptor<Runnable> onChange = ArgumentCaptor.forClass(Runnable.class);
        verify(editor).show(eq(DOCUMENT), any(), onChange.capture(), eq(presenter));
        onChange.getValue().run();
        responses.get(1).accept(report(DocumentPermission.OWNER));
        responses.get(0).accept(report(DocumentPermission.USE));

        final ArgumentCaptor<SafeHtml> details = ArgumentCaptor.forClass(SafeHtml.class);
        verify(view).setDetails(details.capture());
        assertThat(details.getValue().asString()).contains("OWNER");
    }

    @Test
    void testOlderSelectionResponseIsIgnored() {
        select(FIRST_USER, false);
        select(SECOND_USER, false);
        responses.get(1).accept(report(DocumentPermission.OWNER));
        responses.get(0).accept(report(DocumentPermission.USE));

        verify(view, never()).setUserRef(FIRST_USER);
        verify(view).setUserRef(SECOND_USER);
        final ArgumentCaptor<SafeHtml> details = ArgumentCaptor.forClass(SafeHtml.class);
        verify(view).setDetails(details.capture());
        assertThat(details.getValue().asString()).contains("OWNER");
    }

    @Test
    void testClearingSelectionDiscardsPendingResponse() {
        select(FIRST_USER, false);
        select(null, false);
        responses.get(0).accept(report(DocumentPermission.OWNER));

        verify(view, never()).setUserRef(FIRST_USER);
        verify(view).setUserRef(null);
    }

    @Test
    void testResponseForPreviousDocumentIsIgnored() {
        select(FIRST_USER, false);
        presenter.setDocRef(new DocRef("Folder", "other", "Other"));
        clearInvocations(view);
        responses.get(0).accept(report(DocumentPermission.OWNER));

        verify(view, never()).setDetails(any());
    }

    @Test
    void testReopeningDocumentDiscardsPendingReport() {
        select(FIRST_USER, false);
        presenter.setDocRef(new DocRef("Folder", "other", "Other"));
        presenter.setDocRef(DOCUMENT);
        responses.get(0).accept(report(DocumentPermission.OWNER));

        verify(view, never()).setDetails(any());
    }

    private void select(final UserRef user, final boolean edit) {
        when(selection.getSelected()).thenReturn(user == null
                ? null
                : new DocumentUserPermissions(user, DocumentPermission.USE, Set.of()));
        final MultiSelectEvent event = mock(MultiSelectEvent.class);
        final SelectionType type = mock(SelectionType.class);
        when(event.getSelectionType()).thenReturn(type);
        when(type.isDoubleSelect()).thenReturn(edit);
        handler.onSelect(event);
    }

    private DocumentUserPermissionsReport report(final DocumentPermission permission) {
        return new DocumentUserPermissionsReport(permission, Set.of(), null, null);
    }
}
