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

package stroom.query.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.ExplorerConstants;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.query.shared.SuggestionsResource;
import stroom.security.client.component.UserRefSelectionController;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.datepicker.client.component.DateTimeBoxController;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Objects;
import java.util.function.Consumer;

/// What a [TermEditor] can't do itself: the document and user pickers its boxes are, the dialogs
/// its date boxes open, the values it suggests (fetched from the term's data source), and the
/// field filter's help link. Owned by the expression tree's controller, which attaches one to
/// each of its term editors.
public class TermEditorController extends Controller implements TermEditor.Pickers {

    private static final SuggestionsResource SUGGESTIONS_RESOURCE = GWT.create(SuggestionsResource.class);

    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final Provider<DocSelectionController> docSelectionProvider;
    private final Provider<UserRefSelectionController> userRefSelectionProvider;
    private final Provider<DateTimeBoxController> dateTimeBoxControllerProvider;
    private DocSelectionController docSelection;
    private UserRefSelectionController userRefSelection;

    /// Made by GIN; [#attach(TermEditor)] gives it its editor.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param restFactory                   Fetches the values suggested.
    /// @param uiConfigCache                 Says where the field filter's help is.
    /// @param docSelectionProvider          Makes the document picker.
    /// @param userRefSelectionProvider      Makes the user picker.
    /// @param dateTimeBoxControllerProvider Makes what opens each date box's dialog.
    @Inject
    public TermEditorController(final EventBus eventBus,
                                final RestFactory restFactory,
                                final UiConfigCache uiConfigCache,
                                final Provider<DocSelectionController> docSelectionProvider,
                                final Provider<UserRefSelectionController> userRefSelectionProvider,
                                final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.docSelectionProvider = docSelectionProvider;
        this.userRefSelectionProvider = userRefSelectionProvider;
        this.dateTimeBoxControllerProvider = dateTimeBoxControllerProvider;
    }

    /// Attaches this to the editor whose pickers, dialogs and suggestions it provides.
    ///
    /// @param editor The editor.
    /// @return This, for its owner to keep.
    public TermEditorController attach(final TermEditor editor) {
        Objects.requireNonNull(editor, "editor");
        docSelection = make(docSelectionProvider);
        docSelection.setRequiredPermissions(DocumentPermission.USE);
        docSelection.attach(editor.getDocRefBox());
        userRefSelection = make(userRefSelectionProvider).attach(editor.getUserRefBox());
        for (final DateTimeBox box : editor.getDateBoxes()) {
            make(dateTimeBoxControllerProvider).attach(box);
        }
        editor.setPickers(this);
        editor.setSuggestionFetcher(this::fetchSuggestions);
        onDispose(editor::cancelSuggestions);
        uiConfigCache.get(uiConfig ->
                NullSafe.consume(uiConfig, ExtendedUiConfig::getHelpUrl, editor::setFieldFilterHelpUrl));
        return this;
    }

    @Override
    public void showDocRef(final QueryField field, final Condition condition, final DocRef docRef) {
        docSelection.setAllowFolderSelection(false);
        if (Condition.IN_DICTIONARY.equals(condition)) {
            docSelection.setIncludedTypes("Dictionary");
        } else if (Condition.IN_FOLDER.equals(condition) || Condition.OF_DOC_REF.equals(condition)) {
            docSelection.setIncludedTypes(ExplorerConstants.FOLDER_LIKE);
            docSelection.setAllowFolderSelection(true);
        } else if (FieldType.DOC_REF.equals(field.getFldType())) {
            if (field.getDocRefType() != null) {
                docSelection.setIncludedTypes(field.getDocRefType());
            } else {
                docSelection.setAllowFolderSelection(true);
            }
        }
        docSelection.setSelectedEntityReference(docRef, true);
    }

    @Override
    public DocRef getDocRef() {
        return docSelection.getSelectedEntityReference();
    }

    @Override
    public HandlerRegistration addDocRefListener(final Runnable listener) {
        //noinspection unused docRef
        return docSelection.addSelectionListener(docRef -> listener.run());
    }

    @Override
    public void showUserRef(final UserRef userRef) {
        userRefSelection.setSelected(userRef);
    }

    @Override
    public UserRef getUserRef() {
        return userRefSelection.getSelected();
    }

    private void fetchSuggestions(final FetchSuggestionsRequest request, final Consumer<Suggestions> consumer) {
        restFactory
                .create(SUGGESTIONS_RESOURCE)
                .method(res -> res.fetch(request))
                .onSuccess(consumer)
                .owner(this)
                .exec();
    }
}
