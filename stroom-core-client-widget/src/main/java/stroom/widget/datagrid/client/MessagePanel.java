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

package stroom.widget.datagrid.client;

import stroom.util.shared.ErrorMessage;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A message shown over the middle of what it is put in, e.g. a list's or a visualisation's
/// errors; hidden until there is one to show.
public class MessagePanel extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html message;

    /// Makes a hidden message panel, e.g. one a template declares.
    public MessagePanel() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows errors, one line for each line of their messages, unless a message is showing.
    ///
    /// @param errors The errors, or null or empty for none (which shows nothing).
    public void showMessage(final List<ErrorMessage> errors) {
        if (errors != null && !errors.isEmpty()) {
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            for (final ErrorMessage error : errors) {
                final String[] lines = error.getMessage().split("\n");
                for (final String line : lines) {
                    sb.appendEscaped(line);
                    sb.appendHtmlConstant("<br />");
                }
            }
            showMessage(sb.toSafeHtml());
        }
    }

    /// Shows a message as text, unless a message is showing.
    ///
    /// @param message The message.
    public void showMessage(final String message) {
        showMessage(SafeHtmlUtil.from(message));
    }

    /// Shows a message, unless a message is showing.
    ///
    /// @param msg The message.
    public void showMessage(final SafeHtml msg) {
        if (!isVisible()) {
            message.setHTML(msg);
            setVisible(true);
        }
    }

    /// Hides the message.
    public void hideMessage() {
        setVisible(false);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MessagePanel> {

    }
}
