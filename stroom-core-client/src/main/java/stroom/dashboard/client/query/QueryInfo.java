package stroom.dashboard.client.query;

import javax.inject.Inject;
import javax.inject.Provider;

public class QueryInfo {

    private final Provider<QueryInfoPresenter> queryInfoPresenterProvider;
    private String message;

    @Inject
    public QueryInfo(final Provider<QueryInfoPresenter> queryInfoPresenterProvider) {
        this.queryInfoPresenterProvider = queryInfoPresenterProvider;
    }

    public void prompt(final Runnable runnable) {
        queryInfoPresenterProvider.get().show(message, state -> {
            if (state.isOk()) {
                message = state.getQueryInfo();
                runnable.run();
            }
        });
    }

    public String getMessage() {
        return message;
    }
}
