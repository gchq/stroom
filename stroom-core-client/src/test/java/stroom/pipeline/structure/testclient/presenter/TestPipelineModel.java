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

package stroom.pipeline.structure.testclient.presenter;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.docref.DocRef;
import stroom.streamstore.shared.StreamType;

import java.util.ArrayList;
import java.util.List;

public class TestPipelineModel {
    private static final PipelineElementType ELEM_TYPE = new PipelineElementType("TestElement", null,
            new String[]{PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, null);
    private static final PipelinePropertyType PROP_TYPE1 = new PipelinePropertyType.Builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty1")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE2 = new PipelinePropertyType.Builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty2")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE3 = new PipelinePropertyType.Builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty3")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE4 = new PipelinePropertyType.Builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty4")
            .type("String")
            .build();

    @Test
    public void testBasic() {
        test(null, null, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    public void testSimple() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test1");
        pipelineData.addElement(ELEM_TYPE, "test2");
        pipelineData.addLink("test1", "test2");

        test(null, pipelineData, 2, 0, 0, 0, 0, 0, 1, 0);
    }

    @Test
    public void testComplex() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test1");
        pipelineData.addElement(ELEM_TYPE, "test2");
        pipelineData.addElement(ELEM_TYPE, "test3");
        pipelineData.addElement(ELEM_TYPE, "test4");
        pipelineData.addLink("test1", "test2");
        pipelineData.addLink("test2", "test3");
        pipelineData.addLink("test3", "test4");
        pipelineData.removeElement(ELEM_TYPE, "test4");

        test(null, pipelineData, 3, 0, 0, 0, 0, 0, 2, 0);
    }

    @Test
    public void testComplexWithProperties() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test1");
        pipelineData.addElement(ELEM_TYPE, "test2");
        pipelineData.addElement(ELEM_TYPE, "test3");
        pipelineData.addElement(ELEM_TYPE, "test4");
        pipelineData.addLink("test1", "test2");
        pipelineData.addLink("test2", "test3");
        pipelineData.addLink("test3", "test4");
        pipelineData.addProperty("test1", PROP_TYPE1, true);
        pipelineData.addProperty("test2", PROP_TYPE2, true);
        pipelineData.addProperty("test3", PROP_TYPE3, true);
        pipelineData.addProperty("test4", PROP_TYPE4, true);
        pipelineData.removeElement(ELEM_TYPE, "test4");

        test(null, pipelineData, 3, 0, 3, 0, 0, 0, 2, 0);
    }

    @Test
    public void testComplexWithPropRemove() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test1");
        pipelineData.addElement(ELEM_TYPE, "test2");
        pipelineData.addElement(ELEM_TYPE, "test3");
        pipelineData.addElement(ELEM_TYPE, "test4");
        pipelineData.addLink("test1", "test2");
        pipelineData.addLink("test2", "test3");
        pipelineData.addLink("test3", "test4");
        pipelineData.addProperty("test1", PROP_TYPE1, true);
        pipelineData.addProperty("test2", PROP_TYPE2, true);
        pipelineData.addProperty("test3", PROP_TYPE3, true);
        pipelineData.addProperty("test4", PROP_TYPE4, true);
        pipelineData.removeProperty("test2", PROP_TYPE2);
        pipelineData.removeElement(ELEM_TYPE, "test4");

        test(null, pipelineData, 3, 0, 2, 1, 0, 0, 2, 0);
    }

    @Test
    public void testUnknownElement() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test");
        pipelineData.addLink("unknown", "test");

        test(null, pipelineData, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    public void testInheritanceAdditive() {
        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addElement(ELEM_TYPE, "test3");
        override.addLink("test2", "test3");

        test(baseStack, override, 1, 0, 0, 0, 0, 0, 1, 0);
    }

    @Test
    public void testInheritanceRemove() {
        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addElement(ELEM_TYPE, "test3");
        override.removeElement(ELEM_TYPE, "test2");
        override.addLink("test2", "test3");

        test(baseStack, override, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    public void testInheritancePropertiesSame() {
        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addProperty("test1", PROP_TYPE1, false);

        test(baseStack, override, 0, 0, 1, 0, 0, 0, 0, 0);
    }

    @Test
    public void testInheritancePropertiesDiff() {
        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addProperty("test1", PROP_TYPE1, true);

        test(baseStack, override, 0, 0, 1, 0, 0, 0, 0, 0);
    }

    @Test
    public void testInheritancePropertiesRemove() {
        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.removeProperty("test1", PROP_TYPE1);

        test(baseStack, override, 0, 0, 0, 1, 0, 0, 0, 0);
    }

    @Test
    public void testInheritanceRefsSame() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(Feed.ENTITY_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.EVENTS.getName()));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.EVENTS.getName()));

        test(baseStack, override, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    public void testInheritanceRefsDiff() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(Feed.ENTITY_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.EVENTS.getName()));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.REFERENCE.getName()));

        test(baseStack, override, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    public void testInheritanceRefsRemove() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(Feed.ENTITY_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.EVENTS.getName()));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.removePipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamType.EVENTS.getName()));

        test(baseStack, override, 0, 0, 0, 0, 0, 1, 0, 0);
    }

    private void test(final List<PipelineData> baseStack, final PipelineData pipelineData, final int addedElements,
                      final int removedElements, final int addedProperties, final int removedProperties,
                      final int addedPipelineReferences, final int removedPipelineReferences, final int addedLinks,
                      final int removedLinks) throws PipelineModelException {
        final PipelineModel pipelineModel = new PipelineModel();
        pipelineModel.setBaseStack(baseStack);
        pipelineModel.setPipelineData(pipelineData);
        pipelineModel.build();
        final PipelineData diff = pipelineModel.diff();

        Assert.assertEquals(addedElements, diff.getAddedElements().size());
        Assert.assertEquals(removedElements, diff.getRemovedElements().size());
        Assert.assertEquals(addedProperties, diff.getAddedProperties().size());
        Assert.assertEquals(removedProperties, diff.getRemovedProperties().size());
        Assert.assertEquals(addedPipelineReferences, diff.getAddedPipelineReferences().size());
        Assert.assertEquals(removedPipelineReferences, diff.getRemovedPipelineReferences().size());
        Assert.assertEquals(addedLinks, diff.getAddedLinks().size());
        Assert.assertEquals(removedLinks, diff.getRemovedLinks().size());
    }
}
