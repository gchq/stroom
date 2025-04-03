package stroom.dashboard.client.query;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.ParamValues;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.event.logical.shared.SelectionHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SelectionHandlerExpressionBuilder {
//
//    public static Optional<ExpressionOperator> create(final Collection<Component> components,
//                                                      final List<ComponentSelectionHandler> selectionHandlers) {
//        List<ExpressionItem> list = new ArrayList<>();
//        for (final Component component : components) {
//            Optional<ExpressionOperator> optional = create(component, selectionHandlers);
//            optional.ifPresent(list::add);
//        }
//        if (list.isEmpty()) {
//            return Optional.empty();
//        }
//        if (list.size() == 1) {
//            return Optional.of((ExpressionOperator) list.get(0));
//        }
//        return Optional.of(ExpressionOperator.builder().children(list).build());
//    }
//
//    private static Optional<ExpressionOperator> create(final Component component,
//                                                       final List<ComponentSelectionHandler> selectionHandlers) {
//        ExpressionOperator currentSelectionExpression = null;
//        if (component instanceof final HasComponentSelection hasComponentSelection) {
//            final List<ComponentSelection> componentSelections = hasComponentSelection.getSelection();
//            if (selectionHandlers != null) {
//                // Fix selection handlers to include a prefix where they target a specific component.
//
//
//
//
//
//                final List<ComponentSelectionHandler> matchingHandlers = selectionHandlers
//                        .stream()
//                        .filter(ComponentSelectionHandler::isEnabled)
//                        .filter(selectionHandler -> selectionHandler.getComponentId() == null ||
//                                                    selectionHandler.getComponentId().equals(component.getId()))
//                        .collect(Collectors.toList());
//
//                if (!matchingHandlers.isEmpty()) {
//                    final ExpressionOperator.Builder innerBuilder = ExpressionOperator
//                            .builder()
//                            .op(Op.OR);
//                    for (final ComponentSelectionHandler selectionHandler : matchingHandlers) {
//                        for (final ComponentSelection componentSelection : componentSelections) {
//                            ExpressionOperator ex = selectionHandler.getExpression();
//                            ex = ExpressionUtil.replaceExpressionParameters(ex, componentSelection);
//                            innerBuilder.addOperator(ex);
//                        }
//                    }
//
//                    currentSelectionExpression = innerBuilder.build();
//                }
//            }
//        }
//
//        return Optional.ofNullable(currentSelectionExpression);
//    }

    public static List<ComponentSelectionHandler> fixLegacySelectionHandlers(
            final List<ComponentSelectionHandler> selectionHandlers) {
        if (GwtNullSafe.isEmptyCollection(selectionHandlers)) {
            return selectionHandlers;
        }

        final List<ComponentSelectionHandler> out = new ArrayList<>();
        for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
            if (selectionHandler.getExpression() == null) {
                out.add(selectionHandler);
            } else {
                final String componentId = GwtNullSafe.getOrElse(selectionHandler,
                        ComponentSelectionHandler::getComponentId, "?");
                final ExpressionOperator expressionOperator = replaceExpressionParameters(
                        selectionHandler.getExpression(),
                        componentId);
                out.add(selectionHandler.copy().expression(expressionOperator).build());
            }
        }

        return out;
    }

    private static ExpressionOperator replaceExpressionParameters(final ExpressionOperator operator,
                                                                 final String componentId) {
        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(operator.getEnabled())
                .op(operator.getOp());
        if (operator.getChildren() != null) {
            for (ExpressionItem child : operator.getChildren()) {
                if (child instanceof final ExpressionOperator childOperator) {
                    builder.addOperator(replaceExpressionParameters(childOperator, componentId));

                } else if (child instanceof final ExpressionTerm term) {
                    final String value = term.getValue();
                    final List<String> keys = ParamUtil.getKeys(value);
                    if (!keys.isEmpty()) {
                        final Map<String, String> replacements = new HashMap<>();
                        for (final String key : keys) {
                            if (key.contains(".")) {
                                replacements.put(key, "${" + key + "}");
                            } else {
                                replacements.put(key, "${" + "component." + componentId + ".selection." + key + "}");
                            }
                        }
                        final String replaced = ParamUtil.replaceParameters(value, replacements::get);
                        builder.addTerm(ExpressionTerm.builder()
                                .enabled(term.enabled())
                                .field(term.getField())
                                .condition(term.getCondition())
                                .value(replaced)
                                .docRef(term.getDocRef())
                                .build());
                    } else {
                        builder.addTerm(term);
                    }
                }
            }
        }
        return builder.build();
    }
}
