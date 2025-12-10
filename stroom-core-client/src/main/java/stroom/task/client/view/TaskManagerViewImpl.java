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

package stroom.task.client.view;

import stroom.task.client.presenter.TaskManagerPresenter;
import stroom.task.client.presenter.TaskManagerUiHandlers;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class TaskManagerViewImpl extends ViewWithUiHandlers<TaskManagerUiHandlers>
        implements TaskManagerPresenter.TaskManagerView {

    private final Widget widget;

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public TaskManagerViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @Override
    public void setList(final Widget list) {
        listContainer.setWidget(list);
    }

    @Override
    public void focusFilter() {
        nameFilter.focus();
    }

    @Override
    public void setNameFilter(final String nameFilter) {
        this.nameFilter.setText(NullSafe.get(nameFilter, String::trim));
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, TaskManagerViewImpl> {

    }
}
