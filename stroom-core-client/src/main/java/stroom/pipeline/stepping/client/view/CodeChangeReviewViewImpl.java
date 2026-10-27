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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.stepping.client.presenter.CodeChangeReviewPresenter.CodeChangeReviewView;
import stroom.pipeline.stepping.client.presenter.CodeChangeReviewUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// The side by side code change review dialog.
public class CodeChangeReviewViewImpl
        extends ViewWithUiHandlers<CodeChangeReviewUiHandlers>
        implements CodeChangeReviewView {

    private final Widget widget;

    @UiField
    Label summary;
    @UiField
    Label changePosition;
    @UiField
    Button previousChange;
    @UiField
    Button nextChange;
    @UiField
    Button rejectChange;
    @UiField
    SimplePanel left;
    @UiField
    SimplePanel right;
    @UiField
    Label message;
    @UiField
    Button apply;
    @UiField
    Button applyAndStep;
    @UiField
    Button cancel;

    @Inject
    public CodeChangeReviewViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        previousChange.setIcon(SvgImage.UP);
        nextChange.setIcon(SvgImage.DOWN);
        setRejected(false);
        rejectChange.setEnabled(false);
        apply.setIcon(SvgImage.OK);
        applyAndStep.setIcon(SvgImage.STEP_FORWARD);
        cancel.setIcon(SvgImage.CANCEL);
        message.setVisible(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setLeftView(final View view) {
        left.setWidget(view.asWidget());
    }

    @Override
    public void setRightView(final View view) {
        right.setWidget(view.asWidget());
    }

    @Override
    public void setSummary(final String summary) {
        this.summary.setText(summary);
    }

    @Override
    public void setChangePosition(final String position) {
        changePosition.setText(position);
    }

    @Override
    public void setMessage(final String message) {
        this.message.setText(message == null
                ? ""
                : message);
        this.message.setVisible(message != null);
    }

    @Override
    public void setApplyEnabled(final boolean enabled) {
        apply.setEnabled(enabled);
        applyAndStep.setEnabled(enabled);
    }

    @Override
    public void setStepAvailable(final boolean available) {
        applyAndStep.setVisible(available);
    }

    @Override
    public void setApplyText(final String text) {
        apply.setText(text);
    }

    @Override
    public void setRejectAvailable(final boolean available) {
        rejectChange.setVisible(available);
    }

    @Override
    public void setPreviousEnabled(final boolean enabled) {
        previousChange.setEnabled(enabled);
    }

    @Override
    public void setNextEnabled(final boolean enabled) {
        nextChange.setEnabled(enabled);
    }

    @Override
    public void setRejectEnabled(final boolean enabled) {
        rejectChange.setEnabled(enabled);
    }

    @Override
    public void setRejected(final boolean rejected) {
        if (rejected) {
            rejectChange.setText("Keep change");
            rejectChange.setIcon(SvgImage.UNDO);
            rejectChange.setTitle("Apply this change after all");
        } else {
            rejectChange.setText("Reject change");
            rejectChange.setIcon(SvgImage.CLOSE);
            rejectChange.setTitle("Leave this change out of what is applied");
        }
    }

    @UiHandler("rejectChange")
    void onRejectChangeClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onToggleRejectChange();
        }
    }

    @UiHandler("previousChange")
    void onPreviousChangeClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onPreviousChange();
        }
    }

    @UiHandler("nextChange")
    void onNextChangeClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onNextChange();
        }
    }

    @UiHandler("apply")
    void onApplyClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onApply();
        }
    }

    @UiHandler("applyAndStep")
    void onApplyAndStepClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onApplyAndStep();
        }
    }

    @UiHandler("cancel")
    void onCancelClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onCancel();
        }
    }


    // --------------------------------------------------------------------------------


    /// The UiBinder for this view.
    public interface Binder extends UiBinder<Widget, CodeChangeReviewViewImpl> {

    }
}
