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

    SafeHtml toSafeHtml();

    ExpressionOperator replaceExpression(ExpressionOperator operator, boolean keepUnmatched);

    Optional<ExpressionOperator> createSelectionHandlerExpression(List<ComponentSelectionHandler> selectionHandlers);

    HandlerRegistration addComponentChangeHandler(ComponentChangeEvent.Handler handler);

    HandlerRegistration addContextChangeHandler(DashboardContextChangeEvent.Handler handler);

    void fireComponentChangeEvent(Component component);

    void fireContextChangeEvent();
}
