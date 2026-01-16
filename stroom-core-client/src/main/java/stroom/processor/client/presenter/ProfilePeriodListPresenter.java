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

package stroom.processor.client.presenter;

import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.processor.shared.ProfilePeriod;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.Time;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;

public class ProfilePeriodListPresenter
        extends MyPresenterWidget<PagerView> {

    private final MyDataGrid<ProfilePeriod> dataGrid;
    private final MultiSelectionModelImpl<ProfilePeriod> selectionModel;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private final List<ProfilePeriod> profilePeriods = new ArrayList<>();
    private final Provider<ProfilePeriodEditPresenter> newConcurrencyTimeRangePresenter;

    private boolean readOnly = false;

    @Inject
    public ProfilePeriodListPresenter(final EventBus eventBus,
                                      final PagerView view,
                                      final Provider<ProfilePeriodEditPresenter> newConcurrencyTimeRangePresenter) {
        super(eventBus, view);

        view.asWidget().addStyleName("form-control-background form-control-border");
        dataGrid = new MyDataGrid<>(this);
        dataGrid.setMultiLine(true);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        this.newConcurrencyTimeRangePresenter = newConcurrencyTimeRangePresenter;

        addButton = view.addButton(SvgPresets.NEW_ITEM);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.REMOVE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(selectionModel.getSelected());
            }
        }));
        registerHandler(addButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onAdd(event);
            }
        }));
        registerHandler(editButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onEdit(selectionModel.getSelected());
            }
        }));
        registerHandler(removeButton.addClickHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                onRemove();
            }
        }));
    }

    public void focus() {
        addButton.focus();
    }

    public void setProfilePeriods(final List<ProfilePeriod> list) {
        this.profilePeriods.clear();
        if (list != null) {
            this.profilePeriods.addAll(list);
        }
        refresh();
    }

    public List<ProfilePeriod> getProfilePeriods() {
        return new ArrayList<>(profilePeriods);
    }

    private void addColumns() {
        addDays();
        addStartTime();
        addEndTime();
        addMaxNodeThreads();
        addMaxClusterThreads();
        addEndColumn();
    }

    void addDays() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getDays, Days::toString), getEventBus())
                        .build(),
                "Days",
                300);
    }

    void addStartTime() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getStartTime, Time::toString), getEventBus())
                        .build(),
                "Start Time",
                100);
    }

    void addEndTime() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((ProfilePeriod ctr) ->
                                NullSafe.get(ctr, ProfilePeriod::getEndTime, Time::toString), getEventBus())
                        .build(),
                "End Time",
                100);
    }

    void addMaxNodeThreads() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((final ProfilePeriod profilePeriod) -> {
                            if (!profilePeriod.isLimitNodeThreads()) {
                                return "Unlimited";
                            } else {
                                return "" + profilePeriod.getMaxNodeThreads();
                            }
                        }, getEventBus())
                        .build(),
                "Max Node Threads",
                100);
    }

    void addMaxClusterThreads() {
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder((final ProfilePeriod profilePeriod) -> {
                            if (!profilePeriod.isLimitClusterThreads()) {
                                return "Unlimited";
                            } else {
                                return "" + profilePeriod.getMaxClusterThreads();
                            }
                        }, getEventBus())
                        .build(),
                "Max Cluster Threads",
                100);
    }

    private void addEndColumn() {
        dataGrid.addEndColumn(new EndColumn<>());
    }

    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        enableButtons();
    }

    private void onAdd(final ClickEvent event) {
        final ProfilePeriod profilePeriod = ProfilePeriod.builder().build();
        showEditor(profilePeriod, true);
    }

    private void onEdit(final ProfilePeriod profilePeriod) {
        if (profilePeriod != null) {
            showEditor(profilePeriod, false);
        }
    }

    private void showEditor(final ProfilePeriod profilePeriod,
                            final boolean isNew) {
        if (profilePeriod != null) {
            final ProfilePeriodEditPresenter editor = newConcurrencyTimeRangePresenter.get();
            editor.show(profilePeriod, updated -> {
                final int index = profilePeriods.indexOf(updated);
                if (index == -1) {
                    profilePeriods.add(updated);
                } else {
                    profilePeriods.remove(index);
                    profilePeriods.add(index, updated);
                }

//                        setDirty(isNew || editor.isDirty());
                refresh();
            });
        }
    }

    private void onRemove() {
        final ProfilePeriod selected = selectionModel.getSelected();
        if (selected != null) {
            profilePeriods.remove(selected);
            refresh();
        }
    }

    private void refresh() {
        setData(profilePeriods);
    }

    private void setData(final List<ProfilePeriod> profilePeriods) {
        dataGrid.setRowData(0, profilePeriods);
        dataGrid.setRowCount(profilePeriods.size());
        enableButtons();
    }

    protected void enableButtons() {
        addButton.setEnabled(!readOnly);

        final ProfilePeriod selected = selectionModel.getSelected();

        editButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selected != null);

        if (readOnly) {
            addButton.setTitle("New feed dependency disabled as filter is read only");
            editButton.setTitle("Edit feed dependency disabled as filter is read only");
            removeButton.setTitle("Remove feed dependency disabled as filter is read only");
        } else {
            addButton.setTitle("New Reference");
            editButton.setTitle("Edit Reference");
            removeButton.setTitle("Remove Reference");
        }
    }
}
