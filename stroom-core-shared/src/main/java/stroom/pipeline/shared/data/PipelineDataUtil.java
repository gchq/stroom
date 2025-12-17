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

package stroom.pipeline.shared.data;

import stroom.docref.DocRef;

import java.util.Collections;

public class PipelineDataUtil {

    public static PipelineElement createElement(final String id,
                                                final String type,
                                                final String name,
                                                final String description) {
        return new PipelineElement(id, type, name, description);
    }

//    public static PipelineProperty createProperty(final String element, final String name, final BaseEntity entity) {
//        final PipelinePropertyValue value = new PipelinePropertyValue(DocRefUtil.create(entity));
//        final PipelineProperty property = new PipelineProperty();
//        property.setElement(element);
//        property.setName(name);
//        property.setValue(value);
//        return property;
//    }

    public static PipelineProperty createProperty(final String element,
                                                  final String name,
                                                  final DocRef docRef) {
        final PipelinePropertyValue value = new PipelinePropertyValue(docRef);
        return new PipelineProperty(element, name, value);
    }

    public static PipelineProperty createProperty(final String element, final String name, final String string) {
        final PipelinePropertyValue value = new PipelinePropertyValue(string);
        return new PipelineProperty(element, name, value);
    }

    public static PipelineProperty createProperty(final String element, final String name, final boolean b) {
        final PipelinePropertyValue value = new PipelinePropertyValue(b);
        return new PipelineProperty(element, name, value);
    }

    public static PipelineReference createReference(final String element,
                                                    final String name,
                                                    final DocRef pipeline,
                                                    final DocRef feed,
                                                    final String streamType) {
        return new PipelineReference(element, name, pipeline, feed, streamType);
    }

    public static PipelineLink createLink(final String from, final String to) {
        return new PipelineLink(from, to);
    }

    public static PipelineData normalise(final PipelineData pipelineData) {
        final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);
        Collections.sort(builder.getElements().getAddList());
        Collections.sort(builder.getProperties().getAddList());
        Collections.sort(builder.getReferences().getAddList());
        Collections.sort(builder.getLinks().getAddList());
        return builder.build();
    }
}
