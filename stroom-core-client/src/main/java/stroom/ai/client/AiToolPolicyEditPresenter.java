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
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolPolicyResource;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

/// The dialog for creating or editing a tool policy: whether it is enabled, its name and description, and which
/// categories of tools it grants, each read-only or read and write. Who it covers is set on the MCP Tool Policies
/// screen itself, and is kept as it is when the dialog saves.
public class AiToolPolicyEditPresenter extends MyPresenterWidget<AiToolPolicyEditView> {

    private static final AiToolPolicyResource RESOURCE = GWT.create(AiToolPolicyResource.class);

    private final RestFactory restFactory;

    // The policy as it was read, or null for a new one.
    private AiToolPolicy policy;

    /// @param eventBus    The event bus.
    /// @param view        The dialog's view.
    /// @param restFactory For calling the tool policy API.
    @Inject
    public AiToolPolicyEditPresenter(final EventBus eventBus,
                                     final AiToolPolicyEditView view,
                                     final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
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

        final String caption = policy == null
                ? "Create MCP Tool Policy"
                : "Edit MCP Tool Policy";
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 660))
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
        getView().setEnabled(policy.isEnabled());
        getView().setName(policy.getName());
        getView().setDescription(policy.getDescription());
        getView().setGrants(policy.getGrants());
    }

    private AiToolPolicy write() {
        final AiToolPolicy.Builder builder = policy != null
                ? policy.copy()
                : AiToolPolicy.builder();
        return builder
                .enabled(getView().isEnabled())
                .name(NullSafe.trim(getView().getName()))
                .description(NullSafe.trim(getView().getDescription()))
                .grants(getView().getGrants())
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


    // --------------------------------------------------------------------------------


    /// The tool policy dialog.
    public interface AiToolPolicyEditView extends View {

        /// Puts the cursor in the name.
        void focus();

        /// @return True if 'Enabled' is ticked.
        boolean isEnabled();

        /// @param enabled True to tick 'Enabled'.
        void setEnabled(boolean enabled);

        /// @return The name entered.
        String getName();

        /// @param name The name to show.
        void setName(String name);

        /// @return The description entered.
        String getDescription();

        /// @param description The description to show.
        void setDescription(String description);

        /// @return The ticked categories, in the order they are declared, each with the access chosen for it.
        List<AiToolCategoryGrant> getGrants();

        /// @param grants The categories to tick, each with its access.
        void setGrants(List<AiToolCategoryGrant> grants);
    }
}
