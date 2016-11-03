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

package stroom.cache.server;

import javax.inject.Inject;

import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;

import stroom.security.Insecure;
import stroom.entity.shared.VersionedEntityDecorator;
import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverter.TextConverterType;
import stroom.pool.AbstractPoolCacheBean;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Severity;
import stroom.xml.converter.ParserFactory;
import stroom.xml.converter.xmlfragment.XMLFragmentParserFactory;
import net.sf.ehcache.CacheManager;

@Insecure
@Component
public class ParserFactoryPoolImpl
        extends AbstractPoolCacheBean<VersionedEntityDecorator<TextConverter>, StoredParserFactory>
        implements ParserFactoryPool {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ParserFactoryPool.class);

    private final DSChooser dsChooser;

    @Inject
    public ParserFactoryPoolImpl(final CacheManager cacheManager, final DSChooser dsChooser) {
        super(cacheManager, "Parser Factory Pool");
        this.dsChooser = dsChooser;
    }

    @Override
    protected StoredParserFactory createValue(final VersionedEntityDecorator<TextConverter> entity) {
        final TextConverter textConverter = entity.getEntity();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating parser factory: " + textConverter.toString());
        }

        final StoredErrorReceiver errorReceiver = new StoredErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final ErrorHandler errorHandler = new ErrorHandlerAdaptor(getClass().getSimpleName(), locationFactory,
                errorReceiver);
        ParserFactory parserFactory = null;

        try {
            if (TextConverterType.DATA_SPLITTER.equals(textConverter.getConverterType())) {
                parserFactory = dsChooser.configure(textConverter.getData(), errorHandler);

                // } else if
                // (textConverter.getConverterType().equals(TextConverterType.JAVA_CC))
                // {
                // final JavaCCParserFactory javaCCParserFactory =
                // JavaCCParserFactory
                // .create(StreamUtil.stringToStream(textConverter.getData()),
                // errorHandler);
                //
                // parserFactory = javaCCParserFactory;

            } else if (textConverter.getConverterType().equals(TextConverterType.XML_FRAGMENT)) {
                final XMLFragmentParserFactory xmlFragmentParserFactory = XMLFragmentParserFactory
                        .create(StreamUtil.stringToStream(textConverter.getData()), errorHandler);

                parserFactory = xmlFragmentParserFactory;

            } else {
                final String message = "Unknown text converter type: " + textConverter.getConverterType().toString();
                throw new ProcessException(message);
            }

        } catch (final Throwable e) {
            LOGGER.debug(e, e);
            errorReceiver.log(Severity.FATAL_ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
        }

        return new StoredParserFactory(parserFactory, errorReceiver);
    }
}
