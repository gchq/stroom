/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.client.component;

import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A text converter's Settings tab ([TextConverterSettings]), read only in a document the user
/// can't change.
public class TextConverterSettingsController extends Controller {

    private final TextConverterSettings view;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public TextConverterSettingsController(final EventBus eventBus, final TextConverterSettings view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a text converter's settings.
    ///
    /// @param docRef The document.
    /// @param textConverter Its settings.
    public void read(final DocRef docRef, final TextConverterDoc textConverter) {
        view.read(textConverter);
    }

    /// Writes the settings shown into a copy of a text converter.
    ///
    /// @param textConverter The document.
    /// @return A copy of it with the settings shown.
    public TextConverterDoc write(final TextConverterDoc textConverter) {
        return view.write(textConverter);
    }
}
