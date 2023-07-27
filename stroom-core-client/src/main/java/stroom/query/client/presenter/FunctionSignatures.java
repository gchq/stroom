package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.query.shared.QueryResource;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FunctionSignatures implements HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;

    private List<FunctionSignature> functionSignatures;


    @Inject
    FunctionSignatures(final EventBus eventBus,
                       final RestFactory restFactory,
                       final UiConfigCache uiConfigCache) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
    }

    public void fetchFunctions(final Consumer<List<FunctionSignature>> consumer) {
        if (functionSignatures != null) {
            consumer.accept(functionSignatures);
        } else {
            final Rest<List<FunctionSignature>> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        functionSignatures = result;
                        consumer.accept(result);
                    })
                    .onFailure(throwable -> AlertEvent.fireError(
                            this,
                            throwable.getMessage(),
                            null))
                    .call(QUERY_RESOURCE)
                    .fetchFunctions();
        }
    }

    public void fetchHelpUrl(final Consumer<String> consumer) {
        uiConfigCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrlExpressions();
                    if (helpUrl != null && helpUrl.trim().length() > 0) {
                        consumer.accept(helpUrl);
                    } else {
                        AlertEvent.fireError(
                                this,
                                "Help is not configured!",
                                null);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(
                        this,
                        caught.getMessage(),
                        null));
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
