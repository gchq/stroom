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

import stroom.ai.client.AiToolPolicyEditPresenter.AiToolPolicyEditView;
import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.ai.shared.AiToolSummary;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.UserRefPopupPresenter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// The dialog for creating or editing a tool policy: who it covers, which categories of tools it grants, and
/// whether only the tools that cannot change anything or all of them.
public class AiToolPolicyEditPresenter
        extends MyPresenterWidget<AiToolPolicyEditView>
        implements AiToolPolicyEditUiHandlers {

    private static final AiToolPolicyResource RESOURCE = GWT.create(AiToolPolicyResource.class);

    private final RestFactory restFactory;
    private final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider;

    // The policy as it was read, or null for a new one.
    private AiToolPolicy policy;
    private final List<UserRef> members = new ArrayList<>();
    // Stroom's tools, or null until they have been fetched.
    private List<AiToolSummary> tools;

    /// @param eventBus                      The event bus.
    /// @param view                          The dialog's view.
    /// @param restFactory                   For calling the tool policy API.
    /// @param userRefPopupPresenterProvider Provides the picker for adding a user or group.
    @Inject
    public AiToolPolicyEditPresenter(final EventBus eventBus,
                                     final AiToolPolicyEditView view,
                                     final RestFactory restFactory,
                                     final Provider<UserRefPopupPresenter> userRefPopupPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.userRefPopupPresenterProvider = userRefPopupPresenterProvider;
        view.setUiHandlers(this);
    }

    /// Shows the dialog to create a new tool policy.
    ///
    /// @param onSaved Given the policy once it has been saved.
    public void showCreate(final Consumer<AiToolPolicy> onSaved) {
        show(null, onSaved);
    }

    /// Shows the dialog to edit a tool policy.
    ///
    /// @param policy  The policy, as it was read.
    /// @param onSaved Given the policy once it has been saved.
    public void showEdit(final AiToolPolicy policy, final Consumer<AiToolPolicy> onSaved) {
        show(policy, onSaved);
    }

    private void show(final AiToolPolicy policy, final Consumer<AiToolPolicy> onSaved) {
        this.policy = policy;
        read(policy != null
                ? policy
                : AiToolPolicy.builder().build());
        fetchTools();

        final String caption = policy == null
                ? "Create MCP Tool Policy"
                : "Edit MCP Tool Policy";
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(700, 750))
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        save(e, onSaved);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final AiToolPolicy policy) {
        getView().setName(policy.getName());
        getView().setDescription(policy.getDescription());
        getView().setEnabled(policy.isEnabled());
        getView().setAllUsers(policy.isAllUsers());
        members.clear();
        members.addAll(policy.getMembers());
        getView().setMembers(members);
        getView().setMembersEnabled(!policy.isAllUsers());
        getView().setCategories(policy.getCategories());
        getView().setAccess(policy.getAccess());
        updateGrantedTools();
    }

    private AiToolPolicy write() {
        final AiToolPolicy.Builder builder = policy != null
                ? policy.copy()
                : AiToolPolicy.builder();
        return builder
                .name(NullSafe.trim(getView().getName()))
                .description(NullSafe.trim(getView().getDescription()))
                .enabled(getView().isEnabled())
                .allUsers(getView().isAllUsers())
                .members(new ArrayList<>(members))
                .categories(getView().getCategories())
                .access(getView().getAccess())
                .build();
    }

    private void save(final HidePopupRequestEvent e, final Consumer<AiToolPolicy> onSaved) {
        final AiToolPolicy written = write();
        final String error = AiToolPolicyText.validationError(written);
        if (error != null) {
            AlertEvent.fireError(this, error, e::reset);
            return;
        }

        // An update carries the version the policy was read at, so the server refuses it, and says why, if
        // someone else has changed the policy meanwhile.
        final Integer id = written.getId();
        restFactory
                .create(RESOURCE)
                .method(res -> id == null
                        ? res.create(written)
                        : res.update(id, written))
                .onSuccess(saved -> {
                    onSaved.accept(saved);
                    e.hide();
                })
                .onFailure(restError -> AlertEvent.fireError(this, restError.getMessage(), e::reset))
                .taskMonitorFactory(this)
                .exec();
    }

    private void fetchTools() {
        if (tools != null) {
            return;
        }
        restFactory
                .create(RESOURCE)
                .method(AiToolPolicyResource::listTools)
                .onSuccess(result -> {
                    tools = NullSafe.list(result);
                    updateGrantedTools();
                })
                .onFailure(restError -> AlertEvent.fireError(
                        this, "Error fetching the tools: " + restError.getMessage(), null))
                .taskMonitorFactory(this)
                .exec();
    }

    private void updateGrantedTools() {
        getView().setGrantedTools(AiToolPolicyText.grantedTools(
                getView().getCategories(),
                getView().getAccess(),
                tools));
    }

    @Override
    public void onAddMember() {
        userRefPopupPresenterProvider.get().show("Add User Or Group", userRef -> {
            if (userRef != null && !members.contains(userRef)) {
                members.add(userRef);
                getView().setMembers(members);
            }
        });
    }

    @Override
    public void onRemoveMember(final UserRef member) {
        if (members.remove(member)) {
            getView().setMembers(members);
        }
    }

    @Override
    public void onAllUsersChange() {
        getView().setMembersEnabled(!getView().isAllUsers());
    }

    @Override
    public void onGrantChange() {
        updateGrantedTools();
    }


    // --------------------------------------------------------------------------------


    /// The tool policy dialog.
    public interface AiToolPolicyEditView extends View, HasUiHandlers<AiToolPolicyEditUiHandlers> {

        /// Puts the cursor in the first field.
        void focus();

        /// @return The name entered.
        String getName();

        /// @param name The name to show.
        void setName(String name);

        /// @return The description entered.
        String getDescription();

        /// @param description The description to show.
        void setDescription(String description);

        /// @return True if 'Enabled' is ticked.
        boolean isEnabled();

        /// @param enabled True to tick 'Enabled'.
        void setEnabled(boolean enabled);

        /// @return True if 'All users' is ticked.
        boolean isAllUsers();

        /// @param allUsers True to tick 'All users'.
        void setAllUsers(boolean allUsers);

        /// @param members The users and groups to list, each with a button to remove it.
        void setMembers(List<UserRef> members);

        /// @param enabled False to disable the members list and its buttons, e.g. when 'All users' is ticked.
        void setMembersEnabled(boolean enabled);

        /// @return The ticked categories, in the order they are declared.
        List<AiToolCategory> getCategories();

        /// @param categories The categories to tick.
        void setCategories(List<AiToolCategory> categories);

        /// @return The access chosen.
        AiToolAccess getAccess();

        /// @param access The access to show.
        void setAccess(AiToolAccess access);

        /// @param html The tools the ticked categories grant.
        void setGrantedTools(SafeHtml html);
    }
}
