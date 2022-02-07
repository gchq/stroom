package stroom.app.docs;

import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.Element;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.commons.text.WordUtils;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Generate the content for content/en/docs/user-guide/pipelines/element-reference.md
 * in stroom-docs.
 * <p>
 * Produces something like this, with a H2 for each category and a H3 for each
 * element in that category.
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
 */
public class GeneratePipelineElementsDoc {

    private static final String PACKAGE_NAME = "stroom";

    public static void main(String[] args) {
        try (ScanResult scanResult =
                new ClassGraph()
                        .enableAllInfo()             // Scan classes, methods, fields, annotations
                        .whitelistPackages(PACKAGE_NAME)  // Scan com.xyz and subpackages (omit to scan all packages)
                        .scan()) {                   // Start the scan

            scanResult.getClassesImplementing(Element.class.getName())
                    .parallelStream()
                    .map(GeneratePipelineElementsDoc::mapClassInfo)
                    .filter(Objects::nonNull)
                    .filter(elementInfo -> !Category.INTERNAL.equals(elementInfo.category))
                    .sequential()
                    .collect(Collectors.groupingBy(ElementInfo::getCategory))
                    .entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .map(GeneratePipelineElementsDoc::mapCategoryGroup)
                    .forEach(System.out::println);
        }
    }

    private static String mapCategoryGroup(final Entry<Category, List<ElementInfo>> entry) {
        final Category category = entry.getKey();
        final List<ElementInfo> elementInfoList = entry.getValue();

        final String elementsText = elementInfoList.stream()
                .sorted(Comparator.comparing(ElementInfo::getType))
                .map(elementInfo -> {

                    final String descriptionText = elementInfo.description != null
                            ? elementInfo.description
                            : "> TODO - Add description";

                    final String iconText = elementInfo.iconFilename != null
                            ? LogUtil.message("""
                                    \n\n**Icon:** {{< stroom-icon "pipeline/{}" "{}" >}}""",
                            elementInfo.iconFilename,
                            elementInfo.type)
                            : "";

                    final String rolesText = !elementInfo.roles.isEmpty()
                            ? ("\n\n**Roles:**\n" + elementInfo.roles
                            .stream()
                            .sorted()
                            .map(WordUtils::capitalize)
                            .map(role -> "\n* " + role)
                            .collect(Collectors.joining()))
                            : "";

                    final String propsText;
                    if (!elementInfo.propertyInfoList.isEmpty()) {
                        propsText = "\n\n**Properties:**\n\n" + AsciiTable.builder(elementInfo.propertyInfoList)
                                .withColumn(Column.builder("Name", PropertyInfo::getName)
                                        .build())
                                .withColumn(Column.builder("Description", PropertyInfo::getDescription)
                                        .build())
                                .withColumn(Column.builder("Default Value", (PropertyInfo propInfo) ->
                                        propInfo.getDefaultValue().isEmpty()
                                                ? "-"
                                                : propInfo.getDefaultValue())
                                        .build())
                                .build();
                    } else {
                        propsText = "";
                    }

                    final String template = """
                            ### {}

                            {}

                            **Category:** {}{}{}{}

                            """;

                    return LogUtil.message(
                            template,
                            elementInfo.getType(),
                            elementInfo.description,
                            elementInfo.category.getDisplayValue(),
                            iconText,
                            rolesText,
                            propsText
                    );
                })
                .collect(Collectors.joining("\n"));
        return LogUtil.message("""
                ## {}

                {}
                """, category.getDisplayValue(), elementsText);
    }

    @Nullable
    private static ElementInfo mapClassInfo(final ClassInfo elementClassInfo) {
        final Class<?> clazz = elementClassInfo.loadClass();
        if (!clazz.isInterface()
                && !Modifier.isAbstract(clazz.getModifiers())
                && !clazz.getSimpleName().startsWith("Abstract")
                && clazz.isAnnotationPresent(ConfigurableElement.class)) {

            final ConfigurableElement elementAnno = clazz.getAnnotation(ConfigurableElement.class);

            final String description = getStringValue(elementAnno.description());
            final String iconFileName = getStringValue(elementAnno.icon());
            final String type = getStringValue(elementAnno.type());
            final Category category = elementAnno.category();
            final Set<String> roles = new HashSet<>(Arrays.asList(elementAnno.roles()));
            final List<PropertyInfo> propertyInfoList = getPropertyInfoList(clazz);

            return new ElementInfo(type, iconFileName, category, description, roles, propertyInfoList);
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
                .map(method -> {
                    final PipelineProperty pipelinePropertyAnno = method.getAnnotation(PipelineProperty.class);
                    final String name = makePropertyName(method.getName());
                    return new PropertyInfo(
                            name,
                            pipelinePropertyAnno.description(),
                            pipelinePropertyAnno.defaultValue());
                })
                .sorted(Comparator.comparing(PropertyInfo::getName))
                .collect(Collectors.toList());
    }

    private static String makePropertyName(final String methodName) {
        // Convert the setter to a camel case property name.
        String name = methodName.substring(3);
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return name;
    }

    private static class ElementInfo {

        private final String type;
        private final String iconFilename;
        private final Category category;
        private final String description;
        private final Set<String> roles;
        private final List<PropertyInfo> propertyInfoList;

        public ElementInfo(final String type,
                           final String iconFilename,
                           final Category category,
                           final String description,
                           final Set<String> roles,
                           final List<PropertyInfo> propertyInfoList) {
            this.type = type;
            this.iconFilename = iconFilename;
            this.category = category;
            this.description = description;
            this.roles = roles;
            this.propertyInfoList = propertyInfoList;
        }

        public String getType() {
            return type;
        }

        public Category getCategory() {
            return category;
        }

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

    private static class PropertyInfo {

        private final String name;
        private final String description;
        private final String defaultValue;

        public PropertyInfo(final String name,
                            final String description,
                            final String defaultValue) {
            this.name = name;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String toString() {
            return "PropertyInfo{" +
                    "name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", defaultValue='" + defaultValue + '\'' +
                    '}';
        }
    }
}
