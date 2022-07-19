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
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.RichTextArea;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class CommonAlertViewImpl extends ViewImpl implements CommonAlertView {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final SimplePanel container = new SimplePanel();
    private final SimplePanel image = new SimplePanel();
    private final HTML message = new HTML();
    private final Hyperlink showHideDetail = new Hyperlink();
    private final RichTextArea detail = new RichTextArea();
    private boolean detailVisible = false;

    @Inject
    public CommonAlertViewImpl() {
        final HorizontalPanel layout = new HorizontalPanel();
        container.add(layout);

        layout.add(image);
        final VerticalPanel messageArea = new VerticalPanel();
        layout.add(messageArea);

        messageArea.add(message);
        messageArea.add(showHideDetail);
        messageArea.add(detail);
        detail.setEnabled(false);

        layout.addStyleName("alert-table");
        message.addStyleName("alert-message");
        showHideDetail.addStyleName("alert-showHide");
        detail.addStyleName("alert-detail");

        detail.addInitializeHandler(event -> {
            final Element e = detail.getElement();
            final IFrameElement ife = IFrameElement.as(e);
            final Document doc = ife.getContentDocument();
            doc.getBody().getStyle().setPadding(3, Unit.PX);
            doc.getBody().getStyle().setMargin(0, Unit.PX);
        });

        showHideDetail.setVisible(false);
        setDetailVisible(false);

        showHideDetail.addHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                showDetail(!detailVisible);
            }
        }, ClickEvent.getType());
    }

    private void setDetailVisible(final boolean visible) {
        detail.setVisible(visible);
    }

    @Override
    public Widget asWidget() {
        return container;
    }

    @Override
    public void setQuestion(final SafeHtml text) {
        image.getElement().setClassName("alert-icon-question");
        setHTML(text);
    }

    @Override
    public void setInfo(final SafeHtml text) {
        image.getElement().setClassName("alert-icon-info");
        setHTML(text);
    }

    @Override
    public void setWarn(final SafeHtml text) {
        image.getElement().setClassName("alert-icon-warning");
        setHTML(text);
    }

    @Override
    public void setError(final SafeHtml text) {
        image.getElement().setClassName("alert-icon-error");
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
            builder.appendHtmlConstant("<div style=\"font-family: Courier New; font-size: 10px; white-space: pre;\">");
            builder.append(html);
            builder.appendHtmlConstant("</div>");
            detail.setHTML(builder.toSafeHtml());
            showDetail(true);
        } else {
            showHideDetail.setVisible(false);
            setDetailVisible(false);
        }
    }

    private void showDetail(final boolean show) {
        this.detailVisible = show;
        showHideDetail.setVisible(true);
        if (show) {
            showHideDetail.setText("Hide Detail");
            setDetailVisible(true);
        } else {
            showHideDetail.setText("Show Detail");
            setDetailVisible(false);
        }
    }
}
