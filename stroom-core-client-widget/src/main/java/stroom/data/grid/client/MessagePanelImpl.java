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

package stroom.data.grid.client;

import stroom.util.shared.ErrorMessage;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.List;

public class MessagePanelImpl extends SimplePanel implements MessagePanel {

    private Label message;
    private boolean visible;

    public MessagePanelImpl() {
        setStyleName("dashboardVis-messageOuter");
        setVisible(false);
    }

    @Override
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

    @Override
    public void showMessage(final String message) {
        showMessage(SafeHtmlUtil.from(message));
    }

    @Override
    public void showMessage(final SafeHtml msg) {
        if (!visible) {
            if (message == null) {
                message = new Label("", false);
                message.setStyleName("dashboardVis-messageInner");
                add(message);
            }

            message.getElement().setInnerSafeHtml(msg);
            setVisible(true);
            visible = true;
        }
    }

    @Override
    public void hideMessage() {
        if (visible) {
            setVisible(false);
            visible = false;
        }
    }
}
