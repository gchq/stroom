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

package stroom.data.client.view;

import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.MetaPresenter.MetaView;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Objects;

public class MetaViewImpl extends ViewWithUiHandlers<QuickFilterUiHandlers> implements MetaView {

    private final Widget widget;

    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel streamList;
    @UiField
    SimplePanel streamRelationList;
    @UiField
    SimplePanel data;

    @Inject
    public MetaViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        content.addStyleName("dashboard-panel overflow-hidden");
        if (MetaPresenter.STREAM_LIST.equals(slot)) {
            streamList.setWidget(content);
        } else if (MetaPresenter.STREAM_RELATION_LIST.equals(slot)) {
            streamRelationList.setWidget(content);
        } else if (MetaPresenter.DATA.equals(slot)) {
            data.setWidget(content);
        }
    }

    @Override
    public void focus() {
        quickFilter.focus();
    }

    @Override
    public void setQuickFilterText(final String quickFilterText) {
        final String currVal = quickFilter.getText();
        quickFilter.setText(quickFilterText);
        if (!Objects.equals(currVal, quickFilterText)) {
            getUiHandlers().onFilterChange(quickFilterText);
        }
    }

    @UiHandler("quickFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().onFilterChange(quickFilter.getText());
    }

    public interface Binder extends UiBinder<Widget, MetaViewImpl> {

    }
}
