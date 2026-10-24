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

import stroom.editor.client.presenter.EditorPresenter;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.PresenterAdapter;

import com.google.inject.Inject;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import javax.inject.Provider;

/// A dictionary's Words tab: its words, one a line, in a plain text editor. It is read only when
/// its document is.
public class WordsTab extends Controller {

    private final PresenterAdapter<EditorPresenter> editor;
    private Runnable onChange = () -> {
    };

    @Inject
    public WordsTab(final Provider<EditorPresenter> editorPresenterProvider) {
        editor = own(PresenterAdapter.of(editorPresenterProvider::get)
                .onReadOnly(EditorPresenter::setReadOnly));
        final EditorPresenter editorPresenter = editor.getPresenter();
        editorPresenter.setMode(AceEditorMode.TEXT);
        // Text only, no styling or formatting
        editorPresenter.getStylesOption().setUnavailable();
        editorPresenter.getFormatAction().setUnavailable();
        own(editorPresenter.addValueChangeHandler(event -> onChange.run()));
        own(editorPresenter.addFormatHandler(event -> onChange.run()));
        initWidget(editor.asWidget());
    }

    /// @param onChange What to run when the user changes the words.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param words The words, one a line.
    public void setWords(final String words) {
        editor.getPresenter().setText(words);
    }

    /// @return The words, one a line.
    public String getWords() {
        return editor.getPresenter().getText();
    }
}
