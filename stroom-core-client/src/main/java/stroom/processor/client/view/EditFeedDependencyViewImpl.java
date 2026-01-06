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

package stroom.processor.client.view;

import stroom.processor.client.presenter.EditFeedDependencyPresenter.EditFeedDependencyView;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class EditFeedDependencyViewImpl extends ViewImpl implements EditFeedDependencyView {

    private final Widget widget;
    @UiField
    SimplePanel feed;
    @UiField
    SimplePanel type;

    @Inject
    public EditFeedDependencyViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setFeedView(final View view) {
        final Widget w = view.asWidget();
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        this.feed.setWidget(w);
    }

    @Override
    public void setTypeWidget(final Widget w) {
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        this.type.setWidget(w);
    }

    public interface Binder extends UiBinder<Widget, EditFeedDependencyViewImpl> {

    }
}
