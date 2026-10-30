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

package stroom.receive.content.client.component;

import stroom.cell.client.ActionCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.receive.content.shared.ContentTemplate;
import stroom.util.client.DataGridUtil;
import stroom.util.client.cell.DocRefCell;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// The content templates, a row each (whether it is enabled, its number, name, type, whether it
/// copies dependencies, its pipeline, priority, max concurrent and an action menu), faded where
/// disabled; one can be selected.
public class ContentTemplateList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ContentTemplate> dataGrid;

    private final MultiSelectionModelImpl<ContentTemplate> selectionModel;
    private BiConsumer<ContentTemplate, Boolean> onEnabledChange = (template, enabled) -> {
    };
    private BiConsumer<ContentTemplate, PopupPosition> onShowActionMenu = (template, popupPosition) -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(DocRefCell.Builder)].
    public ContentTemplateList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by number (1 to n): a tick rebuilds the row
        dataGrid.setKeyProvider(ContentTemplate::getTemplateNumber);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
    }

    /// Puts the focus on the list.
    public void focus() {
        dataGrid.setFocus(true);
    }

    /// @param data The templates.
    public void setData(final List<ContentTemplate> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());
    }

    /// @return The selection, one template at most.
    public MultiSelectionModel<ContentTemplate> getSelectionModel() {
        return selectionModel;
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// @param onEnabledChange Given a template and whether it is now enabled, when its tick box is
    ///                        pressed.
    public void setOnEnabledChange(final BiConsumer<ContentTemplate, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onShowActionMenu Given a template and where to show its action menu, when its action
    ///                         button is pressed.
    public void setOnShowActionMenu(final BiConsumer<ContentTemplate, PopupPosition> onShowActionMenu) {
        this.onShowActionMenu = onShowActionMenu;
    }

    /// @param row A template.
    /// @return The name of its action menu, e.g. "Actions for template 3 'My template'".
    public static String getActionMenuName(final ContentTemplate row) {
        final String name = row.getName();
        return "Actions for template " + row.getTemplateNumber()
               + (NullSafe.isBlankString(name)
                ? ""
                : " '" + name + "'");
    }

    /// Adds the columns.
    ///
    /// @param pipelineCell The pipeline cell's builder, configured to open its pipelines.
    public void addColumns(final DocRefCell.Builder<ContentTemplate> pipelineCell) {
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(ContentTemplate::isEnabled))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .withFieldUpdater((rowIndex, contentTemplate, tickBoxState) -> {
                            onEnabledChange.accept(contentTemplate, tickBoxState.toBoolean());
                            dataGrid.redrawRow(rowIndex);
                        })
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether the template will be included for matching on data receipt.")
                        .centerAligned()
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getTemplateNumber),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Template No.")
                        .rightAligned()
                        .build(),
                100);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ContentTemplate::getName)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Name")
                        .withToolTip("The name of the template.")
                        .build(),
                200);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ContentTemplate::getTemplateType))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of content to create.")
                        .build(),
                140);

        dataGrid.addColumn(
                DataGridUtil.readOnlyTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                ContentTemplate::isCopyElementDependencies))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("Copy Dependencies")
                        .withToolTip("If Template Type is INHERIT_PIPELINE then this option allows you to copy " +
                                     "any entities set within the properties of the pipeline being inherited from. " +
                                     "It will not copy entities referenced on any ancestor pipelines.")
                        .build(),
                140);

        dataGrid.addResizableColumn(
                DataGridUtil.docRefColumnBuilder(pipelineCell
                                .docRefFunction(row -> NullSafe.get(row, ContentTemplate::getPipeline))
                                .showIcon(true))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Pipeline")
                        .withToolTip("The pipeline to use as a template for creating content.")
                        .build(),
                300);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getProcessorPriority),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Priority")
                        .withToolTip("The priority that will be assigned to the pipeline processor on creation.")
                        .rightAligned()
                        .build(),
                100);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                DataGridUtil.toStringFunc(ContentTemplate::getProcessorMaxConcurrent),
                                TextCell::new)
                        .enabledWhen(ContentTemplate::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Max Concurrent")
                        .withToolTip("The maximum number of concurrent tasks that will be assigned to the pipeline " +
                                     "processor on creation.")
                        .rightAligned()
                        .build(),
                130);

        dataGrid.addColumn(
                DataGridUtil.columnBuilder(
                                Function.identity(),
                                () -> new ActionCell<>(this::showActionMenu,
                                        ContentTemplateList::getActionMenuName))
                        .enabledWhen(ContentTemplate::isEnabled)
                        .centerAligned()
                        .build(),
                "",
                24);
    }

    private void showActionMenu(final ContentTemplate row, final NativeEvent event) {
        onShowActionMenu.accept(row, new PopupPosition(event.getClientX() + 10, event.getClientY()));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ContentTemplateList> {

    }
}
