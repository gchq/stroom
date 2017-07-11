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

package stroom.dashboard.client.table;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Filter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class FilterPresenter extends MyPresenterWidget<FilterPresenter.FilterView>implements PopupUiHandlers {
    private TablePresenter tablePresenter;
    private Field field;
    @Inject
    public FilterPresenter(final EventBus eventBus, final FilterView view) {
        super(eventBus, view);
    }

    public void show(final TablePresenter tablePresenter, final Field field) {
        this.tablePresenter = tablePresenter;
        this.field = field;

        String includes = "";
        String excludes = "";

        if (field.getFilter() != null) {
            if (field.getFilter().getIncludes() != null) {
                includes = field.getFilter().getIncludes();
            }
            if (field.getFilter().getExcludes() != null) {
                excludes = field.getFilter().getExcludes();
            }
        }

        getView().setIncludes(includes);
        getView().setExcludes(excludes);

        final PopupSize popupSize = new PopupSize(400, 500, 300, 300, true);
        ShowPopupEvent.fire(tablePresenter, this, PopupType.OK_CANCEL_DIALOG, popupSize,
                "Filter '" + field.getName() + "'", this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final Filter filter = getFilter();
            if ((filter == null && field.getFilter() != null)
                    || (filter != null && !filter.equals(field.getFilter()))) {
                field.setFilter(filter);
                tablePresenter.setDirty(true);
                tablePresenter.clearAndRefresh();
            }
        }

        HidePopupEvent.fire(tablePresenter, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    private Filter getFilter() {
        Filter filter = null;

        if (getView().getIncludes() != null && getView().getIncludes().trim().length() > 0) {
            if (filter == null) {
                filter = new Filter();
            }
            filter.setIncludes(getView().getIncludes().trim());
        }
        if (getView().getExcludes() != null && getView().getExcludes().trim().length() > 0) {
            if (filter == null) {
                filter = new Filter();
            }
            filter.setExcludes(getView().getExcludes().trim());
        }

        return filter;
    }

    public interface FilterView extends View {
        String getIncludes();

        void setIncludes(String includes);

        String getExcludes();

        void setExcludes(String excludes);
    }
}
