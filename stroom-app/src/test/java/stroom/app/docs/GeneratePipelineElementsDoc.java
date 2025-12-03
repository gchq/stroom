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

package stroom.app.docs;

import stroom.docref.DocRef;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.Element;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.filter.XsltFilter;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.source.SourceElement;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generate the content for
 * content/en/docs/user-guide/pipelines/element-reference.md
 * and
 * layouts/shortcodes/pipe-elm.html in stroom-docs.
 * <p>
 * generatePipelineElementReferenceContent produces something like this, with
 * a H2 for each category and a H3 for each element in that category.
 * <p>
 * Once the doc has been amended with descriptions you will prob need to run this
 * the diff the output against the stroom-docs file to merge new/changed elements
 * over.
 *
 * <pre>
 * ## Reader
 *
 * ### BOMRemovalFilterInput
 *
 * Icon: {{< stroom-icon "pipeline/stream.svg" >}}
 *
 * Roles:
 *
 * * HasTargets
 * * Mutator
 * * Reader
 * * Stepping
 * </pre>
 *
 * <p>
 * generatePipelineElementReferenceContent produces something like this, with
 */
public class GeneratePipelineElementsDoc implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GeneratePipelineElementsDoc.class);

    private static final String MISSING_CATEGORY_DESCRIPTION = "> TODO - Add description";

    private static final Path ELEMENENT_REFERENCE_SUB_PATH = Paths.get(
            "content/en/docs/reference-section/pipeline-elements.md");
    private static final Path PIPE_ELM_SHORT_CODE_SUB_PATH = Paths.get(
            "layouts/shortcodes/pipe-elm.html");

    private static final Map<Class<?>, String> ARG_TYPE_TO_DISPLAY_NAME_MAP = Map.of(
            DocRef.class, "Document",
            Integer.class, "Integer",
            String.class, "String",
            boolean.class, "Boolean",
            int.class, "Integer"
    );

    private static final Set<Entry<Class<?>, String>> EXCLUDED_METHODS = Set.of(
            // This one is not really a prop and needs special explanation.
            Map.entry(XsltFilter.class, "setPipelineReference")
    );

    @GeneratesDocumentation
    public static void main(final String[] args) {
        final GeneratePipelineElementsDoc generatePipelineElementsDoc = new GeneratePipelineElementsDoc();

        generatePipelineElementsDoc.generatePipelineElementReferenceContent();
        generatePipelineElementsDoc.generatePipeElmShortcodeNames();
    }

    @Override
    public void generateAll(final ScanResult scanResult) {
        generatePipelineElementReferenceContent(scanResult);
        generatePipeElmShortcodeNames(scanResult);
    }


    /**
     * This will modify the content of the file
     * {@code [this repo]/../stroom-docs/content/en/docs/user-guide/pipelines/element-reference.md}
     */
    @Disabled // Manual only
    @GeneratesDocumentation
    @Test
    void generatePipelineElementReferenceContent() {
        StroomDocsUtil.doWithClassScanResult(this::generatePipelineElementReferenceContent);
    }

    void generatePipelineElementReferenceContent(final ScanResult scanResult) {
        final String generatedContent = scanResult.getClassesImplementing(Element.class.getName())
                .parallelStream()
                .map(GeneratePipelineElementsDoc::mapClassInfo)
                .filter(Objects::nonNull)
                .filter(elementInfo -> !Category.INTERNAL.equals(elementInfo.category))
                .sequential()
                .collect(Collectors.groupingBy(ElementInfo::category))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(GeneratePipelineElementsDoc::mapCategoryGroup)
                .collect(Collectors.joining("\n"));

        final Path file = StroomDocsUtil.resolveStroomDocsFile(ELEMENENT_REFERENCE_SUB_PATH);

        final boolean didReplace = StroomDocsUtil.replaceGeneratedContent(file, generatedContent);

        if (didReplace) {
            LOGGER.info("Replaced generated content in file: {}", file);
        } else {
            LOGGER.warn("No change made to file: {}", file);
        }
    }

    /**
     * This will modify the content of the file
     * {@code [this repo]/../stroom-docs/layouts/shortcodes/pipe-elm.html}
     */
    @Disabled // Manual only
    @GeneratesDocumentation
    @Test
    void generatePipeElmShortcodeNames() {
        StroomDocsUtil.doWithClassScanResult(this::generatePipeElmShortcodeNames);
    }

    void generatePipeElmShortcodeNames(final ScanResult scanResult) {
        final String dictContent = scanResult.getClassesImplementing(Element.class.getName())
                .parallelStream()
                .map(GeneratePipelineElementsDoc::mapClassInfo)
                .filter(Objects::nonNull)
                .filter(elementInfo ->
                        !Category.INTERNAL.equals(elementInfo.category)
                                || SourceElement.class.equals(elementInfo.clazz))
                .sequential()
                .sorted(Comparator.comparing(ElementInfo::type))
                .map(elementInfo -> {
                    final String template = "\"{}\" \"{}\"";
                    return LogUtil.message(template, elementInfo.type, elementInfo.iconFilename);
                })
                .collect(Collectors.joining("\n"));

        final String dictDefinition = "{{ $element_map := dict\n" + dictContent + "\n}}";

        final Path file = StroomDocsUtil.resolveStroomDocsFile(PIPE_ELM_SHORT_CODE_SUB_PATH);

        final boolean didReplace = StroomDocsUtil.replaceGeneratedContent(file, dictDefinition);

        if (didReplace) {
            LOGGER.info("Replaced generated content in file: {}", file);
        } else {
            LOGGER.info("No change made to file: {}", file);
        }
    }

    private static String mapCategoryGroup(final Entry<Category, List<ElementInfo>> entry) {
        final Category category = entry.getKey();
        final List<ElementInfo> elementInfoList = entry.getValue();

        final String elementsText = convertElementsToText(elementInfoList);
        final String categoryDescription = Optional.ofNullable(category.getDescription())
                .orElse(MISSING_CATEGORY_DESCRIPTION);

        return LogUtil.message("""
                        ## {}

                        {}

                        {}
                        """,
                category.getDisplayValue(),
                categoryDescription,
                elementsText);
    }

    private static String convertElementsToText(final List<ElementInfo> elementInfoList) {
        final String elementsText = elementInfoList.stream()
                .sorted(Comparator.comparing(ElementInfo::type))
                .map(elementInfo -> {

                    final String descriptionText = elementInfo.description != null
                            ? elementInfo.description
                            : "> TODO - Add description";

                    // Add the &nbsp; at the end so the markdown processor treats the line as a <p>
                    final String iconText = elementInfo.iconFilename != null
                            ? LogUtil.message("""
                            {{< pipe-elm "{}" >}}&nbsp;""", elementInfo.type)
                            : "";

                    final String rolesText = buildRolesText(elementInfo.roles);
//                    final String rolesText = !elementInfo.roles.isEmpty()
//                            ? ("\n\n**Roles:**\n" + elementInfo.roles
//                            .stream()
//                            .sorted()
//                            .map(WordUtils::capitalize)
//                            .map(role -> "\n* " + role)
//                            .collect(Collectors.joining()))
//                            : "";

                    final String propsText;
                    if (!elementInfo.propertyInfoList.isEmpty()) {
                        propsText = "\n\n**Element properties:**\n\n" + AsciiTable.builder(elementInfo.propertyInfoList)
                                .withColumn(Column.builder("Name", PropertyInfo::name)
                                        .build())
                                .withColumn(Column.builder("Description", PropertyInfo::description)
                                        .build())
                                .withColumn(Column.builder("Default Value", (PropertyInfo propInfo) ->
                                                propInfo.defaultValue().isEmpty()
                                                        ? "-"
                                                        : propInfo.defaultValue())
                                        .build())
                                .withColumn(Column.builder("Value Type", (PropertyInfo propInfo) ->
                                                propInfo.argTypeStr().isEmpty()
                                                        ? "-"
                                                        : propInfo.argTypeStr())
                                        .build())
                                .build();
                    } else {
                        propsText = "";
                    }

                    final String template = """
                            ### {}

                            {}

                            {}{}

                            """;

                    return LogUtil.message(
                            template,
                            elementInfo.type(),
                            iconText,
                            descriptionText,
//                            elementInfo.category.getDisplayValue(),
//                            rolesText,
                            propsText
                    );
                })
                .collect(Collectors.joining("\n"));
        return elementsText;
    }

    private static String buildRolesText(final Set<String> roles) {
        final Function<String, String> hasRole = roleName ->
                roles.contains(roleName)
                        ? "Yes"
                        : "No";

        final List<Tuple2<String, String>> data = List.of(
                Tuple.of("Can be stepped:", hasRole.apply(PipelineElementType.VISABILITY_STEPPING)),
                Tuple.of("Transforms input:", hasRole.apply(PipelineElementType.ROLE_MUTATOR)),
                Tuple.of("Validates input:", hasRole.apply(PipelineElementType.ROLE_MUTATOR))
        );
        return "\n\n**Features**:\n\n" + AsciiTable.builder(data)
                .withColumn(Column.builder("Feature", (Tuple2<String, String> tuple) -> tuple._1()).build())
                .withColumn(Column.builder("Yes/No", (Tuple2<String, String> tuple) -> tuple._2).build())
                .build();
    }

    private static ElementInfo mapClassInfo(final ClassInfo elementClassInfo) {
        final Class<? extends Element> clazz = (Class<? extends Element>) elementClassInfo.loadClass();
        if (!clazz.isInterface()
                && !Modifier.isAbstract(clazz.getModifiers())
                && !clazz.getSimpleName().startsWith("Abstract")
                && clazz.isAnnotationPresent(ConfigurableElement.class)) {

            final ConfigurableElement elementAnno = clazz.getAnnotation(ConfigurableElement.class);

            final String description = getStringValue(elementAnno.description());
            final String iconFileName = getStringValue(elementAnno.icon().getRelativePathStr());
            final String type = getStringValue(elementAnno.type());
            final Category category = elementAnno.category();
            final Set<String> roles = new HashSet<>(Arrays.asList(elementAnno.roles()));
            final List<PropertyInfo> propertyInfoList = getPropertyInfoList(clazz);

            return new ElementInfo(clazz, type, iconFileName, category, description, roles, propertyInfoList);
        } else {
            return null;
        }
    }

    private static String getStringValue(final String value) {
        if (value == null || value.isEmpty() || value.isBlank()) {
            return null;
        } else {
            return value;
        }
    }

    private static List<PropertyInfo> getPropertyInfoList(final Class<?> clazz) {

        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.isAnnotationPresent(PipelineProperty.class))
                .filter(Predicate.not(GeneratePipelineElementsDoc::isMethodExcluded))
                .map(method -> {
                    final PipelineProperty pipelinePropertyAnno = method.getAnnotation(PipelineProperty.class);
                    final Class<?> argType = method.getParameterTypes()[0];

                    final String argTypeStr = Objects.requireNonNull(ARG_TYPE_TO_DISPLAY_NAME_MAP.get(argType),
                            () ->
                                    "No display name mapped for return type: "
                                            + argType.getName()
                                            + " on method: " + method.getName()
                                            + " on class: " + clazz.getSimpleName());

                    final String name = makePropertyName(method.getName());
                    return new PropertyInfo(
                            name,
                            pipelinePropertyAnno.description(),
                            pipelinePropertyAnno.defaultValue(),
                            argTypeStr);
                })
                .sorted(Comparator.comparing(PropertyInfo::name))
                .toList();
    }

    private static String makePropertyName(final String methodName) {
        // Convert the setter to a camel case property name.
        String name = methodName.substring(3);
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return name;
    }

    private static boolean isMethodExcluded(final Method method) {
        return EXCLUDED_METHODS.contains(Map.entry(method.getDeclaringClass(), method.getName()));
    }


    // --------------------------------------------------------------------------------


    private record ElementInfo(
            Class<? extends Element> clazz,
            String type,
            String iconFilename,
            Category category,
            String description,
            Set<String> roles,
            List<PropertyInfo> propertyInfoList
    ) {

        @Override
        public String toString() {
            return "ElementInfo{" +
                    "type='" + type + '\'' +
                    ", iconFilename='" + iconFilename + '\'' +
                    ", category=" + category +
                    ", description='" + description + '\'' +
                    ", roles=" + roles +
                    ", propertyInfoList=" + propertyInfoList +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    private record PropertyInfo(
            String name,
            String description,
            String defaultValue,
            String argTypeStr) {

        @Override
        public String toString() {
            return "PropertyInfo{" +
                    "name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", defaultValue='" + defaultValue + '\'' +
                    ", returnTypeStr='" + argTypeStr + '\'' +
                    '}';
        }
    }
}
