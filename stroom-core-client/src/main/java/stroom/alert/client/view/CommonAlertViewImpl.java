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

import stroom.alert.client.presenter.CommonAlertPresenter.CommonAlertView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.LinkButton;
import stroom.widget.popup.client.view.PopupUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class CommonAlertViewImpl extends ViewImpl implements CommonAlertView {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    // Regex is testing html, hence the <br>
    private static RegExp STACK_TRACE_PATTERN = RegExp.compile("<br>(\\sat |Caused by: )");
    // The button's text and name, whether the detail is shown or not: whether it is shown is the
    // button's expanded state, which screen readers announce and its arrow shows
    private static final SafeHtml SHOW_DETAIL_HTML = SafeHtmlUtils.fromSafeConstant(
            "<span class=\"alert-showHide-arrow\" aria-hidden=\"true\"></span>Show Detail");

    private final FlowPanel layout = new FlowPanel();
    private final SimplePanel image = new SimplePanel();
    private final HTML message = new HTML();
    private final LinkButton showHideDetail = new LinkButton();
    private final SimplePanel detail = new SimplePanel();
    private boolean detailVisible = false;

    @Inject
    public CommonAlertViewImpl() {
        final FlowPanel messageArea = new FlowPanel();
        messageArea.setStyleName("alert-message-area");
        messageArea.add(message);
        messageArea.add(showHideDetail);
        messageArea.add(detail);

        image.setStyleName("alert-icon");
        message.setStyleName("alert-message");
        showHideDetail.addStyleName("alert-showHide");
        showHideDetail.setHTML(SHOW_DETAIL_HTML);
        detail.setStyleName("alert-detail");
        // The button shows and hides the detail
        final String detailId = DOM.createUniqueId();
        detail.getElement().setId(detailId);
        showHideDetail.getElement().setAttribute("aria-controls", detailId);

        layout.setStyleName("alert-layout");
        layout.add(image);
        layout.add(messageArea);

        showHideDetail.setVisible(false);
        setDetailVisible(false);

        // Once in its dialog, make the dialog an alert dialog described by the message, so a screen
        // reader reads the message when the dialog opens, rather than just the button it focuses
        layout.addAttachHandler(event -> {
            if (event.isAttached()) {
                PopupUtil.setAlertDialogRole(layout.getElement(), getDescription());
            }
        });

        // A button, so this is a click, Enter or Space
        showHideDetail.addClickHandler(event -> showDetail(!detailVisible));
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

    private void setDetailVisible(final boolean visible) {
        detail.setVisible(visible);
    }

    @Override
    public Widget asWidget() {
        return layout;
    }

    @Override
    public void setQuestion(final SafeHtml text) {
        image.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.QUESTION));
        setHTML(text);
    }

    @Override
    public void setInfo(final SafeHtml text) {
        image.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.INFO));
        setHTML(text);
    }

    @Override
    public void setWarn(final SafeHtml text) {
        image.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.WARNING));
        setHTML(text);
    }

    @Override
    public void setError(final SafeHtml text) {
        image.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.ERROR));
        setHTML(text);
    }

    private void setHTML(final SafeHtml html) {
        if (html.asString().length() > MAX_MESSAGE_LENGTH) {
            message.setText("");
            setDetail(html);
        } else {
            message.setHTML(html);
        }
    }

    @Override
    public void setDetail(final SafeHtml html) {
        if (html != null && html.asString().length() > 0) {
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            builder.appendHtmlConstant("<div class=\"alert-detail-text\">");
            builder.append(html);
            builder.appendHtmlConstant("</div>");
            detail.getElement().setInnerHTML(builder.toSafeHtml().asString());
            // If it looks like a stack trace then your average user doesn't want to see that nastiness,
            // but if not then it is probably useful for them to see.
            showDetail(!STACK_TRACE_PATTERN.test(html.asString()));
        } else {
            showHideDetail.setVisible(false);
            setDetailVisible(false);
        }
    }

    private void showDetail(final boolean show) {
        this.detailVisible = show;
        showHideDetail.setVisible(true);
        showHideDetail.getElement().setAttribute("aria-expanded", String.valueOf(show));
        setDetailVisible(show);
    }
}
