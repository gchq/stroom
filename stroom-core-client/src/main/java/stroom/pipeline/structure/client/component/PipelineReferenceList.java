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

package stroom.pipeline.structure.client.component;

import stroom.data.client.component.DocRefCell;
import stroom.docref.DocRef;
import stroom.docref.DocRef.DisplayType;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// A pipeline element's reference pipelines, in the order they are tried (inherited ones first), a row
/// each (the pipeline, its feed and stream type, and the pipeline it is inherited from), each coloured
/// by whether it is inherited, added or removed, with New, Edit and Remove (Restore for a removed
/// one); one can be selected, and a double click edits it. When read only, the buttons are disabled
/// and say so.
public class PipelineReferenceList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";
    private static final String RESTORE_TITLE = "Restore Selected Pipeline Reference";

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<PipelineReference> dataGrid;

    private final String addTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<PipelineReference> selectionModel;
    private Function<PipelineReference, ReferenceState> state = reference -> null;
    private boolean canAdd;
    private Runnable onAdd = () -> {
    };
    private Consumer<PipelineReference> onEdit = reference -> {
    };
    private Consumer<PipelineReference> onRemove = reference -> {
    };

    /// Makes an empty list, with no columns until
    /// [#addColumns(Function, Function, Predicate, Supplier)].
    public PipelineReferenceList() {
        initWidget(BINDER.createAndBindUi(this));
        addTitle = addButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        addButton.setOnClick(() -> onAdd.run());
        editButton.setOnClick(() -> onEdit.accept(selectionModel.getSelected()));
        removeButton.setOnClick(() -> onRemove.accept(selectionModel.getSelected()));
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.accept(selectionModel.getSelected());
            }
        });
    }

    /// Adds the columns, once.
    ///
    /// @param state           Whether a reference is inherited, added or removed, which colours it.
    /// @param inheritedFrom   The pipeline a reference is inherited from, or `null`.
    /// @param isThisPipeline  Whether a pipeline is the one shown, so not shown as inherited from.
    /// @param docRefCell      Makes a document cell's builder, configured to open its documents.
    public void addColumns(final Function<PipelineReference, ReferenceState> state,
                           final Function<PipelineReference, DocRef> inheritedFrom,
                           final Predicate<DocRef> isThisPipeline,
                           final Supplier<DocRefCell.Builder<PipelineReference>> docRefCell) {
        this.state = state;

        // Pipeline.
        dataGrid.addResizableColumn(DataGridUtil.docRefColumnBuilder(docRefCell.get()
                                .showIcon(true)
                                .cssClassFunction(this::getStateCssClass)
                                .cellTextFunction(pipelineReference -> displayValue(
                                        NullSafe.get(pipelineReference, PipelineReference::getPipeline)))
                                .docRefFunction(pipelineReference -> NullSafe.get(
                                        pipelineReference,
                                        PipelineReference::getPipeline)))
                        .build(),
                "Pipeline", 200);

        // Feed.
        dataGrid.addResizableColumn(DataGridUtil.docRefColumnBuilder(docRefCell.get()
                                .cssClassFunction(this::getStateCssClass)
                                .cellTextFunction(pipelineReference -> displayValue(
                                        NullSafe.get(pipelineReference, PipelineReference::getFeed)))
                                .docRefFunction(pipelineReference -> NullSafe.get(
                                        pipelineReference,
                                        PipelineReference::getFeed)))
                        .build(),
                "Feed", 350);

        // Stream type.
        dataGrid.addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getStreamType() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getStreamType());
            }
        }, "Type", 200);

        // Inherited from.
        dataGrid.addResizableColumn(DataGridUtil.docRefColumnBuilder(docRefCell.get()
                                .cssClassFunction(this::getStateCssClass)
                                .cellTextFunction(pipelineReference -> {
                                    final DocRef source = inheritedFrom.apply(pipelineReference);
                                    if (pipelineReference == null
                                        || source == null
                                        || isThisPipeline.test(source)) {
                                        return SafeHtmlUtils.EMPTY_SAFE_HTML;
                                    } else {
                                        return SafeHtmlUtils.fromString(source.getDisplayValue(DisplayType.AUTO));
                                    }
                                })
                                .docRefFunction(inheritedFrom))
                        .build(),
                "Inherited From", 350);
    }

    /// Shows references, enabling the buttons for them.
    ///
    /// @param references The references, in order.
    public void setData(final List<PipelineReference> references) {
        dataGrid.setRowData(0, references);
        dataGrid.setRowCount(references.size());
        enableButtons();
    }

    /// @param canAdd Whether the element shown can have references added.
    public void setCanAdd(final boolean canAdd) {
        this.canAdd = canAdd;
        enableButtons();
    }

    /// @param tableName The list's name, for assistive technology.
    public void setTableName(final String tableName) {
        dataGrid.setTableName(tableName);
    }

    /// @return The selection, one reference at most.
    public MultiSelectionModelImpl<PipelineReference> getSelectionModel() {
        return selectionModel;
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEdit Given the selected reference, or `null`, when Edit is pressed or a reference is
    ///               double clicked.
    public void setOnEdit(final Consumer<PipelineReference> onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Given the selected reference, or `null`, when Remove (or Restore) is pressed.
    public void setOnRemove(final Consumer<PipelineReference> onRemove) {
        this.onRemove = onRemove;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        addButton.setEnabled(!readOnly && canAdd);

        final PipelineReference selected = selectionModel.getSelected();
        final ReferenceState selectedState = selected == null
                ? null
                : state.apply(selected);

        editButton.setEnabled(!readOnly && ReferenceState.ADDED.equals(selectedState));
        removeButton.setEnabled(!readOnly && selected != null);

        final String readOnlySuffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        addButton.setTitle(addTitle + readOnlySuffix);
        editButton.setTitle(editTitle + readOnlySuffix);
        // Pressing it on a reference that is already removed restores it, so say so
        final String title = ReferenceState.REMOVED.equals(selectedState)
                ? RESTORE_TITLE
                : removeTitle;
        removeButton.setTitle(title + readOnlySuffix);
    }

    private String getStateCssClass(final PipelineReference pipelineReference) {
        return state.apply(pipelineReference).getClassName();
    }

    private SafeHtml getSafeHtmlWithState(final PipelineReference pipelineReference, final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.append(SafeHtmlUtils.fromTrustedString(
                "<div class=\"" + getStateCssClass(pipelineReference) + "\">"));
        builder.appendEscaped(string);
        builder.append(SafeHtmlUtils.fromTrustedString("</div>"));
        return builder.toSafeHtml();
    }

    private static SafeHtml displayValue(final DocRef docRef) {
        return docRef == null
                ? SafeHtmlUtils.EMPTY_SAFE_HTML
                : SafeHtmlUtils.fromString(docRef.getDisplayValue(DisplayType.AUTO));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PipelineReferenceList> {

    }


    /// Whether a reference is inherited, added or removed, which colours it.
    public enum ReferenceState {
        /// Inherited from a parent pipeline.
        INHERITED("pipeline-property pipeline-property--inherited"),
        /// Added on this pipeline.
        ADDED("pipeline-property pipeline-property--added"),
        /// An inherited one removed on this pipeline.
        REMOVED("pipeline-property pipeline-property--removed");

        private final String className;

        ReferenceState(final String className) {
            this.className = className;
        }

        /// @return The class that colours it.
        public String getClassName() {
            return className;
        }
    }
}
