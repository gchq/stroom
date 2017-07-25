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
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterSettingsView;
import stroom.pipeline.stepping.client.presenter.SteppingFilterUiHandlers;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.button.client.SvgButton;

public class SteppingFilterViewImpl extends ViewWithUiHandlers<SteppingFilterUiHandlers>
        implements SteppingFilterSettingsView {
    private final Widget widget;
    @UiField
    ListBox skipToErrors;
    @UiField
    ListBox skipToOutput;
    @UiField(provided = true)
    SvgButton addXPath;
    @UiField(provided = true)
    SvgButton editXPath;
    @UiField(provided = true)
    SvgButton removeXPath;
    @UiField
    SimplePanel xPathList;
    @Inject
    public SteppingFilterViewImpl(final Binder binder) {
        addXPath = SvgButton.create(SvgPresets.ADD);
        addXPath.setTitle("Add XPath");
        editXPath = SvgButton.create(SvgPresets.EDIT);
        editXPath.setTitle("Edit XPath");
        removeXPath = SvgButton.create(SvgPresets.REMOVE);
        removeXPath.setTitle("Delete XPath");

        widget = binder.createAndBindUi(this);

        skipToErrors.addItem("");
        skipToErrors.addItem(Severity.INFO.getDisplayValue());
        skipToErrors.addItem(Severity.WARNING.getDisplayValue());
        skipToErrors.addItem(Severity.ERROR.getDisplayValue());
        skipToErrors.addItem(Severity.FATAL_ERROR.getDisplayValue());

        skipToOutput.addItem("");
        skipToOutput.addItem(OutputState.NOT_EMPTY.getDisplayValue());
        skipToOutput.addItem(OutputState.EMPTY.getDisplayValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Severity getSkipToErrors() {
        final int index = skipToErrors.getSelectedIndex();
        if (index <= 0) {
            return null;
        }

        return Severity.getSeverity(skipToErrors.getItemText(index));
    }

    @Override
    public void setSkipToErrors(Severity value) {
        if (value == null) {
            skipToErrors.setSelectedIndex(0);
        } else if (Severity.INFO.equals(value)) {
            skipToErrors.setSelectedIndex(1);
        } else if (Severity.WARNING.equals(value)) {
            skipToErrors.setSelectedIndex(2);
        } else if (Severity.ERROR.equals(value)) {
            skipToErrors.setSelectedIndex(3);
        } else if (Severity.FATAL_ERROR.equals(value)) {
            skipToErrors.setSelectedIndex(4);
        }
    }

    @Override
    public OutputState getSkipToOutput() {
        final int index = skipToOutput.getSelectedIndex();
        if (index <= 0) {
            return null;
        }
        if (index == 1) {
            return OutputState.NOT_EMPTY;
        }
        return OutputState.EMPTY;
    }

    @Override
    public void setSkipToOutput(OutputState value) {
        if (value == null) {
            skipToOutput.setSelectedIndex(0);
        } else if (OutputState.NOT_EMPTY.equals(value)) {
            skipToOutput.setSelectedIndex(1);
        } else if (OutputState.EMPTY.equals(value)) {
            skipToOutput.setSelectedIndex(2);
        }
    }

    @UiHandler("addXPath")
    public void onAddXPathClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().addXPathFilter();
        }
    }

    @UiHandler("editXPath")
    public void onEditXPathClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().editXPathFilter();
        }
    }

    @UiHandler("removeXPath")
    public void onRemoveXPathClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().removeXPathFilter();
        }
    }

    @Override
    public void setEditEnabled(final boolean enabled) {
        editXPath.setEnabled(enabled);
    }

    @Override
    public void setRemoveEnabled(final boolean enabled) {
        removeXPath.setEnabled(enabled);
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (SteppingFilterPresenter.LIST.equals(slot)) {
            xPathList.setWidget(content);
        }
    }

    public interface Binder extends UiBinder<Widget, SteppingFilterViewImpl> {
    }
}
