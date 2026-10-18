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

import stroom.ai.client.AiCommandErrorPresenter.AiCommandErrorView;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// Laid out as an alert is (its styles), with a link to the command's documentation under the message.
public class AiCommandErrorViewImpl extends ViewWithUiHandlers<AiCommandErrorUiHandlers>
        implements AiCommandErrorView {

    private final FlowPanel layout = new FlowPanel();
    private final Label message = new Label();
    private final Hyperlink link = new Hyperlink();

    /// Makes the dialog's widgets.
    public AiCommandErrorViewImpl() {
        final SimplePanel image = new SimplePanel();
        image.setStyleName("alert-icon");
        image.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.WARNING));
        message.setStyleName("alert-message");
        link.addStyleName("alert-showHide");
        link.addHandler(event -> {
            if (MouseUtil.isPrimary(event) && getUiHandlers() != null) {
                getUiHandlers().onOpenReference();
            }
        }, ClickEvent.getType());

        final FlowPanel messageArea = new FlowPanel();
        messageArea.setStyleName("alert-message-area");
        messageArea.add(message);
        messageArea.add(link);
        layout.setStyleName("alert-layout");
        layout.add(image);
        layout.add(messageArea);
    }

    @Override
    public Widget asWidget() {
        return layout;
    }

    @Override
    public void setMessage(final String message, final String linkText) {
        this.message.setText(message);
        link.setText(linkText == null
                ? ""
                : linkText);
        link.setVisible(linkText != null);
    }
}
