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

package stroom.cache;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.DocumentPermissionCache;
import stroom.guice.StroomBeanStore;
import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorListenerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.shared.XSLT;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.xsltfunctions.StroomXSLTFunctionLibrary;
import stroom.pool.AbstractEntityPool;
import stroom.pool.PoolItem;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.util.List;

@Singleton
class XSLTPoolImpl extends AbstractEntityPool<XSLT, StoredXsltExecutable> implements XSLTPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(XSLTPoolImpl.class);

    private final URIResolver uriResolver;
    private final StroomBeanStore beanStore;

    @Inject
    XSLTPoolImpl(final CacheManager cacheManager,
                 final DocumentPermissionCache documentPermissionCache,
                 final Security security,
                 final SecurityContext securityContext,
                 final URIResolver uriResolver,
                 final StroomBeanStore beanStore) {
        super(cacheManager, "XSLT Pool", documentPermissionCache, security, securityContext);
        this.uriResolver = uriResolver;
        this.beanStore = beanStore;
    }

    @Override
    public PoolItem<StoredXsltExecutable> borrowConfiguredTemplate(final XSLT k, final ErrorReceiver errorReceiver, final LocationFactory locationFactory, final List<PipelineReference> pipelineReferences, final boolean usePool) {
        // Get the item from the pool.
        final PoolItem<StoredXsltExecutable> poolItem = super.borrowObject(k, usePool);

        // Configure the item.
        if (poolItem != null && poolItem.getValue() != null && poolItem.getValue().getFunctionLibrary() != null) {
            poolItem.getValue().getFunctionLibrary().configure(beanStore, errorReceiver, locationFactory,
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
    protected StoredXsltExecutable createValue(final XSLT xslt) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating xslt executable: " + xslt.toString());
        }

        XsltExecutable xsltExecutable = null;
        StroomXSLTFunctionLibrary functionLibrary = null;
        final StoredErrorReceiver errorReceiver = new StoredErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final ErrorListener errorListener = new ErrorListenerAdaptor(getClass().getSimpleName(), locationFactory,
                errorReceiver);

        try {
            // Create a new Saxon processor.
            final Processor processor = new Processor(false);

            // Register the Stroom XSLT extension functions.
            functionLibrary = new StroomXSLTFunctionLibrary(processor.getUnderlyingConfiguration());

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
}
