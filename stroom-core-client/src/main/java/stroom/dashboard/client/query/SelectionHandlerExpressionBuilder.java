package stroom.dashboard.client.query;

import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ParamUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectionHandlerExpressionBuilder {

    @Deprecated
    public static List<ComponentSelectionHandler> fixLegacySelectionHandlers(
            final List<ComponentSelectionHandler> selectionHandlers) {
        if (NullSafe.isEmptyCollection(selectionHandlers)) {
            return selectionHandlers;
        }

        final List<ComponentSelectionHandler> out = new ArrayList<>();
        for (final ComponentSelectionHandler selectionHandler : selectionHandlers) {
            if (selectionHandler.getExpression() == null) {
                out.add(selectionHandler);
            } else {
                final String componentId = NullSafe.getOrElse(selectionHandler,
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
            for (final ExpressionItem child : operator.getChildren()) {
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
