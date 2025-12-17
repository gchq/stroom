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

package stroom.statistics.impl.sql.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class StatisticsFieldEditPresenter
        extends MyPresenterWidget<StatisticsFieldEditPresenter.StatisticsFieldEditView> {

    private static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private String fieldNamePattern;
    private List<StatisticField> otherFields;

    @Inject
    public StatisticsFieldEditPresenter(final EventBus eventBus, final StatisticsFieldEditView view,
                                        final UiConfigCache clientPropertyCache) {
        super(eventBus, view);

        final StatisticsFieldEditPresenter thisPresenter = this;

        clientPropertyCache.get(result -> {
            if (result != null) {
                String fieldNamePattern = result.getNamePattern();

                if (fieldNamePattern == null || fieldNamePattern.isEmpty()) {
                    fieldNamePattern = DEFAULT_NAME_PATTERN_VALUE;
                }

                thisPresenter.setFieldNamePattern(fieldNamePattern);
            }
        }, this);
    }

    public void read(final StatisticField field, final List<StatisticField> otherFields) {
        this.otherFields = otherFields;
        getView().setFieldName(field.getFieldName());

    }

    public boolean write(final StatisticField field) {
        String name = getView().getFieldName();
        name = name.trim();

        field.setFieldName(name);

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return false;
        }
        if (otherFields.contains(field)) {
            AlertEvent.fireWarn(this, "Another field with this name already exists", null);
            return false;
        }
        if (fieldNamePattern != null && !fieldNamePattern.isEmpty()) {
            if (!name.matches(fieldNamePattern)) {
                AlertEvent.fireWarn(this,
                        "Invalid name \"" + name + "\" (valid name pattern: " + fieldNamePattern + ")", null);
                return false;
            }
        }
        return true;
    }

    void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    private void setFieldNamePattern(final String fieldNamePattern) {
        this.fieldNamePattern = fieldNamePattern;
    }

    public interface StatisticsFieldEditView extends View, Focus {

        String getFieldName();

        void setFieldName(final String fieldName);
    }
}
