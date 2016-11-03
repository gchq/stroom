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

package stroom.dashboard.client.query;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.dashboard.client.query.QueryFavouritesPresenter.QueryFavouritesView;
import stroom.dashboard.shared.Query;
import stroom.cell.list.client.CustomCellList;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.ImageButtonView;

public class QueryFavouritesViewImpl extends ViewImpl implements QueryFavouritesView {
    public interface Binder extends UiBinder<Widget, QueryFavouritesViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    CustomCellList<Query> cellList;
    @UiField
    SimplePanel bottom;
    @UiField
    ButtonPanel buttonPanel;

    @Inject
    public QueryFavouritesViewImpl(final Binder binder) {
        cellList = new CustomCellList<Query>(new QueryCell());
        widget = binder.createAndBindUi(this);
    }

    @Override
    public CellList<Query> getCellList() {
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

    @Override
    public ImageButtonView addButton(final String title, final ImageResource enabledImage,
            final ImageResource disabledImage, final boolean enabled) {
        return buttonPanel.add(title, enabledImage, disabledImage, enabled);
    }

    @Override
    public GlyphButtonView addButton(final GlyphIcon preset) {
        return buttonPanel.add(preset);
    }
}
