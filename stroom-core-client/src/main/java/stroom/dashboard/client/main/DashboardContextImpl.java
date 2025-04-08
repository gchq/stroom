package stroom.dashboard.client.main;

import stroom.dashboard.client.main.DashboardContextChangeEvent.Handler;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.CopyTextUtil;
import stroom.docref.DocRef;
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
import java.util.stream.Collectors;

public class DashboardContextImpl implements HasHandlers, DashboardContext {

    private final EventBus eventBus;
    private final Components components;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private List<ComponentParams> parentParams = new ArrayList<>();
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
        this.parentParams = getComponentParams("parent.", "Parent ", parent);
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

    public SafeHtml toSafeHtml() {
        final TableBuilder tb = new TableBuilder();
        List<ComponentParams> list = getComponentParams();
        printContext(tb, list);
        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private List<ComponentParams> getComponentParams() {
        return getComponentParams("", "", this);
    }

    private List<ComponentParams> getComponentParams(final String keyPrefix,
                                                     final String namePrefix,
                                                     final DashboardContext dashboardContext) {
        final List<ComponentParams> list = new ArrayList<>();
        if (dashboardContext != null) {
            final TimeRange timeRange = dashboardContext.getRawTimeRange();
            final List<Param> timeRangeParams = List.of(
                    new Param(keyPrefix + "timeRange.from", timeRange.getFrom()),
                    new Param(keyPrefix + "timeRange.to", timeRange.getTo()));
            list.add(new ComponentParams(
                    "timeRange",
                    namePrefix + "Time Range",
                    timeRangeParams,
                    Collections.emptyList()));

            if (linkParams != null) {
                final List<Param> linkParams = this.linkParams
                        .stream()
                        .map(param -> new Param(
                                keyPrefix + "link.param." + param.getKey(),
                                param.getValue()))
                        .collect(Collectors.toList());

                list.add(new ComponentParams(
                        "link",
                        namePrefix + "Link Parameters",
                        linkParams,
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
                List<List<Param>> selectionList = new ArrayList<>();
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

                list.add(new ComponentParams(
                        component.getId(),
                        namePrefix + component.getDisplayValue(),
                        paramList,
                        selectionList));
            }

            if (dashboardContext instanceof final DashboardContextImpl ctx) {
                list.addAll(ctx.parentParams);
            }
        }
        return list;
    }

    private void printContext(final TableBuilder tb,
                              final List<ComponentParams> list) {
        if (list != null) {
            for (final ComponentParams componentParams : list) {
                tb.row(TableCell.header(componentParams.name));
                for (final Param param : componentParams.params) {
                    final String key = ParamUtil.create(param.getKey());
                    tb.row(TableCell.data(CopyTextUtil.render(key)), TableCell.data(param.getValue()));
                }

                if (NullSafe.hasItems(componentParams.selections)) {
                    boolean first = true;
                    for (final List<Param> selection : componentParams.selections) {
                        if (!first) {
                            tb.row(TableCell.data("------"));
                        }
                        for (final Param param : selection) {
                            final String key = ParamUtil.create(param.getKey());
                            tb.row(TableCell.data(CopyTextUtil.render(key)), TableCell.data(param.getValue()));
                        }
                        first = false;
                    }
                }
            }
        }
    }

//    private void printContext(final TableBuilder tb,
//                              final String prefix,
//                              final DashboardContext dashboardContext) {
//        if (dashboardContext != null) {
//            final TimeRange timeRange = dashboardContext.getRawTimeRange();
//            tb.row(TableCell.header("Time Range"));
//            final List<Param> timeRangeParams = List.of(
//                    new Param("from", timeRange.getFrom()),
//                    new Param("to", timeRange.getTo()));
//            printParams(tb, appendPrefix(prefix, "timeRange"), timeRangeParams);
//
//            final List<Param> linkParams = this.linkParams;
//            if (linkParams != null) {
//                tb.row(TableCell.header("Link Parameters"));
//                printParams(tb, appendPrefix(prefix, "link.param"), linkParams);
//            }
//
//            final Components components = dashboardContext.getComponents();
//            final Collection<Component> componentList = components
//                    .getComponents()
//                    .stream()
//                    .sorted(Comparator.comparing(Component::getDisplayValue))
//                    .collect(Collectors.toList());
//            for (final Component component : componentList) {
//                tb.row(TableCell.header(component.getDisplayValue()));
//                if (component instanceof final HasParams hasParams) {
//                    final List<Param> componentParams = hasParams.getParams();
//                    printParams(tb,
//                            appendPrefix(prefix, "component." + component.getId() + ".param"), componentParams);
//                }
//
//                if (component instanceof final HasComponentSelection hasComponentSelection) {
//                    final List<ComponentSelection> selections = hasComponentSelection.getSelection();
//                    boolean first = true;
//                    for (final ComponentSelection selection : selections) {
//                        if (!first) {
//                            tb.row(TableCell.data("------"));
//                        }
//                        final List<Param> selectionParams = selection.getParams();
//                        final String start = appendPrefix(prefix, "component." + component.getId() + ".selection");
//                        printParams(tb, start, selectionParams);
//                        first = false;
//                    }
//                }
//            }
//        }
//    }
//
//    private void printParams(final TableBuilder tb,
//                             final String prefix,
//                             final List<Param> params) {
//        for (final Param param : params) {
//            final String key = ParamUtil.create(appendPrefix(prefix, param.getKey()));
//            tb.row(TableCell.data(CopyTextUtil.render(key)), TableCell.data(param.getValue()));
//        }
//    }

//    private String appendPrefix(final String prefix, final String key) {
//        if (NullSafe.isBlankString(prefix)) {
//            return key;
//        }
//        return prefix + "." + key;
//    }

//    private Map<String, String> createParamMap(final String prefix,
//                                               final DashboardContext dashboardContext) {
//        final Map<String, String> paramMap = new HashMap<>();
//        if (dashboardContext != null) {
//            final TimeRange timeRange = dashboardContext.getRawTimeRange();
//            final List<Param> timeRangeParams = List.of(
//                    new Param("from", timeRange.getFrom()),
//                    new Param("to", timeRange.getTo()));
//            appendParams(appendPrefix(prefix, "timeRange"), timeRangeParams, paramMap);
//
//            final List<Param> externalParameters = linkParams;
//            if (externalParameters != null) {
//                appendParams(appendPrefix(prefix, "link.param"), externalParameters, paramMap);
//            }
//
//            final Components components = dashboardContext.getComponents();
//            for (final Component component : components.getComponents()) {
//                if (component instanceof final HasParams hasParams) {
//                    final List<Param> componentParams = hasParams.getParams();
//                    appendParams(appendPrefix(prefix, "component." + component.getId() + ".param"),
//                            componentParams,
//                            paramMap);
//                }
//
//                if (component instanceof final HasComponentSelection hasComponentSelection) {
//                    final List<ComponentSelection> selections = hasComponentSelection.getSelection();
//                    for (final ComponentSelection selection : selections) {
//                        final List<Param> selectionParams = selection.getParams();
//                        final String start = appendPrefix(prefix, "component." + component.getId() + ".selection");
//                        appendParams(start, selectionParams, paramMap);
//                    }
//                }
//            }
//
//            // Add grandparents if there are any.
//            if (dashboardContext instanceof final DashboardContextImpl impl) {
//                final Map<String, String> parentParentParams = impl.parentParams;
//                if (NullSafe.hasEntries(parentParentParams)) {
//                    parentParentParams.forEach((key, value) -> paramMap.put("parent." + key, value));
//                }
//            }
//        }
//        return paramMap;
//    }

//    private void appendParams(final String prefix,
//                              final List<Param> params,
//                              final Map<String, String> paramMap) {
//        for (final Param param : params) {
//            final String key = appendPrefix(prefix, param.getKey());
//            paramMap.put(key, param.getValue());
//        }
//    }

    @Override
    public Optional<ExpressionOperator> createSelectionHandlerExpression(
            final List<ComponentSelectionHandler> selectionHandlers) {
        if (NullSafe.isEmptyCollection(selectionHandlers)) {
            return Optional.empty();
        }

        final List<ComponentParams> componentParams = getComponentParams();
        List<ExpressionOperator> list = new ArrayList<>();
        for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
            if (selectionHandler.isEnabled()) {
                replaceComponentSelection(componentParams,
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
        return replaceComponentSelection(getComponentParams(), operator, keepUnmatched).orElseGet(() ->
                ExpressionOperator.builder().build());
    }

    private Optional<ExpressionOperator> replaceComponentSelection(final List<ComponentParams> componentParams,
                                                                   final ExpressionOperator operator,
                                                                   final boolean keepUnmatched) {
        if (!operator.enabled() || operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return Optional.empty();
        }

        final List<ExpressionItem> children = new ArrayList<>(operator.getChildren().size());
        // Gather children.
        if (NullSafe.hasItems(operator.getChildren())) {
            final Map<String, List<ExpressionTerm>> termMap = new HashMap<>();
            for (final ExpressionItem item : operator.getChildren()) {
                if (item instanceof final ExpressionTerm term) {
                    if (NullSafe.isBlankString(term.getValue())) {
                        children.add(term);
                    } else {
                        final List<String> keys = ParamUtil.getKeys(term.getValue());
                        boolean found = false;
                        for (final String key : keys) {
                            final int index = key.lastIndexOf(".selection.");
                            if (index != -1) {
                                final String prefix = key.substring(0, index + ".selection.".length());
                                termMap.computeIfAbsent(prefix, k -> new ArrayList<>()).add(term);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            final String value = term.getValue();
                            final String resolved = ParamUtil.replaceParameters(term.getValue(),
                                    v -> getParamValue(v, componentParams), keepUnmatched);
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
                } else if (item instanceof final ExpressionOperator op) {
                    replaceComponentSelection(componentParams, op, keepUnmatched).map(children::add);
                }
            }

            if (NullSafe.hasEntries(termMap)) {
                final List<ExpressionItem> items = new ArrayList<>();
                for (final Entry<String, List<ExpressionTerm>> entry : termMap.entrySet()) {
                    expandSelectionTerms(
                            entry.getKey(),
                            entry.getValue(),
                            componentParams)
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
                                                          final List<ComponentParams> list) {
        final List<ExpressionItem> outerItems = new ArrayList<>();
        for (final ComponentParams componentParams : list) {
            final boolean wildcard = prefix.contains(".?.");

            if (prefix.contains("." + componentParams.id + ".") || wildcard) {
                for (final List<Param> params : componentParams.selections) {
                    final Map<String, String> paramMap = ParamUtil.createParamMap(params);
                    final List<ExpressionItem> innerItems = new ArrayList<>(terms.size());
                    for (final ExpressionTerm term : terms) {
                        String value = term.getValue();
                        if (wildcard) {
                            value = value.replaceAll("\\.?\\.", componentParams.id);
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

//    private String resolve(final String value,
//                           final String componentId,
//                           final ComponentSelection componentSelection) {
//        if (value == null) {
//            return null;
//        }
//        String result = value;
//        List<String> keys = ParamUtil.getKeys(value);
//        // Loop to allow for some recursive param usage.
//        while (!keys.isEmpty()) {
//            final Map<String, String> replacements = getReplacements(keys, componentId, componentSelection);
//            result = ParamUtil.replaceParameters(result, replacements::get);
//            keys = ParamUtil.getKeys(result);
//        }
//        return result;
//    }

    @Override
    public String getParamValue(final String key) {
        return getParamValue(key, getComponentParams());
    }

    private String getParamValue(final String key, final List<ComponentParams> list) {
        for (final ComponentParams componentParams : list) {
            final Map<String, String> paramMap = ParamUtil.createParamMap(componentParams.params);
            final String value = paramMap.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
//
//    private Map<String, String> getReplacements(final List<String> keys,
//                                                final String componentId,
//                                                final ComponentSelection componentSelection) {
//        final Map<String, String> replacements = new HashMap<>();
//        for (final String key : keys) {
//            final String v = getReplacement(key, componentId, componentSelection, this);
//            replacements.put(key, v);
//        }
//        return replacements;
//    }
//
//    private String getReplacement(final String key,
//                                  final String componentId,
//                                  final ComponentSelection componentSelection,
//                                  final DashboardContext dashboardContext) {
//        String v = null;
//        if (dashboardContext != null) {
//            if (componentId != null &&
//                (key.startsWith("component." + componentId + ".selection.") ||
//                 key.startsWith("component.?.selection."))) {
//                if (componentSelection != null) {
//                    v = componentSelection.getParamValue(getFinalKeyPart(key));
//                }
//            } else if (key.startsWith("component.") && key.contains(".param.")) {
//                final int componentIdIndex = "component.".length();
//                String id = key.substring(componentIdIndex);
//                id = id.substring(0, id.indexOf("."));
//                final Component component = dashboardContext.getComponents().get(id);
//                if (component instanceof final HasParams hasParams) {
//                    final List<Param> params = hasParams.getParams();
//                    if (NullSafe.hasItems(params)) {
//                        final Map<String, String> map = ParamUtil.createParamMap(params);
//                        v = map.get(getFinalKeyPart(key));
//                    }
//                }
//            } else if (key.equals("timeRange.from")) {
//                final TimeRange timeRange = dashboardContext.getRawTimeRange();
//                v = timeRange.getFrom();
//            } else if (key.equals("timeRange.to")) {
//                final TimeRange timeRange = dashboardContext.getRawTimeRange();
//                v = timeRange.getTo();
//            } else if (key.startsWith("link.param.")) {
//                final Map<String, String> map = ParamUtil.createParamMap(linkParams);
//                if (NullSafe.hasEntries(map)) {
//                    v = map.get(getFinalKeyPart(key));
//                }
//            } else if (key.startsWith("parent.")) {
//                final Map<String, String> map = parentParams;
//                v = map.get(key);
//            }
//        }
//        return v;
//    }
//
//    private String getFinalKeyPart(final String key) {
//        String k = key;
//        final int index = key.lastIndexOf(".");
//        if (index != -1) {
//            k = key.substring(index + 1);
//        }
//        return k;
//    }

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

    private record ComponentParams(String id, String name, List<Param> params, List<List<Param>> selections) {

    }
}
