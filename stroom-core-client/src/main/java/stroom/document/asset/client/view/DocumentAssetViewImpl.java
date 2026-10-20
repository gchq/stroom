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

package stroom.document.asset.client.view;

import stroom.document.asset.client.presenter.DocumentAssetPresenter.DocumentAssetView;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DocumentAssetViewImpl extends ViewImpl
        implements DocumentAssetView, ReadOnlyChangeHandler {

    private final Widget widget;
    // Shown in the editor's place while no file is selected, so the area says what it is for
    private final Label editorPlaceholder = new Label("Select a file to view or edit it");
    private Widget editor;

    @UiField
    FlowPanel rootPanel;

    @UiField
    SplitPane splitPane;

    @Inject
    public DocumentAssetViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {

    }

    @Override
    public void setTreeAndEditor(final Tree tree, final EditorPresenter editorPresenter) {
        editor = editorPresenter.getWidget();
        editorPlaceholder.setStyleName("visualisationAssets-placeholder");
        final FlowPanel editorArea = new FlowPanel();
        editorArea.setStyleName("max visualisationAssets-editorArea");
        editorArea.add(editorPlaceholder);
        editorArea.add(editor);
        splitPane.add(tree);
        splitPane.add(editorArea);
    }

    @Override
    public void setEditorVisible(final boolean visible) {
        editor.setVisible(visible);
        editorPlaceholder.setVisible(!visible);
    }

    // --------------------------------------------------------------------------------
    public interface Binder extends UiBinder<Widget, DocumentAssetViewImpl> {

    }
}
