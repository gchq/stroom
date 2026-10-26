/*
 * Copyright 2021 Crown Copyright
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

package stroom.query.client.view;

import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.widget.layout.client.ScrollPane;
import stroom.widget.presenter.client.View;
import stroom.widget.presenter.client.ViewImpl;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class ResultStoreViewImpl extends ViewImpl implements ResultStoreView {

    private final Widget widget;

    @UiField
    ScrollPane infoScroll;
    @UiField
    SimplePanel tableContainer;
    @UiField
    SimplePanel infoPanel;

    @Inject
    public ResultStoreViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        // The store's details scroll when they are long, so the keyboard can reach them to scroll
        // them: a named region that takes the focus
        infoScroll.getElement().setTabIndex(0);
        infoScroll.getElement().setAttribute("role", "region");
        infoScroll.getElement().setAttribute("aria-label", "Info");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setListView(final View view) {
        tableContainer.setWidget(view.asWidget());
    }

    @Override
    public void setData(final SafeHtml safeHtml) {
        infoPanel.getElement().setInnerHTML(safeHtml.asString());
    }

    public interface Binder extends UiBinder<Widget, ResultStoreViewImpl> {

    }
}
