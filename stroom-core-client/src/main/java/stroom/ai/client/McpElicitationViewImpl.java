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

package stroom.ai.client;

import stroom.ai.client.McpElicitationPresenter.McpElicitationView;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class McpElicitationViewImpl extends ViewWithUiHandlers<McpElicitationUiHandlers>
        implements McpElicitationView {

    private final Widget widget;

    @UiField
    Label message;
    @UiField
    Label warning;
    @UiField
    SimplePanel form;
    @UiField
    Button decline;

    @Inject
    public McpElicitationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setForm(final String server, final String text, final Widget fields) {
        // The server's text is shown as plain text, never as HTML.
        message.setText(text);
        warning.setText("Your answer goes to " + server + ", not to the AI model. Don't enter passwords or "
                        + "other secrets.");
        form.setWidget(fields);
    }

    @UiHandler("decline")
    public void onDecline(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDecline();
        }
    }

    public interface Binder extends UiBinder<Widget, McpElicitationViewImpl> {

    }
}
