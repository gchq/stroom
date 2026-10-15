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

package stroom.alert.client.view;

import stroom.alert.client.presenter.PromptPresenter;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * A Stroom dialog asking the user for a value, in place of the browser's own prompt.
 */
public class PromptViewImpl extends ViewImpl implements PromptPresenter.PromptView {

    private final FlowPanel widget = new FlowPanel();
    private final Label message = new Label();
    private final TextBox value = new TextBox();
    private Runnable onEnter;

    public PromptViewImpl() {
        widget.setStyleName("max form promptViewImpl");
        message.setStyleName("promptViewImpl-message");
        value.addStyleName("w-100 allow-focus");
        value.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER && onEnter != null) {
                event.preventDefault();
                onEnter.run();
            }
        });
        widget.add(message);
        widget.add(value);
    }

    @Override
    public void setPrompt(final String text, final String initialValue) {
        message.setText(text);
        value.setText(initialValue == null
                ? ""
                : initialValue);
    }

    @Override
    public String getValue() {
        return value.getText();
    }

    @Override
    public void setOnEnter(final Runnable onEnter) {
        this.onEnter = onEnter;
    }

    @Override
    public void focus() {
        value.setFocus(true);
        value.selectAll();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }
}
