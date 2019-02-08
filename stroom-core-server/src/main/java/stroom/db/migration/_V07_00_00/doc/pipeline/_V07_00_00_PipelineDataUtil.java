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

package stroom.db.migration._V07_00_00.doc.pipeline;

import stroom.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_BaseEntity;
import stroom.db.migration._V07_00_00.entity.shared._V07_00_00_DocRefUtil;

import java.util.Collections;

public class _V07_00_00_PipelineDataUtil {
    public static _V07_00_00_PipelineElement createElement(final String id, final String type) {
        final _V07_00_00_PipelineElement element = new _V07_00_00_PipelineElement();
        element.setId(id);
        element.setType(type);
        return element;
    }

    public static _V07_00_00_PipelineProperty createProperty(final String element, final String name, final _V07_00_00_BaseEntity entity) {
        final _V07_00_00_PipelinePropertyValue value = new _V07_00_00_PipelinePropertyValue(_V07_00_00_DocRefUtil.create(entity));
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static _V07_00_00_PipelineProperty createProperty(final String element, final String name,
                                                             final _V07_00_00_DocRef docRef) {
        final _V07_00_00_PipelinePropertyValue value = new _V07_00_00_PipelinePropertyValue(docRef);
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static _V07_00_00_PipelineProperty createProperty(final String element, final String name, final String string) {
        final _V07_00_00_PipelinePropertyValue value = new _V07_00_00_PipelinePropertyValue(string);
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static _V07_00_00_PipelineProperty createProperty(final String element, final String name, final boolean b) {
        final _V07_00_00_PipelinePropertyValue value = new _V07_00_00_PipelinePropertyValue(b);
        final _V07_00_00_PipelineProperty property = new _V07_00_00_PipelineProperty();
        property.setElement(element);
        property.setName(name);
        property.setValue(value);
        return property;
    }

    public static _V07_00_00_PipelineReference createReference(final String element,
                                                               final String name,
                                                               final _V07_00_00_DocRef pipeline,
                                                               final _V07_00_00_DocRef feed,
                                                               final String streamType) {
        return new _V07_00_00_PipelineReference(element, name, pipeline, feed, streamType);
    }

    public static _V07_00_00_PipelineLink createLink(final String from, final String to) {
        final _V07_00_00_PipelineLink link = new _V07_00_00_PipelineLink();
        link.setFrom(from);
        link.setTo(to);
        return link;
    }

    public static void normalise(final _V07_00_00_PipelineData pipelineData) {
        Collections.sort(pipelineData.getElements().getAdd());
        Collections.sort(pipelineData.getProperties().getAdd());
        Collections.sort(pipelineData.getPipelineReferences().getAdd());
        Collections.sort(pipelineData.getLinks().getAdd());
    }
}
