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

package stroom.editor.client.view;

import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.editor.client.presenter.HtmlPresenter.HtmlView;

public class HtmlViewImpl extends ViewImpl implements HtmlView {
    private ScrollPanel scrollPanel;

    public HtmlViewImpl() {
        this.scrollPanel = new ScrollPanel();
        scrollPanel.setWidth("100%");
        scrollPanel.setHeight("100%");
        scrollPanel.getElement().getStyle().setBackgroundColor("white");
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
