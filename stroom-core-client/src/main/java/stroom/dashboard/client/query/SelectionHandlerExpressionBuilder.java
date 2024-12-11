package stroom.dashboard.client.query;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SelectionHandlerExpressionBuilder {

    public static Optional<ExpressionOperator> create(final Collection<Component> components,
                                                      final List<ComponentSelectionHandler> selectionHandlers) {
        List<ExpressionItem> list = new ArrayList<>();
        for (final Component component : components) {
            Optional<ExpressionOperator> optional = create(component, selectionHandlers);
            optional.ifPresent(list::add);
        }
        if (list.size() == 0) {
            return Optional.empty();
        }
        if (list.size() == 1) {
            return Optional.of((ExpressionOperator) list.get(0));
        }
        return Optional.of(ExpressionOperator.builder().children(list).build());
    }

    private static Optional<ExpressionOperator> create(final Component component,
                                                       final List<ComponentSelectionHandler> selectionHandlers) {
        ExpressionOperator currentSelectionExpression = null;
        if (component instanceof HasComponentSelection) {
            final HasComponentSelection hasComponentSelection = (HasComponentSelection) component;
            final List<ComponentSelection> componentSelections = hasComponentSelection.getSelection();
            if (selectionHandlers != null) {
                final List<ComponentSelectionHandler> matchingHandlers = selectionHandlers
                        .stream()
                        .filter(ComponentSelectionHandler::isEnabled)
                        .filter(selectionHandler -> selectionHandler.getComponentId() == null ||
                                                    selectionHandler.getComponentId().equals(component.getId()))
                        .collect(Collectors.toList());

                if (matchingHandlers.size() > 0) {
                    final ExpressionOperator.Builder innerBuilder = ExpressionOperator
                            .builder()
                            .op(Op.OR);
                    for (final ComponentSelectionHandler selectionHandler : matchingHandlers) {
                        for (final ComponentSelection componentSelection : componentSelections) {
                            ExpressionOperator ex = selectionHandler.getExpression();
                            ex = ExpressionUtil.replaceExpressionParameters(ex, componentSelection);
                            innerBuilder.addOperator(ex);
                        }
                    }

                    currentSelectionExpression = innerBuilder.build();
                }
            }
        }

        return Optional.ofNullable(currentSelectionExpression);
    }
}
