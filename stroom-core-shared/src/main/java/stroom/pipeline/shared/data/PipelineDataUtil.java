/*
 * Copyright 2017 Crown Copyright
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

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.DocRef;

import java.util.Collections;

public class PipelineDataUtil {
    public static PipelineElement createElement(final String id, final String type) {
        final PipelineElement element = new PipelineElement();
        element.setId(id);
        element.setType(type);
        return element;
    }

    public static PipelineProperty createProperty(final String element, final String name, final BaseEntity entity) {
        final PipelinePropertyValue value = new PipelinePropertyValue(DocRefUtil.create(entity));
        final PipelineProperty property = new PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static PipelineProperty createProperty(final String element, final String name,
                                                  final DocRef docRef) {
        final PipelinePropertyValue value = new PipelinePropertyValue(docRef);
        final PipelineProperty property = new PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static PipelineProperty createProperty(final String element, final String name, final String string) {
        final PipelinePropertyValue value = new PipelinePropertyValue(string);
        final PipelineProperty property = new PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static PipelineProperty createProperty(final String element, final String name, final boolean b) {
        final PipelinePropertyValue value = new PipelinePropertyValue(b);
        final PipelineProperty property = new PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static PipelineReference createReference(final String element,
                                                    final String name,
                                                    final DocRef pipeline,
                                                    final DocRef feed,
                                                    final String streamType) {
        return new PipelineReference(element, name, pipeline, feed, streamType);
    }

    public static PipelineLink createLink(final String from, final String to) {
        final PipelineLink link = new PipelineLink();
        link.setFrom(from);
        link.setTo(to);
        return link;
    }

    public static void normalise(final PipelineData pipelineData) {
        Collections.sort(pipelineData.getElements().getAdd());
        Collections.sort(pipelineData.getProperties().getAdd());
        Collections.sort(pipelineData.getPipelineReferences().getAdd());
        Collections.sort(pipelineData.getLinks().getAdd());
    }
}
