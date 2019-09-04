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


import org.junit.jupiter.api.Test;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.presenter.DefaultPipelineTreeBuilder;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineModel {
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
    void testBasic() {
        test(null, null, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testSimple() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test1");
        pipelineData.addElement(ELEM_TYPE, "test2");
        pipelineData.addLink("test1", "test2");

        test(null, pipelineData, 2, 0, 0, 0, 0, 0, 1, 0);
    }

    @Test
    void testComplex() {
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
    void testComplexWithProperties() {
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
    void testComplexWithPropRemove() {
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
    void testUnknownElement() {
        final PipelineData pipelineData = new PipelineData();
        pipelineData.addElement(ELEM_TYPE, "test");
        pipelineData.addLink("unknown", "test");

        test(null, pipelineData, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testInheritanceAdditive() {
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
    void testInheritanceRemove() {
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
    void testInheritancePropertiesSame() {
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
    void testInheritancePropertiesDiff() {
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
    void testInheritancePropertiesRemove() {
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
    void testInheritanceRefsSame() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.DOCUMENT_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));

        test(baseStack, override, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void testInheritanceRefsDiff() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.DOCUMENT_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.REFERENCE));

        test(baseStack, override, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void testInheritanceRefsRemove() {
        final DocRef pipeline = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.DOCUMENT_TYPE, "1");

        final List<PipelineData> baseStack = new ArrayList<>();

        final PipelineData base = new PipelineData();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(base);

        final PipelineData override = new PipelineData();
        override.removePipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));

        test(baseStack, override, 0, 0, 0, 0, 0, 1, 0, 0);
    }

    @Test
    void testMove1() {
        final DefaultPipelineTreeBuilder builder = new DefaultPipelineTreeBuilder();

        final PipelineElement source = createElement("Source", null, "Source");
        final PipelineElement combinedParser = createElement("CombinedParser", Category.PARSER, "combinedParser");
        final PipelineElement findReplaceFilter = createElement("FindReplaceFilter", Category.READER, "FindReplaceFilter");

        final PipelineData pipelineData = new PipelineData();
//        pipelineData.addElement(sourceElementType, "Source");
        pipelineData.addElement(combinedParser);
        pipelineData.addElement(findReplaceFilter);

        pipelineData.addLink(source, combinedParser);
        pipelineData.addLink(source, findReplaceFilter);

        final PipelineModel pipelineModel = new PipelineModel();
        pipelineModel.setBaseStack(null);
        pipelineModel.setPipelineData(pipelineData);
        pipelineModel.build();
        final DefaultTreeForTreeLayout<PipelineElement> tree1 = builder.getTree(pipelineModel);
        checkChildren(tree1, source, new PipelineElement[]{combinedParser, findReplaceFilter});

        pipelineModel.moveElement(findReplaceFilter, combinedParser);

        pipelineModel.build();
        final DefaultTreeForTreeLayout<PipelineElement> tree2 = builder.getTree(pipelineModel);
        checkChildren(tree2, source, new PipelineElement[]{findReplaceFilter});
        checkChildren(tree2, findReplaceFilter, new PipelineElement[]{combinedParser});
    }

    @Test
    void testMove2() {
        final DefaultPipelineTreeBuilder builder = new DefaultPipelineTreeBuilder();

        final PipelineElement source = createElement("Source", null, "Source");
        final PipelineElement xmlParser = createElement("XMLParser", Category.PARSER, "xmlParser");
        final PipelineElement idEnrichmentFilter = createElement("IDEnrichmentFilter", Category.FILTER, "idEnrichmentFilter");
        final PipelineElement xsltFilter = createElement("XSLTFilter", Category.FILTER, "xsltFilter");

        final PipelineData base = new PipelineData();
//        pipelineData.addElement(sourceElementType, "Source");
        base.addElement(xmlParser);
        base.addElement(idEnrichmentFilter);
        base.addElement(xsltFilter);

        base.addLink(source, xmlParser);
        base.addLink(xmlParser, idEnrichmentFilter);
        base.addLink(idEnrichmentFilter, xsltFilter);

        final PipelineModel pipelineModel = new PipelineModel();
        pipelineModel.setBaseStack(Collections.singletonList(base));
        pipelineModel.setPipelineData(new PipelineData());
        pipelineModel.build();

        final DefaultTreeForTreeLayout<PipelineElement> tree1 = builder.getTree(pipelineModel);
        checkChildren(tree1, source, new PipelineElement[]{xmlParser});
        checkChildren(tree1, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree1, idEnrichmentFilter, new PipelineElement[]{xsltFilter});

        pipelineModel.moveElement(idEnrichmentFilter, xsltFilter);

        pipelineModel.build();
        final DefaultTreeForTreeLayout<PipelineElement> tree2 = builder.getTree(pipelineModel);
        checkChildren(tree2, source, new PipelineElement[]{xmlParser});
        checkChildren(tree2, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree2, idEnrichmentFilter, new PipelineElement[]{xsltFilter});
    }

    @Test
    void testMove3() {
        final DefaultPipelineTreeBuilder builder = new DefaultPipelineTreeBuilder();

        final PipelineElement source = createElement("Source", null, "Source");
        final PipelineElement xmlParser = createElement("XMLParser", Category.PARSER, "xmlParser");
        final PipelineElement idEnrichmentFilter = createElement("IDEnrichmentFilter", Category.FILTER, "idEnrichmentFilter");
        final PipelineElement xsltFilter = createElement("XSLTFilter", Category.FILTER, "xsltFilter");
//        final PipelineElement xsltFilter2 = createElement("XSLTFilter", Category.FILTER, "xsltFilter2");

        final PipelineData base = new PipelineData();
//        pipelineData.addElement(sourceElementType, "Source");
        base.addElement(xmlParser);
        base.addElement(idEnrichmentFilter);
        base.addElement(xsltFilter);

        base.addLink(source, xmlParser);
        base.addLink(xmlParser, idEnrichmentFilter);
        base.addLink(idEnrichmentFilter, xsltFilter);

        final PipelineModel pipelineModel = new PipelineModel();
        pipelineModel.setBaseStack(Collections.singletonList(base));
        pipelineModel.setPipelineData(new PipelineData());
        pipelineModel.build();

        final DefaultTreeForTreeLayout<PipelineElement> tree1 = builder.getTree(pipelineModel);
        checkChildren(tree1, source, new PipelineElement[]{xmlParser});
        checkChildren(tree1, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree1, idEnrichmentFilter, new PipelineElement[]{xsltFilter});

        final PipelineElement xsltFilter2 = pipelineModel.addElement(idEnrichmentFilter, createType("XSLTFilter", Category.FILTER), "xsltFilter2");
        pipelineModel.moveElement(xsltFilter2, xsltFilter);

        pipelineModel.build();
        final DefaultTreeForTreeLayout<PipelineElement> tree2 = builder.getTree(pipelineModel);
        checkChildren(tree2, source, new PipelineElement[]{xmlParser});
        checkChildren(tree2, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree2, idEnrichmentFilter, new PipelineElement[]{xsltFilter2});
        checkChildren(tree2, xsltFilter2, new PipelineElement[]{xsltFilter});

        pipelineModel.moveElement(idEnrichmentFilter, xsltFilter);

        pipelineModel.build();
        final DefaultTreeForTreeLayout<PipelineElement> tree3 = builder.getTree(pipelineModel);
        checkChildren(tree3, source, new PipelineElement[]{xmlParser});
        checkChildren(tree3, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree3, idEnrichmentFilter, new PipelineElement[]{xsltFilter, xsltFilter2});
    }

    private void checkChildren(final DefaultTreeForTreeLayout<PipelineElement> tree, final PipelineElement parent, final PipelineElement[] children) {
        List<PipelineElement> list = tree.getChildren(parent);
        if (children.length == 0) {
            assertThat(list == null || list.size() == 0).isTrue();
        } else {
            assertThat(list.size()).isEqualTo(children.length);
            for (final PipelineElement child : children) {
                assertThat(list.contains(child)).isTrue();
            }
        }
    }

    private PipelineElement createElement(final String type, final Category category, final String id) {
        final PipelineElementType elementType = createType(type, category);
        final PipelineElement element = new PipelineElement();
        element.setId(id);
        element.setType(elementType.getType());
        element.setElementType(elementType);
        return element;
    }

    private PipelineElementType createType(final String type, final Category category) {
        String[] roles = null;
        if (category == null) {
            roles = new String[]{PipelineElementType.ROLE_SOURCE, PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE};
        } else {
            switch (category) {
                case READER:
                    roles = new String[]{PipelineElementType.ROLE_TARGET,
                            PipelineElementType.ROLE_HAS_TARGETS,
                            PipelineElementType.ROLE_READER,
                            PipelineElementType.ROLE_MUTATOR,
                            PipelineElementType.VISABILITY_STEPPING};
                    break;
                case PARSER:
                    roles = new String[]{PipelineElementType.ROLE_PARSER,
                            PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
                            PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
                            PipelineElementType.ROLE_HAS_CODE};
                    break;

                case FILTER:
                    roles = new String[]{PipelineElementType.ROLE_TARGET,
                            PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
                            PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
                            PipelineElementType.ROLE_HAS_CODE};
                    break;
            }
        }

        return new PipelineElementType(
                type,
                category,
                roles, null);
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

        assertThat(diff.getAddedElements().size()).isEqualTo(addedElements);
        assertThat(diff.getRemovedElements().size()).isEqualTo(removedElements);
        assertThat(diff.getAddedProperties().size()).isEqualTo(addedProperties);
        assertThat(diff.getRemovedProperties().size()).isEqualTo(removedProperties);
        assertThat(diff.getAddedPipelineReferences().size()).isEqualTo(addedPipelineReferences);
        assertThat(diff.getRemovedPipelineReferences().size()).isEqualTo(removedPipelineReferences);
        assertThat(diff.getAddedLinks().size()).isEqualTo(addedLinks);
        assertThat(diff.getRemovedLinks().size()).isEqualTo(removedLinks);
    }
}
