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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.shared.EditableLong;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.editorsandviewers.EditorWidgets;
import stroom.gwt.workbench.client.widgets.query.ExpressionBuilderStories;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.item.client.SelectionBox;
import stroom.processor.client.view.DaysWidget;
import stroom.query.api.UserTimeZone;
import stroom.query.client.view.TimeRangeSelector;
import stroom.query.client.view.TimeZoneWidget;
import stroom.schedule.client.ScheduleBox;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.Time;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeModel;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.datepicker.client.DateTimeViewImpl;
import stroom.widget.datepicker.client.TimeBox;
import stroom.widget.dropdowntree.client.view.DropDownViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FormGroup;
import stroom.widget.linecolinput.client.LineColInput;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/// A review sheet of Stroom's controls in each view state, side by side: as normal, read only and
/// disabled. Each control is the real widget, put into its state with its own API
/// (`setReadOnly` and `setEnabled(false)`), so the sheet shows what Stroom shows. Read only is the
/// normal field with its value greyed; disabled is a filled field with no border.
public final class ViewStateStories {

    private static final String NO_READ_ONLY = "No read-only state";
    private static final String NOT_APPLICABLE = "Not applicable: read-only documents hide or disable it";
    private static final String FILTER_NOTE = "Not applicable: it chooses what is shown, so read-only "
                                              + "documents leave it usable";
    private static final String NO_RADIOS = "No read-only state: Stroom has no radio buttons in use";
    private static final long DATE_TIME = 1_700_000_000_000L;

    private ViewStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/ViewStates", ViewStateStories.class)
                .layout(StoryLayout.PADDED)
                // Every control as normal, read only and disabled
                .story("AllControls", context -> {
                    // For the controls whose popups are Stroom dialogs (e.g. DateTimeBox)
                    final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
                    harness.add(sheet(harness));
                    return harness.asWidget();
                })
                .withPlay(play -> {
                    // Read only: readable and focusable, but nothing can change the value
                    // A form group with no identity names its field (it once didn't)
                    play.expect(play.getAllByRole("textbox", "Feed Name")).toHaveLength(3);
                    final Query readOnlyText = play.getAllByDisplayValue("My Feed").nth(1);
                    play.expect(readOnlyText).toHaveAttribute("readonly");
                    play.expect(readOnlyText).not().toBeDisabled();
                    final Query tickBoxes = play.getAllByRole("checkbox", "Send Empty Reports");
                    play.click(tickBoxes.nth(1));
                    play.expect(tickBoxes.nth(1)).toBeChecked();
                    play.expect(tickBoxes.nth(1)).toHaveAttribute("aria-readonly", "true");
                    play.expect(play.getAllByRole("button", TextMatch.containing("My Pipeline")).nth(1))
                            .toHaveAttribute("aria-disabled", "true");
                    play.click(play.getAllByDisplayValue("Excel").nth(1));
                    play.expect(play.screen().querySelector(".SelectionPopup")).toBeNull();
                    // Disabled: out of the tab order (native controls)
                    play.expect(tickBoxes.nth(2)).toBeDisabled();
                })
                // Tables, editors and the other controls, as normal, read only and disabled
                .story("TablesAndEditors", context -> {
                    final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());
                    harness.add(otherSheet(harness));
                    return harness.asWidget();
                });
    }

    private static Widget sheet(final ScreenHarness harness) {
        final FlexTable table = new FlexTable();
        table.getElement().getStyle().setProperty("borderCollapse", "separate");
        table.getElement().getStyle().setProperty("borderSpacing", "16px 10px");
        // On the page colour that Stroom's screens and dialogs have, not the workbench's
        table.getElement().getStyle().setProperty("background", "var(--page__background-color)");
        table.getElement().getStyle().setProperty("color", "var(--text-color)");
        header(table, "Control", "Normal", "Read only", "Disabled");

        final List<Row> rows = List.of(
                new Row("Text box", () -> textBox("My Feed"), w -> ((TextBox) w).setReadOnly(true),
                        w -> ((TextBox) w).setEnabled(false)),
                new Row("Password", ViewStateStories::password, w -> ((PasswordTextBox) w).setReadOnly(true),
                        w -> ((PasswordTextBox) w).setEnabled(false)),
                new Row("Text area", ViewStateStories::textArea, w -> ((TextArea) w).setReadOnly(true),
                        w -> ((TextArea) w).setEnabled(false)),
                new Row("Number spinner", ViewStateStories::spinner, w -> ((ValueSpinner) w).setReadOnly(true),
                        w -> ((ValueSpinner) w).setEnabled(false)),
                new Row("List (selection box)", ViewStateStories::selectionBox,
                        w -> selectionBoxOf(w).setReadOnly(true),
                        w -> selectionBoxOf(w).setEnabled(false)),
                new Row("Tick box", ViewStateStories::tickBox, w -> ((CustomCheckBox) w).setReadOnly(true),
                        w -> ((CustomCheckBox) w).setEnabled(false)),
                new Row("Document picker", PickerField::new, w -> ((PickerField) w).view.setReadOnly(true),
                        w -> ((PickerField) w).view.setEnabled(false)),
                new Row("Date and time", () -> dateTimeBox(harness), w -> ((DateTimeBox) w).setReadOnly(true),
                        w -> ((DateTimeBox) w).setEnabled(false)),
                new Row("Time", ViewStateStories::timeBox, w -> ((TimeBox) w).setReadOnly(true),
                        w -> ((TimeBox) w).setEnabled(false)),
                new Row("Date (text)", ViewStateStories::dateBox, w -> ((MyDateBox) w).setReadOnly(true),
                        w -> ((MyDateBox) w).setEnabled(false)),
                new Row("Duration", ViewStateStories::duration, w -> ((DurationPicker) w).setReadOnly(true),
                        w -> ((DurationPicker) w).setEnabled(false)),
                new Row("Schedule", ViewStateStories::schedule, w -> ((ScheduleBox) w).setReadOnly(true),
                        w -> ((ScheduleBox) w).setEnabled(false)),
                new Row("Line and column", ViewStateStories::lineCol, w -> ((LineColInput) w).setReadOnly(true),
                        w -> ((LineColInput) w).setEnabled(false)),
                new Row("Time zone", ViewStateStories::timeZone, w -> ((TimeZoneWidget) w).setReadOnly(true),
                        w -> ((TimeZoneWidget) w).setEnabled(false)),
                new Row("Active days", ViewStateStories::days, w -> ((DaysWidget) w).setReadOnly(true),
                        w -> ((DaysWidget) w).setEnabled(false)),
                new Row("Quick filter", QuickFilter::new, null, w -> ((QuickFilter) w).setEnabled(false), FILTER_NOTE),
                new Row("Time range", TimeRangeSelector::new, w -> ((TimeRangeSelector) w).setReadOnly(true),
                        w -> ((TimeRangeSelector) w).setEnabled(false)),
                new Row("Form group (label)", LabelledField::new,
                        w -> ((LabelledField) w).textBox.setReadOnly(true),
                        w -> {
                            ((LabelledField) w).formGroup.setDisabled(true);
                            ((LabelledField) w).textBox.setEnabled(false);
                        }),
                new Row("Text button", ViewStateStories::textButton, null, w -> ((Button) w).setEnabled(false),
                        NOT_APPLICABLE),
                new Row("Icon button", ViewStateStories::iconButton, null,
                        w -> ((InlineSvgButton) w).setEnabled(false), NOT_APPLICABLE));

        int row = 1;
        for (final Row r : rows) {
            table.setWidget(row, 0, label(r.name, true));
            table.setWidget(row, 1, cell(r.name, r.factory, null));
            if (r.readOnly != null) {
                table.setWidget(row, 2, cell(r.name, r.factory, r.readOnly));
            } else {
                table.setWidget(row, 2, note(r.noReadOnly));
            }
            if (r.disabled != null) {
                table.setWidget(row, 3, cell(r.name, r.factory, r.disabled));
            } else {
                table.setWidget(row, 3, note("Not applicable"));
            }
            row++;
        }
        return table;
    }

    // One control in one state, or what went wrong making it. It is named by its row, as a
    // FormGroup's label would name it in Stroom (a FormGroup or a control with its own label keeps
    // that).
    private static Widget cell(final String name, final Supplier<Widget> factory, final State state) {
        try {
            final Widget widget = factory.get();
            if (!hasOwnName(widget)) {
                AccessibleName.set(widget, name);
            }
            if (state != null) {
                state.apply(widget);
            }
            return sized(widget);
        } catch (final RuntimeException e) {
            return note("Failed: " + e.getMessage());
        }
    }

    private static Widget otherSheet(final ScreenHarness harness) {
        final FlexTable table = new FlexTable();
        table.getElement().getStyle().setProperty("borderCollapse", "separate");
        table.getElement().getStyle().setProperty("borderSpacing", "16px 12px");
        table.getElement().getStyle().setProperty("background", "var(--page__background-color)");
        table.getElement().getStyle().setProperty("color", "var(--text-color)");
        header(table, "Control", "Normal", "Read only", "Disabled");

        int row = 1;
        // A grid as Stroom's list screens draw one: the second row is a switched-off item (its
        // text greyed, its controls still drawn as usual)
        table.setWidget(row, 0, label("Table", true));
        table.setWidget(row, 1, gridFrame(grid(harness, true)));
        table.setWidget(row, 2, gridFrame(grid(harness, false)));
        table.setWidget(row, 3, note("No disabled state: its cells are disabled one by one"));
        row++;

        table.setWidget(row, 0, label("Expression", true));
        // The expression editor draws its own border, as a field does
        table.setWidget(row, 1, frame(ExpressionBuilderStories.expressionEditor(harness, false), "300px", "170px",
                false));
        table.setWidget(row, 2, frame(ExpressionBuilderStories.expressionEditor(harness, true), "300px", "170px",
                false));
        table.setWidget(row, 3, note("No disabled state"));
        row++;

        table.setWidget(row, 0, label("Code editor", true));
        table.setWidget(row, 1, frame(codeEditor(harness, false), "300px", "110px", true));
        table.setWidget(row, 2, frame(codeEditor(harness, true), "300px", "110px", true));
        table.setWidget(row, 3, note("No disabled state"));
        row++;

        table.setWidget(row, 0, label("Native list", true));
        table.setWidget(row, 1, sized(named(listBox(true), "Native list")));
        table.setWidget(row, 2, note("No read-only state: only used in dialogs, not documents"));
        table.setWidget(row, 3, sized(named(listBox(false), "Native list")));
        row++;

        table.setWidget(row, 0, label("Radio buttons", true));
        table.setWidget(row, 1, radios("normal", true));
        table.setWidget(row, 2, note(NO_RADIOS));
        table.setWidget(row, 3, radios("disabled", false));
        row++;

        table.setWidget(row, 0, label("Toggle button", true));
        table.setWidget(row, 1, StoryPanels.row(8, toggle(false, true), toggle(true, true)));
        table.setWidget(row, 2, note(NOT_APPLICABLE));
        table.setWidget(row, 3, StoryPanels.row(8, toggle(false, false), toggle(true, false)));
        return table;
    }

    // Controls that name themselves (their own label, or names for their parts)
    private static boolean hasOwnName(final Widget widget) {
        return widget instanceof LabelledField
               || widget instanceof CustomCheckBox
               || widget instanceof DaysWidget
               || widget instanceof DurationPicker
               || widget instanceof TimeZoneWidget;
    }

    private static Widget named(final Widget widget, final String name) {
        AccessibleName.set(widget, name);
        return widget;
    }

    private static Widget gridFrame(final Widget grid) {
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", "300px");
        frame.getElement().getStyle().setProperty("height", "90px");
        frame.getElement().getStyle().setProperty("position", "relative");
        grid.setSize("100%", "100%");
        frame.add(grid);
        return frame;
    }

    // A grid of jobs: enabled, name, maximum tasks and an action, editable or as read-only screens
    // draw it (a borderless tick, a plain number and a disabled action)
    private static Widget grid(final ScreenHarness harness, final boolean editable) {
        final MyDataGrid<Job> grid = new MyDataGrid<>(new SimpleEventBus());
        grid.addColumn(DataGridUtil.updatableTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(Job::isEnabled), editable)
                        .enabledWhen(Job::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Enabled").build(),
                ColumnSizeConstants.ENABLED_COL);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder(Job::getName)
                        .enabledWhen(Job::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Job").build(),
                110);
        grid.addColumn(DataGridUtil.valueSpinnerColumnBuilder(
                                (Job job) -> maxTasks(job, editable),
                                0L, 100L)
                        .enabledWhen(Job::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Max Tasks").build(),
                80);
        grid.addColumn(DataGridUtil.svgPresetColumnBuilder(true,
                                (Job job) -> SvgPresets.DELETE.enabled(editable))
                        .build(),
                DataGridUtil.headingBuilder("").build(),
                ColumnSizeConstants.ICON_COL);
        grid.setRowData(0, List.of(new Job("Data Retention", true, 20), new Job("XX Processor", false, 5)));
        grid.setRowCount(2, true);
        return grid;
    }

    // As a read-only screen gives it: an editable value whose editing is switched off
    private static Number maxTasks(final Job job, final boolean editable) {
        final EditableLong value = new EditableLong(job.getMaxTasks());
        value.setEditable(editable);
        return value;
    }

    private static Widget codeEditor(final ScreenHarness harness, final boolean readOnly) {
        final EditorPresenter editor = EditorWidgets.editorPresenter(harness.getEventBus());
        editor.setMode(AceEditorMode.XML);
        editor.setReadOnly(readOnly);
        editor.getLineNumbersOption().setOn(true);
        editor.setText("<feed>\n  <name>MY_FEED</name>\n</feed>");
        return editor.getWidget();
    }

    private static Widget frame(final Widget widget,
                                final String width,
                                final String height,
                                final boolean bordered) {
        final FlowPanel frame = new FlowPanel();
        frame.getElement().getStyle().setProperty("width", width);
        frame.getElement().getStyle().setProperty("height", height);
        frame.getElement().getStyle().setProperty("position", "relative");
        frame.getElement().getStyle().setProperty("display", "flex");
        frame.getElement().getStyle().setProperty("flexDirection", "column");
        if (bordered) {
            // As a field's border, for the code editor, which has none of its own
            frame.getElement().getStyle().setProperty("border", "1px solid var(--control__border-color)");
            frame.getElement().getStyle().setProperty("borderRadius", ".25rem");
            frame.getElement().getStyle().setProperty("overflow", "hidden");
        }
        widget.getElement().getStyle().setProperty("flex", "1 1 auto");
        widget.getElement().getStyle().setProperty("minHeight", "0");
        frame.add(widget);
        return frame;
    }

    private static Widget listBox(final boolean enabled) {
        final ListBox listBox = new ListBox();
        listBox.addItem("Excel");
        listBox.addItem("CSV");
        listBox.setEnabled(enabled);
        return listBox;
    }

    private static Widget radios(final String group, final boolean enabled) {
        final RadioButton first = new RadioButton(group, "Ascending");
        final RadioButton second = new RadioButton(group, "Descending");
        first.setValue(true);
        first.setEnabled(enabled);
        second.setEnabled(enabled);
        return StoryPanels.row(8, first, second);
    }

    private static Widget toggle(final boolean on, final boolean enabled) {
        final InlineSvgToggleButton button = new InlineSvgToggleButton();
        button.setSvg(SvgImage.FILTER);
        button.setTitle(on ? "Filter on" : "Filter off");
        button.setState(on);
        button.setEnabled(enabled);
        return button;
    }

    private static void header(final FlexTable table, final String... names) {
        for (int i = 0; i < names.length; i++) {
            table.setWidget(0, i, label(names[i], true));
        }
    }

    private static Label label(final String text, final boolean bold) {
        final Label label = new Label(text);
        if (bold) {
            label.getElement().getStyle().setProperty("fontWeight", "600");
        }
        label.getElement().getStyle().setProperty("whiteSpace", "nowrap");
        return label;
    }

    private static Label note(final String text) {
        final Label label = new Label(text);
        label.getElement().getStyle().setProperty("fontSize", "12px");
        label.getElement().getStyle().setProperty("fontStyle", "italic");
        label.getElement().getStyle().setProperty("maxWidth", "240px");
        label.getElement().getStyle().setProperty("whiteSpace", "normal");
        return label;
    }

    private static Widget sized(final Widget widget) {
        widget.getElement().getStyle().setProperty("width", "240px");
        widget.getElement().getStyle().setProperty("boxSizing", "border-box");
        return widget;
    }

    private static TextBox textBox(final String value) {
        final TextBox textBox = new TextBox();
        textBox.setValue(value);
        return textBox;
    }

    private static Widget password() {
        final PasswordTextBox password = new PasswordTextBox();
        password.setValue("secret");
        return password;
    }

    private static Widget textArea() {
        final TextArea textArea = new TextArea();
        textArea.setVisibleLines(2);
        textArea.setValue("Summarise the report's data.");
        return textArea;
    }

    private static Widget spinner() {
        final ValueSpinner spinner = new ValueSpinner();
        spinner.setMin(0);
        spinner.setMax(100);
        spinner.setValue(20);
        return spinner;
    }

    private static Widget selectionBox() {
        final SelectionBox<String> box = new SelectionBox<>();
        box.addItems(List.of("Excel", "CSV", "Markdown"));
        box.setValue("Excel");
        return box;
    }

    @SuppressWarnings("unchecked")
    private static SelectionBox<String> selectionBoxOf(final Widget widget) {
        return (SelectionBox<String>) widget;
    }

    private static Widget tickBox() {
        final CustomCheckBox tickBox = new CustomCheckBox();
        tickBox.setLabel("Send Empty Reports");
        tickBox.setValue(true);
        return tickBox;
    }

    private static Widget dateTimeBox(final ScreenHarness harness) {
        final DateTimeBox box = new DateTimeBox();
        // As Stroom's presenters give it the dialog, e.g. ScheduledProcessEditPresenter
        box.setPopupProvider(() -> new DateTimePopup(harness.getEventBus(),
                new DateTimeViewImpl(GWT.create(DateTimeViewImpl.Binder.class)),
                new DateTimeModel()));
        box.setValue(DATE_TIME);
        return box;
    }

    private static Widget timeBox() {
        final TimeBox box = new TimeBox();
        box.setValue(new Time(14, 30, 0));
        return box;
    }

    private static Widget dateBox() {
        final MyDateBox box = new MyDateBox();
        box.setUtc(true);
        box.setValue("2023-11-14T22:13:20.000Z");
        return box;
    }

    private static Widget duration() {
        final DurationPicker picker = new DurationPicker();
        picker.setValue(SimpleDuration.builder().time(30).timeUnit(TimeUnit.DAYS).build());
        return picker;
    }

    private static Widget schedule() {
        final ScheduleBox box = new ScheduleBox();
        box.setValue(Schedule.builder().type(ScheduleType.CRON).expression("0 0 * * * ?").build());
        return box;
    }

    private static Widget timeZone() {
        final TimeZoneWidget widget = new TimeZoneWidget(GWT.create(TimeZoneWidget.Binder.class));
        widget.setTimeZoneUse(UserTimeZone.Use.ID);
        widget.setTimeZoneId("Europe/London");
        widget.changeVisible();
        return widget;
    }

    private static Widget days() {
        final DaysWidget widget = new DaysWidget();
        widget.setValue(Days.create(Set.of(Day.MONDAY, Day.TUESDAY, Day.WEDNESDAY)));
        return widget;
    }

    private static Widget lineCol() {
        final LineColInput input = new LineColInput();
        input.setValue(12, 4);
        return input;
    }

    private static Widget textButton() {
        final Button button = new Button();
        button.setText("OK");
        return button;
    }

    private static Widget iconButton() {
        final InlineSvgButton button = new InlineSvgButton();
        button.setSvg(SvgImage.DELETE);
        button.setTitle("Delete");
        return button;
    }


    // --------------------------------------------------------------------------------


    // A document picker's view (as DocSelectionBoxPresenter uses it), as a widget
    private static final class PickerField extends Composite {

        private final DropDownViewImpl view = new DropDownViewImpl(GWT.create(DropDownViewImpl.Binder.class));

        private PickerField() {
            view.setText("My Pipeline", false);
            initWidget(view.asWidget());
        }
    }

    // A FormGroup with its text box, as Stroom's views lay out a labelled field
    private static final class LabelledField extends Composite {

        private final FormGroup formGroup = new FormGroup();
        private final TextBox textBox = textBox("My Feed");

        private LabelledField() {
            formGroup.setLabel("Feed Name");
            formGroup.add(textBox);
            initWidget(formGroup);
        }
    }

    // A row of the sheet's grid
    private static final class Job {

        private final String name;
        private final boolean enabled;
        private final long maxTasks;

        private Job(final String name, final boolean enabled, final long maxTasks) {
            this.name = name;
            this.enabled = enabled;
            this.maxTasks = maxTasks;
        }

        private String getName() {
            return name;
        }

        private boolean isEnabled() {
            return enabled;
        }

        private Long getMaxTasks() {
            return maxTasks;
        }
    }

    private interface State {

        void apply(Widget widget);
    }

    private static final class Row {

        private final String name;
        private final Supplier<Widget> factory;
        private final State readOnly;
        private final State disabled;
        private final String noReadOnly;

        private Row(final String name,
                    final Supplier<Widget> factory,
                    final State readOnly,
                    final State disabled,
                    final String noReadOnly) {
            this.name = name;
            this.factory = factory;
            this.readOnly = readOnly;
            this.disabled = disabled;
            this.noReadOnly = noReadOnly;
        }

        private Row(final String name, final Supplier<Widget> factory, final State readOnly, final State disabled) {
            this(name, factory, readOnly, disabled, NO_READ_ONLY);
        }
    }
}
