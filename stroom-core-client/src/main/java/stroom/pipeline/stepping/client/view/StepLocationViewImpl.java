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

package stroom.pipeline.stepping.client.view;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.pipeline.stepping.client.presenter.StepLocationUIHandlers;

public class StepLocationViewImpl extends ViewWithUiHandlers<StepLocationUIHandlers>implements StepLocationView {
    public interface Binder extends UiBinder<Widget, StepLocationViewImpl> {
    }

    private static final String EMPTY = "&nbsp;-&nbsp;";
    private final Widget widget;
    private StepLocation stepLocation;
    private boolean editing;

    @UiField
    HTML lblStreamId;
    @UiField
    TextBox txtStreamId;
    @UiField
    HTML lblStreamNo;
    @UiField
    TextBox txtStreamNo;
    @UiField
    HTML lblRecordNo;
    @UiField
    TextBox txtRecordNo;

    @Inject
    public StepLocationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("lblStreamId")
    public void onClickStreamId(final ClickEvent event) {
        setEditing(true);
        txtStreamId.setFocus(true);
    }

    @UiHandler("lblStreamNo")
    public void onClickStreamNo(final ClickEvent event) {
        setEditing(true);
        txtStreamNo.setFocus(true);
    }

    @UiHandler("lblRecordNo")
    public void onClickRecordNo(final ClickEvent event) {
        setEditing(true);
        txtRecordNo.setFocus(true);
    }

    @UiHandler("txtStreamId")
    void onKeyDownStreamId(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    @UiHandler("txtStreamNo")
    void onKeyDownStreamNo(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    @UiHandler("txtRecordNo")
    void onKeyDownRecordNo(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    public void setEditing(final boolean editing) {
        this.editing = editing;
        if (editing) {
            final Long streamId = getLong(lblStreamId);
            final Long streamNo = getLong(lblStreamNo);
            final Long recordNo = getLong(lblRecordNo);

            if (streamId != null) {
                txtStreamId.setText(streamId.toString());
            } else {
                txtStreamId.setText("");
            }
            if (streamNo != null) {
                txtStreamNo.setText(streamNo.toString());
            } else {
                txtStreamNo.setText("");
            }
            if (recordNo != null) {
                txtRecordNo.setText(recordNo.toString());
            } else {
                txtRecordNo.setText("");
            }

            txtStreamId.setVisible(true);
            txtStreamNo.setVisible(true);
            txtRecordNo.setVisible(true);
            lblStreamId.setVisible(false);
            lblStreamNo.setVisible(false);
            lblRecordNo.setVisible(false);
        } else {
            fireMoveEvent();

            lblStreamId.setVisible(true);
            lblStreamNo.setVisible(true);
            lblRecordNo.setVisible(true);
            txtStreamId.setVisible(false);
            txtStreamNo.setVisible(false);
            txtRecordNo.setVisible(false);
        }
    }

    private void fireMoveEvent() {
        final Long streamId = getLong(txtStreamId);
        final Long streamNo = getLong(txtStreamNo);
        final Long recordNo = getLong(txtRecordNo);

        if (streamId != null && streamNo != null && recordNo != null) {
            // Fire location change.
            if (getUiHandlers() != null) {
                final StepLocation newLocation = new StepLocation(streamId, streamNo, recordNo);
                getUiHandlers().changeLocation(newLocation);
            }
        } else {
            // Do not fire an event and just reset to previous location.
            setStepLocation(stepLocation);
        }
    }

    @Override
    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;

        if (editing) {
            setEditing(false);
        }

        if (stepLocation == null) {
            lblStreamId.setHTML(EMPTY);
            lblStreamNo.setHTML(EMPTY);
            lblRecordNo.setHTML(EMPTY);
        } else {
            lblStreamId.setHTML(Long.toString(stepLocation.getStreamId()));
            lblStreamNo.setHTML(Long.toString(stepLocation.getStreamNo()));
            lblRecordNo.setHTML(Long.toString(stepLocation.getRecordNo()));
        }
    }

    private Long getLong(final HasText textBox) {
        try {
            return Long.valueOf(textBox.getText().trim());
        } catch (final NumberFormatException e) {
        }

        return null;
    }
}
