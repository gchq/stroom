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

package stroom.annotation.client.component;

import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.query.api.ConditionalFormattingType;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/// Annotation tags of one type, a row each (its name, its text where the type has it, and its style
/// for labels); several can be selected. New, Edit and Delete are on its toolbar.
public class AnnotationTagList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<AnnotationTag> dataGrid;

    private final MultiSelectionModelImpl<AnnotationTag> selectionModel;
    private Consumer<TagAction> onAction = action -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(AnnotationTagType)].
    public AnnotationTagList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        newButton.setOnClick(() -> onAction.accept(TagAction.NEW));
        editButton.setOnClick(() -> onAction.accept(TagAction.EDIT));
        deleteButton.setOnClick(() -> onAction.accept(TagAction.DELETE));
    }

    /// Adds the columns for a type of tag, once.
    ///
    /// @param annotationTagType The type.
    public void addColumns(final AnnotationTagType annotationTagType) {
        // Name.
        final Column<AnnotationTag, String> nameColumn = new Column<AnnotationTag, String>(new TextCell()) {
            @Override
            public String getValue(final AnnotationTag annotationTag) {
                return annotationTag.getName();
            }
        };
        dataGrid.addResizableColumn(nameColumn, "Name", 400);

        if (annotationTagType.hasTagText()) {
            // Tag text.
            final Column<AnnotationTag, String> tagTextColumn = new Column<AnnotationTag, String>(new TextCell()) {
                @Override
                public String getValue(final AnnotationTag annotationTag) {
                    return annotationTag.getTagText();
                }
            };
            dataGrid.addResizableColumn(tagTextColumn, annotationTagType.getDisplayValue(), 600);
        }

        if (annotationTagType == AnnotationTagType.LABEL) {
            // Style.
            final Function<AnnotationTag, SafeHtml> function = annotationTag ->
                    ConditionalFormattingSwatchUtil.createSwatch(
                            ConditionalFormattingType.BACKGROUND,
                            annotationTag.getStyle(),
                            null,
                            null);
            dataGrid.addColumn(DataGridUtil.htmlColumnBuilder(function)
                            .build(),
                    DataGridUtil.headingBuilder("Style")
                            .withToolTip("The style the " + annotationTagType.getDisplayValue() + ".")
                            .build(),
                    200);
        }
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<AnnotationTag> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<AnnotationTag> getSelectionModel() {
        return selectionModel;
    }

    /// @param enabled Whether Edit and Delete are enabled, for a selected tag.
    public void setSelectionActionsEnabled(final boolean enabled) {
        editButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    /// Names the buttons after the kind of tag listed, e.g. "New Annotation Status".
    ///
    /// @param tagName The kind of tag, e.g. `Annotation Status`.
    public void setTagName(final String tagName) {
        newButton.setTitle("New " + tagName);
        editButton.setTitle("Edit Selected " + tagName);
        deleteButton.setTitle("Delete Selected " + tagName);
    }

    /// @param onAction Given the action pressed.
    public void setOnAction(final Consumer<TagAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnnotationTagList> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the tags.
    public enum TagAction {
        /// Makes a tag.
        NEW,
        /// Edits the tag selected.
        EDIT,
        /// Deletes the tags selected.
        DELETE
    }
}
