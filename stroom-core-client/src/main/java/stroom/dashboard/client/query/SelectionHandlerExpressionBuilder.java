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
