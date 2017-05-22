/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.client;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Provider;
import stroom.data.grid.client.MouseHelper;
import stroom.datasource.api.v1.DataSourceField;
import stroom.dictionary.shared.Dictionary;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.structure.client.view.Box;
import stroom.pipeline.structure.client.view.TreePanel;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.htree.client.BracketConnectorRenderer;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.LayeredCanvas;
import stroom.widget.htree.client.TreeRenderer;
import stroom.widget.htree.client.TreeRenderer2;
import stroom.widget.htree.client.treelayout.CenteredParentTreeLayout;
import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;
import stroom.widget.htree.client.treelayout.TreeLayout;
import stroom.widget.htree.client.treelayout.util.DefaultConfiguration;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import java.util.List;

public class ExpressionTreePanel extends TreePanel<Item> {
    private static final double HORIZONTAL_SEPARATION = 20;
    private static final double VERTICAL_SEPARATION = 0;
    private final LayeredCanvas canvas;
    private final FlowPanel panel;
    private final FlowPanel boxPanel;
    private final OperatorEditor operatorEditor;
    private final TermEditor termEditor;
    private TreeRenderer2<Item> renderer;
    private TreeLayout<Item> treeLayout;
    private ExpressionItemRenderer cellRenderer;
    private DefaultTreeForTreeLayout<Item> tree;

    public ExpressionTreePanel(final Provider<EntityDropDownPresenter> dictionaryProvider) {
        final EntityDropDownPresenter dictionaryPresenter = dictionaryProvider.get();
        dictionaryPresenter.setIncludedTypes(Dictionary.ENTITY_TYPE);
        dictionaryPresenter.setRequiredPermissions(DocumentPermissionNames.USE);
        dictionaryPresenter.getWidget().getElement().getStyle().setMargin(0, Unit.PX);
        operatorEditor = new OperatorEditor();
        termEditor = new TermEditor(dictionaryPresenter);

        panel = new FlowPanel();
        setAbsoluteLeftTop(panel.getElement());
        boxPanel = new FlowPanel();
        setAbsoluteLeftTop(boxPanel.getElement());

        canvas = LayeredCanvas.createIfSupported();
        if (canvas != null) {
            final Context2d arrowContext = canvas.getLayer(TreeRenderer.ARROW_LAYER).getContext2d();

            cellRenderer = new ExpressionItemRenderer(boxPanel, operatorEditor, termEditor);
            final ConnectorRenderer<Item> connectorRenderer = new BracketConnectorRenderer<>(arrowContext);

            // setup the tree layout configuration
            final DefaultConfiguration<Item> layoutConfig = new DefaultConfiguration<>(HORIZONTAL_SEPARATION,
                    VERTICAL_SEPARATION, Location.Left, AlignmentInLevel.TowardsRoot);
            final NodeExtentProvider<Item> extentProvider = cellRenderer;

            treeLayout = new CenteredParentTreeLayout<>(extentProvider, layoutConfig);

            renderer = new TreeRenderer2<>(canvas, cellRenderer, connectorRenderer);
            renderer.setTreeLayout(treeLayout);

            setAbsoluteLeftTop(canvas.getElement());
            panel.add(canvas);
        }

        panel.add(boxPanel);

        initWidget(panel);
    }

    private void setAbsoluteLeftTop(final Element element) {
        final Style style = element.getStyle();
        style.setPosition(Position.ABSOLUTE);
        style.setLeft(0, Unit.PX);
        style.setTop(0, Unit.PX);
    }

    @Override
    public Box<Item> getBox(final Item item) {
        if (renderer != null) {
            for (final ExpressionItemBox box : cellRenderer.getBoxes()) {
                if (box.getItem() == item) {
                    return box;
                }
            }
        }

        return null;
    }

    @Override
    public Box<Item> getTargetBox(final Event event, final boolean usePosition) {
        if (renderer != null) {
            final Element target = event.getEventTarget().cast();
            for (final ExpressionItemBox box : cellRenderer.getBoxes()) {
                if (usePosition) {
                    if (MouseHelper.mouseIsOverElement(event, box.getElement())) {
                        return box;
                    }
                } else {
                    if (box.getElement().isOrHasChild(target)) {
                        return box;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void setSelectionModel(final SelectionModel<Item> selectionModel) {
        if (renderer != null) {
            cellRenderer.setSelectionModel(selectionModel);
        }
    }

    @Override
    public void refresh() {
        refresh(null);
    }

    @Override
    public void refresh(final RefreshCallback callback) {
        boxPanel.clear();
        if (renderer != null) {
            cellRenderer.clear();
            renderer.draw();
        }
    }

    @Override
    public DefaultTreeForTreeLayout<Item> getTree() {
        return tree;
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        this.tree = tree;
        if (treeLayout != null) {
            treeLayout.setTree(tree);
        }
    }

    public void setFields(final List<DataSourceField> fields) {
        termEditor.setFields(fields);
    }

    public void endEditing() {
        if (operatorEditor != null) {
            operatorEditor.endEdit();
        }
        if (termEditor != null) {
            termEditor.endEdit();
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        if (operatorEditor != null) {
            operatorEditor.setUiHandlers(uiHandlers);
        }
        if (termEditor != null) {
            termEditor.setUiHandlers(uiHandlers);
        }
    }
}