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

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiToolApproval;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestError;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

/// The Tool Approvals dialog: the tools the user has allowed, or not, for the rest of a chat, to remove so they
/// are asked again, and the chat's 'approve everything' switch.
public class AiToolApprovalsPresenter extends MyPresenterWidget<AiToolApprovalsPresenter.AiToolApprovalsView>
        implements AiToolApprovalsUiHandlers {

    private static final String REMOVE_ATTRIBUTE = "data-remove-approval";
    private static final String ALL_READ_ONLY_TOOLS = "*";

    private final AskStroomAiClient client;
    private AiChat chat;

    @Inject
    public AiToolApprovalsPresenter(final EventBus eventBus,
                                    final AiToolApprovalsView view,
                                    final AskStroomAiClient client) {
        super(eventBus, view);
        this.client = client;
        view.setUiHandlers(this);
    }

    /// Shows the dialog.
    ///
    /// @param chat The chat.
    public void show(final AiChat chat) {
        this.chat = chat;
        refresh();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(600, 450))
                .caption("Tool Approvals")
                .fire();
    }

    private void refresh() {
        client.listToolApprovals(chat.getId(), response -> {
            getView().setApprovals(approvalsHtml(response.getApprovals()), !response.getApprovals().isEmpty());
            getView().setApproveEverything(response.isApproveEverything(), response.isApproveEverythingAllowed());
        }, this::showError, this);
    }

    @Override
    public void onRemove(final int approvalId) {
        client.removeToolApproval(chat.getId(), approvalId, done -> refresh(), this::showError, this);
    }

    @Override
    public void onResetAll() {
        ConfirmEvent.fire(this, "Remove every decision about tools in this chat? You will be asked again.", ok -> {
            if (ok) {
                client.resetToolApprovals(chat.getId(), done -> refresh(), this::showError, this);
            }
        });
    }

    @Override
    public void onApproveEverything(final boolean enabled) {
        if (!enabled) {
            setApproveEverything(false);
            return;
        }
        final String message = "Approve everything in this chat? Tool calls on trusted servers will run without "
                               + "asking you, including ones that change things.";
        ConfirmEvent.fire(this, message, ok -> {
            if (ok) {
                setApproveEverything(true);
            } else {
                refresh();
            }
        });
    }

    private void setApproveEverything(final boolean enabled) {
        client.setApproveEverything(new SetAiApproveEverythingRequest(chat, enabled), done -> refresh(),
                error -> {
                    showError(error);
                    refresh();
                }, this);
    }

    private static SafeHtml approvalsHtml(final List<AiToolApproval> approvals) {
        if (approvals.isEmpty()) {
            return SafeHtmlUtils.fromString("You have not allowed or denied any tools in this chat.");
        }
        final HtmlBuilder hb = new HtmlBuilder();
        for (final AiToolApproval approval : approvals) {
            hb.div(row -> {
                final String tool = ALL_READ_ONLY_TOOLS.equals(approval.getToolName())
                        ? "All read-only tools"
                        : approval.getToolName();
                row.span(text -> text.append(tool + " on " + approval.getServerKey()),
                        Attribute.className("aiMemory-change"));
                row.span(text -> text.append(approval.getDecision() == AiToolApprovalDecision.ALLOW
                                ? "Allowed"
                                : "Denied"),
                        Attribute.className("ai-approval-decision ai-approval-decision--"
                                            + (approval.getDecision() == AiToolApprovalDecision.ALLOW
                                ? "allowed"
                                : "denied")));
                row.elem("Remove", SafeHtmlUtils.fromSafeConstant("button"),
                        Attribute.className("allow-focus ai-approval-button"),
                        new Attribute(REMOVE_ATTRIBUTE, String.valueOf(approval.getId())));
            }, Attribute.className("aiMemory-changeRow"));
        }
        return hb.toSafeHtml();
    }

    private void showError(final RestError error) {
        AlertEvent.fireError(this, error.getMessage(), null);
    }


    // --------------------------------------------------------------------------------


    public interface AiToolApprovalsView extends View, HasUiHandlers<AiToolApprovalsUiHandlers> {

        /// Shows the decisions.
        ///
        /// @param html The decisions, with buttons to remove them.
        /// @param any  True if there are any.
        void setApprovals(SafeHtml html, boolean any);

        /// Shows whether 'approve everything' is on.
        ///
        /// @param enabled True if it is on.
        /// @param allowed True if an administrator allows it.
        void setApproveEverything(boolean enabled, boolean allowed);
    }
}
