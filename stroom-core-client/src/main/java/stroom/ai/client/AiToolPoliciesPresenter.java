/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AiToolPoliciesPresenter.AiToolPoliciesView;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestFactory;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/// The MCP Tool Policies screen: the tool policies, by name, to add, edit and delete. A tool policy grants the
/// users and groups it covers the tools in its categories, in the "Ask Stroom AI" chat and over MCP.
public class AiToolPoliciesPresenter
        extends ContentTabPresenter<AiToolPoliciesView>
        implements Refreshable {

    /// The type of the screen's tab.
    public static final String TAB_TYPE = "McpToolPolicies";
    /// The screen's name, as its menu item and tab show it.
    public static final String SCREEN_NAME = "MCP Tool Policies";

    private static final AiToolPolicyResource RESOURCE = GWT.create(AiToolPolicyResource.class);

    private final RestFactory restFactory;
    private final Provider<AiToolPolicyEditPresenter> editPresenterProvider;
    private final MyDataGrid<AiToolPolicy> dataGrid;
    private final MultiSelectionModelImpl<AiToolPolicy> selectionModel;
    private final ListDataProvider<AiToolPolicy> dataProvider = new ListDataProvider<>();
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView deleteButton;
    private final ButtonView refreshButton;

    /// @param eventBus              The event bus.
    /// @param view                  The screen's view.
    /// @param pagerView             Holds the list of policies and its buttons.
    /// @param restFactory           For calling the tool policy API.
    /// @param editPresenterProvider Provides the dialog for creating and editing a policy.
    @Inject
    public AiToolPoliciesPresenter(final EventBus eventBus,
                                   final AiToolPoliciesView view,
                                   final PagerView pagerView,
                                   final RestFactory restFactory,
                                   final Provider<AiToolPolicyEditPresenter> editPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.editPresenterProvider = editPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName(SCREEN_NAME);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);
        view.setList(pagerView);

        addButton = pagerView.addButton(SvgPresets.ADD.title("Add MCP Tool Policy"));
        editButton = pagerView.addButton(SvgPresets.EDIT.title("Edit MCP Tool Policy"));
        deleteButton = pagerView.addButton(SvgPresets.DELETE.title("Delete MCP Tool Policy"));
        refreshButton = pagerView.addButton(SvgPresets.REFRESH_BLUE.title("Refresh"));

        initColumns();
        dataProvider.addDataDisplay(dataGrid);
        enableButtons();
        refresh();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(addButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onAdd();
            }
        }));
        registerHandler(editButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(deleteButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onDelete();
            }
        }));
        registerHandler(refreshButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                refresh();
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(e -> {
            enableButtons();
            if (e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
    }

    private void initColumns() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(AiToolPolicy::getName)
                        .enabledWhen(AiToolPolicy::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the tool policy.")
                        .build(),
                200);
        dataGrid.addColumn(
                DataGridUtil.<AiToolPolicy>readOnlyTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(AiToolPolicy::isEnabled))
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .centerAligned()
                        .withToolTip("Whether the policy grants anything. A disabled policy grants nothing.")
                        .build(),
                80);
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(AiToolPolicyText::covers)
                        .enabledWhen(AiToolPolicy::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Covers")
                        .withToolTip("The users and groups the policy covers, or all users.")
                        .build(),
                250);
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(AiToolPolicyText::categories)
                        .enabledWhen(AiToolPolicy::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Categories")
                        .withToolTip("The categories of tools the policy grants.")
                        .build(),
                250);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((AiToolPolicy policy) -> policy.getAccess().getDisplayValue())
                        .enabledWhen(AiToolPolicy::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Access")
                        .withToolTip("Whether the policy grants only the tools that cannot change anything, "
                                     + "or all of them.")
                        .build(),
                120);
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder(AiToolPolicy::getDescription)
                        .enabledWhen(AiToolPolicy::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("What the policy is for.")
                        .build(),
                300);
    }

    @Override
    public void refresh() {
        restFactory
                .create(RESOURCE)
                .method(AiToolPolicyResource::list)
                .onSuccess(policies -> {
                    final List<AiToolPolicy> sorted = new ArrayList<>(NullSafe.list(policies));
                    sorted.sort(Comparator.comparing(AiToolPolicy::getName, String.CASE_INSENSITIVE_ORDER));
                    final AiToolPolicy selected = selectionModel.getSelected();
                    dataProvider.setCompleteList(sorted);
                    reselect(selected, sorted);
                    enableButtons();
                })
                .onFailure(restError -> AlertEvent.fireError(
                        this, "Error fetching MCP tool policies: " + restError.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    // Selects the policy with the same id as the one selected before the list was fetched again, if it is still
    // there, as the fetched one may have changed.
    private void reselect(final AiToolPolicy previous, final List<AiToolPolicy> policies) {
        selectionModel.clear();
        if (previous != null) {
            policies.stream()
                    .filter(policy -> Objects.equals(policy.getId(), previous.getId()))
                    .findFirst()
                    .ifPresent(selectionModel::setSelected);
        }
    }

    private void enableButtons() {
        final boolean selected = selectionModel.getSelected() != null;
        editButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    private void onAdd() {
        editPresenterProvider.get().showCreate(saved -> {
            selectionModel.setSelected(saved);
            refresh();
        });
    }

    private void onEdit() {
        final AiToolPolicy selected = selectionModel.getSelected();
        if (selected != null) {
            editPresenterProvider.get().showEdit(selected, saved -> refresh());
        }
    }

    private void onDelete() {
        final AiToolPolicy selected = selectionModel.getSelected();
        if (selected == null) {
            return;
        }
        final String message = "Are you sure you want to delete the MCP tool policy '" + selected.getName() + "'?"
                               + "\n\nThe users and groups it covers will lose the tools it grants, unless "
                               + "another policy grants them.";
        ConfirmEvent.fire(this, message, ok -> {
            if (ok) {
                restFactory
                        .create(RESOURCE)
                        .method(res -> res.delete(selected.getId()))
                        .onSuccess(deleted -> {
                            selectionModel.clear();
                            refresh();
                        })
                        .onFailure(restError -> {
                            AlertEvent.fireError(this, restError.getMessage(), null);
                            refresh();
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.AI;
    }

    @Override
    public String getLabel() {
        return SCREEN_NAME;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    /// The MCP Tool Policies screen.
    public interface AiToolPoliciesView extends View {

        /// @param view The list of policies, with its buttons.
        void setList(View view);
    }
}
