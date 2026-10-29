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

import stroom.editor.client.component.CodeEditorController;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// A dictionary's Words tab ([WordsTab]): its words in a plain text editor, read only when its
/// document is.
public class WordsTabController extends Controller {

    private final CodeEditorController editor;
    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The tab.
    /// @param codeEditorControllerProvider Makes the code editor's controller.
    @Inject
    public WordsTabController(final EventBus eventBus,
                              final WordsTab view,
                              final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        initWidget(view);
        editor = make(codeEditorControllerProvider).attach(view.getEditor());
        editor.setMode(AceEditorMode.TEXT);
        // Text only, no styling or formatting
        editor.getOptions().getStylesOption().setUnavailable();
        editor.getFormatAction().setUnavailable();
        own(editor.addValueChangeHandler(event -> onChange.run()));
        own(editor.addFormatListener(() -> onChange.run()));
        // Read only as its document is
        watch(Contexts.READ_ONLY, editor::setReadOnly);
    }

    /// @param onChange What to run when the user changes the words.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param words The words, one a line.
    public void setWords(final String words) {
        editor.setText(words);
    }

    /// @return The words, one a line.
    public String getWords() {
        return editor.getText();
    }
}
