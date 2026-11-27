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

import stroom.ai.client.AiToolApprovalsPresenter.AiToolApprovalsView;
import stroom.widget.button.client.Button;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AiToolApprovalsViewImpl extends ViewWithUiHandlers<AiToolApprovalsUiHandlers>
        implements AiToolApprovalsView {

    private static final String REMOVE_ATTRIBUTE = "data-remove-approval";

    private final Widget widget;

    @UiField
    HTML approvals;
    @UiField
    CustomCheckBox approveEverything;
    @UiField
    Label approveEverythingNote;
    @UiField
    Button resetAll;

    @Inject
    public AiToolApprovalsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        approvals.addClickHandler(event -> {
            final Element target = event.getNativeEvent().getEventTarget().cast();
            final String approvalId = target.getAttribute(REMOVE_ATTRIBUTE);
            if (approvalId != null && !approvalId.isEmpty() && getUiHandlers() != null) {
                getUiHandlers().onRemove(Integer.parseInt(approvalId));
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setApprovals(final SafeHtml html, final boolean any) {
        approvals.setHTML(html);
        resetAll.setEnabled(any);
    }

    @Override
    public void setApproveEverything(final boolean enabled, final boolean allowed) {
        approveEverything.setValue(enabled);
        approveEverything.setEnabled(allowed || enabled);
        approveEverythingNote.setText(allowed
                ? "Tool calls on trusted servers run without asking. Tools on untrusted servers, and tools that "
                  + "can destroy or send data out, still ask, and nothing runs without asking while you are away."
                : "An administrator has not allowed this.");
    }

    @UiHandler("approveEverything")
    public void onApproveEverything(final ValueChangeEvent<Boolean> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onApproveEverything(Boolean.TRUE.equals(event.getValue()));
        }
    }

    @UiHandler("resetAll")
    public void onResetAll(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onResetAll();
        }
    }

    public interface Binder extends UiBinder<Widget, AiToolApprovalsViewImpl> {

    }
}
