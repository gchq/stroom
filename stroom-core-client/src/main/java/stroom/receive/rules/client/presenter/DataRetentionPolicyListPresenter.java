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

package stroom.receive.rules.client.presenter;

import stroom.cell.info.client.SvgCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonView;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DataRetentionPolicyListPresenter extends MyPresenterWidget<DataGridView<DataRetentionRule>> {

    private BiConsumer<DataRetentionRule, Boolean> enabledStateHandler;
    private Consumer<Integer> ruleNumberConsumer;

    @Inject
    public DataRetentionPolicyListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<>(true, false));

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        initTableColumns();
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
        addButtonColumn("", 20, SvgPresets.INSERT_ABOVE.title("Add new rule above this one"));
        getView().addEndColumn(new EndColumn<>());
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
        getView().addResizableColumn(expressionColumn, name, width);
    }

    private void addButtonColumn(final String name,
                                 final int width,
                                 final Preset svgPreset) {
        final Column<DataRetentionRule, Preset> expressionColumn = new Column<DataRetentionRule, Preset>(
                new SvgCell(true)) {

            @Override
            public Preset getValue(final DataRetentionRule object) {
                return svgPreset;
            }

            @Override
            public void onBrowserEvent(final Context context,
                                       final Element elem,
                                       final DataRetentionRule rule,
                                       final NativeEvent event) {
                super.onBrowserEvent(context, elem, rule, event);

                GWT.log("Rule " + rule.getRuleNumber() + " clicked");
                if (ruleNumberConsumer != null) {
                    // We want a rule above this one, so -1
                    ruleNumberConsumer.accept(rule.getRuleNumber() - 1);
                }
            }
        };
        getView().addResizableColumn(expressionColumn, name, width);
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
        getView().addColumn(enabledColumn, name, width);
    }

    private boolean isDefaultRule(final DataRetentionRule rule) {
        return Objects.equals(
                DataRetentionPolicyPresenter.DEFAULT_UI_ONLY_RETAIN_ALL_RULE.getName(),
                rule.getName());
    }

    private SafeHtml getSafeHtml(final String string, final DataRetentionRule rule) {
        if (isDefaultRule(rule)) {
            return TooltipUtil.styledSpan(string, builder ->
                    builder.trustedColor("#484848"));
        } else if (!rule.isEnabled()) {
            return TooltipUtil.styledSpan(string, builder ->
                    builder.trustedColor("#A9A9A9"));
        } else {
            return SafeHtmlUtils.fromString(string);
        }
    }

    public void setData(final List<DataRetentionRule> data) {
        getView().setRowData(0, data);
        getView().setRowCount(data.size());
    }

    public MultiSelectionModel<DataRetentionRule> getSelectionModel() {
        return getView().getSelectionModel();
    }

    public ButtonView add(final Preset preset) {
        return getView().addButton(preset);
    }

    public void setEnabledStateHandler(final BiConsumer<DataRetentionRule, Boolean> enabledStateHandler) {
        this.enabledStateHandler = enabledStateHandler;
    }

    public void setAddRuleAboveHandler(final Consumer<Integer> ruleNumberConsumer) {
        this.ruleNumberConsumer = ruleNumberConsumer;
    }
}
