package stroom.core.meta;

import stroom.meta.shared.MetaFields;
import stroom.suggestions.api.SuggestionsServiceBinder;

import com.google.inject.AbstractModule;

public class MetaModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MetaSuggestionsQueryHandler.class).to(MetaSuggestionsQueryHandlerImpl.class);

        SuggestionsServiceBinder.create(binder())
                .bind(MetaFields.STREAM_STORE_TYPE, MetaSuggestionsQueryHandler.class);
    }
}
