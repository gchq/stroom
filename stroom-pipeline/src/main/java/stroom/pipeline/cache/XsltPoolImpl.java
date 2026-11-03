/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.docref.DocRef;
import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.xslt.XsltImports;
import stroom.pipeline.xsltfunctions.StroomXsltFunctionLibrary;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

import java.util.List;
import java.util.function.Consumer;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

@Singleton
@EntityEventHandler(
        type = XsltDoc.TYPE,
        action = {EntityAction.CREATE, EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
class XsltPoolImpl extends AbstractDocPool<XsltDoc, StoredXsltExecutable> implements XsltPool, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltPoolImpl.class);
    private static final ElementId ELEMENT_ID = new ElementId(XsltPool.class.getSimpleName());

    private final URIResolver uriResolver;
    private final Provider<StroomXsltFunctionLibrary> stroomXsltFunctionLibraryProvider;

    @Inject
    XsltPoolImpl(final CacheManager cacheManager,
                 final Provider<XsltConfig> xsltConfigProvider,
                 final DocumentPermissionCache documentPermissionCache,
                 final SecurityContext securityContext,
                 final URIResolver uriResolver,
                 final Provider<StroomXsltFunctionLibrary> stroomXsltFunctionLibraryProvider) {
        super(
                cacheManager,
                "XSLT Pool",
                () -> xsltConfigProvider.get().getCacheConfig(),
                documentPermissionCache,
                securityContext);
        this.uriResolver = uriResolver;
        this.stroomXsltFunctionLibraryProvider = stroomXsltFunctionLibraryProvider;
    }

    @Override
    public PoolItem<StoredXsltExecutable> borrowConfiguredTemplate(final XsltDoc k,
                                                                   final ErrorReceiver errorReceiver,
                                                                   final LocationFactory locationFactory,
                                                                   final List<PipelineReference> pipelineReferences,
                                                                   final boolean usePool) {
        // Get the item from the pool.
        final PoolItem<StoredXsltExecutable> poolItem = super.borrowObject(k, usePool);

        // Configure the item.
        if (poolItem != null && poolItem.getValue() != null && poolItem.getValue().getFunctionLibrary() != null) {
            poolItem.getValue().getFunctionLibrary().configure(errorReceiver, locationFactory,
                    pipelineReferences);
        }

        return poolItem;
    }

    @Override
    public void returnObject(final PoolItem<StoredXsltExecutable> poolItem, final boolean usePool) {
        // Reset all references to function library classes to release memory.
        if (poolItem != null && poolItem.getValue() != null && poolItem.getValue().getFunctionLibrary() != null) {
            poolItem.getValue().getFunctionLibrary().reset();
        }

        super.returnObject(poolItem, usePool);
    }

    @Override
    protected StoredXsltExecutable createValue(final XsltDoc xslt) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating xslt executable: " + xslt.toString());
        }

        XsltExecutable xsltExecutable = null;
        StroomXsltFunctionLibrary functionLibrary = null;
        final StoredErrorReceiver errorReceiver = new StoredErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final ErrorListener errorListener = new ErrorListenerAdaptor(
                ELEMENT_ID,
                locationFactory,
                errorReceiver);

        try {
            // Create a new Saxon processor.
            final Processor processor = new Processor(false);

            // Register the Stroom XSLT extension functions.
            functionLibrary = stroomXsltFunctionLibraryProvider.get();
            functionLibrary.init(processor.getUnderlyingConfiguration());

            final XsltCompiler xsltCompiler = processor.newXsltCompiler();
            xsltCompiler.setErrorListener(errorListener);
            xsltCompiler.setURIResolver(uriResolver);

            xsltExecutable = xsltCompiler.compile(new StreamSource(StreamUtil.stringToStream(xslt.getData())));

        } catch (final SaxonApiException e) {
            LOGGER.debug(e.getMessage(), e);
            errorReceiver.log(Severity.FATAL_ERROR, null, ELEMENT_ID, e.getMessage(), e);
        }

        return new StoredXsltExecutable(xsltExecutable, functionLibrary, errorReceiver);
    }

    @Override
    public void onChange(final EntityEvent event) {
        // super deals with invalidating the affected doc
        super.onChange(event);

        final DocRef changedXsltDocRef = event.getOldDocRef() != null
                ? event.getOldDocRef()
                : event.getDocRef();

        // An XSLT can import or include other XSLTs, by name, UUID or doc ref string, directly or through
        // another XSLT. A pooled XSLT holds the imports as they were when it was compiled, and a failed compile
        // (e.g. an import of an XSLT that didn't exist yet) is pooled too. Working out which pooled XSLTs
        // depend on the changed one would mean resolving every import, so invalidate every pooled XSLT that
        // has any. The cost is recompiling them when next borrowed, and XSLTs are saved far less often than
        // they are used.
        final Consumer<XsltDoc> loggingPeekFunc = LOGGER.createIfDebugConsumer(xsltDoc ->
                LOGGER.debug("Invalidating XsltDoc {} as it has imports and {} changed", xsltDoc, changedXsltDocRef));

        getKeys()
                .stream()
                .filter(xsltDoc -> XsltImports.hasImports(xsltDoc.getData()))
                .peek(loggingPeekFunc)
                .forEach(this::invalidate);

        LOGGER.debug("Completed event handler for {}", event);
    }
}
