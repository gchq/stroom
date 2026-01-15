/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.client;

import stroom.annotation.client.CommentEditPresenter.CommentEditView;
import stroom.editor.client.presenter.EditorPresenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CommentEditPresenter
        extends MyPresenterWidget<CommentEditView> {

    private final EditorPresenter editorPresenter;

    @Inject
    public CommentEditPresenter(final EventBus eventBus,
                                final CommentEditView view,
                                final EditorPresenter editorPresenter) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        view.setContent(editorPresenter.getView());
    }

    public void setText(final String text) {
        this.editorPresenter.setText(text);
    }

    public String getText() {
        return editorPresenter.getText();
    }

    public void focus() {
        editorPresenter.focus();
    }

    public interface CommentEditView extends View {

        void setContent(View view);
    }
}
