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

import stroom.ai.client.AiToolPolicyMembersPresenter.AiToolPolicyMembersView;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The lower panel of the MCP Tool Policies screen: the users and groups the selected policy covers, to add to and
/// remove from, each change saved at once. A policy for all users, such as the default read-only one, shows
/// 'Everyone', which cannot be removed.
public class AiToolPolicyMembersPresenter extends MyPresenterWidget<AiToolPolicyMembersView> {

    private static final AiToolPolicyResource RESOURCE = GWT.create(AiToolPolicyResource.class);
    // Stands for all users, in a policy that covers them.
    private static final UserRef EVERYONE = UserRef.builder()
            .uuid("aiToolPolicyEveryone")
            .displayName("Everyone")
            .group()
            .build();

    private final RestFactory restFactory;
    private final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider;
    private final MyDataGrid<UserRef> dataGrid;
    private final MultiSelectionModelImpl<UserRef> selectionModel;
    private final ListDataProvider<UserRef> dataProvider = new ListDataProvider<>();
    private final ButtonView addButton;
    private final ButtonView removeButton;

    // The selected policy, as last read or saved, or null if none is selected.
    private AiToolPolicy policy;
    private Consumer<AiToolPolicy> onSaved = saved -> {
    };

    /// @param eventBus                      The event bus.
    /// @param view                          The panel's view.
    /// @param pagerView                     Holds the list of users and groups and its buttons.
    /// @param restFactory                   For calling the tool policy API.
    /// @param userRefPopupPresenterProvider Provides the picker for adding a user or group.
    @Inject
    public AiToolPolicyMembersPresenter(final EventBus eventBus,
                                        final AiToolPolicyMembersView view,
                                        final PagerView pagerView,
                                        final RestFactory restFactory,
                                        final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.userRefPopupPresenterProvider = userRefPopupPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("MCP Tool Policy Users And Groups");
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);
        view.setList(pagerView);
        view.setHeading("Assigned members");

        addButton = pagerView.addButton(SvgPresets.ADD.title("Add User Or Group"));
        removeButton = pagerView.addButton(SvgPresets.DELETE.title("Remove User Or Group"));

        initColumns();
        dataProvider.addDataDisplay(dataGrid);
        setPolicy(null);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(addButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onAdd();
            }
        }));
        registerHandler(removeButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onRemove();
            }
        }));
        registerHandler(selectionModel.addSelectionHandler(e -> enableButtons()));
    }

    private void initColumns() {
        dataGrid.addResizableColumn(
                DataGridUtil.textWithTooltipColumnBuilder((UserRef member) -> member.toDisplayString()).build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The user or group. A group covers everyone in it. 'Everyone' is all users.")
                        .build(),
                300);
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder((UserRef member) -> member == EVERYONE
                        ? "All users"
                        : member.getType()).build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("Whether it is a user or a group.")
                        .build(),
                100);
    }

    /// @param onSaved Given the policy each time a change here has been saved.
    public void setOnSaved(final Consumer<AiToolPolicy> onSaved) {
        this.onSaved = onSaved;
    }

    /// Shows who a policy covers.
    ///
    /// @param policy The selected policy, or null if none is selected.
    public void setPolicy(final AiToolPolicy policy) {
        this.policy = policy;
        selectionModel.clear();
        if (policy == null) {
            dataProvider.setCompleteList(new ArrayList<>());
        } else {
            final List<UserRef> rows = new ArrayList<>();
            if (policy.isAllUsers()) {
                rows.add(EVERYONE);
            }
            rows.addAll(policy.getMembers());
            dataProvider.setCompleteList(rows);
        }
        enableButtons();
    }

    // A policy for everyone covers everyone added too, so it takes no more.
    private void enableButtons() {
        final UserRef selected = selectionModel.getSelected();
        addButton.setEnabled(policy != null && !policy.isAllUsers());
        removeButton.setEnabled(policy != null && selected != null && selected != EVERYONE);
    }

    private void onAdd() {
        if (policy == null) {
            return;
        }
        userRefPopupPresenterProvider.get().show("Add User Or Group", userRef -> {
            if (userRef != null && !policy.getMembers().contains(userRef)) {
                final List<UserRef> members = new ArrayList<>(policy.getMembers());
                members.add(userRef);
                save(policy.copy().members(members).build());
            }
        });
    }

    private void onRemove() {
        final UserRef selected = selectionModel.getSelected();
        if (policy == null || selected == null || selected == EVERYONE) {
            return;
        }
        final List<UserRef> members = new ArrayList<>(policy.getMembers());
        members.remove(selected);
        save(policy.copy().members(members).build());
    }

    // An update carries the version the policy was read at, so the server refuses it, and says why, if someone
    // else has changed the policy meanwhile. A refused change is undone on screen.
    private void save(final AiToolPolicy changed) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.update(changed.getId(), changed))
                .onSuccess(saved -> {
                    setPolicy(saved);
                    onSaved.accept(saved);
                })
                .onFailure(restError -> {
                    AlertEvent.fireError(this, restError.getMessage(), null);
                    setPolicy(policy);
                })
                .taskMonitorFactory(this)
                .exec();
    }


    // --------------------------------------------------------------------------------


    /// The users and groups panel.
    public interface AiToolPolicyMembersView extends View {

        /// @param view The list of users and groups, with its buttons.
        void setList(View view);

        /// @param text What the panel is showing.
        void setHeading(String text);
    }
}
