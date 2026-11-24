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

import stroom.query.api.ColumnRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;
import stroom.query.client.component.QueryToolbarController;
import stroom.query.client.result.table.ComponentSelection;
import stroom.query.client.result.table.HasComponentSelection;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDashboardContextImpl {

    private DashboardContextImpl dashboardContext;

    @BeforeEach
    void setUp() {
        // The query toolbar's widgets are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        final QueryToolbarController queryToolbar = Mockito.mock(QueryToolbarController.class);
        Mockito.when(queryToolbar.getTimeRange()).thenReturn(new TimeRange("All", null, null));
        final Panes components = Mockito.mock(Panes.class);
        Mockito.when(components.getComponents()).thenReturn(List.of());
        dashboardContext = new DashboardContextImpl(new SimpleEventBus(), components, queryToolbar);
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

    @Test
    void getKeysWithoutSelection_nothingSelected() {
        // Regression test (#6059): a pane's selection keys can be inserted before anything is
        // selected, whatever they are, not just name and value
        final DashboardContextImpl context = contextWith(selectingPane("vis1", "Map", List.of(
                new ColumnRef("lat", "lat"),
                new ColumnRef("lon", "lon"),
                new ColumnRef("floor", "floor"),
                new ColumnRef("lat", "lat")), List.of()));

        assertThat(context.getKeysWithoutSelection()).containsExactly(
                "component.vis1.selection.lat",
                "component.vis1.selection.lon",
                "component.vis1.selection.floor");
    }

    @Test
    void getKeysWithoutSelection_somethingSelected() {
        // With something selected, the selection's own keys and values are shown instead
        final ComponentSelection selection = Mockito.mock(ComponentSelection.class);
        Mockito.when(selection.getParams()).thenReturn(List.of(new Param("lat", "51.5")));
        final DashboardContextImpl context = contextWith(selectingPane("vis1", "Map", List.of(
                new ColumnRef("lat", "lat"),
                new ColumnRef("lon", "lon")), List.of(selection)));

        assertThat(context.getKeysWithoutSelection()).isEmpty();
    }

    @Test
    void getKeysWithoutSelection_noKeys() {
        final DashboardContextImpl context = contextWith(selectingPane("vis1", "Map", null, List.of()));

        assertThat(context.getKeysWithoutSelection()).isEmpty();
    }

    @Test
    void getKeysWithoutSelection_blankKeysLeftOut() {
        final DashboardContextImpl context = contextWith(selectingPane("vis1", "Map", Arrays.asList(
                new ColumnRef("x", null),
                new ColumnRef("y", " "),
                null,
                new ColumnRef("series", "series")), List.of()));

        assertThat(context.getKeysWithoutSelection()).containsExactly("component.vis1.selection.series");
    }

    private DashboardContextImpl contextWith(final DashboardPane pane) {
        final QueryToolbarController queryToolbar = Mockito.mock(QueryToolbarController.class);
        Mockito.when(queryToolbar.getTimeRange()).thenReturn(new TimeRange("All", null, null));
        final Panes components = Mockito.mock(Panes.class);
        Mockito.when(components.getComponents()).thenReturn(List.of(pane));
        return new DashboardContextImpl(new SimpleEventBus(), components, queryToolbar);
    }

    // A pane whose selections have the given keys, with the given selection
    private DashboardPane selectingPane(final String id,
                                        final String name,
                                        final List<ColumnRef> keys,
                                        final List<ComponentSelection> selection) {
        final DashboardPane pane = Mockito.mock(DashboardPane.class,
                Mockito.withSettings().extraInterfaces(HasComponentSelection.class));
        Mockito.when(pane.getId()).thenReturn(id);
        Mockito.when(pane.getDisplayValue()).thenReturn(name);
        final HasComponentSelection hasComponentSelection = (HasComponentSelection) pane;
        Mockito.when(hasComponentSelection.getColumnRefs()).thenReturn(keys);
        Mockito.when(hasComponentSelection.getSelection()).thenReturn(selection);
        return pane;
    }
}
