/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.factory;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class CommonPipelineElementModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        // Source
        elementBinder.addBinding().to(stroom.pipeline.source.SourceElement.class);

        // Readers
        elementBinder.addBinding().to(stroom.pipeline.reader.BOMRemovalFilterInputElement.class);
        elementBinder.addBinding().to(stroom.pipeline.reader.BadTextXMLFilterReaderElement.class);
        elementBinder.addBinding().to(stroom.pipeline.reader.InvalidCharFilterReaderElement.class);
        elementBinder.addBinding().to(stroom.pipeline.reader.InvalidXMLCharFilterReaderElement.class);
        elementBinder.addBinding().to(stroom.pipeline.reader.ReaderElement.class);

        // Parsers
        elementBinder.addBinding().to(stroom.pipeline.parser.CombinedParser.class);
        elementBinder.addBinding().to(stroom.pipeline.parser.DSParser.class);
        elementBinder.addBinding().to(stroom.pipeline.parser.JSONParser.class);
        elementBinder.addBinding().to(stroom.pipeline.parser.XMLFragmentParser.class);
        elementBinder.addBinding().to(stroom.pipeline.parser.XMLParser.class);

        // XML filters
        elementBinder.addBinding().to(stroom.pipeline.filter.HttpPostFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.IdEnrichmentFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.RecordCountFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.RecordOutputFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.ReferenceDataFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.SchemaFilterSplit.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.SplitFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.TestFilter.class);
        elementBinder.addBinding().to(stroom.pipeline.filter.XsltFilter.class);

        // Writers
        elementBinder.addBinding().to(stroom.pipeline.writer.JSONWriter.class);
        elementBinder.addBinding().to(stroom.pipeline.writer.TextWriter.class);
        elementBinder.addBinding().to(stroom.pipeline.writer.XMLWriter.class);

        // Appenders
        elementBinder.addBinding().to(stroom.pipeline.writer.FileAppender.class);
        elementBinder.addBinding().to(stroom.pipeline.writer.HTTPAppender.class);
        elementBinder.addBinding().to(stroom.pipeline.writer.RollingFileAppender.class);
        elementBinder.addBinding().to(stroom.pipeline.writer.TestAppender.class);
    }
}