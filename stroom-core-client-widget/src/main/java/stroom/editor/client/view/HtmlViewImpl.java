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

package stroom.editor.client.view;

import stroom.editor.client.presenter.HtmlPresenter.HtmlView;

import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class HtmlViewImpl extends ViewImpl implements HtmlView {

    private final ScrollPanel scrollPanel;

    public HtmlViewImpl() {
        this.scrollPanel = new ScrollPanel();
        scrollPanel.getElement().addClassName("max");
        scrollPanel.getElement().addClassName("info-page");
    }

    @Override
    public Widget asWidget() {
        return scrollPanel;
    }

    @Override
    public void setHtml(final String html) {
        final HTMLPanel htmlPanel = new HTMLPanel(html);
        scrollPanel.setWidget(htmlPanel);
    }
}
