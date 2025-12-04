/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.pipeline.structure.client.presenter.PipelineStructureUiHandlers;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class PipelineStructureViewImpl extends ViewWithUiHandlers<PipelineStructureUiHandlers>
        implements PipelineStructureView {

    private final Widget widget;
    @UiField(provided = true)
    SvgButton add;
    @UiField(provided = true)
    SvgButton remove;
    @UiField(provided = true)
    SvgButton edit;
    @UiField(provided = true)
    SvgButton restore;
    @UiField
    SimplePanel inherit;
    @UiField
    ScrollPanel treeContainer;
    @UiField
    SimplePanel properties;
    @UiField
    SimplePanel pipelineReferences;
    @UiField
    Hyperlink viewSource;

    @Inject
    public PipelineStructureViewImpl(final Binder binder) {
        add = SvgButton.create(SvgPresets.ADD);
        add.setTitle("Add New Pipeline Element");
        remove = SvgButton.create(SvgPresets.REMOVE);
        remove.setTitle("Remove Pipeline Element");
        edit = SvgButton.create(SvgPresets.EDIT);
        edit.setTitle("Edit Pipeline Element");
        restore = SvgButton.create(SvgPresets.UNDO);
        restore.setTitle("Restore Pipeline Element");

        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setTreeView(final View view) {
        treeContainer.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInheritanceTree(final View view) {
        inherit.setWidget(view.asWidget());
    }

    @Override
    public void setProperties(final View view) {
        properties.setWidget(view.asWidget());
    }

    @Override
    public void setPipelineReferences(final View view) {
        pipelineReferences.setWidget(view.asWidget());
    }

    @Override
    public void setAddEnabled(final boolean enabled) {
        add.setEnabled(enabled);
    }

    @Override
    public void setRemoveEnabled(final boolean enabled) {
        remove.setEnabled(enabled);
    }

    @Override
    public void setEditEnabled(final boolean enabled) {
        edit.setEnabled(enabled);
    }

    @Override
    public void setRestoreEnabled(final boolean enabled) {
        restore.setEnabled(enabled);
    }

    @UiHandler("add")
    void onAddClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAdd(event);
        }
    }

    @UiHandler("remove")
    void onRemoveClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRemove(event);
        }
    }

    @UiHandler("edit")
    void onEditClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onEdit(event);
        }
    }

    @UiHandler("restore")
    void onRestoreClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRestore(event);
        }
    }

    @UiHandler("viewSource")
    void onViewSource(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().viewSource();
            }
        }
    }

    public interface Binder extends UiBinder<Widget, PipelineStructureViewImpl> {

    }
}
