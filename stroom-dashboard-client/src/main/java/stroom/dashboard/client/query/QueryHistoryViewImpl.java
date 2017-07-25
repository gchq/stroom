/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.query;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.cell.list.client.CustomCellList;
import stroom.dashboard.client.query.QueryHistoryPresenter.QueryHistoryView;
import stroom.dashboard.shared.QueryEntity;

public class QueryHistoryViewImpl extends ViewImpl implements QueryHistoryView {
    private final Widget widget;
    @UiField(provided = true)
    CustomCellList<QueryEntity> cellList;
    @UiField
    SimplePanel bottom;

    @Inject
    public QueryHistoryViewImpl(final Binder binder) {
        cellList = new CustomCellList<QueryEntity>(new QueryCell());
        widget = binder.createAndBindUi(this);
    }

    @Override
    public CellList<QueryEntity> getCellList() {
        return cellList;
    }

    @Override
    public void setExpressionView(final View view) {
        bottom.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, QueryHistoryViewImpl> {
    }
}
