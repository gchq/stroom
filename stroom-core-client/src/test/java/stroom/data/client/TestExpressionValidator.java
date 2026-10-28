/*
 * Copyright 2026 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dispatch.client.FakeRestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionValidator {

    private final SimpleEventBus eventBus = new SimpleEventBus();
    private final HasHandlers hasHandlers = eventBus::fireEvent;
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<ExpressionOperator> valid = new ArrayList<>();
    private final AtomicInteger invalid = new AtomicInteger();
    private final FakeRestFactory restFactory = new FakeRestFactory();
    private ExpressionValidator validator;

    @BeforeEach
    void setUp() {
        // The REST resource is made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        validator = new ExpressionValidator(Mockito.mock(DateTimeSettingsFactory.class), restFactory);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void anInvalidExpressionTellsTheCallerOnceTheMessageIsClosed() {
        // Regression test: an invalid expression was only shown, so a dialog waiting for the result
        // was never reset and was left with OK and Cancel disabled.
        validate();

        restFactory.getLastRequest().succeed(
                new ValidateExpressionResult(false, "Expression term has a missing field", false));

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Expression term has a missing field");
        assertThat(invalid.get())
                .isZero();
        alerts.getFirst().getCallback().onClose();
        assertThat(invalid.get())
                .isOne();
        assertThat(valid)
                .isEmpty();
    }

    @Test
    void aFailedValidationTellsTheCallerOnceTheMessageIsClosed() {
        validate();

        restFactory.getLastRequest().fail(new RuntimeException("Refused"));

        assertThat(alerts)
                .hasSize(1);
        alerts.getFirst().getCallback().onClose();
        assertThat(invalid.get())
                .isOne();
        assertThat(valid)
                .isEmpty();
    }

    @Test
    void aValidExpressionIsPassedOn() {
        final ExpressionOperator expression = validate();

        restFactory.getLastRequest().succeed(new ValidateExpressionResult(true, null, false));

        assertThat(valid)
                .containsExactly(expression);
        assertThat(alerts)
                .isEmpty();
        assertThat(invalid.get())
                .isZero();
    }

    @Test
    void noExpressionTellsTheCaller() {
        validator.validateExpression(hasHandlers, List.of(), null, valid::add, invalid::incrementAndGet,
                Mockito.mock(TaskMonitorFactory.class));

        assertThat(invalid.get())
                .isOne();
        assertThat(restFactory.getRequests())
                .isEmpty();
    }

    // Validates an expression with one term, returning it.
    private ExpressionOperator validate() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS).value("TEST").build())
                .build();
        validator.validateExpression(hasHandlers, List.of(), expression, valid::add, invalid::incrementAndGet,
                Mockito.mock(TaskMonitorFactory.class));
        assertThat(restFactory.getRequests())
                .hasSize(1);
        return expression;
    }
}
