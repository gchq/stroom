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

package stroom.security.client.view;

import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter.DocumentCreatePermissionsListView;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class DocumentCreatePermissionsListViewImpl
        extends ViewWithUiHandlers<QuickFilterUiHandlers>
        implements DocumentCreatePermissionsListView {

    @UiField
    SimplePanel dataGrid;
    @UiField
    HTML details;
    @UiField
    CustomCheckBox includeDescendants;

    private final Widget widget;

    @Inject
    public DocumentCreatePermissionsListViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setTable(final Widget widget) {
        dataGrid.setWidget(widget);
    }

    @Override
    public void setDetails(final SafeHtml details) {
        this.details.setHTML(details);
    }

    @Override
    public boolean isIncludeDescendants() {
        return includeDescendants.getValue();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, DocumentCreatePermissionsListViewImpl> {

    }
}
