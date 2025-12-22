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

package stroom.pipeline.structure.testclient.presenter;


import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.structure.client.presenter.DefaultPipelineTreeBuilder;
import stroom.pipeline.structure.client.presenter.PipelineElementTypes;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineModel {

    private static final PipelineElementType ELEM_TYPE = new PipelineElementType(
            "TestElement",
            "Test Element",
            null,
            new String[]{PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, null);
    private static final PipelinePropertyType PROP_TYPE1 = PipelinePropertyType.builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty1")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE2 = PipelinePropertyType.builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty2")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE3 = PipelinePropertyType.builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty3")
            .type("String")
            .build();
    private static final PipelinePropertyType PROP_TYPE4 = PipelinePropertyType.builder()
            .elementType(ELEM_TYPE)
            .name("TestProperty4")
            .type("String")
            .build();
    private static final DocRef BASE = new DocRef(PipelineDoc.TYPE, "base", "base");
    private static final DocRef OVERRIDE = new DocRef(PipelineDoc.TYPE, "override", "override");
    private static final DocRef SINGLE = new DocRef(PipelineDoc.TYPE, "single", "single");

    @Test
    void testBasic() {
        test(null, null, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testSimple() {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(ELEM_TYPE, "test1");
        builder.addElement(ELEM_TYPE, "test2");
        builder.addLink("test1", "test2");

        test(null, new PipelineLayer(SINGLE, builder.build()), 2, 0, 0, 0, 0, 0, 1, 0);
    }

    @Test
    void testComplex() {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(ELEM_TYPE, "test1");
        builder.addElement(ELEM_TYPE, "test2");
        builder.addElement(ELEM_TYPE, "test3");
        builder.addElement(ELEM_TYPE, "test4");
        builder.addLink("test1", "test2");
        builder.addLink("test2", "test3");
        builder.addLink("test3", "test4");
        builder.removeElement(ELEM_TYPE, "test4");

        test(null, new PipelineLayer(SINGLE, builder.build()), 3, 0, 0, 0, 0, 0, 2, 0);
    }

    @Test
    void testComplexWithProperties() {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(ELEM_TYPE, "test1");
        builder.addElement(ELEM_TYPE, "test2");
        builder.addElement(ELEM_TYPE, "test3");
        builder.addElement(ELEM_TYPE, "test4");
        builder.addLink("test1", "test2");
        builder.addLink("test2", "test3");
        builder.addLink("test3", "test4");
        builder.addProperty("test1", PROP_TYPE1, true);
        builder.addProperty("test2", PROP_TYPE2, true);
        builder.addProperty("test3", PROP_TYPE3, true);
        builder.addProperty("test4", PROP_TYPE4, true);
        builder.removeElement(ELEM_TYPE, "test4");

        test(null, new PipelineLayer(SINGLE, builder.build()), 3, 0, 3, 0, 0, 0, 2, 0);
    }

    @Test
    void testComplexWithPropRemove() {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(ELEM_TYPE, "test1");
        builder.addElement(ELEM_TYPE, "test2");
        builder.addElement(ELEM_TYPE, "test3");
        builder.addElement(ELEM_TYPE, "test4");
        builder.addLink("test1", "test2");
        builder.addLink("test2", "test3");
        builder.addLink("test3", "test4");
        builder.addProperty("test1", PROP_TYPE1, true);
        builder.addProperty("test2", PROP_TYPE2, true);
        builder.addProperty("test3", PROP_TYPE3, true);
        builder.addProperty("test4", PROP_TYPE4, true);
        builder.removeProperty("test2", PROP_TYPE2);
        builder.removeElement(ELEM_TYPE, "test4");

        test(null, new PipelineLayer(SINGLE, builder.build()), 3, 0, 2, 1, 0, 0, 2, 0);
    }

    @Test
    void testUnknownElement() {
        final PipelineDataBuilder builder = new PipelineDataBuilder();
        builder.addElement(ELEM_TYPE, "test");
        builder.addLink("unknown", "test");

        test(null, new PipelineLayer(SINGLE, builder.build()), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testInheritanceAdditive() {
        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addElement(ELEM_TYPE, "test3");
        override.addLink("test2", "test3");

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 1, 0, 0, 0, 0, 0, 1, 0);
    }

    @Test
    void testInheritanceRemove() {
        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addElement(ELEM_TYPE, "test3");
        override.removeElement(ELEM_TYPE, "test2");
        override.addLink("test2", "test3");

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void testInheritanceRemoveRejoin() {
        final PipelineElementType sourceType = new PipelineElementType(
                "Source",
                "Source",
                null,
                new String[]{
                        PipelineElementType.ROLE_SOURCE,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.VISABILITY_SIMPLE},
                SvgImage.PIPELINE_STREAM);
        final PipelineElementType parserType = new PipelineElementType(
                "CombinedParser",
                "Combined Parser",
                Category.PARSER,
                new String[]{
                        PipelineElementType.ROLE_PARSER,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.VISABILITY_SIMPLE,
                        PipelineElementType.VISABILITY_STEPPING,
                        PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.ROLE_HAS_CODE},
                SvgImage.PIPELINE_TEXT);
        final PipelineElementType xsltType = new PipelineElementType(
                "XSLTFilter",
                "XSLT Filter",
                Category.FILTER,
                new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.VISABILITY_SIMPLE,
                        PipelineElementType.VISABILITY_STEPPING,
                        PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.ROLE_HAS_CODE},
                SvgImage.PIPELINE_XSLT);
        final PipelineElementType schemaType = new PipelineElementType(
                "SchemaFilter",
                "Schema Filter",
                Category.FILTER,
                new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.VISABILITY_STEPPING,
                        PipelineElementType.ROLE_VALIDATOR},
                SvgImage.PIPELINE_XSD);
        final PipelineElementType writerType = new PipelineElementType(
                "XMLWriter",
                "XML Writer",
                Category.WRITER,
                new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.ROLE_WRITER,
                        PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.VISABILITY_STEPPING},
                SvgImage.PIPELINE_XML);
        final PipelineElementType appenderType = new PipelineElementType(
                "StreamAppender",
                "Stream Appender",
                Category.DESTINATION,
                new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_DESTINATION,
                        PipelineElementType.VISABILITY_STEPPING},
                SvgImage.PIPELINE_STREAM);


        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(sourceType, "Source");
        base.addElement(parserType, "combinedParser");
        base.addElement(xsltType, "xsltFilter1");
        base.addElement(xsltType, "xsltFilter2");
        base.addElement(xsltType, "xsltFilter3");
        base.addElement(schemaType, "schemaFilter");
        base.addElement(writerType, "xmlWriter");
        base.addElement(appenderType, "streamAppender");
        base.addLink("Source", "combinedParser");
        base.addLink("combinedParser", "xsltFilter1");
        base.addLink("xsltFilter1", "xsltFilter2");
        base.addLink("xsltFilter2", "xsltFilter3");
        base.addLink("xsltFilter3", "schemaFilter");
        base.addLink("schemaFilter", "xmlWriter");
        base.addLink("xmlWriter", "streamAppender");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.removeElement(xsltType, "xsltFilter2");
        override.addLink("xsltFilter1", "schemaFilter");
        override.removeLink("xsltFilter1", "xsltFilter2");

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 1, 0, 0, 0, 0, 1, 1);
    }

    @Test
    void testInheritancePropertiesSame() {
        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addProperty("test1", PROP_TYPE1, false);

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 1, 0, 0, 0, 0, 0);
    }

    @Test
    void testInheritancePropertiesDiff() {
        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addProperty("test1", PROP_TYPE1, true);

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 1, 0, 0, 0, 0, 0);
    }

    @Test
    void testInheritancePropertiesRemove() {
        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addProperty("test1", PROP_TYPE1, false);
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.removeProperty("test1", PROP_TYPE1);

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 0, 1, 0, 0, 0, 0);
    }

    @Test
    void testInheritanceRefsSame() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.TYPE, "1");

        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void testInheritanceRefsDiff() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.TYPE, "1");

        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.REFERENCE));

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void testInheritanceRefsRemove() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "1");
        final DocRef feed = new DocRef(FeedDoc.TYPE, "1");

        final List<PipelineLayer> baseStack = new ArrayList<>();

        final PipelineDataBuilder base = new PipelineDataBuilder();
        base.addElement(ELEM_TYPE, "test1");
        base.addElement(ELEM_TYPE, "test2");
        base.addPipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));
        base.addLink("test1", "test2");
        baseStack.add(new PipelineLayer(BASE, base.build()));

        final PipelineDataBuilder override = new PipelineDataBuilder();
        override.removePipelineReference(
                PipelineDataUtil.createReference("test1", "testProp", pipeline, feed, StreamTypeNames.EVENTS));

        test(baseStack, new PipelineLayer(OVERRIDE, override.build()), 0, 0, 0, 0, 0, 1, 0, 0);
    }

    @Test
    void testMove1() {
        final DefaultPipelineTreeBuilder builder = new DefaultPipelineTreeBuilder();

        final Map<String, PipelineElementType> elementTypesByTypeName = new HashMap<>();
        final PipelineElement source = createElement(elementTypesByTypeName, "Source", null, "Source");
        final PipelineElement combinedParser = createElement(elementTypesByTypeName,
                "CombinedParser",
                Category.PARSER,
                "combinedParser");
        final PipelineElement findReplaceFilter = createElement(elementTypesByTypeName, "FindReplaceFilter",
                Category.READER,
                "FindReplaceFilter");

        final PipelineDataBuilder pipelineData = new PipelineDataBuilder();
//        pipelineData.addElement(sourceElementType, "Source");
        pipelineData.addElement(combinedParser);
        pipelineData.addElement(findReplaceFilter);

        pipelineData.addLink(source, combinedParser);
        pipelineData.addLink(source, findReplaceFilter);

        final PipelineElementTypes elementTypes = new PipelineElementTypes(elementTypesByTypeName);
        final PipelineModel pipelineModel = new PipelineModel(elementTypes);
        pipelineModel.setBaseStack(null);
        pipelineModel.setPipelineLayer(
                new PipelineLayer(SINGLE, pipelineData.build()));
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

        final Map<String, PipelineElementType> elementTypesByTypeName = new HashMap<>();
        final PipelineElement source = createElement(elementTypesByTypeName, "Source", null, "Source");
        final PipelineElement xmlParser = createElement(elementTypesByTypeName,
                "XMLParser",
                Category.PARSER,
                "xmlParser");
        final PipelineElement idEnrichmentFilter = createElement(elementTypesByTypeName, "IDEnrichmentFilter",
                Category.FILTER,
                "idEnrichmentFilter");
        final PipelineElement xsltFilter = createElement(elementTypesByTypeName,
                "XSLTFilter",
                Category.FILTER,
                "xsltFilter");

        final PipelineDataBuilder base = new PipelineDataBuilder();
//        pipelineData.addElement(sourceElementType, "Source");
        base.addElement(xmlParser);
        base.addElement(idEnrichmentFilter);
        base.addElement(xsltFilter);

        base.addLink(source, xmlParser);
        base.addLink(xmlParser, idEnrichmentFilter);
        base.addLink(idEnrichmentFilter, xsltFilter);

        final PipelineElementTypes elementTypes = new PipelineElementTypes(elementTypesByTypeName);
        final PipelineModel pipelineModel = new PipelineModel(elementTypes);
        pipelineModel.setBaseStack(Collections.singletonList(new PipelineLayer(BASE, base.build())));
        pipelineModel.setPipelineLayer(new PipelineLayer(OVERRIDE, new PipelineDataBuilder().build()));
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

        final Map<String, PipelineElementType> elementTypesByTypeName = new HashMap<>();
        final PipelineElement source = createElement(elementTypesByTypeName, "Source", null, "Source");
        final PipelineElement xmlParser = createElement(elementTypesByTypeName,
                "XMLParser",
                Category.PARSER,
                "xmlParser");
        final PipelineElement idEnrichmentFilter = createElement(elementTypesByTypeName, "IDEnrichmentFilter",
                Category.FILTER,
                "idEnrichmentFilter");
        final PipelineElement xsltFilter = createElement(elementTypesByTypeName,
                "XSLTFilter",
                Category.FILTER,
                "xsltFilter");
//        final PipelineElement xsltFilter2 = createElement("XSLTFilter", Category.FILTER, "xsltFilter2");

        final PipelineDataBuilder base = new PipelineDataBuilder();
//        pipelineData.addElement(sourceElementType, "Source");
        base.addElement(xmlParser);
        base.addElement(idEnrichmentFilter);
        base.addElement(xsltFilter);

        base.addLink(source, xmlParser);
        base.addLink(xmlParser, idEnrichmentFilter);
        base.addLink(idEnrichmentFilter, xsltFilter);

        final PipelineElementTypes elementTypes = new PipelineElementTypes(elementTypesByTypeName);
        final PipelineModel pipelineModel = new PipelineModel(elementTypes);
        pipelineModel.setBaseStack(Collections.singletonList(new PipelineLayer(BASE, base.build())));
        pipelineModel.setPipelineLayer(new PipelineLayer(OVERRIDE, new PipelineDataBuilder().build()));
        pipelineModel.build();

        final DefaultTreeForTreeLayout<PipelineElement> tree1 = builder.getTree(pipelineModel);
        checkChildren(tree1, source, new PipelineElement[]{xmlParser});
        checkChildren(tree1, xmlParser, new PipelineElement[]{idEnrichmentFilter});
        checkChildren(tree1, idEnrichmentFilter, new PipelineElement[]{xsltFilter});

        final PipelineElement xsltFilter2 = pipelineModel.addElement(idEnrichmentFilter,
                createType("XSLTFilter", Category.FILTER),
                "xsltFilter2", "xsltFilter2", null);
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

    private void checkChildren(final DefaultTreeForTreeLayout<PipelineElement> tree,
                               final PipelineElement parent,
                               final PipelineElement[] children) {
        final List<PipelineElement> list = tree.getChildren(parent);
        if (children.length == 0) {
            assertThat(list == null || list.isEmpty()).isTrue();
        } else {
            assertThat(list.size()).isEqualTo(children.length);
            for (final PipelineElement child : children) {
                assertThat(list.contains(child)).isTrue();
            }
        }
    }

    private PipelineElement createElement(final Map<String, PipelineElementType> elementTypesByTypeName,
                                          final String type,
                                          final Category category,
                                          final String id) {
        final PipelineElementType elementType = createType(type, category);
        final PipelineElement element = new PipelineElement(id, elementType.getType());
        elementTypesByTypeName.put(elementType.getType(), elementType);
        return element;
    }

    private PipelineElementType createType(final String type, final Category category) {
        String[] roles = null;
        if (category == null) {
            roles = new String[]{
                    PipelineElementType.ROLE_SOURCE,
                    PipelineElementType.ROLE_HAS_TARGETS,
                    PipelineElementType.VISABILITY_SIMPLE};
        } else {
            roles = switch (category) {
                case READER -> new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_HAS_TARGETS,
                        PipelineElementType.ROLE_READER,
                        PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.VISABILITY_STEPPING};
                case PARSER -> new String[]{
                        PipelineElementType.ROLE_PARSER,
                        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
                        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.ROLE_HAS_CODE};
                case FILTER -> new String[]{
                        PipelineElementType.ROLE_TARGET,
                        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
                        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR,
                        PipelineElementType.ROLE_HAS_CODE};
                default -> roles;
            };
        }

        return new PipelineElementType(
                type,
                type,
                category,
                roles, null);
    }

    private void test(final List<PipelineLayer> baseStack,
                      final PipelineLayer pipelineLayer,
                      final int addedElements,
                      final int removedElements,
                      final int addedProperties,
                      final int removedProperties,
                      final int addedPipelineReferences,
                      final int removedPipelineReferences,
                      final int addedLinks,
                      final int removedLinks) throws PipelineModelException {
        final PipelineModel pipelineModel = new PipelineModel(new PipelineElementTypes(Collections.emptyMap()));
        pipelineModel.setBaseStack(baseStack);
        pipelineModel.setPipelineLayer(pipelineLayer);
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
