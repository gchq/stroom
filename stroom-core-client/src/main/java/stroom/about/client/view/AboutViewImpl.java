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

package stroom.about.client.view;

import stroom.about.client.presenter.AboutPresenter;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class AboutViewImpl extends ViewImpl implements AboutPresenter.AboutView {

    private final Widget widget;

    @UiField
    SimplePanel logo;
    @UiField
    SimplePanel html;
    @UiField
    Label buildVersion;
    @UiField
    Label buildDate;
    @UiField
    Label upDate;
    @UiField
    Label nodeName;

    @Inject
    public AboutViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        logo.getElement().setInnerSafeHtml(SvgImageUtil.toSafeHtml(SvgImage.LOGO));
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setHTML(final String h) {
        html.getElement().setInnerHTML(h);
    }

    @Override
    public HasText getBuildDate() {
        return buildDate;
    }

    @Override
    public HasText getBuildVersion() {
        return buildVersion;
    }

    @Override
    public HasText getUpDate() {
        return upDate;
    }

    @Override
    public HasText getNodeName() {
        return nodeName;
    }

    public interface Binder extends UiBinder<Widget, AboutViewImpl> {

    }
}
