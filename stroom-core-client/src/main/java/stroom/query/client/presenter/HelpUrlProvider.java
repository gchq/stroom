package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.ui.config.client.UiConfigCache;

import com.google.web.bindery.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.function.Consumer;
import javax.inject.Inject;

class HelpUrlProvider implements HasHandlers {

    private final EventBus eventBus;
    private final UiConfigCache uiConfigCache;

    @Inject
    HelpUrlProvider(final EventBus eventBus,
                    final UiConfigCache uiConfigCache) {
        this.eventBus = eventBus;
        this.uiConfigCache = uiConfigCache;
    }

    public void fetchHelpUrl(final Consumer<String> consumer) {
        uiConfigCache.get()
                .onSuccess(result -> {
                    final String helpUrl = result.getHelpUrlStroomQueryLanguage();
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
