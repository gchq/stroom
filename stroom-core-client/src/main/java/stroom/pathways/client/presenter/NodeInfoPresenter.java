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

package stroom.pathways.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.PathNode;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.client.DataGridUtil;
import stroom.util.client.NumberUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * What is known about the node picked out on the drawing, beside the constraints it holds.
 *
 * <p>A table of one row per thing rather than a form, because what is worth saying about a node is a
 * short list of unrelated facts and a table of them can be read, sorted and copied out like any other.
 */
public class NodeInfoPresenter extends MyPresenterWidget<PagerView> {

    private static final int NAME_COL = 160;

    private final MyDataGrid<Fact> dataGrid;
    private final DateTimeFormatter dateTimeFormatter;
    private final MultiSelectionModelImpl<Fact> selectionModel;

    @Inject
    public NodeInfoPresenter(final EventBus eventBus,
                             final PagerView view,
                             final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("Node Info");
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        addColumn("Name", NAME_COL, Fact::name);
        addColumn("Value", ColumnSizeConstants.BIG_COL, Fact::value);
    }

    /**
     * @param node   the node picked out, or null where none is.
     * @param change how often the model has been taught something about this node and when it last
     *               was, which the node does not hold — the changes say it, and holding it twice
     *               would let the two differ.
     */
    public void setData(final PathNode node, final NodeChange change) {
        if (node == null) {
            dataGrid.setRowData(0, Collections.emptyList());
            dataGrid.setRowCount(0, true);
            return;
        }

        final List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("Node Name", node.getName()));
        facts.add(new Fact("Path", String.join(" / ", NullSafe.list(node.getNodePath()))));
        facts.add(new Fact("Depth", NumberUtil.formatInt(NullSafe.list(node.getNodePath()).size())));

        // How much work has gone through it, which is what the drawing sizes and colours it by.
        facts.add(new Fact("Times Used", NumberUtil.formatLong(node.getTimesUsed())));
        facts.add(new Fact("Last Used", when(node.getLastUsedTime())));

        // How often the model has had to change its mind about it. A node that is still being taught
        // something is one whose behaviour has not settled.
        facts.add(new Fact("Times Updated", NumberUtil.formatLong(change == null
                ? 0L
                : change.getCount())));
        facts.add(new Fact("Last Updated", change == null
                ? ""
                : when(change.getLastUpdated())));

        facts.add(new Fact("Constraints", NumberUtil.formatInt(NullSafe.map(node.getConstraints()).size())));
        facts.add(new Fact("Children", NumberUtil.formatInt(NullSafe.list(node.getChildren()).size())));

        dataGrid.setRowData(0, facts);
        dataGrid.setRowCount(facts.size(), true);
    }

    public MultiSelectionModelImpl<Fact> getSelectionModel() {
        return selectionModel;
    }

    private String when(final NanoTime time) {
        return NullSafe.get(time, value -> dateTimeFormatter.format(value.toEpochMillis()));
    }

    private void addColumn(final String name, final int width, final Function<Fact, String> value) {
        final Column<Fact, String> column = DataGridUtil.textColumnBuilder(value).build();
        dataGrid.addResizableColumn(column, name, width);
    }


    // --------------------------------------------------------------------------------


    /** One thing worth saying about a node. */
    public static class Fact {

        private final String name;
        private final String value;

        Fact(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        public String name() {
            return name;
        }

        public String value() {
            return value;
        }
    }
}
