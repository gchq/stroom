package stroom.explorer.client.presenter;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.presenter.FindPresenter.FindProxy;
import stroom.explorer.client.presenter.FindPresenter.FindView;
import stroom.explorer.shared.ExplorerDocContentMatch;
import stroom.explorer.shared.ExplorerFields;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.FindExplorerNodeQuery;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.Objects;
import java.util.function.Consumer;

public class FindPresenter extends MyPresenter<FindView, FindProxy> implements FindUiHandlers {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    private static final int TIMER_DELAY_MS = 500;

    private final MyDataGrid<ExplorerDocContentMatch> dataGrid;
    private final RestDataProvider<ExplorerDocContentMatch, ResultPage<ExplorerDocContentMatch>> dataProvider;

    private String currentPattern = "";
    private boolean initialised;

    private final Timer filterRefreshTimer = new Timer() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Inject
    public FindPresenter(final EventBus eventBus,
                         final FindView view,
                         final FindProxy proxy,
                         final PagerView pagerView,
                         final RestFactory restFactory) {
        super(eventBus, view, proxy);

        dataGrid = new MyDataGrid<>();
        dataGrid.addDefaultSelectionModel(true);
        pagerView.setDataWidget(dataGrid);

        view.setResultView(pagerView);
        view.setUiHandlers(this);

        dataProvider = new RestDataProvider<ExplorerDocContentMatch, ResultPage<ExplorerDocContentMatch>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<ExplorerDocContentMatch>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                final ExpressionOperator expression = ExpressionOperator
                        .builder()
                        .addTerm(ExplorerFields.CONTENT, Condition.MATCHES_REGEX, currentPattern)
                        .build();
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final FindExplorerNodeQuery findExplorerNodeQuery =
                        new FindExplorerNodeQuery(pageRequest, null, currentPattern, false, false);

                final Rest<ResultPage<ExplorerDocContentMatch>> rest = restFactory.create();
                rest
                        .onSuccess(dataConsumer)
                        .onFailure(throwableConsumer)
                        .call(EXPLORER_RESOURCE)
                        .findContent(findExplorerNodeQuery);
            }
        };

        dataGrid.addResizableColumn(new Column<ExplorerDocContentMatch, String>(new TextCell()) {
            @Override
            public String getValue(final ExplorerDocContentMatch row) {
                return row.getDocContentMatch().getDocRef().getType();
            }
        }, "Type", ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(new Column<ExplorerDocContentMatch, String>(new TextCell()) {
            @Override
            public String getValue(final ExplorerDocContentMatch row) {
                return row.getDocContentMatch().getDocRef().getName();
            }
        }, "Name", ColumnSizeConstants.BIG_COL);

        dataGrid.addResizableColumn(new Column<ExplorerDocContentMatch, String>(new TextCell()) {
            @Override
            public String getValue(final ExplorerDocContentMatch row) {
                return row.getPath();
            }
        }, "Path", ColumnSizeConstants.BIG_COL);
    }

    @ProxyEvent
    public void onShow(final ShowFindEvent event) {
        revealInParent();
    }

    @Override
    public void changePattern(final String pattern) {
        String trimmed;
        if (pattern == null) {
            trimmed = "";
        } else {
            trimmed = pattern.trim();
        }

        if (!Objects.equals(trimmed, currentPattern)) {
            this.currentPattern = trimmed;
            // Add in a slight delay to give the user a chance to type a few chars before we fire off
            // a rest call. This helps to reduce the logging too
            if (!filterRefreshTimer.isRunning()) {
                filterRefreshTimer.schedule(TIMER_DELAY_MS);
            }
        }
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Find Content")
                .onShow(e -> getView().focus())
                .onHideRequest(HidePopupRequestEvent::hide)
                .fire();
    }

    @ProxyCodeSplit
    public interface FindProxy extends Proxy<FindPresenter> {

    }

    public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

        String getPattern();

        void setResultView(View view);
    }
}
