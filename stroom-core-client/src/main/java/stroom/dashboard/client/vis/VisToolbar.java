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

import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.pager.client.RefreshButton;
import stroom.docref.DocRef;
import stroom.item.client.SelectionBox;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import java.util.List;
import java.util.function.Supplier;

/// The toolbar above a visualisation, under the pane's title, as a table has one: the controls the visualisation
/// says it has ([VisControls]), as the app's icon buttons, so they aren't drawn over the chart where they are hard
/// to see.
/// Select a time range (on while its range tool is), Clear the selection, Zoom in, Zoom out, Reset zoom, Pause
/// layout (a network graph's), Show all series, Download (an image, or the data as CSV for those who may download
/// search results), Ask Stroom AI (about the data the chart draws), Interval (for Auto Interval), what is selected,
/// and a note (e.g. that rows were left out); and, on the right as a table's toolbar has
/// it, the refresh button that shows the visualisation's data loading. A visualisation without controls has only
/// Ask Stroom AI and the refresh button.
public class VisToolbar extends Composite {

    /// The toolbar's height, in pixels: the chart's container starts this far below the pane's top.
    public static final int HEIGHT = 28;

    private final FlowPanel toolbar;
    private final InlineSvgToggleButton rangeButton;
    private final ButtonView clearButton;
    private final ButtonView zoomInButton;
    private final ButtonView zoomOutButton;
    private final ButtonView resetZoomButton;
    private final InlineSvgToggleButton pauseLayoutButton;
    private final SelectionBox<String> intervalBox;
    private final ButtonView showAllButton;
    private final ButtonView downloadButton;
    private final ButtonView askAiButton;
    private final Label selectedLabel;
    private final Label noteLabel;

    private VisFrame visFrame;
    // What fires the toolbar's menu and chat events: the pane's presenter.
    private HasHandlers handlers;
    private Supplier<String> fileName = () -> "chart";
    private boolean dataDownloadAllowed;
    private Supplier<AskStroomAiContext> askAiContext;

    /// @param refreshButton The visualisation's refresh button, which shows its data loading.
    public VisToolbar(final RefreshButton refreshButton) {
        final ButtonPanel buttons = new ButtonPanel();
        rangeButton = new InlineSvgToggleButton();
        rangeButton.setSvg(SvgImage.HISTORY);
        rangeButton.setTitle("Select a time range");
        buttons.addButton(rangeButton);
        clearButton = buttons.addButton(new Preset(SvgImage.CLEAR, "Clear the selection", false));
        zoomInButton = buttons.addButton(new Preset(SvgImage.ZOOM_IN, "Zoom in", true));
        zoomOutButton = buttons.addButton(new Preset(SvgImage.ZOOM_OUT, "Zoom out", false));
        resetZoomButton = buttons.addButton(new Preset(SvgImage.FIT_WIDTH, "Reset zoom", false));
        pauseLayoutButton = new InlineSvgToggleButton();
        pauseLayoutButton.setSvg(SvgImage.PAUSE);
        pauseLayoutButton.setTitle("Pause the layout");
        buttons.addButton(pauseLayoutButton);
        showAllButton = buttons.addButton(new Preset(SvgImage.EYE, "Show all series", true));
        downloadButton = buttons.addButton(new Preset(SvgImage.DOWNLOAD, "Download", true));
        askAiButton = buttons.addButton(SvgPresets.AI.title("Ask Stroom AI about this chart's data"));
        askAiButton.setVisible(false);

        selectedLabel = new Label();
        selectedLabel.setStyleName("dashboardVis-toolbarSelected");
        noteLabel = new Label();
        noteLabel.setStyleName("dashboardVis-toolbarNote");
        // The interval Auto Interval groups the chart's time by: Auto (suiting the time shown), or a fixed one.
        intervalBox = new SelectionBox<>();
        intervalBox.addStyleName("dashboardVis-toolbarInterval");
        intervalBox.setTitle("Interval: how the chart's time is grouped");
        intervalBox.addValueChangeHandler(event -> command(VisControls.INTERVAL, event.getValue()));

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
        toolbar.add(intervalBox);
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
        zoomInButton.addClickHandler(event -> command(VisControls.ZOOM_IN));
        zoomOutButton.addClickHandler(event -> {
            if (zoomOutButton.isEnabled()) {
                command(VisControls.ZOOM_OUT);
            }
        });
        pauseLayoutButton.addClickHandler(event -> command(VisControls.PAUSE_LAYOUT));
        resetZoomButton.addClickHandler(event -> {
            if (resetZoomButton.isEnabled()) {
                command(VisControls.RESET_ZOOM);
            }
        });
        showAllButton.addClickHandler(event -> command(VisControls.SHOW_ALL_SERIES));
        downloadButton.addClickHandler(event -> showDownloadMenu(downloadButton.asWidget().getElement()));
        askAiButton.addClickHandler(event -> askStroomAi());
        update(VisControls.NONE);
    }

    /// Takes the controls the frame's visualisation sends, and sends it the commands the buttons give.
    ///
    /// @param visFrame The frame the visualisation is drawn in.
    public void setVisFrame(final VisFrame visFrame) {
        this.visFrame = visFrame;
        visFrame.setControlsHandler(this::update);
    }

    /// Places the toolbar at the top of the pane's content, under its title, and the chart's container below it.
    /// The pane's layer starts at the top of its whole tab, title bar included (seen in testing: the toolbar sat
    /// behind the title bar), so the title bar's height is measured, as the pane is laid out.
    ///
    /// @param pane           The pane's element, the toolbar's parent.
    /// @param chartContainer The element the chart's frame is placed over.
    public void layOut(final Element pane, final Element chartContainer) {
        Element content = pane;
        while (content != null && !content.hasClassName("tabLayout-content")) {
            content = content.getParentElement();
        }
        final int top = content == null
                ? 0
                : Math.max(0, content.getAbsoluteTop() - pane.getAbsoluteTop());
        toolbar.getElement().getStyle().setTop(top, Unit.PX);
        chartContainer.getStyle().setTop(top + HEIGHT, Unit.PX);
        chartContainer.getStyle().setProperty("height", "calc(100% - " + (top + HEIGHT) + "px)");
    }

    /// @param handlers What fires the toolbar's menu and chat events: the pane's presenter.
    public void setHandlers(final HasHandlers handlers) {
        this.handlers = handlers;
    }

    /// @param fileName Gives the name of a file the chart is saved to, without its extension, e.g. the pane's name.
    public void setFileName(final Supplier<String> fileName) {
        this.fileName = fileName;
    }

    /// @param dataDownloadAllowed True if the user may download the chart's data as CSV, as they may a table's
    ///                            (the Download Search Results permission). The image can always be saved.
    public void setDataDownloadAllowed(final boolean dataDownloadAllowed) {
        this.dataDownloadAllowed = dataDownloadAllowed;
    }

    /// Shows Ask Stroom AI, which attaches the chart to the chat (see [#chartContext(DocRef)]).
    ///
    /// @param askAiContext Gives what is attached, or null if there is nothing to attach yet.
    public void setAskAiContext(final Supplier<AskStroomAiContext> askAiContext) {
        this.askAiContext = askAiContext;
        askAiButton.setVisible(askAiContext != null);
    }

    /// @param visualisation The Visualisation a pane shows, or null.
    /// @return The chart, as the chat is given it for Ask Stroom AI: its Visualisation, which the chat's agent reads
    /// (its kind, columns and settings) with the dashboard it is on; or null if the pane shows none. Seen in testing:
    /// attaching the chart's table's data instead attached the table, not the chart.
    public static AskStroomAiContext chartContext(final DocRef visualisation) {
        return visualisation == null
                ? null
                : new DocumentReferenceContext(List.of(new DocumentReference(visualisation, null)));
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
        zoomInButton.setVisible(controls.zoom());
        zoomOutButton.setVisible(controls.zoom());
        zoomOutButton.setEnabled(controls.zoomed());
        resetZoomButton.setVisible(controls.zoom());
        resetZoomButton.setEnabled(controls.zoomed());
        pauseLayoutButton.setVisible(controls.layout());
        pauseLayoutButton.setState(controls.layoutPaused());
        pauseLayoutButton.setTitle(controls.layoutPaused()
                ? "Resume the layout"
                : "Pause the layout");
        intervalBox.setVisible(!controls.intervals().isEmpty());
        if (!controls.intervals().equals(intervalBox.getItems())) {
            intervalBox.clear();
            intervalBox.addItems(controls.intervals());
        }
        intervalBox.setValue(controls.interval(), false);
        showAllButton.setVisible(controls.hiddenSeries());
        downloadButton.setVisible(controls.download());
        selectedLabel.setText(controls.selected() == null
                ? ""
                : "Selected: " + controls.selected());
        selectedLabel.setTitle(selectedLabel.getText());
        noteLabel.setText(controls.note() == null
                ? ""
                : controls.note());
    }

    private void command(final String name) {
        command(name, null);
    }

    private void command(final String name, final String value) {
        if (visFrame != null) {
            visFrame.command(name, value);
        }
    }

    // The ways the chart can be saved: as an image, and its data as CSV if the user may download search results.
    private void showDownloadMenu(final Element target) {
        if (handlers == null) {
            command(VisControls.DOWNLOAD_IMAGE, safeFileName());
            return;
        }
        final List<Item> items = MenuBuilder.builder()
                .withIconMenuItem(item -> item
                        .icon(SvgImage.FILE_IMAGE)
                        .text("Image (PNG)")
                        .command(() -> command(VisControls.DOWNLOAD_IMAGE, safeFileName())))
                .withIconMenuItem(item -> item
                        .icon(SvgImage.TABLE)
                        .text("Data (CSV)")
                        .enabled(dataDownloadAllowed)
                        .command(() -> command(VisControls.DOWNLOAD_DATA, safeFileName())))
                .build();
        ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(target).grow(3), PopupLocation.BELOW))
                .fire(handlers);
    }

    // The file's name: the name given, without what a file's name can't have.
    private String safeFileName() {
        final String name = NullSafe.string(fileName == null
                ? null
                : fileName.get()).replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]+", "_").trim();
        return name.isEmpty()
                ? "chart"
                : name;
    }

    private void askStroomAi() {
        final AskStroomAiContext context = askAiContext == null
                ? null
                : askAiContext.get();
        if (handlers == null) {
            return;
        }
        if (context == null) {
            AlertEvent.fireInfo(handlers, "This pane shows no Visualisation yet.", null);
        } else {
            AskStroomAiEvent.fire(handlers, context);
        }
    }
}
