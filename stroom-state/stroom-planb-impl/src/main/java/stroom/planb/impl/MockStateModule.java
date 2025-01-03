package stroom.planb.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.pipeline.PlanBElementModule;
import stroom.planb.impl.pipeline.PlanBLookupImpl;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class MockStateModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PlanBElementModule());

        bind(PlanBLookup.class).to(PlanBLookupImpl.class);

//        // Services
//
//        bind(StateStoreService.class).to(ElasticSearchProvider.class);
//        bind(ElasticSuggestionsQueryHandler.class).to(ElasticSuggestionsQueryHandlerImpl.class);
//
//        SuggestionsServiceBinder.create(binder())
//                .bind(StateStoreDoc.DOCUMENT_TYPE, ElasticSuggestionsQueryHandler.class);
//
        // Caches
        bind(PlanBDocCache.class).to(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(PlanBDocCacheImpl.class);

        // State
        bind(PlanBDocStore.class).to(PlanBDocStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(PlanBDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(PlanBDocStoreImpl.class);

//        bind(StateLookup.class).toInstance((docRef, lookupIdentifier, result) -> {
//
//        });
    }
}
