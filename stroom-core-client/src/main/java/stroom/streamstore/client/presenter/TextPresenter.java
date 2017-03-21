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

package stroom.streamstore.client.presenter;

import java.util.List;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import stroom.util.shared.Highlight;
import stroom.editor.client.presenter.EditorPresenter;

public class TextPresenter extends MyPresenterWidget<TextPresenter.TextView> {
    public interface TextView extends View, HasUiHandlers<TextUiHandlers> {
        void setTextView(View view);

        void setPlayVisible(boolean visible);
    }

    private final EditorPresenter textPresenter;

    @Inject
    public TextPresenter(final EventBus eventBus, final TextView view, final EditorPresenter textPresenter) {
        super(eventBus, view);
        this.textPresenter = textPresenter;

        textPresenter.getIndicatorsOption().setAvailable(false);
        textPresenter.getIndicatorsOption().setOn(false);
        textPresenter.getLineNumbersOption().setAvailable(true);
        textPresenter.getLineNumbersOption().setOn(true);
        textPresenter.setReadOnly(true);

        view.setTextView(textPresenter.getView());
    }

    public void setUiHandlers(final TextUiHandlers uiHandlers) {
        getView().setUiHandlers(uiHandlers);
    }

    public void setText(final String text) {
        textPresenter.setText(text);
    }

    public void format() {
        textPresenter.format();
    }

    public void setFirstLineNumber(final int firstLineNumber) {
        textPresenter.setFirstLineNumber(firstLineNumber);
    }

    public void setHighlights(final List<Highlight> highlights) {
        textPresenter.setHighlights(highlights);
    }

    public void setControlsVisible(final boolean controlsVisible) {
        getView().setPlayVisible(controlsVisible);
        textPresenter.setControlsVisible(controlsVisible);
    }
}
