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

package stroom.dashboard.client.table;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.widget.layout.client.view.ResizeFlowPanel;
import stroom.widget.spinner.client.SpinnerSmall;

public class TableViewImpl extends ViewImpl
        implements TableView {
    private static TableResources resources;
    private final Widget widget;
    private final SpinnerSmall spinnerSmall;

    @UiField
    ResizeFlowPanel layout;

    @Inject
    public TableViewImpl(final Binder binder) {
        if (resources == null) {
            resources = GWT.create(TableResources.class);
            final TableStyle style = resources.style();
            style.ensureInjected();
        }

        widget = binder.createAndBindUi(this);

        spinnerSmall = new SpinnerSmall();
        spinnerSmall.setStyleName(resources.style().smallSpinner());
        spinnerSmall.setVisible(false);

        layout.add(spinnerSmall);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTableView(final View view) {
        layout.add(view.asWidget());
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        spinnerSmall.setVisible(refreshing);
    }

    public interface Binder extends UiBinder<Widget, TableViewImpl> {
    }
}
