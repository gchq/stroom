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

package stroom.pipeline.stepping.client.component;

import stroom.cell.info.client.CellIcon;
import stroom.item.client.SelectionBox;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.PipelineElementTypes;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// The step filters of a pipeline's elements: a list of the elements, each marked when it has an
/// active filter, beside the filters of the one chosen (a minimum severity, an output state and
/// XPath filters).
public class SteppingFilterEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "stepping-filter-editor";
    private static final int ICON_COL_WIDTH = 22;

    @UiField
    DataGrid<PipelineElement> elementChooser;
    @UiField
    Label nameLabel;
    @UiField
    SelectionBox<Severity> skipToErrors;
    @UiField
    SelectionBox<OutputState> skipToOutput;
    @UiField
    XPathList xPathList;

    private final MultiSelectionModelImpl<PipelineElement> elementSelectionModel = new MultiSelectionModelImpl<>();
    private Consumer<PipelineElement> onElementSelected = element -> {
    };
    private Runnable onChange = () -> {
    };

    /// Makes the editor, with no elements until [#setElements(List)].
    public SteppingFilterEditor() {
        initWidget(BINDER.createAndBindUi(this));
        skipToErrors.addValueChangeHandler(this::onSkipToErrorsValueChange);
        skipToOutput.addValueChangeHandler(this::onSkipToOutputValueChange);

        skipToErrors.addItem(Severity.INFO);
        skipToErrors.addItem(Severity.WARNING);
        skipToErrors.addItem(Severity.ERROR);
        skipToErrors.addItem(Severity.FATAL_ERROR);

        skipToOutput.addItem(OutputState.NOT_EMPTY);
        skipToOutput.addItem(OutputState.EMPTY);

        elementSelectionModel.addSelectionChangeHandler(event ->
                onElementSelected.accept(elementSelectionModel.getSelected()));
    }

    /// Adds the element list's columns: each element's icon, its name and, if it has an active
    /// filter, a filter icon.
    ///
    /// @param elementTypes    The pipeline's element types, for the icons.
    /// @param hasActiveFilter Whether an element has an active filter.
    public void setElementTypes(final PipelineElementTypes elementTypes,
                                final Predicate<PipelineElement> hasActiveFilter) {
        final Column<PipelineElement, CellIcon> iconColumn = DataGridUtil.cellIconColumnBuilder(false,
                        (final PipelineElement pipelineElement) -> {
                            final PipelineElementType pipelineElementType =
                                    elementTypes.getElementType(pipelineElement);
                            if (pipelineElementType != null && pipelineElementType.getIcon() != null) {
                                return CellIcon.of(
                                        pipelineElementType.getIcon(),
                                        pipelineElementType.getType(),
                                        true);
                            } else {
                                return null;
                            }
                        })
                .withStyleName(BLOCK + "__icon-cell svgIcon")
                .centerAligned()
                .build();
        elementChooser.addColumn(iconColumn, "", ICON_COL_WIDTH);

        final Column<PipelineElement, String> textColumn = DataGridUtil.textColumnBuilder(
                        PipelineElement::getDisplayName)
                .withStyleName(BLOCK + "__text-cell")
                .build();
        elementChooser.addAutoResizableColumn(textColumn, "", 100);

        // Only visible if the element has an active filter
        final Function<PipelineElement, String> filterActiveStyleFunc = pipelineElement ->
                hasActiveFilter.test(pipelineElement)
                        ? BLOCK + "__filter-icon-cell--on"
                        : BLOCK + "__filter-icon-cell--off";
        final Column<PipelineElement, CellIcon> filterIconColumn = DataGridUtil.cellIconColumnBuilder(
                        false,
                        (PipelineElement pipelineElement) ->
                                CellIcon.of(SvgImage.FILTER, "Has active filter(s)"))
                .withStyleName(BLOCK + "__filter-icon-cell svgIcon icon-colour__green")
                .withConditionalStyleName(filterActiveStyleFunc)
                .centerAligned()
                .build();
        elementChooser.addColumn(filterIconColumn, "", ICON_COL_WIDTH);

        elementChooser.setSelectionModel(elementSelectionModel,
                new DataGridSelectionEventManager<>(elementChooser, elementSelectionModel, false));
    }

    /// @param elements The pipeline's elements, to list.
    public void setElements(final List<PipelineElement> elements) {
        elementChooser.setRowData(0, elements);
        elementChooser.setRowCount(elements.size());
    }

    /// Chooses an element, the keyboard focus on its row.
    ///
    /// @param element The element.
    /// @param row     Its row.
    public void selectElement(final PipelineElement element, final int row) {
        // Distinction between the item that is selected and the one that has focus while
        // moving focus up/down with the keyboard
        elementSelectionModel.setSelected(element);
        elementChooser.setKeyboardSelectedRow(row, true);
    }

    /// Draws the element list again, e.g. once an element's filters are on or off.
    public void redrawElements() {
        elementChooser.redraw();
    }

    /// Puts the keyboard focus on the element list.
    public void focusElements() {
        elementChooser.setFocus(true);
    }

    /// @param name The chosen element's name, the filters' heading.
    public void setName(final String name) {
        nameLabel.setText(name);
    }

    /// @return The minimum severity to stop at, or `null` for none.
    public Severity getSkipToErrors() {
        return skipToErrors.getValue();
    }

    /// @param severity The minimum severity to stop at, or `null` for none.
    public void setSkipToErrors(final Severity severity) {
        skipToErrors.setValue(severity);
    }

    /// @return The output state to stop at, or `null` for none.
    public OutputState getSkipToOutput() {
        return skipToOutput.getValue();
    }

    /// @param outputState The output state to stop at, or `null` for none.
    public void setSkipToOutput(final OutputState outputState) {
        skipToOutput.setValue(outputState);
    }

    /// @return The chosen element's XPath filters, for its controller to fill and add buttons to.
    public XPathList getXPathList() {
        return xPathList;
    }

    /// @param onElementSelected Given the element the user chooses.
    public void setOnElementSelected(final Consumer<PipelineElement> onElementSelected) {
        this.onElementSelected = onElementSelected;
    }

    /// @param onChange Called when the minimum severity or the output state is changed.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void onSkipToErrorsValueChange(final ValueChangeEvent<Severity> e) {
        onChange.run();
    }

    private void onSkipToOutputValueChange(final ValueChangeEvent<OutputState> e) {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SteppingFilterEditor> {

    }
}
