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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Param;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.Optional;

public interface DashboardContext extends ParamValues {

    @Deprecated
    List<Param> getParams();

    TimeRange getRawTimeRange();

    TimeRange getResolvedTimeRange();

    Components getComponents();

    DocRef getDashboardDocRef();

    SafeHtml toSafeHtml(boolean showInsert);

    ExpressionOperator replaceExpression(ExpressionOperator operator, boolean keepUnmatched);

    Optional<ExpressionOperator> createSelectionHandlerExpression(List<ComponentSelectionHandler> selectionHandlers);

    HandlerRegistration addComponentChangeHandler(ComponentChangeEvent.Handler handler);

    HandlerRegistration addContextChangeHandler(DashboardContextChangeEvent.Handler handler);

    void fireComponentChangeEvent(Component component);

    void fireContextChangeEvent();
}
