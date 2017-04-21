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

package stroom.pipeline.structure.client.view;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
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
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.pipeline.structure.client.presenter.PipelineStructureUiHandlers;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.layout.client.view.ResizeSimplePanel;
import stroom.widget.tickbox.client.view.TickBox;

public class PipelineStructureViewImpl extends ViewWithUiHandlers<PipelineStructureUiHandlers>
        implements PipelineStructureView {
    public interface Binder extends UiBinder<Widget, PipelineStructureViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    GlyphButton add;
    @UiField(provided = true)
    GlyphButton remove;
    @UiField(provided = true)
    GlyphButton restore;
    @UiField
    SimplePanel inherit;
    @UiField
    ScrollPanel treeContainer;
    @UiField
    ResizeSimplePanel properties;
    @UiField
    ResizeSimplePanel pipelineReferences;
    @UiField
    TickBox advancedMode;
    @UiField
    Hyperlink viewSource;

    @Inject
    public PipelineStructureViewImpl(final Binder binder) {
        add = GlyphButton.create(GlyphIcons.ADD);
        add.setTitle("Add New Pipeline Element");
        restore = GlyphButton.create(GlyphIcons.UNDO);
        restore.setTitle("Restore Pipeline Element");
        remove = GlyphButton.create(GlyphIcons.REMOVE);
        remove.setTitle("Remove Pipeline Element");

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
    public void setRestoreEnabled(final boolean enabled) {
        restore.setEnabled(enabled);
    }

    @Override
    public void setRemoveEnabled(final boolean enabled) {
        remove.setEnabled(enabled);
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

    @UiHandler("restore")
    void onRestoreClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRestore(event);
        }
    }

    @UiHandler("advancedMode")
    void onAdvancedMode(final ValueChangeEvent<TickBoxState> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().setAdvancedMode(event.getValue().toBoolean());
        }
    }

    @UiHandler("viewSource")
    void onViewSource(final ClickEvent event) {
        if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            if (getUiHandlers() != null) {
                getUiHandlers().viewSource();
            }
        }
    }
}
