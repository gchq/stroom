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

package stroom.search.solr.client.view;

import stroom.search.solr.client.presenter.SolrIndexFieldListPresenter.SolrIndexFieldListView;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class SolrIndexFieldListViewImpl extends ViewImpl implements SolrIndexFieldListView {

    private final Widget widget;

    @UiField
    SimplePanel dataGrid;
    @UiField
    HTML synchState;

    @Inject
    public SolrIndexFieldListViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDataGridView(final View view) {
        dataGrid.setWidget(view.asWidget());
    }

    @Override
    public void setSynchState(final String syncState) {
        this.synchState.setHTML(SafeHtmlUtils.fromTrustedString(syncState));
    }

    public interface Binder extends UiBinder<Widget, SolrIndexFieldListViewImpl> {

    }
}
