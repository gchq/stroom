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

package stroom.pipeline.factory;

import org.springframework.context.annotation.Profile;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.filter.RecordCountFilter;
import stroom.pipeline.filter.RecordOutputFilter;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.filter.SchemaFilterSplit;
import stroom.pipeline.filter.SplitFilter;
import stroom.pipeline.filter.XSLTFilter;
import stroom.pipeline.parser.CombinedParser;
import stroom.pipeline.parser.DSParser;
import stroom.pipeline.parser.JSONParser;
import stroom.pipeline.parser.XMLFragmentParser;
import stroom.pipeline.parser.XMLParser;
import stroom.pipeline.source.SourceElement;
import stroom.pipeline.writer.FileAppender;
import stroom.pipeline.writer.JSONWriter;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.TextWriter;
import stroom.pipeline.writer.XMLWriter;
import stroom.pipeline.state.PipelineContext;
import stroom.util.spring.StroomSpringProfiles;

import java.util.ArrayList;
import java.util.List;

// @Profile(StroomSpringProfiles.TEST)
public class MockPipelineElementRegistryFactory implements ElementRegistryFactory, ElementFactory {
    private final ElementRegistry registry;

    public MockPipelineElementRegistryFactory() {
        final List<Class<?>> elementClasses = new ArrayList<>();
        elementClasses.add(SourceElement.class);
        elementClasses.add(CombinedParser.class);
        elementClasses.add(DSParser.class);
        elementClasses.add(JSONParser.class);
        elementClasses.add(XMLFragmentParser.class);
        elementClasses.add(XMLParser.class);
        elementClasses.add(RecordCountFilter.class);
        elementClasses.add(SplitFilter.class);
        elementClasses.add(XSLTFilter.class);
        elementClasses.add(SchemaFilterSplit.class);
        elementClasses.add(RecordOutputFilter.class);
        elementClasses.add(XMLWriter.class);
        elementClasses.add(JSONWriter.class);
        elementClasses.add(TextWriter.class);
        elementClasses.add(StreamAppender.class);
        elementClasses.add(FileAppender.class);
        this.registry = new ElementRegistry(elementClasses);
    }

    @Override
    public ElementRegistry get() {
        return registry;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Element> T getElementInstance(final Class<T> elementClass) {
        try {
            if (elementClass.equals(CombinedParser.class)) {
                return (T) new CombinedParser(null, null, null, null);
            }
            if (elementClass.equals(DSParser.class)) {
                return (T) new DSParser(null, null, null, null);
            }
            if (elementClass.equals(JSONParser.class)) {
                return (T) new JSONParser(null, null);
            }
            if (elementClass.equals(XMLFragmentParser.class)) {
                return (T) new XMLFragmentParser(null, null, null, null);
            }
            if (elementClass.equals(XMLParser.class)) {
                return (T) new XMLParser(null, null);
            }
            if (elementClass.equals(RecordCountFilter.class)) {
                return (T) new RecordCountFilter(null, null);
            }
            if (elementClass.equals(SplitFilter.class)) {
                return (T) new SplitFilter();
            }
            if (elementClass.equals(XSLTFilter.class)) {
                return (T) new XSLTFilter(null, null, null, null, null, null, null);
            }
            if (elementClass.equals(SchemaFilterSplit.class)) {
                return (T) new SchemaFilterSplit(
                        new SchemaFilter(null, null, null, new LocationFactoryProxy(), new PipelineContext()), null);
            }
            if (elementClass.equals(RecordOutputFilter.class)) {
                return (T) new RecordOutputFilter(null);
            }
            if (elementClass.equals(XMLWriter.class)) {
                return (T) new XMLWriter();
            }
            if (elementClass.equals(JSONWriter.class)) {
                return (T) new JSONWriter(null);
            }
            if (elementClass.equals(TextWriter.class)) {
                return (T) new TextWriter(null);
            }
            if (elementClass.equals(StreamAppender.class)) {
                return (T) new StreamAppender(null, null, null, null, null, null, null, null);
            }
            if (elementClass.equals(FileAppender.class)) {
                return (T) new FileAppender(null, null);
            }

            return elementClass.newInstance();
        } catch (final IllegalAccessException e) {
            throw new PipelineFactoryException(e);
        } catch (final InstantiationException e) {
            throw new PipelineFactoryException(e);
        }
    }
}
