package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.datasource.api.v2.QueryField;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.task.client.TaskHandlerFactory;

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

    protected void validateExpression(final HasHandlers hasHandlers,
                                      final List<QueryField> fields,
                                      final ExpressionOperator expression,
                                      final Consumer<ExpressionOperator> consumer,
                                      final TaskHandlerFactory taskHandlerFactory) {
        if (expression != null) {
            if (Objects.equals(expression, validatedExpression)) {
                // Same expression as last time, nothing new to validate, so carry on
                if (expressionValidationMessage == null) {
                    consumer.accept(expression);
                } else {
                    AlertEvent.fireError(
                            hasHandlers,
                            expressionValidationMessage,
                            null);
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
                                        hasHandlers, result.getString(), null);
                                expressionValidationMessage = result.getString();
                            }
                        })
                        .onFailure(throwable -> {
                            AlertEvent.fireError(
                                    hasHandlers, throwable.getMessage(), null);
                            expressionValidationMessage = throwable.getMessage();
                        })
                        .taskHandlerFactory(taskHandlerFactory)
                        .exec();
            }
        }
    }
}
