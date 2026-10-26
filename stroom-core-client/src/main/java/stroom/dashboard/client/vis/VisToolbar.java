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

package stroom.dashboard.client.vis;

import stroom.data.pager.client.RefreshButton;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

/// The toolbar above a visualisation, under the pane's title, as a table has one: the controls the visualisation
/// says it has ([VisControls]), as the app's icon buttons, so they aren't drawn over the chart where they are hard
/// to see.
/// Select a time range (on while its range tool is), Clear the selection, Reset zoom, what is selected, and a
/// note (e.g. that rows were left out); and, on the right as a table's toolbar has it, the refresh button that
/// shows the visualisation's data loading. A visualisation without controls has only the refresh button.
public class VisToolbar extends Composite {

    /// The toolbar's height, in pixels: the chart's container starts this far below the pane's top.
    public static final int HEIGHT = 28;

    private final FlowPanel toolbar;
    private final InlineSvgToggleButton rangeButton;
    private final ButtonView clearButton;
    private final ButtonView resetZoomButton;
    private final Label selectedLabel;
    private final Label noteLabel;

    private VisFrame visFrame;

    /// @param refreshButton The visualisation's refresh button, which shows its data loading.
    public VisToolbar(final RefreshButton refreshButton) {
        final ButtonPanel buttons = new ButtonPanel();
        rangeButton = new InlineSvgToggleButton();
        rangeButton.setSvg(SvgImage.HISTORY);
        rangeButton.setTitle("Select a time range");
        buttons.addButton(rangeButton);
        clearButton = buttons.addButton(new Preset(SvgImage.CLEAR, "Clear the selection", false));
        resetZoomButton = buttons.addButton(new Preset(SvgImage.ZOOM_OUT, "Reset zoom", false));

        selectedLabel = new Label();
        selectedLabel.setStyleName("dashboardVis-toolbarSelected");
        noteLabel = new Label();
        noteLabel.setStyleName("dashboardVis-toolbarNote");

        final FlowPanel right = new FlowPanel();
        right.setStyleName("button-container dashboardVis-toolbarRight");
        right.add(refreshButton);

        toolbar = new FlowPanel();
        toolbar.setStyleName("dashboardVis-toolbar dock-container-horizontal align-center");
        // Placed here as well as by its style, so it is above the chart even with a style sheet the browser cached
        // from before the toolbar (it would otherwise sit under the chart's frame, which floats over the pane).
        final Style style = toolbar.getElement().getStyle();
        style.setPosition(Position.ABSOLUTE);
        style.setLeft(0, Unit.PX);
        style.setRight(0, Unit.PX);
        style.setTop(0, Unit.PX);
        style.setHeight(HEIGHT, Unit.PX);
        toolbar.add(buttons);
        toolbar.add(selectedLabel);
        toolbar.add(noteLabel);
        toolbar.add(right);
        initWidget(toolbar);

        rangeButton.addClickHandler(event -> command(VisControls.RANGE_MODE));
        clearButton.addClickHandler(event -> {
            if (clearButton.isEnabled()) {
                command(VisControls.CLEAR_SELECTION);
            }
        });
        resetZoomButton.addClickHandler(event -> {
            if (resetZoomButton.isEnabled()) {
                command(VisControls.RESET_ZOOM);
            }
        });
        update(VisControls.NONE);
    }

    /// Takes the controls the frame's visualisation sends, and sends it the commands the buttons give.
    ///
    /// @param visFrame The frame the visualisation is drawn in.
    public void setVisFrame(final VisFrame visFrame) {
        this.visFrame = visFrame;
        visFrame.setControlsHandler(this::update);
    }

    // Shows the controls the visualisation has, as they now are.
    private void update(final VisControls controls) {
        rangeButton.setVisible(controls.range());
        rangeButton.setState(controls.rangeMode());
        rangeButton.setTitle(controls.rangeMode()
                ? "Stop selecting a time range"
                : "Select a time range (drag across the chart)");
        clearButton.setVisible(controls.selectable());
        clearButton.setEnabled(controls.selected() != null);
        resetZoomButton.setVisible(controls.zoom());
        resetZoomButton.setEnabled(controls.zoomed());
        selectedLabel.setText(controls.selected() == null
                ? ""
                : "Selected: " + controls.selected());
        selectedLabel.setTitle(selectedLabel.getText());
        noteLabel.setText(controls.note() == null
                ? ""
                : controls.note());
    }

    private void command(final String name) {
        if (visFrame != null) {
            visFrame.command(name);
        }
    }
}
