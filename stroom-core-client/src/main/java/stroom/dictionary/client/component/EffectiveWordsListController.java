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

package stroom.dictionary.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dictionary.shared.WordList;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The words a dictionary has once its imports are added ([EffectiveWordsList]): fetched for the
/// dictionary given, and again when refresh is pressed; a source dictionary's link opens it.
public class EffectiveWordsListController extends Controller {

    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private EffectiveWordsList view;
    private final RestFactory restFactory;
    private DocRef docRef;

    /// Made by GIN; [#attach(EffectiveWordsList)] gives it its list.
    ///
    /// @param eventBus    The application's event bus.
    /// @param restFactory Fetches the words.
    @Inject
    public EffectiveWordsListController(final EventBus eventBus,
                                        final RestFactory restFactory) {
        super(eventBus);
        this.restFactory = restFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget, e.g. to show it as a tab.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public EffectiveWordsListController attach(final EffectiveWordsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnRefresh(this::refresh);
        view.setOnOpen(source -> OpenDocumentEvent.fire(this, source, true));
        return this;
    }

    /// Shows a dictionary's effective words, fetching them.
    ///
    /// @param docRef The dictionary, or null to show none.
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        view.setDocRef(docRef);
        refresh();
    }

    /// Fetches the words again.
    public void refresh() {
        if (docRef == null) {
            view.setWordList(WordList.EMPTY);
        } else {
            final String uuid = docRef.getUuid();
            restFactory
                    .create(WORD_LIST_RESOURCE)
                    .method(resource -> resource.getWords(uuid))
                    .onSuccess(view::setWordList)
                    .onFailure(error -> AlertEvent.fireError(this, error.getMessage(), null))
                    .owner(this)
                    .taskMonitorFactory(view.getTaskMonitor())
                    .exec();
        }
    }
}
