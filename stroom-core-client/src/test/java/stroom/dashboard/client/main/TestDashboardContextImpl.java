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

package stroom.dashboard.client.main;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDashboardContextImpl {

    private DashboardContextImpl dashboardContext;

    @BeforeEach
    void setUp() {
        // The query toolbar's widgets are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        final QueryToolbarPresenter queryToolbarPresenter = Mockito.mock(QueryToolbarPresenter.class);
        Mockito.when(queryToolbarPresenter.getTimeRange()).thenReturn(new TimeRange("All", null, null));
        final Components components = Mockito.mock(Components.class);
        Mockito.when(components.getComponents()).thenReturn(List.of());
        dashboardContext = new DashboardContextImpl(new SimpleEventBus(), components, queryToolbarPresenter);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void replaceExpression_noChildren() {
        // Regression test (gwt-bugs #37): an operator with nothing added has null children
        final ExpressionOperator operator = ExpressionOperator.builder().build();
        assertThat(operator.getChildren())
                .isNull();

        assertThat(dashboardContext.replaceExpression(operator, false))
                .isEqualTo(ExpressionOperator.builder().build());
    }

    @Test
    void replaceExpression_emptyChildren() {
        final ExpressionOperator operator = new ExpressionOperator(true, Op.AND, List.of());

        assertThat(dashboardContext.replaceExpression(operator, false))
                .isEqualTo(ExpressionOperator.builder().build());
    }

    @Test
    void replaceExpression_disabled() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(false)
                .addTerm(ExpressionTerm.equals("Feed", "EVENTS"))
                .build();

        assertThat(dashboardContext.replaceExpression(operator, false))
                .isEqualTo(ExpressionOperator.builder().build());
    }

    @Test
    void replaceExpression_term() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field("Feed")
                .condition(Condition.EQUALS)
                .value("EVENTS")
                .build();
        final ExpressionOperator operator = ExpressionOperator.builder()
                .addTerm(term)
                .build();

        final ExpressionOperator replaced = dashboardContext.replaceExpression(operator, false);

        assertThat(replaced.getChildren())
                .containsExactly(term);
    }
}
