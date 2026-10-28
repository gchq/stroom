/*
 * Copyright 2024 Crown Copyright
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

package stroom.data.client;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ExpressionValidator {

    public static final ExpressionOperator ALL_UNLOCKED_EXPRESSION = MetaExpressionUtil.createStatusExpression(
            Status.UNLOCKED);
    private static final ExpressionResource EXPRESSION_RESOURCE = GWT.create(ExpressionResource.class);

    private final DateTimeSettingsFactory dateTimeSettingsFactory;
    private final RestFactory restFactory;

    private ExpressionOperator validatedExpression = null;
    private String expressionValidationMessage = null;

    @Inject
    public ExpressionValidator(final DateTimeSettingsFactory dateTimeSettingsFactory,
                               final RestFactory restFactory) {
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        this.restFactory = restFactory;
    }

    public void validateExpression(final HasHandlers hasHandlers,
                                      final List<QueryField> fields,
                                      final ExpressionOperator expression,
                                      final Consumer<ExpressionOperator> consumer,
                                      final TaskMonitorFactory taskMonitorFactory) {
        validateExpression(hasHandlers, fields, expression, consumer, null, taskMonitorFactory);
    }

    /// Validates an expression, telling the caller whether it is valid, e.g. so that a dialog waiting
    /// for the result can be used again when it isn't.
    ///
    /// @param hasHandlers        Where to show why the expression isn't valid.
    /// @param fields             The fields the expression may use.
    /// @param expression         The expression to validate.
    /// @param consumer           Called with the expression if it is valid.
    /// @param onInvalid          Called if the expression isn't valid or can't be validated, once the
    ///                           message saying why has been closed. May be null.
    /// @param taskMonitorFactory Shows that the validation is in progress.
    public void validateExpression(final HasHandlers hasHandlers,
                                   final List<QueryField> fields,
                                   final ExpressionOperator expression,
                                   final Consumer<ExpressionOperator> consumer,
                                   final Runnable onInvalid,
                                   final TaskMonitorFactory taskMonitorFactory) {
        if (expression == null) {
            // Nothing to validate or apply
            if (onInvalid != null) {
                onInvalid.run();
            }
        } else {
            if (Objects.equals(expression, validatedExpression)) {
                // Same expression as last time, nothing new to validate, so carry on
                if (expressionValidationMessage == null) {
                    consumer.accept(expression);
                } else {
                    AlertEvent.fireError(
                            hasHandlers,
                            expressionValidationMessage,
                            whenClosed(onInvalid));
                }
            } else if (Objects.equals(ALL_UNLOCKED_EXPRESSION, expression)) {
                // Standard expression that needs no validation, so save on the rest call
                consumer.accept(expression);
            } else {
                restFactory
                        .create(EXPRESSION_RESOURCE)
                        .method(res -> res.validate(new ValidateExpressionRequest(
                                expression,
                                fields,
                                dateTimeSettingsFactory.getDateTimeSettings())))
                        .onSuccess(result -> {
                            if (result.isOk()) {
                                validatedExpression = expression;
                                expressionValidationMessage = null;
                                consumer.accept(expression);
                            } else {
                                AlertEvent.fireError(
                                        hasHandlers, result.getString(), whenClosed(onInvalid));
                                expressionValidationMessage = result.getString();
                            }
                        })
                        .onFailure(throwable -> {
                            AlertEvent.fireError(
                                    hasHandlers, throwable.getMessage(), whenClosed(onInvalid));
                            expressionValidationMessage = throwable.getMessage();
                        })
                        .taskMonitorFactory(taskMonitorFactory)
                        .exec();
            }
        }
    }

    // Runs the callback, if there is one, when an alert is closed
    private static AlertCallback whenClosed(final Runnable onInvalid) {
        return onInvalid == null
                ? null
                : onInvalid::run;
    }
}
