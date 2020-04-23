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

package stroom.importexport.client.view;

import com.google.gwt.dom.client.Style;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter.ImportConfigConfirmView;
import stroom.widget.customdatebox.client.MyDateBox;

public class ImportConfigConfirmViewImpl extends ViewImpl implements ImportConfigConfirmView {
    private final Widget widget;
    @UiField
    Grid grid;
    @UiField
    SimplePanel dataGridView;
    @UiField
    MyDateBox enableFrom;
    @UiField
    CheckBox enableFilters;

    @Inject
    public ImportConfigConfirmViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        grid.getRowFormatter().getElement(0).getStyle().setHeight(100, Style.Unit.PCT);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDataGridView(final View view) {
        dataGridView.setWidget(view.asWidget());
    }

    @Override
    public Widget getDataGridViewWidget() {
        return dataGridView;
    }

    @Override
    public Long getEnableFromDate() {
        return enableFrom.getMilliseconds();
    }

    @Override
    public boolean isEnableFilters () {return  enableFilters.getValue();}

    @Override
    public void setEnableFilters (boolean enableFilters) {this.enableFilters.setValue(enableFilters);}

    public interface Binder extends UiBinder<Widget, ImportConfigConfirmViewImpl> {
    }
}
