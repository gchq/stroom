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

package stroom.datagen.client.component;

import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.editor.client.component.CodeEditorController;
import stroom.explorer.client.component.DocSelectionController;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A data generator's Settings tab ([DataGenSettings]): the feed the generated data is sent to and
/// the template that is sent, which can't be changed in a document the user can't change.
public class DataGenSettingsController extends Controller {

    private final DocSelectionController destinationFeed;
    private final CodeEditorController template;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The tab.
    /// @param docSelectionProvider         Makes the destination feed's picker's controller.
    /// @param codeEditorControllerProvider Makes the template's editor's controller.
    @Inject
    public DataGenSettingsController(final EventBus eventBus,
                                     final DataGenSettings view,
                                     final Provider<DocSelectionController> docSelectionProvider,
                                     final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        initWidget(view);

        destinationFeed = make(docSelectionProvider);
        destinationFeed.attach(view.getDestinationFeedBox());
        destinationFeed.setIncludedTypes(FeedDoc.TYPE);
        destinationFeed.setRequiredPermissions(DocumentPermission.VIEW);
        template = make(codeEditorControllerProvider).attach(view.getTemplateEditor());

        own(destinationFeed.addSelectionListener(e -> markChanged()));
        own(template.addValueChangeHandler(e -> markChanged()));
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows a data generator's settings.
    ///
    /// @param docRef     The data generator.
    /// @param dataGenDoc Its settings.
    public void read(final DocRef docRef, final DataGenDoc dataGenDoc) {
        // Set unconditionally - a null docRef clears the box, which is what a doc with no feed
        // should show. Guarding on null would leave a previous read's selection in place, and
        // write would then write it straight back.
        destinationFeed.setSelectedEntityReference(dataGenDoc.getFeed(), true);
        template.setText(dataGenDoc.getTemplate());
    }

    /// Writes the settings shown into a copy of a data generator.
    ///
    /// @param doc The data generator.
    /// @return A copy of it with the settings shown.
    public DataGenDoc write(final DataGenDoc doc) {
        return doc
                .copy()
                .template(template.getText())
                .feed(destinationFeed.getSelectedEntityReference())
                .build();
    }

    private void setReadOnly(final boolean readOnly) {
        destinationFeed.setReadOnly(readOnly);
        template.setReadOnly(readOnly);
        template.getFormatAction().setAvailable(!readOnly);
    }
}
