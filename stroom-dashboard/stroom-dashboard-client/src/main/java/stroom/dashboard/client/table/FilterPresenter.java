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

import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.BiConsumer;

public class FilterPresenter extends MyPresenterWidget<FilterPresenter.FilterView> {

    private Field field;
    private BiConsumer<Field, Field> fieldChangeConsumer;

    @Inject
    public FilterPresenter(final EventBus eventBus, final FilterView view) {
        super(eventBus, view);
    }

    public void show(final TablePresenter tablePresenter,
                     final Field field,
                     final BiConsumer<Field, Field> fieldChangeConsumer) {
        this.field = field;
        this.fieldChangeConsumer = fieldChangeConsumer;

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

        final PopupSize popupSize = PopupSize.resizable(400, 500);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Filter '" + field.getName() + "'")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Filter filter = getFilter();
                        if ((filter == null && field.getFilter() != null)
                                || (filter != null && !filter.equals(field.getFilter()))) {
                            fieldChangeConsumer.accept(field, field.copy().filter(filter).build());
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    private Filter getFilter() {
        String includes = null;
        String excludes = null;
        if (getView().getIncludes() != null && getView().getIncludes().trim().length() > 0) {
            includes = getView().getIncludes().trim();
        }
        if (getView().getExcludes() != null && getView().getExcludes().trim().length() > 0) {
            excludes = getView().getExcludes().trim();
        }

        Filter filter = null;
        if (includes != null || excludes != null) {
            filter = new Filter(includes, excludes);
        }
        return filter;
    }

    public interface FilterView extends View, Focus {

        String getIncludes();

        void setIncludes(String includes);

        String getExcludes();

        void setExcludes(String excludes);
    }
}
