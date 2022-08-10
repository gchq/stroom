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
import stroom.pipeline.xslt.XsltStore;
import stroom.pipeline.xsltfunctions.StroomXsltFunctionLibrary;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

@Singleton
@EntityEventHandler(
        type = XsltDoc.DOCUMENT_TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE})
class XsltPoolImpl extends AbstractDocPool<XsltDoc, StoredXsltExecutable> implements XsltPool, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltPoolImpl.class);

    private final URIResolver uriResolver;
    private final Provider<StroomXsltFunctionLibrary> stroomXsltFunctionLibraryProvider;
    private final XsltStore xsltStore;

    @Inject
    XsltPoolImpl(final CacheManager cacheManager,
                 final XsltConfig xsltConfig,
                 final DocumentPermissionCache documentPermissionCache,
                 final SecurityContext securityContext,
                 final URIResolver uriResolver,
                 final Provider<StroomXsltFunctionLibrary> stroomXsltFunctionLibraryProvider,
                 final XsltStore xsltStore) {
        super(cacheManager, "XSLT Pool", xsltConfig::getCacheConfig, documentPermissionCache, securityContext);
        this.uriResolver = uriResolver;
        this.stroomXsltFunctionLibraryProvider = stroomXsltFunctionLibraryProvider;
        this.xsltStore = xsltStore;
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
        final ErrorListener errorListener = new ErrorListenerAdaptor(getClass().getSimpleName(), locationFactory,
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
            errorReceiver.log(Severity.FATAL_ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
        }

        return new StoredXsltExecutable(xsltExecutable, functionLibrary, errorReceiver);
    }

    @Override
    public void onChange(final EntityEvent event) {
        // Get the doc object for the changed xslt then invalidate it in the cache.
        final DocRef changedXsltDocRef = event.getDocRef();
        final String changedXsltName = changedXsltDocRef.getName();
        final XsltDoc changedXsltDoc = xsltStore.readDocument(changedXsltDocRef);
        LOGGER.debug("Invalidating XsltDoc {}", changedXsltDocRef);
        invalidate(changedXsltDoc);

        // An XSLT can have an xsl:include or xsl:import of other XSLTs (referenced by name)
        // so we need to find any that reference our changed one and invalidate them too.
        // E.g. if an imported XSLT is modified then we need to invalidate the one that uses
        // it, so it picks up the updated import.
        final String searchStr = "href=\"" + changedXsltName + "\"";
        final Consumer<Tuple2<DocRef, XsltDoc>> loggingPeekFunc = LOGGER.isDebugEnabled()
                ? tuple2 ->
                LOGGER.debug("Invalidating XsltDoc {} with dependency on {}", tuple2._1, changedXsltDocRef)
                : tuple2 -> {};

        // TODO AT: This is not efficient at all, would be better to find these using a single db query that
        //  did the test in sql
        // This xslt may be imported/included in other XSLTs, so we need to invalidate all of them as well
        xsltStore.list()
                .stream()
                .map(docRef -> {
                    final XsltDoc xsltDoc = xsltStore.readDocument(docRef);
                    if (xsltDoc == null) {
                        return null;
                    } else {
                        return Tuple.of(docRef, xsltDoc);
                    }
                })
                .filter(Objects::nonNull)
                .filter(tuple2 -> {
                    final XsltDoc xsltDoc = tuple2._2;
                    final String xsltData = xsltDoc.getData();
                    return xsltData != null
                            && !xsltData.isBlank()
                            && xsltData.contains(searchStr);
                })
                .peek(loggingPeekFunc)
                .map(Tuple2::_2)
                .forEach(this::invalidate);

        LOGGER.debug("Done event handler");
    }
}
