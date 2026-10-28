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

package stroom.alert.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.popup.client.PopupUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

/// An alert's or confirmation's content: an icon for its level, its message, and a detail (e.g. a
/// stack trace) that Show Detail shows and hides. A message too long to show is put in the detail.
public class CommonAlert extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final int MAX_MESSAGE_LENGTH = 1000;
    // Regex is testing html, hence the <br>
    private static final RegExp STACK_TRACE_PATTERN = RegExp.compile("<br>(\\sat |Caused by: )");

    @UiField
    DivElement icon;
    @UiField
    HTML message;
    @UiField
    LinkButton showHideDetail;
    @UiField
    AriaPanel detail;

    private boolean detailVisible;

    /// Makes empty content.
    public CommonAlert() {
        initWidget(BINDER.createAndBindUi(this));
        // The button shows and hides the detail
        final String detailId = DOM.createUniqueId();
        detail.getElement().setId(detailId);
        showHideDetail.getElement().setAttribute("aria-controls", detailId);
        // A button, so this is a click, Enter or Space
        showHideDetail.setOnClick(() -> showDetail(!detailVisible));

        // Once in its dialog, make the dialog an alert dialog described by the message, so a screen
        // reader reads the message when the dialog opens, rather than just the button it focuses
        addAttachHandler(event -> {
            if (event.isAttached()) {
                PopupUtil.setAlertDialogRole(getElement(), getDescription());
            }
        });
    }

    /// @param text A question, e.g. a confirmation's.
    public void setQuestion(final SafeHtml text) {
        setMessage(SvgImage.QUESTION, text);
    }

    /// @param text Information.
    public void setInfo(final SafeHtml text) {
        setMessage(SvgImage.INFO, text);
    }

    /// @param text A warning.
    public void setWarn(final SafeHtml text) {
        setMessage(SvgImage.WARNING, text);
    }

    /// @param text An error.
    public void setError(final SafeHtml text) {
        setMessage(SvgImage.ERROR, text);
    }

    /// Sets the detail, shown at once unless it looks like a stack trace, which most users don't
    /// want to see.
    ///
    /// @param html The detail, or null or empty for none.
    public void setDetail(final SafeHtml html) {
        if (html != null && !html.asString().isEmpty()) {
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            builder.appendHtmlConstant("<div class=\"common-alert__detail-text\">");
            builder.append(html);
            builder.appendHtmlConstant("</div>");
            detail.getElement().setInnerHTML(builder.toSafeHtml().asString());
            showDetail(!STACK_TRACE_PATTERN.test(html.asString()));
        } else {
            showHideDetail.setVisible(false);
            detail.setVisible(false);
        }
    }

    private void setMessage(final SvgImage svgImage, final SafeHtml html) {
        icon.setInnerSafeHtml(SvgImageUtil.toSafeHtml(svgImage));
        if (html.asString().length() > MAX_MESSAGE_LENGTH) {
            message.setText("");
            setDetail(html);
        } else {
            message.setHTML(html);
        }
    }

    // The message, or the detail if the message was too long to show and so is in the detail instead
    private Element getDescription() {
        if (!message.getText().trim().isEmpty()) {
            return message.getElement();
        }
        return detail.getElement().hasChildNodes()
                ? detail.getElement()
                : null;
    }

    private void showDetail(final boolean show) {
        this.detailVisible = show;
        showHideDetail.setVisible(true);
        showHideDetail.getElement().setAttribute("aria-expanded", String.valueOf(show));
        detail.setVisible(show);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CommonAlert> {

    }
}
