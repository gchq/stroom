/*
 * Copyright 2022 Crown Copyright
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

import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.visualisation.shared.VisColourList;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface DashboardContext extends ParamValues {

    @Deprecated
    List<Param> getParams();

    TimeRange getRawTimeRange();

    TimeRange getResolvedTimeRange();

    Panes getComponents();

    DocRef getDashboardDocRef();

    SafeHtml toSafeHtml(boolean showInsert);

    ExpressionOperator replaceExpression(ExpressionOperator operator, boolean keepUnmatched);

    Optional<ExpressionOperator> createSelectionHandlerExpression(List<ComponentSelectionHandler> selectionHandlers);

    /// Listens for a component's selection or settings changing, until the registration is removed.
    ///
    /// @param listener Told the component.
    /// @return The registration, for the listener's owner to own.
    HandlerRegistration addComponentChangeListener(Consumer<DashboardPane> listener);

    /// Listens for anything the context gives changing (its parameters, time range or
    /// selections), until the registration is removed.
    ///
    /// @param listener What to run.
    /// @return The registration, for the listener's owner to own.
    HandlerRegistration addContextChangeListener(Runnable listener);

    void fireComponentChangeEvent(DashboardPane component);

    void fireContextChangeEvent();

    /// @return The colours of the values the dashboard's visualisations colour by, which they
    /// share so that a value has the same colour in each of them.
    VisColourList getVisColourList();
}
