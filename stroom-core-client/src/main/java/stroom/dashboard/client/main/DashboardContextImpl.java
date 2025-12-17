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

package stroom.dashboard.client.main;

import stroom.dashboard.client.main.DashboardContextChangeEvent.Handler;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.CopyTextUtil;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Param;
import stroom.query.api.ParamUtil;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DashboardContextImpl implements HasHandlers, DashboardContext {

    private final EventBus eventBus;
    private final Components components;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private List<ComponentState> parentComponentStates = new ArrayList<>();
    private List<Param> linkParams = Collections.emptyList();
    private DocRef dashboardDocRef;

    public DashboardContextImpl(final EventBus eventBus,
                                final Components components,
                                final QueryToolbarPresenter queryToolbarPresenter) {
        this.eventBus = eventBus;
        this.components = components;
        this.queryToolbarPresenter = queryToolbarPresenter;
    }

    public void setParent(final DashboardContext parent) {
        this.parentComponentStates = getComponentStates("parent.", "Parent ", parent);
    }

    public void setLinkParams(final List<Param> linkParams) {
        this.linkParams = linkParams;
    }

    @Override
    public TimeRange getRawTimeRange() {
        return queryToolbarPresenter.getTimeRange();
    }

    @Override
    public TimeRange getResolvedTimeRange() {
        final TimeRange rawTimeRange = getRawTimeRange();
        return new TimeRange(rawTimeRange.getName(),
                ParamUtil.replaceParameters(rawTimeRange.getFrom(), this),
                ParamUtil.replaceParameters(rawTimeRange.getTo(), this));
    }

    @Override
    public Components getComponents() {
        return components;
    }

    @Override
    public DocRef getDashboardDocRef() {
        return dashboardDocRef;
    }

    public void setDashboardDocRef(final DocRef dashboardDocRef) {
        this.dashboardDocRef = dashboardDocRef;
    }

    @Deprecated
    @Override
    public List<Param> getParams() {
        final List<Param> combinedParams = new ArrayList<>();
        for (final Component component : components) {
            if (component instanceof HasParams) {
                final List<Param> params = ((HasParams) component).getParams();
                combinedParams.addAll(params);
            }
        }
        combinedParams.addAll(linkParams);
        return combinedParams;
    }

    public SafeHtml toSafeHtml(final boolean showInsert) {
        final TableBuilder tb = new TableBuilder();
        final List<ComponentState> componentStates = getComponentStates();
        printContext(tb, componentStates, showInsert);
        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private List<ComponentState> getComponentStates() {
        return getComponentStates("", "", this);
    }

    private List<ComponentState> getComponentStates(final String keyPrefix,
                                                    final String namePrefix,
                                                    final DashboardContext dashboardContext) {
        final List<ComponentState> list = new ArrayList<>();
        if (dashboardContext != null) {
            final TimeRange timeRange = dashboardContext.getRawTimeRange();
            final List<Param> timeRangeParams = List.of(
                    new Param(keyPrefix + "timeRange.from", timeRange.getFrom()),
                    new Param(keyPrefix + "timeRange.to", timeRange.getTo()));
            list.add(new ComponentState(
                    "timeRange",
                    namePrefix + "Time Range",
                    timeRangeParams,
                    Collections.emptyList(),
                    Collections.emptyList()));

            if (linkParams != null) {
                final List<Param> linkParams = this.linkParams
                        .stream()
                        .map(param -> new Param(
                                keyPrefix + "link.param." + param.getKey(),
                                param.getValue()))
                        .collect(Collectors.toList());

                list.add(new ComponentState(
                        "link",
                        namePrefix + "Link Parameters",
                        linkParams,
                        Collections.emptyList(),
                        Collections.emptyList()));
            }

            final Components components = dashboardContext.getComponents();
            final Collection<Component> componentList = components
                    .getComponents()
                    .stream()
                    .sorted(Comparator.comparing(Component::getDisplayValue))
                    .collect(Collectors.toList());
            for (final Component component : componentList) {
                List<Param> paramList = new ArrayList<>();
                final List<List<Param>> selectionList = new ArrayList<>();
                final List<ColumnSelectionFilter> filterList = new ArrayList<>();
                if (component instanceof final HasParams hasParams) {
                    final String start = keyPrefix + "component." + component.getId() + ".param.";
                    final List<Param> componentParams = hasParams.getParams();
                    paramList = componentParams
                            .stream()
                            .map(param -> new Param(start + param.getKey(),
                                    param.getValue()))
                            .collect(Collectors.toList());
                }

                if (component instanceof final HasComponentSelection hasComponentSelection) {
                    final List<ComponentSelection> selections = hasComponentSelection.getSelection();
                    for (final ComponentSelection selection : selections) {
                        final String start = keyPrefix + "component." + component.getId() + ".selection.";
                        final List<Param> selectionParams = selection.getParams()
                                .stream()
                                .map(param -> new Param(start + param.getKey(), param.getValue()))
                                .collect(Collectors.toList());
                        selectionList.add(selectionParams);
                    }
                }

                if (component instanceof final TablePresenter tablePresenter) {
                    final List<Column> columns = tablePresenter.getTableComponentSettings().getColumns();
                    if (NullSafe.hasItems(columns)) {
                        for (final Column column : columns) {
                            final ColumnValueSelection columnValueSelection = column.getColumnValueSelection();
                            if (columnValueSelection != null) {
                                filterList.add(new ColumnSelectionFilter(
                                        keyPrefix +
                                        "component." +
                                        column.getId() +
                                        ".values",
                                        namePrefix +
                                        component.getDisplayValue() +
                                        " " +
                                        column.getName(),
                                        columnValueSelection));
                            }
                        }
                    }
                }

                list.add(new ComponentState(
                        component.getId(),
                        namePrefix + component.getDisplayValue(),
                        paramList,
                        selectionList,
                        filterList));
            }

            if (dashboardContext instanceof final DashboardContextImpl ctx) {
                list.addAll(ctx.parentComponentStates);
            }
        }
        return list;
    }

    private void printContext(final TableBuilder tb,
                              final List<ComponentState> componentStates,
                              final boolean showInsert) {
        if (componentStates != null) {
            for (final ComponentState componentState : componentStates) {
                tb.row(TableCell.header(componentState.name));
                for (final Param param : componentState.params) {
                    final String key = ParamUtil.create(param.getKey());
                    tb.row(TableCell.data(CopyTextUtil.render(key, showInsert)),
                            TableCell.data(CopyTextUtil.render(param.getValue(), showInsert)));
                }

                if (NullSafe.hasItems(componentState.selectionList)) {
                    boolean first = true;
                    for (final List<Param> selection : componentState.selectionList) {
                        if (!first) {
                            tb.row(TableCell.data("------"));
                        }
                        for (final Param param : selection) {
                            final String key = ParamUtil.create(param.getKey());

                            tb.row(TableCell.data(CopyTextUtil.render(key, showInsert)),
                                    TableCell.data(CopyTextUtil.render(param.getValue(), showInsert)));
                        }
                        first = false;
                    }
                }

                if (NullSafe.hasItems(componentState.filterList)) {
                    for (final ColumnSelectionFilter filter : componentState.filterList) {
                        if (filter.selection.isEnabled()) {
                            tb.row(TableCell.header(filter.name));
                            String value = filter
                                    .selection
                                    .getValues()
                                    .stream()
                                    .collect(Collectors.joining(","));
                            if (filter.getSelection().isInvert()) {
                                value = "not in [" + value + "]";
                            } else {
                                value = "in [" + value + "]";
                            }

                            final String key = ParamUtil.create(filter.id);
                            tb.row(TableCell.data(CopyTextUtil.render(key, showInsert)),
                                    TableCell.data(CopyTextUtil.render(value, showInsert)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public Optional<ExpressionOperator> createSelectionHandlerExpression(
            final List<ComponentSelectionHandler> selectionHandlers) {
        if (NullSafe.isEmptyCollection(selectionHandlers)) {
            return Optional.empty();
        }

        final List<ComponentState> componentStates = getComponentStates();
        final List<ExpressionOperator> list = new ArrayList<>();
        for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
            if (selectionHandler.isEnabled()) {
                replaceComponentSelection(componentStates,
                        selectionHandler.getExpression(), false).map(list::add);
            }
        }

        if (list.isEmpty()) {
            return Optional.empty();
        } else if (list.size() == 1) {
            return Optional.of(list.get(0));
        } else {
            return Optional.of(ExpressionOperator.builder().op(Op.OR).addOperators(list).build());
        }
    }

    @Override
    public ExpressionOperator replaceExpression(final ExpressionOperator operator,
                                                final boolean keepUnmatched) {
        return replaceComponentSelection(getComponentStates(), operator, keepUnmatched).orElseGet(() ->
                ExpressionOperator.builder().build());
    }

    private Optional<ExpressionOperator> replaceComponentSelection(final List<ComponentState> componentStates,
                                                                   final ExpressionOperator operator,
                                                                   final boolean keepUnmatched) {
        if (!operator.enabled() || operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return Optional.empty();
        }

        final List<ExpressionItem> children = new ArrayList<>(operator.getChildren().size());
        // Gather children.
        if (NullSafe.hasItems(operator.getChildren())) {
            final Map<String, List<ExpressionTerm>> selectionTermMap = new HashMap<>();
            for (final ExpressionItem item : operator.getChildren()) {
                if (item instanceof final ExpressionTerm term) {
                    if (NullSafe.isBlankString(term.getValue())) {
                        children.add(term);
                    } else {
                        final List<String> keys = ParamUtil.getKeys(term.getValue());
                        boolean found = false;
                        String valueFilter = null;
                        for (final String key : keys) {
                            if (key.contains("component.")) {
                                int index = key.lastIndexOf(".selection.");
                                if (index != -1) {
                                    final String prefix = key.substring(0, index + ".selection.".length());
                                    selectionTermMap.computeIfAbsent(prefix, k -> new ArrayList<>()).add(term);
                                    found = true;
                                    break;
                                } else {
                                    index = key.lastIndexOf(".values");
                                    if (index != -1) {
                                        valueFilter = key;
                                    }
                                }
                            }
                        }
                        if (!found) {
                            if (valueFilter != null) {
                                expandFilterTerm(term, valueFilter, componentStates).map(children::add);

                            } else {
                                final String value = term.getValue();
                                final String resolved = ParamUtil.replaceParameters(term.getValue(),
                                        v -> getParamValue(v, componentStates), keepUnmatched);
                                if (NullSafe.isNonBlankString(resolved)) {
                                    if (Objects.equals(value, resolved)) {
                                        children.add(term);
                                    } else {
                                        children.add(ExpressionTerm.builder()
                                                .enabled(term.enabled())
                                                .field(term.getField())
                                                .condition(term.getCondition())
                                                .value(resolved)
                                                .docRef(term.getDocRef())
                                                .build());
                                    }
                                }
                            }
                        }
                    }
                } else if (item instanceof final ExpressionOperator op) {
                    replaceComponentSelection(componentStates, op, keepUnmatched).map(children::add);
                }
            }

            if (NullSafe.hasEntries(selectionTermMap)) {
                final List<ExpressionItem> items = new ArrayList<>();
                for (final Entry<String, List<ExpressionTerm>> entry : selectionTermMap.entrySet()) {
                    expandSelectionTerms(
                            entry.getKey(),
                            entry.getValue(),
                            componentStates)
                            .map(items::add);
                }

                if (items.size() > 1) {
                    children.add(ExpressionOperator.builder().op(Op.AND).add(items).build());
                } else if (items.size() == 1) {
                    children.addAll(items);
                }
            }
        }

        if (children.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp())
                .add(children)
                .build());
    }

    private Optional<ExpressionItem> expandSelectionTerms(final String prefix,
                                                          final List<ExpressionTerm> terms,
                                                          final List<ComponentState> componentStates) {
        final List<ExpressionItem> outerItems = new ArrayList<>();
        for (final ComponentState componentState : componentStates) {
            final boolean wildcard = prefix.contains(".?.");

            if (prefix.contains("." + componentState.id + ".") || wildcard) {
                for (final List<Param> params : componentState.selectionList) {
                    final Map<String, String> paramMap = ParamUtil.createParamMap(params);
                    final List<ExpressionItem> innerItems = new ArrayList<>(terms.size());
                    for (final ExpressionTerm term : terms) {
                        String value = term.getValue();
                        if (wildcard) {
                            value = value.replaceAll("\\.?\\.", componentState.id);
                        }
                        value = ParamUtil.replaceParameters(value, paramMap::get);
                        innerItems.add(term.copy().value(value).build());
                    }

                    if (innerItems.size() > 1) {
                        outerItems.add(ExpressionOperator.builder().op(Op.AND).add(innerItems).build());
                    } else if (innerItems.size() == 1) {
                        outerItems.addAll(innerItems);
                    }
                }
            }
        }

        if (outerItems.size() > 1) {
            return Optional.of(ExpressionOperator.builder().op(Op.OR).add(outerItems).build());
        } else if (outerItems.size() == 1) {
            return Optional.of(outerItems.get(0));
        }

        return Optional.empty();
    }

    private Optional<ExpressionItem> expandFilterTerm(final ExpressionTerm term,
                                                      final String key,
                                                      final List<ComponentState> componentStates) {
        // Find filter.
        ColumnSelectionFilter found = null;
        for (final ComponentState componentState : componentStates) {
            for (final ColumnSelectionFilter filter : componentState.filterList) {
                if (Objects.equals(filter.id, key)) {
                    found = filter;
                    break;
                }
            }
            if (found != null) {
                break;
            }
        }

        if (found == null) {
            return Optional.empty();
        }

        final Set<String> values = found.selection.getValues();
        final List<ExpressionItem> children = new ArrayList<>();
        for (final String value : values) {
            if (ParamUtil.containsWhitespace(value)) {
                children.add(term.copy().value("'" + value + "'").build());
            } else {
                children.add(term.copy().value(value).build());
            }
        }

        final ExpressionOperator child = ExpressionOperator.builder().op(Op.OR).children(children).build();
        if (found.getSelection().isInvert()) {
            return Optional.of(ExpressionOperator.builder().op(Op.NOT).addOperator(child).build());
        }
        return Optional.of(child);
    }

    @Override
    public String getParamValue(final String key) {
        return getParamValue(key, getComponentStates());
    }

    private String getParamValue(final String key, final List<ComponentState> componentStates) {
        for (final ComponentState componentState : componentStates) {
            final Map<String, String> paramMap = ParamUtil.createParamMap(componentState.params);
            final String value = paramMap.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEventFromSource(event, this);
    }

    @Override
    public HandlerRegistration addComponentChangeHandler(final ComponentChangeEvent.Handler handler) {
        return eventBus.addHandlerToSource(ComponentChangeEvent.getType(), this, handler);
    }

    @Override
    public HandlerRegistration addContextChangeHandler(final Handler handler) {
        return eventBus.addHandlerToSource(DashboardContextChangeEvent.getType(), this, handler);
    }

    @Override
    public void fireComponentChangeEvent(final Component component) {
        ComponentChangeEvent.fire(this, component);
        fireContextChangeEvent();
    }

    @Override
    public void fireContextChangeEvent() {
        DashboardContextChangeEvent.fire(this, this);
    }

    private static class ComponentState {

        private final String id;
        private final String name;
        private final List<Param> params;
        private final List<List<Param>> selectionList;
        private final List<ColumnSelectionFilter> filterList;

        public ComponentState(final String id,
                              final String name,
                              final List<Param> params,
                              final List<List<Param>> selectionList,
                              final List<ColumnSelectionFilter> filterList) {
            this.id = id;
            this.name = name;
            this.params = params;
            this.selectionList = selectionList;
            this.filterList = filterList;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Param> getParams() {
            return params;
        }

        public List<List<Param>> getSelectionList() {
            return selectionList;
        }

        public List<ColumnSelectionFilter> getFilterList() {
            return filterList;
        }
    }

    private static class ColumnSelectionFilter {

        private final String id;
        private final String name;
        private final ColumnValueSelection selection;

        public ColumnSelectionFilter(final String id,
                                     final String name,
                                     final ColumnValueSelection selection) {
            this.id = id;
            this.name = name;
            this.selection = selection;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public ColumnValueSelection getSelection() {
            return selection;
        }
    }
}
