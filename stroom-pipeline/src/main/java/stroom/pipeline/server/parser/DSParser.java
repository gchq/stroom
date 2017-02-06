/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.pipeline.server.parser;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import stroom.cache.server.ParserFactoryPool;
import stroom.cache.server.StoredParserFactory;
import stroom.entity.shared.VersionedEntityDecorator;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.SupportsCodeInjection;
import stroom.pipeline.server.errorhandler.ErrorReceiverIdDecorator;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.TextConverterService;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pool.PoolItem;
import stroom.util.spring.StroomScope;
import stroom.xml.converter.ParserFactory;

import javax.inject.Inject;

@Component
@Scope(value = StroomScope.TASK)
@ConfigurableElement(type = "DSParser", category = Category.PARSER, roles = { PipelineElementType.ROLE_PARSER,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
        PipelineElementType.ROLE_HAS_CODE }, icon = ElementIcons.TEXT)
public class DSParser extends AbstractParser implements SupportsCodeInjection {
    private final ParserFactoryPool parserFactoryPool;
    private final TextConverterService textConverterService;
    private String injectedCode;
    private boolean usePool = true;
    private TextConverter textConverter;
    private PoolItem<VersionedEntityDecorator<TextConverter>, StoredParserFactory> poolItem;
    @Inject
    public DSParser(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory,
            final ParserFactoryPool parserFactoryPool, final TextConverterService textConverterService) {
        super(errorReceiverProxy, locationFactory);
        this.parserFactoryPool = parserFactoryPool;
        this.textConverterService = textConverterService;
    }

    @Override
    protected XMLReader createReader() throws SAXException {
        if (textConverter == null) {
            throw new ProcessException(
                    "No data splitter configuration has been assigned to the parser but one is required");
        }

        // Load the latest TextConverter to get round the issue of the pipeline
        // being cached and therefore holding onto
        // stale TextConverter.

        // TODO: We need to use the cached TextConverter service ideally but
        // before we do it needs to be aware cluster
        // wide when TextConverter has been updated.
        final TextConverter tc = textConverterService.load(textConverter);
        if (tc == null) {
            throw new ProcessException(
                    "TextConverter \"" + textConverter.getName() + "\" appears to have been deleted");
        }

        // If we are in stepping mode and have made code changes then we want to
        // add them to the newly loaded text
        // converter.
        if (injectedCode != null) {
            tc.setData(injectedCode);
            usePool = false;
        }

        // Get a text converter generated parser from the pool.
        poolItem = parserFactoryPool.borrowObject(new VersionedEntityDecorator<>(tc), usePool);
        final StoredParserFactory storedParserFactory = poolItem.getValue();
        final StoredErrorReceiver storedErrorReceiver = storedParserFactory.getErrorReceiver();
        final ParserFactory parserFactory = storedParserFactory.getParserFactory();

        if (storedErrorReceiver.getTotalErrors() == 0 && parserFactory != null) {
            return parserFactory.getParser();
        } else {
            storedErrorReceiver.replay(new ErrorReceiverIdDecorator(getElementId(), getErrorReceiverProxy()));
        }

        return null;
    }

    @Override
    public void endProcessing() {
        try {
            super.endProcessing();
        } catch (final LoggedException e) {
            throw e;
        } catch (final Throwable e) {
            throw ProcessException.wrap(e);
        } finally {
            // Return the parser factory to the pool if we have used one.
            if (poolItem != null && parserFactoryPool != null) {
                parserFactoryPool.returnObject(poolItem, usePool);
            }
        }
    }

    @Override
    public void setInjectedCode(final String injectedCode) {
        this.injectedCode = injectedCode;
    }

    @PipelineProperty(description = "The data splitter configuration that should be used to parse the input data.")
    public void setTextConverter(final TextConverter textConverter) {
        this.textConverter = textConverter;
    }
}
