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

package stroom.pipeline.structure.client.component;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.BiConsumer;

/// A pipeline's Structure tab: its elements (a tree, with buttons to add, remove, edit and restore
/// them, the pipeline it inherits from and a button to view its source) above the selected
/// element's properties and pipeline references (lists its controller shows here).
public class PipelineStructure extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton add;
    @UiField
    ToolbarButton remove;
    @UiField
    ToolbarButton edit;
    @UiField
    ToolbarButton restore;
    @UiField
    DropDownBox inherit;
    @UiField
    PipelineTree tree;
    @UiField
    SimplePanel properties;
    @UiField
    SimplePanel pipelineReferences;
    @UiField
    ToolbarButton viewSource;

    private BiConsumer<PipelineStructureAction, Element> onAction = (action, element) -> {
    };

    /// Makes the tab, with an empty tree and no lists.
    public PipelineStructure() {
        initWidget(BINDER.createAndBindUi(this));
        add.addClickHandler(this::onAddClick);
        remove.addClickHandler(this::onRemoveClick);
        edit.addClickHandler(this::onEditClick);
        restore.addClickHandler(this::onRestoreClick);
        viewSource.addClickHandler(this::onViewSource);
    }

    /// @return The box its controller attaches the Inherit From picker to.
    public DropDownBox getInheritBox() {
        return inherit;
    }

    /// @return The tree of elements, which its controller fills.
    public PipelineTree getTree() {
        return tree;
    }

    /// @param view The selected element's properties.
    public void setProperties(final IsWidget view) {
        properties.setWidget(view);
    }

    /// @param view The selected element's pipeline references.
    public void setPipelineReferences(final IsWidget view) {
        pipelineReferences.setWidget(view);
    }

    /// @param enabled Whether an element can be added.
    public void setAddEnabled(final boolean enabled) {
        add.setEnabled(enabled);
    }

    /// @param enabled Whether the selected element can be removed.
    public void setRemoveEnabled(final boolean enabled) {
        remove.setEnabled(enabled);
    }

    /// @param enabled Whether the selected element can be edited.
    public void setEditEnabled(final boolean enabled) {
        edit.setEnabled(enabled);
    }

    /// @param enabled Whether an element can be restored.
    public void setRestoreEnabled(final boolean enabled) {
        restore.setEnabled(enabled);
    }

    /// @param onAction Given the button pressed and the element pressed (e.g. to show a menu
    ///                 below).
    public void setOnAction(final BiConsumer<PipelineStructureAction, Element> onAction) {
        this.onAction = onAction;
    }

    private void fire(final PipelineStructureAction action, final ClickEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        onAction.accept(action, target);
    }

    private void onAddClick(final ClickEvent event) {
        fire(PipelineStructureAction.ADD, event);
    }

    private void onRemoveClick(final ClickEvent event) {
        fire(PipelineStructureAction.REMOVE, event);
    }

    private void onEditClick(final ClickEvent event) {
        fire(PipelineStructureAction.EDIT, event);
    }

    private void onRestoreClick(final ClickEvent event) {
        fire(PipelineStructureAction.RESTORE, event);
    }

    private void onViewSource(final ClickEvent event) {
        fire(PipelineStructureAction.VIEW_SOURCE, event);
    }


    // --------------------------------------------------------------------------------


    /// The tab's buttons.
    public enum PipelineStructureAction {
        /// Adds an element below the selected one, from a menu.
        ADD,
        /// Removes the selected element.
        REMOVE,
        /// Edits the selected element's name and description.
        EDIT,
        /// Restores an inherited element that was removed, from a menu.
        RESTORE,
        /// Shows the pipeline's source.
        VIEW_SOURCE
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PipelineStructure> {

    }
}
