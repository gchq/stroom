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

package stroom.pathways.client.view;

import stroom.pathways.client.presenter.TracesPresenter.TracesView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class TracesViewImpl
        extends ViewImpl
        implements TracesView {

    @UiField
    SimplePanel dataSource;
    @UiField
    Label label;
    @UiField
    SimplePanel topWidget;
    @UiField
    SimplePanel bottomWidget;

    private final Widget widget;

    @Inject
    public TracesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setDataSourceView(final View view) {
        dataSource.setWidget(view.asWidget());
    }

    @Override
    public void setLabel(final String label) {
        this.label.setText(label);
    }

    @Override
    public void setTopWidget(final View view) {
        this.topWidget.setWidget(view.asWidget());
    }

    @Override
    public void setBottomWidget(final Widget widget) {
        this.bottomWidget.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, TracesViewImpl> {

    }
}
