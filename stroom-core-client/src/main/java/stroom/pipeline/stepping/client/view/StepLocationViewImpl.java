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

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.pipeline.stepping.client.presenter.StepLocationUIHandlers;

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

public class StepLocationViewImpl extends ViewWithUiHandlers<StepLocationUIHandlers> implements StepLocationView {

    private static final String EMPTY = "&nbsp;-&nbsp;";
    private final Widget widget;
    @UiField
    HTML lblMetaId;
    @UiField
    TextBox txtMetaId;
    @UiField
    HTML lblPartNo;
    @UiField
    TextBox txtPartNo;
    @UiField
    HTML lblRecordNo;
    @UiField
    TextBox txtRecordNo;
    private StepLocation stepLocation;
    private boolean editing;

    @Inject
    public StepLocationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @UiHandler("lblMetaId")
    public void onClickMetaId(final ClickEvent event) {
        setEditing(true);
        txtMetaId.setFocus(true);
    }

    @UiHandler("lblPartNo")
    public void onClickPartNo(final ClickEvent event) {
        setEditing(true);
        txtPartNo.setFocus(true);
    }

    @UiHandler("lblRecordNo")
    public void onClickRecordNo(final ClickEvent event) {
        setEditing(true);
        txtRecordNo.setFocus(true);
    }

    @UiHandler("txtMetaId")
    void onKeyDownMetaId(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            setEditing(false);
        }
    }

    @UiHandler("txtPartNo")
    void onKeyDownPartNo(final KeyDownEvent event) {
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
            final Long metaId = getLong(lblMetaId);
            final Long partNo = getLong(lblPartNo);
            final Long recordNo = getLong(lblRecordNo);

            if (metaId != null) {
                txtMetaId.setText(metaId.toString());
            } else {
                txtMetaId.setText("");
            }
            if (partNo != null) {
                txtPartNo.setText(partNo.toString());
            } else {
                txtPartNo.setText("");
            }
            if (recordNo != null) {
                txtRecordNo.setText(recordNo.toString());
            } else {
                txtRecordNo.setText("");
            }

            txtMetaId.setVisible(true);
            txtPartNo.setVisible(true);
            txtRecordNo.setVisible(true);
            lblMetaId.setVisible(false);
            lblPartNo.setVisible(false);
            lblRecordNo.setVisible(false);
        } else {
            fireMoveEvent();

            lblMetaId.setVisible(true);
            lblPartNo.setVisible(true);
            lblRecordNo.setVisible(true);
            txtMetaId.setVisible(false);
            txtPartNo.setVisible(false);
            txtRecordNo.setVisible(false);
        }
    }

    private void fireMoveEvent() {
        final Long metaId = getLong(txtMetaId);
        final Long partNo = getLong(txtPartNo);
        final Long recordNo = getLong(txtRecordNo);

        if (metaId != null && partNo != null && recordNo != null) {
            // Fire location change.
            if (getUiHandlers() != null) {
                // Convert 1 based part and record numbers used for display into 0 based part index and record index.
                final StepLocation newLocation =
                        new StepLocation(metaId, partNo - 1, recordNo - 1);
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
            lblMetaId.setHTML(EMPTY);
            lblPartNo.setHTML(EMPTY);
            lblRecordNo.setHTML(EMPTY);
        } else {
            lblMetaId.setHTML(Long.toString(stepLocation.getMetaId()));
            // Convert 0 based part index and record index into 1 based part and record numbers used for display.
            lblPartNo.setHTML(Long.toString(stepLocation.getPartIndex() + 1));
            lblRecordNo.setHTML(Long.toString(stepLocation.getRecordIndex() + 1));
        }
    }

    private Long getLong(final HasText textBox) {
        try {
            return Long.valueOf(textBox.getText().trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public interface Binder extends UiBinder<Widget, StepLocationViewImpl> {

    }
}
