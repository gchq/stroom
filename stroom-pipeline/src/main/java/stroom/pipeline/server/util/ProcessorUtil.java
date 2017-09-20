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

package stroom.pipeline.server.util;

import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.Processor;
import stroom.pipeline.server.factory.ProcessorFactory;
import stroom.pipeline.server.factory.SimpleProcessorFactory;
import stroom.pipeline.server.factory.Target;
import stroom.pipeline.server.filter.XMLFilter;
import stroom.pipeline.server.parser.AbstractParser;
import stroom.pipeline.server.parser.XMLParser;

import java.io.InputStream;
import java.util.List;

public class ProcessorUtil {
//    public static void processCombined(final InputStream inputStream, final ErrorReceiverProxy errorReceiverProxy,
//            final XMLFilter filter, final LocationFactoryProxy locationFactory,
//            final ParserFactoryPool parserFactoryPool, final TextConverterService textConverterService) {
//        final CombinedParser parser = new CombinedParser(errorReceiverProxy, locationFactory, parserFactoryPool,
//                textConverterService);
//        doProcess(filter, inputStream, errorReceiverProxy, parser);
//
//    }

    public static void processXml(final InputStream inputStream, final ErrorReceiverProxy errorReceiverProxy,
                                  final XMLFilter filter, final LocationFactoryProxy locationFactory) {
        final XMLParser parser = new XMLParser(errorReceiverProxy, locationFactory);
        doProcess(filter, inputStream, errorReceiverProxy, parser);

    }

    private static void doProcess(final Target target, final InputStream inputStream,
                                  final ErrorReceiverProxy errorReceiverProxy, final AbstractParser parser) {
        final ProcessorFactory processorFactory = new SimpleProcessorFactory(errorReceiverProxy);

        parser.setTarget(target);

        parser.setInputStream(inputStream, null);
        final List<Processor> processors = parser.createProcessors();
        final Processor processor = processorFactory.create(processors);

        parser.startProcessing();
        parser.startStream();
        processor.process();
        parser.endStream();
        parser.endProcessing();
    }

}
