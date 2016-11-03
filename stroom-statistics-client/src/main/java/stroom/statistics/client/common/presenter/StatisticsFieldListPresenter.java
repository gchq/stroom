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

package stroom.statistics.client.common.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.DoubleClickEvent;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.ArrayList;
import java.util.List;

public class StatisticsFieldListPresenter extends MyPresenterWidget<DataGridView<StatisticField>>
        implements HasRead<StatisticStoreEntity>, HasWrite<StatisticStoreEntity>, HasDirtyHandlers {
    private final StatisticsFieldEditPresenter statisticsFieldEditPresenter;
    private final GlyphButtonView newButton;
    private final GlyphButtonView editButton;
    private final GlyphButtonView removeButton;
    private final MySingleSelectionModel<StatisticField> selectionModel;
    private StatisticField selectedElement;
    private StatisticsDataSourceData statisticsDataSourceData;

    private StatisticsCustomMaskListPresenter customMaskListPresenter;

    @SuppressWarnings("unchecked")
    @Inject
    public StatisticsFieldListPresenter(final EventBus eventBus,
                                        final StatisticsFieldEditPresenter statisticsFieldEditPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, new DataGridViewImpl<StatisticField>(true));
        this.statisticsFieldEditPresenter = statisticsFieldEditPresenter;

        selectionModel = (MySingleSelectionModel<StatisticField>) getView().getSelectionModel();

        newButton = getView().addButton(GlyphIcons.NEW_ITEM);
        newButton.setTitle("New Field");
        editButton = getView().addButton(GlyphIcons.EDIT);
        editButton.setTitle("Edit Field");
        removeButton = getView().addButton(GlyphIcons.REMOVE);
        removeButton.setTitle("Remove Field");

        addColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(newButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onAdd();
                }
            }
        }));

        registerHandler(editButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onEdit();
                }
            }
        }));

        registerHandler(removeButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onRemove();
                }
            }
        }));

        registerHandler(selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                enableButtons();
            }
        }));

        registerHandler(getView().addDoubleClickHandler(new DoubleClickEvent.Handler() {
            @Override
            public void onDoubleClick(final DoubleClickEvent event) {
                onEdit();
            }
        }));
    }

    private void enableButtons() {
        if (statisticsDataSourceData != null && statisticsDataSourceData.getStatisticFields() != null) {
            selectedElement = selectionModel.getSelectedObject();
            final boolean enabled = selectedElement != null;
            editButton.setEnabled(enabled);
            removeButton.setEnabled(enabled);
        } else {
            editButton.setEnabled(false);
            removeButton.setEnabled(false);
        }
    }

    private void addColumns() {
        addNameColumn();
        getView().addEndColumn(new EndColumn<StatisticField>());
    }

    private void addNameColumn() {
        getView().addResizableColumn(new Column<StatisticField, String>(new TextCell()) {
            @Override
            public String getValue(final StatisticField row) {
                return row.getFieldName();
            }
        }, "Name", 150);
    }

    private void onAdd() {
        final StatisticField statisticField = new StatisticField();
        final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();
        final List<StatisticField> otherFields = statisticsDataSourceData.getStatisticFields();

        statisticsFieldEditPresenter.read(statisticField, otherFields);
        statisticsFieldEditPresenter.show("New Field", new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    if (statisticsFieldEditPresenter.write(statisticField)) {
                        statisticsDataSourceData.addStatisticField(statisticField);
                        reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                        refresh();
                        statisticsFieldEditPresenter.hide();
                        DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                    }
                } else {
                    statisticsFieldEditPresenter.hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Ignore.
            }
        });
    }

    private void onEdit() {
        final StatisticField statisticField = selectionModel.getSelectedObject();
        if (statisticField != null) {
            final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

            // make a copy of the list of stat fields and remove the one we are
            // editing so we can check the new value
            // is not already in the list
            final List<StatisticField> otherFields = new ArrayList<StatisticField>(
                    statisticsDataSourceData.getStatisticFields());
            otherFields.remove(statisticField);

            statisticsFieldEditPresenter.read(statisticField, otherFields);
            statisticsFieldEditPresenter.show("Edit Field", new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        if (statisticsFieldEditPresenter.write(statisticField)) {
                            statisticsDataSourceData.reOrderStatisticFields();
                            reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
                            refresh();
                            statisticsFieldEditPresenter.hide();
                            DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
                        }
                    } else {
                        statisticsFieldEditPresenter.hide();
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Ignore.
                }
            });
        }
    }

    private void onRemove() {
        final StatisticField statisticField = selectionModel.getSelectedObject();
        if (statisticField != null) {
            final StatisticsDataSourceData oldStatisticsDataSourceData = statisticsDataSourceData.deepCopy();

            statisticsDataSourceData.getStatisticFields().remove(statisticField);
            selectionModel.clear();
            reComputeRollUpBitMask(oldStatisticsDataSourceData, statisticsDataSourceData);
            refresh();
            DirtyEvent.fire(StatisticsFieldListPresenter.this, true);
        }
    }

    private void reComputeRollUpBitMask(final StatisticsDataSourceData oldStatisticsDataSourceData,
                                        final StatisticsDataSourceData newStatisticsDataSourceData) {
        if (customMaskListPresenter != null) {
            customMaskListPresenter.reComputeRollUpBitMask(oldStatisticsDataSourceData, newStatisticsDataSourceData);
        }
    }

    public void refresh() {
        if (statisticsDataSourceData == null) {
            statisticsDataSourceData = new StatisticsDataSourceData(new ArrayList<StatisticField>());
        }

        getView().setRowData(0, new ArrayList<StatisticField>(statisticsDataSourceData.getStatisticFields()));
        getView().setRowCount(statisticsDataSourceData.getStatisticFields().size(), true);
    }

    @Override
    public void read(final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSource != null) {
            statisticsDataSourceData = statisticsDataSource.getStatisticDataSourceDataObject();

            if (customMaskListPresenter != null) {
                customMaskListPresenter.read(statisticsDataSource);
            }
            refresh();
        }
    }

    @Override
    public void write(final StatisticStoreEntity entity) {
        entity.setStatisticDataSourceDataObject(statisticsDataSourceData);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public void setCustomMaskListPresenter(final StatisticsCustomMaskListPresenter customMaskListPresenter) {
        this.customMaskListPresenter = customMaskListPresenter;
    }
}
