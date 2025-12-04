/*
 * Copyright 2016-2025 Crown Copyright
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

public class CommonPipelineElementModule extends PipelineElementModule {
    @Override
    protected void configureElements() {
        // Source
        bindElement(stroom.pipeline.source.SourceElement.class);

        // Readers
        bindElement(stroom.pipeline.reader.BOMRemovalFilterInputElement.class);
        bindElement(stroom.pipeline.reader.BadTextXMLFilterReaderElement.class);
        bindElement(stroom.pipeline.reader.FindReplaceFilterElement.class);
        bindElement(stroom.pipeline.reader.InvalidCharFilterReaderElement.class);
        bindElement(stroom.pipeline.reader.InvalidXMLCharFilterReaderElement.class);
        bindElement(stroom.pipeline.reader.ReaderElement.class);
        bindElement(stroom.pipeline.reader.ReaderRecordDetectorElement.class);

        // Parsers
        bindElement(stroom.pipeline.parser.CombinedParser.class);
        bindElement(stroom.pipeline.parser.DSParser.class);
        bindElement(stroom.pipeline.parser.JSONParser.class);
        bindElement(stroom.pipeline.parser.XMLFragmentParser.class);
        bindElement(stroom.pipeline.parser.XMLParser.class);

        // XML filters
        bindElement(stroom.pipeline.filter.HttpPostFilter.class);
        bindElement(stroom.pipeline.filter.IdEnrichmentFilter.class);
        bindElement(stroom.pipeline.filter.RecordCountFilter.class);
        bindElement(stroom.pipeline.filter.RecordOutputFilter.class);
        bindElement(stroom.pipeline.filter.SafeXMLFilter.class);
        bindElement(stroom.pipeline.filter.SAXEventRecorder.class);
        bindElement(stroom.pipeline.filter.SAXRecordDetector.class);
        bindElement(stroom.pipeline.filter.SchemaFilterSplit.class);
        bindElement(stroom.pipeline.filter.SplitFilter.class);
        bindElement(stroom.pipeline.filter.MergeFilter.class);
        bindElement(stroom.pipeline.filter.TestFilter.class);
        bindElement(stroom.pipeline.filter.XsltFilter.class);

        // Writers
        bindElement(stroom.pipeline.writer.JSONWriter.class);
        bindElement(stroom.pipeline.writer.OutputRecorder.class);
        bindElement(stroom.pipeline.writer.TextWriter.class);
        bindElement(stroom.pipeline.writer.XMLWriter.class);

        // Appenders
        bindElement(stroom.pipeline.writer.FileAppender.class);
        bindElement(stroom.pipeline.writer.HTTPAppender.class);
        bindElement(stroom.pipeline.writer.RollingFileAppender.class);
        bindElement(stroom.pipeline.writer.TestAppender.class);
    }
}
