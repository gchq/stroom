/*
 * Copyright 2025 Crown Copyright
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

package stroom.widget.popup.client.presenter;

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.TextBoxPopup.TextBoxView;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class TextBoxPopup extends MyPresenterWidget<TextBoxView> {

    static final String NO_NAME_MESSAGE = "You must provide a name";

    @Inject
    public TextBoxPopup(final EventBus eventBus,
                        final TextBoxView view) {
        super(eventBus, view);
    }

    /// Shows the popup. OK with a blank name says so under the field and keeps the popup open.
    ///
    /// @param caption  The popup's caption.
    /// @param consumer Given the name when OK is pressed with one.
    public void show(final String caption, final Consumer<String> consumer) {
        getView().setValid();
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String text = getText();
                        if (text != null && !text.isBlank()) {
                            getView().setValid();
                            consumer.accept(text);
                            e.hide();
                        } else {
                            getView().setInvalid(NO_NAME_MESSAGE);
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    public String getText() {
        return getView().getText();
    }

    public void setText(final String text) {
        getView().setText(text);
    }

    public interface TextBoxView extends View, Focus {

        String getText();

        void setText(String text);

        /// Marks the name invalid, showing why under it and moving the focus to it.
        ///
        /// @param message What is wrong with the name.
        void setInvalid(String message);

        /// Marks the name valid, clearing any message.
        void setValid();
    }
}
