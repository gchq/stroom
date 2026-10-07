package stroom.app.docs;


import stroom.docs.shared.NotDocumented;
import stroom.pipeline.xsltfunctions.XsltDataType;
import stroom.pipeline.xsltfunctions.XsltFunctionCategory;
import stroom.pipeline.xsltfunctions.XsltFunctionDef;
import stroom.pipeline.xsltfunctions.XsltFunctionSignature;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// This class generates documentation for all the XSLT functions, using the annotations
/// on the functions. For each function, it generates a JSON file containing the annotation data.
/// This JSON data can be read by a Hugo shortcode in the docs (e.g `{{< xslt-func "format-dateTime" >}}`).
///
/// Each category of XSLT function has its own doc page. There is also an index page that lists all the functions.
/// This class checks that each category page contains the shortcodes for all of the functions in that category.
/// It also checks that the main index page contains the links for all the functions.
/// It is up to the human to add the shortcodes and links to the doc pages, but this class will output
/// what is missing so it is just a copy/paste job.
///
/// The reason for not automating the updating of the doc pages is that it allows the doc page to be manually
/// edited to add extra information that is not really suitable for a java annotation.
public class GenerateXsltFunctionDefinitions implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateXsltFunctionDefinitions.class);

    private static final Path DOCS_SUB_PATH = Paths.get(
            "content/en/docs/reference-section/xslt-functions");
    private static final Path DATA_SUB_PATH = Paths.get(
            "assets/data/xslt-functions");
    private static final String INDEX_DATA_FILENAME = "_index.json";
    private static final String INDEX_DOC_FILENAME = "_index.md";

    private final JsonMapper objectMapper;

    @GeneratesDocumentation
    public static void main(String[] args) {
        new GenerateXsltFunctionDefinitions().generate();
    }

    public GenerateXsltFunctionDefinitions() {
        this.objectMapper = JsonUtil.getMapper();
    }

    @Override
    @GeneratesDocumentation
    public void generateAll(final ScanResult scanResult) {
        try {
            final Path outputPath = StroomDocsUtil.resolveStroomDocsFile(DATA_SUB_PATH, false);
            Files.createDirectories(outputPath);
            LOGGER.info("Clearing dir {}", outputPath.toAbsolutePath().normalize());

            // Remove existing function files
            try (final Stream<Path> pathStream = Files.list(outputPath)) {
                pathStream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(ThrowingConsumer.unchecked(path -> {
                            LOGGER.debug("Deleting file {}", path.toAbsolutePath().normalize());
                            Files.delete(path);
                        }));
            }

            final List<AnnotatedClass> annotatedClasses = getAllFunctionDefs(scanResult);
            annotatedClasses.forEach(this::processFunction);
            produceIndexFile(annotatedClasses);
            LOGGER.info("All XSLT functions present in the documentation content");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void produceIndexFile(final List<AnnotatedClass> annotatedClasses) {

        final Map<XsltFunctionCategory, List<AnnotatedClass>> groups = annotatedClasses.stream()
                .collect(Collectors.groupingBy(annotatedClass -> {
                    final XsltFunctionCategory[] categories = annotatedClass.getAnnotation().commonCategory();
                    Objects.requireNonNull(categories, () -> LogUtil.message(
                            "function class {} should have a commonCategory",
                            annotatedClass.getClazz().getName()));
                    return categories[0];
                }));

        final Map<XsltFunctionCategory, XsltFunctionCategoryIndex> map = new HashMap<>();
        final AtomicInteger errorCounter = new AtomicInteger();
        groups.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry ->
                        entry.getKey().name()))
                .forEach(entry -> {
                    final XsltFunctionCategory category = entry.getKey();
                    final List<AnnotatedClass> classesGroup = entry.getValue();
                    final String docFilename = category.name()
                                                       .toLowerCase()
                                                       .replace("[^a-zA-Z0-9-]", "-") + ".md";
                    final XsltFunctionCategoryIndex index = map.computeIfAbsent(category,
                            k -> new XsltFunctionCategoryIndex(null, k, docFilename));
                    classesGroup.forEach(annotatedClass -> {
                        final String functionName = annotatedClass.getEffectiveName();
                        index.addFunction(functionName);
                    });
                    final int errorCount = checkDocPage(index);
                    errorCounter.addAndGet(errorCount);
                });

        try {
            final String json = JsonUtil.getMapper().writeValueAsString(map);
            LOGGER.debug("Index:\n{}", json);

            final Path outputFile = buildDataFilePath(INDEX_DATA_FILENAME);
            Files.writeString(outputFile, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        if (errorCounter.get() > 0) {
            throw new RuntimeException(LogUtil.message("There were {} errors, check the logs", errorCounter.get()));
        }
    }

    private int checkDocPage(final XsltFunctionCategoryIndex index) {
        try {
            // Check the _index.md file contains a link for each func with the appropriate category
            final Path indexDocFilePath = buildDocsFilePath(INDEX_DOC_FILENAME);
            if (!Files.isRegularFile(indexDocFilePath)) {
                throw new RuntimeException(LogUtil.message("File {} does not exist",
                        indexDocFilePath.toAbsolutePath()));
            }

            // Check the appropriate category file (e.g. conversion.md) contains a shortcode
            // for each of the funcs in that category.
            final Path categoryDocFilePath = buildDocsFilePath(index.getDocFilename());
            LOGGER.info("Checking category {} in page {}", index.category, indexDocFilePath.toAbsolutePath());
            if (!Files.isRegularFile(categoryDocFilePath)) {
                throw new RuntimeException(LogUtil.message("File {} does not exist",
                        indexDocFilePath.toAbsolutePath()));
            }

            final String categoryDocContent = Files.readString(categoryDocFilePath);

            final Set<FuncAndShortCode> missingShortCodes = index.functionNames
                    .stream()
                    .map(functionName -> {
                        final String shortCode = "{{< xslt-func \"" + functionName + "\" >}}";
                        return new FuncAndShortCode(functionName, shortCode);
                    })
                    .collect(Collectors.toCollection(HashSet::new));

            missingShortCodes.removeIf(funcAndShortCode ->
                    categoryDocContent.contains(funcAndShortCode.shortCode()));

            if (!missingShortCodes.isEmpty()) {
                LOGGER.error("File {} is missing content for the following functions. " +
                             "Each function should have a shortcode call like '{{< xslt-func \"hash\" >}}'.\n{}",
                        categoryDocFilePath.toAbsolutePath(),
                        missingShortCodes.stream()
                                .sorted(Comparator.comparing(FuncAndShortCode::funcName))
                                .map(FuncAndShortCode::toString)
                                .collect(Collectors.joining("\n\n")));
            }
            return missingShortCodes.size();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generate() {
        StroomDocsUtil.doWithClassScanResult(this::generateAll);
    }

    private List<AnnotatedClass> getAllFunctionDefs(final ScanResult scanResult) {
        try {
            // Ideally we would look for all subclasses of StroomExtensionFunctionCall but that is not
            // visible from here.  However, TestXsltFunctions will check that all subclasses of that
            // have the annotation
            return scanResult.getAllClasses()
                    .parallelStream()
                    .filter(classInfo -> classInfo.hasAnnotation(XsltFunctionDef.class))
                    .filter(classInfo -> !classInfo.hasAnnotation(NotDocumented.class))
                    .filter(Predicate.not(ClassInfo::isInterface))
                    .filter(Predicate.not(ClassInfo::isAbstract))
                    .map(ClassInfo::loadClass)
                    .flatMap(clazz -> {
                        final XsltFunctionDef anno = clazz.getAnnotation(XsltFunctionDef.class);
                        if (anno == null) {
                            LOGGER.error("XSLT Function {} is missing annotation {}",
                                    clazz.getName(),
                                    XsltFunctionDef.class.getName());
                            return null;
                        } else {
                            final String[] aliases = anno.aliases();
                            // Duplicate for each alias present
                            return Stream.concat(
                                            Stream.of(anno.name()),
                                            NullSafe.stream(aliases))
                                    .map(name -> new AnnotatedClass(clazz, anno, name));
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (final Exception e) {
            LOGGER.error("Error {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Path buildDataFilePath(final String filename) {
        return StroomDocsUtil.resolveStroomDocsFile(DATA_SUB_PATH.resolve(filename), false);
    }

    private Path buildDocsFilePath(final String filename) {
        return StroomDocsUtil.resolveStroomDocsFile(DOCS_SUB_PATH.resolve(filename), false);
    }

    private void processFunction(final AnnotatedClass annotatedClass) {
        try {
            final XsltFunctionDefPojo xsltFunctionDefPojo = annotatedClass.getXsltFunctionDefPojo();
            // Use the XsltFunctionDefPojo as that has the mutated name/aliases
            final String json = objectMapper.writeValueAsString(xsltFunctionDefPojo);
            final String filename = annotatedClass.getEffectiveName() + ".json";
            final Path filePath = buildDataFilePath(filename);
            LOGGER.debug("{} - {} - {}\n{}",
                    annotatedClass.getClazz().getName(),
                    filename,
                    filePath.toAbsolutePath(),
                    json);
            Files.writeString(filePath, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    ///
    private static final class AnnotatedClass {

        private final Class<?> clazz;
        private final XsltFunctionDef annotation;
        private final XsltFunctionDefPojo xsltFunctionDefPojo;
//        private final String effectiveName;
//        private final List<String> effectiveAlises;


        private AnnotatedClass(final Class<?> clazz,
                               final XsltFunctionDef annotation,
                               final String effectiveName) {
            this.clazz = clazz;
            this.annotation = annotation;
            this.xsltFunctionDefPojo = XsltFunctionDefPojo.create(annotation, effectiveName);
//            this.effectiveName = effectiveName;
//            if (Objects.equals(effectiveName, annotation.name())) {
//                this.effectiveAlises = NullSafe.asList(annotation.aliases());
//            } else {
//                // Acting under an alias so re-jig the aliases list to include the main
//                // name and exclude the alias we are under.
//                this.effectiveAlises = Stream.concat(
//                                Stream.of(effectiveName),
//                                NullSafe.stream(annotation.aliases()))
//                        .filter(name ->
//                                !Objects.equals(name, effectiveName))
//                        .toList();
//            }
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public XsltFunctionDef getAnnotation() {
            return annotation;
        }

        public String getEffectiveName() {
            return xsltFunctionDefPojo.getName();
        }

        public XsltFunctionDefPojo getXsltFunctionDefPojo() {
            return xsltFunctionDefPojo;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final AnnotatedClass that = (AnnotatedClass) o;
            return Objects.equals(clazz, that.clazz) && Objects.equals(annotation,
                    that.annotation) && Objects.equals(xsltFunctionDefPojo, that.xsltFunctionDefPojo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, annotation, xsltFunctionDefPojo);
        }

        @Override
        public String toString() {
            return "AnnotatedClass{" +
                   "clazz=" + clazz +
                   ", annotation=" + annotation +
                   ", xsltFunctionDefPojo=" + xsltFunctionDefPojo +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


//    @JsonPropertyOrder(alphabetic = true)
//    @JsonInclude(Include.NON_NULL)
//    private static class XsltFunctionIndex {
//
//        @JsonProperty
//        private final Map<XsltFunctionCategory, List<XsltFunctionIndexItem>> functions;
//
//        private XsltFunctionIndex(
//                @JsonProperty("functions") final Map<XsltFunctionCategory, List<XsltFunctionIndexItem>> functions) {
//            this.functions = functions;
//        }
//
//        public Map<XsltFunctionCategory, List<XsltFunctionIndexItem>> getFunctions() {
//            return functions;
//        }
//    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    private static class XsltFunctionIndexItem {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final XsltFunctionCategory category;
        private final String docFilename;

        private XsltFunctionIndexItem(@JsonProperty("name") final String name,
                                      @JsonProperty("category") final XsltFunctionCategory category,
                                      @JsonProperty("docFilename") final String docFilename) {
            this.name = name;
            this.category = category;
            this.docFilename = docFilename;
        }

        public String getName() {
            return name;
        }

        public XsltFunctionCategory getCategory() {
            return category;
        }

        public String getDocFilename() {
            return docFilename;
        }
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    private static class XsltFunctionCategoryIndex {

        @JsonProperty
        private final List<String> functionNames;
        @JsonProperty
        private final XsltFunctionCategory category;
        @JsonProperty
        private final String docFilename;

        private XsltFunctionCategoryIndex(@JsonProperty("name") final List<String> functionNames,
                                          @JsonProperty("category") final XsltFunctionCategory category,
                                          @JsonProperty("docFilename") final String docFilename) {
            this.functionNames = NullSafe.mutableList(functionNames);
            this.category = category;
            this.docFilename = docFilename;
        }

        public List<String> getFunctionNames() {
            return functionNames;
        }

        private void addFunction(final String functionName) {
            this.functionNames.add(functionName);
        }

        public XsltFunctionCategory getCategory() {
            return category;
        }

        public String getDocFilename() {
            return docFilename;
        }
    }


    // --------------------------------------------------------------------------------


    private record FuncAndShortCode(String funcName, String shortCode) {

        @Override
        public String toString() {
            return LogUtil.message("""
                    ## {}

                    {}
                    """, funcName, shortCode);
        }
    }


    /// We need this class, which is basically the same as {@link XsltFunctionDef} so that
    /// we can instantiate a new one as a modified copy of an {@link XsltFunctionDef} instance.
    private static class XsltFunctionDefPojo {

        @JsonProperty("name")
        private final String name;

        @JsonProperty("aliasOf")
        private final String aliasOf;

        @JsonProperty("helpAnchor")
        private final String helpAnchor;

        @JsonProperty("aliases")
        private final String[] aliases;

        @JsonProperty("commonCategory")
        private final XsltFunctionCategory[] commonCategory;

        @JsonProperty("commonSubCategories")
        private final String[] commonSubCategories;

        @JsonProperty("commonDescription")
        private final String commonDescription;

        @JsonProperty("commonReturnType")
        private final XsltDataType[] commonReturnType;

        @JsonProperty("commonReturnDescription")
        private final String commonReturnDescription;

        @JsonProperty("signatures")
        private final XsltFunctionSignature[] signatures;

        @JsonCreator
        private XsltFunctionDefPojo(@JsonProperty("name") final String name,
                                    @JsonProperty("name") final String aliasOf,
                                    @JsonProperty("helpAnchor") final String helpAnchor,
                                    @JsonProperty("aliases") final String[] aliases,
                                    @JsonProperty("commonCategory") final XsltFunctionCategory[] commonCategory,
                                    @JsonProperty("commonSubCategories") final String[] commonSubCategories,
                                    @JsonProperty("commonDescription") final String commonDescription,
                                    @JsonProperty("commonReturnType") final XsltDataType[] commonReturnType,
                                    @JsonProperty("commonReturnDescription") final String commonReturnDescription,
                                    @JsonProperty("signatures") final XsltFunctionSignature[] signatures) {
            this.name = name;
            this.aliasOf = aliasOf;
            this.helpAnchor = helpAnchor;
            this.aliases = aliases;
            this.commonCategory = commonCategory;
            this.commonSubCategories = commonSubCategories;
            this.commonDescription = commonDescription;
            this.commonReturnType = commonReturnType;
            this.commonReturnDescription = commonReturnDescription;
            this.signatures = signatures;
        }

        private static XsltFunctionDefPojo create(final XsltFunctionDef annotaion, final String effectiveName) {
            if (Objects.equals(effectiveName, annotaion.name())) {
                return new XsltFunctionDefPojo(
                        annotaion.name(),
                        null, // Not an alias, this is primary name
                        annotaion.helpAnchor(),
                        annotaion.aliases(),
                        annotaion.commonCategory(),
                        annotaion.commonSubCategories(),
                        annotaion.commonDescription(),
                        annotaion.commonReturnType(),
                        annotaion.commonReturnDescription(),
                        annotaion.signatures());
            } else {
                final boolean foundAlias = NullSafe.stream(annotaion.aliases())
                        .anyMatch(aName -> Objects.equals(aName, effectiveName));
                if (!foundAlias) {
                    throw new IllegalArgumentException(
                            "effectiveName '" + effectiveName + "' not found for function '" + annotaion.name() + "'");
                }
                final String[] newAliases = Stream.concat(
                                Stream.of(annotaion.name()),
                                NullSafe.stream(annotaion.aliases()))
                        .filter(name ->
                                !Objects.equals(name, effectiveName))
                        .toArray(String[]::new);

                return new XsltFunctionDefPojo(
                        effectiveName,
                        annotaion.name(), // This is an alias so record the primary name
                        "", // Clear the help anchor as it is an alias
                        newAliases,
                        annotaion.commonCategory(),
                        annotaion.commonSubCategories(),
                        annotaion.commonDescription(),
                        annotaion.commonReturnType(),
                        annotaion.commonReturnDescription(),
                        annotaion.signatures());
            }
        }

        public String getName() {
            return name;
        }

        public String getHelpAnchor() {
            return helpAnchor;
        }

        public String[] getAliases() {
            return aliases;
        }

        public XsltFunctionCategory[] getCommonCategory() {
            return commonCategory;
        }

        public String[] getCommonSubCategories() {
            return commonSubCategories;
        }

        public String getCommonDescription() {
            return commonDescription;
        }

        public XsltDataType[] getCommonReturnType() {
            return commonReturnType;
        }

        public String getCommonReturnDescription() {
            return commonReturnDescription;
        }

        public XsltFunctionSignature[] getSignatures() {
            return signatures;
        }
    }
}
