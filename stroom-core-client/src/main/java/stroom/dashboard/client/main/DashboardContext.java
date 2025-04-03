package stroom.dashboard.client.main;

import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.TimeRange;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.Optional;

public interface DashboardContext extends HasParams {

    DashboardContext getParent();

    List<Param> getExternalParams();

    TimeRange getTimeRange();

    Components getComponents();

    DocRef getDashboardDocRef();

    SafeHtml toSafeHtml();

    Optional<ExpressionOperator> createSelectionHandlerExpression(List<ComponentSelectionHandler> selectionHandlers);

    HandlerRegistration addComponentChangeHandler(ComponentChangeEvent.Handler handler);

    HandlerRegistration addContextChangeHandler(DashboardContextChangeEvent.Handler handler);

    void fireComponentChangeEvent(Component component);

    void fireContextChangeEvent();
}
