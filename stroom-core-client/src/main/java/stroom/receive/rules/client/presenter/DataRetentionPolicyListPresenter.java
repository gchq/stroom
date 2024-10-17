/*
 * Copyright 2017-2024 Crown Copyright
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

package stroom.receive.rules.client.presenter;

import stroom.cell.info.client.ActionCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.svg.client.Preset;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DataRetentionPolicyListPresenter extends MyPresenterWidget<PagerView> implements Focus {

    private final MyDataGrid<DataRetentionRule> dataGrid;
    private final MultiSelectionModelImpl<DataRetentionRule> selectionModel;
    private BiConsumer<DataRetentionRule, Boolean> enabledStateHandler;
    private Function<DataRetentionRule, List<Item>> actionMenuItemProvider;

    @Inject
    public DataRetentionPolicyListPresenter(final EventBus eventBus,
                                            final PagerView view) {
        super(eventBus, view);

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        initTableColumns();
    }

    @Override
    public void focus() {
        dataGrid.setFocus(true);
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {

        addTickBoxColumn("Enabled", 50, DataRetentionRule::isEnabled);
        addColumn("Rule", 40, row -> String.valueOf(row.getRuleNumber()));
        addColumn("Name", 200, DataRetentionRule::getName);
        addColumn("Retention", 90, DataRetentionRule::getAgeString);
        addColumn("Expression", 500, row -> row.getExpression().toString());
        addActionButtonColumn("", 20);
        dataGrid.addEndColumn(new EndColumn<>());
    }

    private void addColumn(final String name,
                           final int width,
                           final Function<DataRetentionRule, String> valueFunc) {

        final Column<DataRetentionRule, SafeHtml> expressionColumn = new Column<DataRetentionRule, SafeHtml>(
                new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final DataRetentionRule rule) {
                return getSafeHtml(valueFunc.apply(rule), rule);
            }
        };
        dataGrid.addResizableColumn(expressionColumn, name, width);
    }

    private void addActionButtonColumn(final String name,
                                       final int width) {

        final ActionCell<DataRetentionRule> actionCell = new stroom.cell.info.client.ActionCell<>(this::showActionMenu);

        final Column<DataRetentionRule, DataRetentionRule> expressionColumn =
                new Column<DataRetentionRule, DataRetentionRule>(actionCell) {

                    @Override
                    public DataRetentionRule getValue(final DataRetentionRule row) {
                        return row;
                    }

                    @Override
                    public void onBrowserEvent(final Context context,
                                               final Element elem,
                                               final DataRetentionRule rule,
                                               final NativeEvent event) {
                        super.onBrowserEvent(context, elem, rule, event);
//                        GWT.log("Rule " + rule.getRuleNumber() + " clicked, event " + event.getType());
                    }
                };
        dataGrid.addResizableColumn(expressionColumn, name, width);
    }

    private void showActionMenu(final DataRetentionRule row, final NativeEvent event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX() + 10, event.getClientY());
        final List<Item> items = actionMenuItemProvider.apply(row);
        ShowMenuEvent
                .builder()
                .items(items)
                .popupPosition(popupPosition)
                .fire(this);
    }

    private void addTickBoxColumn(final String name,
                                  final int width,
                                  final Function<DataRetentionRule, Boolean> valueFunc) {

        final Column<DataRetentionRule, TickBoxState> enabledColumn = new Column<DataRetentionRule, TickBoxState>(
                TickBoxCell.create(false, false)) {

            @Override
            public TickBoxState getValue(final DataRetentionRule rule) {
                if (rule != null && !isDefaultRule(rule)) {
                    return TickBoxState.fromBoolean(valueFunc.apply(rule));
                }
                return null;
            }
        };

        enabledColumn.setFieldUpdater((index, rule, value) -> {
            if (enabledStateHandler != null && !isDefaultRule(rule)) {
                enabledStateHandler.accept(rule, value.toBoolean());
            }
        });
        dataGrid.addColumn(enabledColumn, name, width);
    }

    private boolean isDefaultRule(final DataRetentionRule rule) {
        return Objects.equals(
                DataRetentionPolicyPresenter.DEFAULT_UI_ONLY_RETAIN_ALL_RULE.getName(),
                rule.getName());
    }

    private SafeHtml getSafeHtml(final String string, final DataRetentionRule rule) {
        if (isDefaultRule(rule)) {
            return HtmlBuilder
                    .builder()
                    .span(hb ->
                            hb.append(string), Attribute.className("dataRetention--defaultRule"))
                    .toSafeHtml();
        } else if (!rule.isEnabled()) {
            return HtmlBuilder
                    .builder()
                    .span(hb ->
                            hb.append(string), Attribute.className("dataRetention--disabledRule"))
                    .toSafeHtml();
        } else {
            return SafeHtmlUtils.fromString(string);
        }
    }

    public void setData(final List<DataRetentionRule> data) {
        dataGrid.setRowData(0, data);
        dataGrid.setRowCount(data.size());

    }

    public MultiSelectionModel<DataRetentionRule> getSelectionModel() {
        return selectionModel;
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public void setEnabledStateHandler(final BiConsumer<DataRetentionRule, Boolean> enabledStateHandler) {
        this.enabledStateHandler = enabledStateHandler;
    }

    public void setActionMenuItemProvider(final Function<DataRetentionRule, List<Item>> actionMenuItemProvider) {
        this.actionMenuItemProvider = actionMenuItemProvider;
    }
}
