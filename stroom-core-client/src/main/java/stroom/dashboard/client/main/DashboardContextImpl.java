package stroom.dashboard.client.main;

import stroom.dashboard.client.main.DashboardContextChangeEvent.Handler;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.data.client.presenter.CopyTextUtil;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter;
import stroom.util.shared.GwtNullSafe;
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
import java.util.Optional;
import java.util.stream.Collectors;

public class DashboardContextImpl implements HasHandlers, DashboardContext {

    private final EventBus eventBus;
    private final Components components;
    private final QueryToolbarPresenter queryToolbarPresenter;
    private DashboardContext parent;
    private List<Param> externalParams = Collections.emptyList();
    private DocRef dashboardDocRef;

    public DashboardContextImpl(final EventBus eventBus,
                                final Components components,
                                final QueryToolbarPresenter queryToolbarPresenter) {
        this.eventBus = eventBus;
        this.components = components;
        this.queryToolbarPresenter = queryToolbarPresenter;
    }

    public void setParent(final DashboardContext parent) {
        this.parent = parent;
    }

    @Override
    public DashboardContext getParent() {
        return parent;
    }

    @Override
    public List<Param> getExternalParams() {
        return externalParams;
    }

    public void setGetExternalParams(final List<Param> externalParams) {
        this.externalParams = externalParams;
    }

    @Override
    public TimeRange getTimeRange() {
        return queryToolbarPresenter.getTimeRange();
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

    @Override
    public List<Param> getParams() {
        final List<Param> combinedParams = new ArrayList<>();
        for (final Component component : components) {
            if (component instanceof HasParams) {
                final List<Param> params = ((HasParams) component).getParams();
                combinedParams.addAll(params);
                for (final Param param : params) {
                    combinedParams.add(new Param("components." +
                                                 component.getId() +
                                                 ".param." +
                                                 param.getKey(), param.getValue()));
                }
            }
        }
        combinedParams.addAll(externalParams);
        for (final Param param : externalParams) {
            combinedParams.add(new Param("link" +
                                         ".param." +
                                         param.getKey(), param.getValue()));
        }
        return combinedParams;
    }

    public SafeHtml toSafeHtml() {
        final TableBuilder tb = new TableBuilder();
        printContext(tb, "", this);

        if (parent != null) {
            tb.row("");
            tb.row(TableCell.header("Parent Context"));
            printContext(tb, "parent", parent);
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void printContext(final TableBuilder tb,
                              final String prefix,
                              final DashboardContext dashboardContext) {
        if (dashboardContext != null) {
            final TimeRange timeRange = dashboardContext.getTimeRange();
            tb.row(TableCell.header("Time Range"));
            final List<Param> timeRangeParams = List.of(
                    new Param("from", timeRange.getFrom()),
                    new Param("to", timeRange.getTo()));
            printParams(tb, appendPrefix(prefix, "timeRange"), timeRangeParams);

            final List<Param> externalParameters = dashboardContext.getExternalParams();
            if (externalParameters != null) {
                tb.row(TableCell.header("Link Parameters"));
                printParams(tb, appendPrefix(prefix, "link.param"), externalParameters);
            }

            final Components components = dashboardContext.getComponents();
            final Collection<Component> componentList = components
                    .getComponents()
                    .stream()
                    .sorted(Comparator.comparing(Component::getDisplayValue))
                    .collect(Collectors.toList());
            for (final Component component : componentList) {
                tb.row(TableCell.header(component.getDisplayValue()));
                if (component instanceof final HasParams hasParams) {
                    final List<Param> componentParams = hasParams.getParams();
                    printParams(tb,
                            appendPrefix(prefix, "component." + component.getId() + ".param"), componentParams);
                }

                if (component instanceof final HasComponentSelection hasComponentSelection) {
                    final List<ComponentSelection> selections = hasComponentSelection.getSelection();
                    boolean first = true;
                    for (final ComponentSelection selection : selections) {
                        if (!first) {
                            tb.row(TableCell.data("------"));
                        }
                        final List<Param> selectionParams = selection.getParams();
                        final String start = appendPrefix(prefix, "component." + component.getId() + ".selection");
                        printParams(tb, start, selectionParams);
                        first = false;
                    }
                }
            }
        }
    }

    private void printParams(final TableBuilder tb,
                             final String prefix,
                             final List<Param> params) {
        for (final Param param : params) {
            final String key = "${" + appendPrefix(prefix, param.getKey()) + "}";
            tb.row(TableCell.data(CopyTextUtil.render(key)), TableCell.data(param.getValue()));
        }
    }

    private String appendPrefix(final String prefix, final String key) {
        if (GwtNullSafe.isBlankString(prefix)) {
            return key;
        }
        return prefix + "." + key;
    }

    @Override
    public Optional<ExpressionOperator> createSelectionHandlerExpression(
            final List<ComponentSelectionHandler> selectionHandlers) {
        if (GwtNullSafe.isEmptyCollection(selectionHandlers)) {
            return Optional.empty();
        }

        List<ExpressionOperator> list = new ArrayList<>();
        for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
            if (selectionHandler.isEnabled()) {
                for (final Component component : components.getComponents()) {
                    if (component instanceof final HasComponentSelection hasComponentSelection) {
                        final List<ComponentSelection> selections = hasComponentSelection.getSelection();
                        if (GwtNullSafe.hasItems(selections)) {
                            for (final ComponentSelection selection : selections) {
                                replaceComponentSelection(
                                        component.getId(),
                                        selection,
                                        selectionHandler.getExpression()).map(list::add);
                            }
                        }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            // Add non selection handler item.
            for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
                if (selectionHandler.isEnabled()) {
                    replaceComponentSelection(
                            null,
                            null,
                            selectionHandler.getExpression()).map(list::add);
                }
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

    private Optional<ExpressionOperator> replaceComponentSelection(final String componentId,
                                                                   final ComponentSelection componentSelection,
                                                                   final ExpressionOperator operator) {
        if (!operator.enabled() || operator.getChildren() == null || operator.getChildren().isEmpty()) {
            return Optional.empty();
        }

        final List<ExpressionItem> children = new ArrayList<>(operator.getChildren().size());
        for (ExpressionItem child : operator.getChildren()) {
            if (child.enabled()) {
                if (child instanceof final ExpressionOperator childOperator) {
                    replaceComponentSelection(componentId, componentSelection, childOperator).map(children::add);

                } else if (child instanceof final ExpressionTerm term) {
                    final String value = term.getValue();
                    final List<String> keys = ParamUtil.getKeys(value);
                    if (!keys.isEmpty()) {
                        final Map<String, String> replacements = new HashMap<>();
                        for (final String key : keys) {
                            String v;
                            if (key.startsWith("parent.")) {
                                final String k = key.substring("parent.".length());
                                v = getReplacement(k, componentId, componentSelection, parent);
                            } else {
                                v = getReplacement(key, componentId, componentSelection, this);
                            }
                            replacements.put(key, v);
                        }

                        final String replaced = ParamUtil.replaceParameters(value, replacements::get);
                        if (GwtNullSafe.isNonBlankString(replaced)) {
                            children.add(ExpressionTerm.builder()
                                    .enabled(term.enabled())
                                    .field(term.getField())
                                    .condition(term.getCondition())
                                    .value(replaced)
                                    .docRef(term.getDocRef())
                                    .build());
                        }
                    } else {
                        children.add(term);
                    }
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

    private static String getReplacement(final String key,
                                         final String componentId,
                                         final ComponentSelection componentSelection,
                                         final DashboardContext dashboardContext) {
        String v = null;
        if (dashboardContext != null) {
            String k = key;
            final int index = key.lastIndexOf(".");
            if (index != -1) {
                k = key.substring(index + 1);
            }
            if (key.startsWith("component." + componentId + ".selection") ||
                key.startsWith("component.?.selection")) {
                if (componentSelection != null) {
                    v = componentSelection.get(k);
                }
            } else if (key.startsWith("component.") && key.contains(".param.")) {
                final int componentIdIndex = "component.".length();
                String id = key.substring(componentIdIndex);
                id = id.substring(0, id.indexOf("."));
                final Component component = dashboardContext.getComponents().get(id);
                if (component instanceof final HasParams hasParams) {
                    final List<Param> params = hasParams.getParams();
                    final Map<String, String> map = ParamUtil.createParamMap(params);
                    v = map.get(k);
                }
            } else if (key.equals("timeRange.from")) {
                final TimeRange timeRange = dashboardContext.getTimeRange();
                v = timeRange.getFrom();
            } else if (key.equals("timeRange.to")) {
                final TimeRange timeRange = dashboardContext.getTimeRange();
                v = timeRange.getTo();
            } else if (key.startsWith("link.param")) {
                final Map<String, String> map = ParamUtil.createParamMap(dashboardContext.getExternalParams());
                v = map.get(k);
            } else if (key.startsWith("param")) {
                final Map<String, String> map = ParamUtil.createParamMap(dashboardContext.getParams());
                v = map.get(k);
            }
        }
        return v;
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
}
