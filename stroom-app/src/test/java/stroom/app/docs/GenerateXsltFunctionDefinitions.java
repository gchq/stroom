package stroom.app.docs;


import stroom.docs.shared.NotDocumented;
import stroom.pipeline.xsltfunctions.XsltFunctionCategory;
import stroom.pipeline.xsltfunctions.XsltFunctionDef;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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

public class GenerateXsltFunctionDefinitions implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateXsltFunctionDefinitions.class);

    private static final Path DOCS_SUB_PATH = Paths.get(
            "content/en/docs/reference-section/xslt-functions");
    private static final Path DATA_SUB_PATH = Paths.get(
            "assets/data/xslt-functions");
    private static final String INDEX_DATA_FILENAME = "_index.json";
    private static final String INDEX_DOC_FILENAME = "_index.md";

    private final ObjectMapper objectMapper;

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
                            LOGGER.info("Deleting file {}", path.toAbsolutePath().normalize());
                            Files.delete(path);
                        }));
            }

            final List<AnnotatedClass<XsltFunctionDef>> annotatedClasses = getAllFunctionDefs(scanResult);
            annotatedClasses.forEach(this::processFunction);
            produceIndexFile(annotatedClasses);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void produceIndexFile(final List<AnnotatedClass<XsltFunctionDef>> annotatedClasses) {
        final Map<XsltFunctionCategory, List<AnnotatedClass<XsltFunctionDef>>> groups = annotatedClasses.stream()
                .collect(Collectors.groupingBy(annotatedClass -> {
                    final XsltFunctionCategory[] categories = annotatedClass.annotation().commonCategory();
                    Objects.requireNonNull(categories, () -> LogUtil.message(
                            "functions {} should have a commonCategory",
                            annotatedClass.clazz().getName()));
                    return categories[0];
                }));

        final Map<XsltFunctionCategory, XsltFunctionCategoryIndex> map = new HashMap<>();
        final AtomicInteger errorCounter = new AtomicInteger();
        groups.forEach((category, classesGroup) -> {
            final String docFilename = category.name()
                                               .toLowerCase()
                                               .replace("[^a-zA-Z0-9-]", "-") + ".md";
            final XsltFunctionCategoryIndex index = map.computeIfAbsent(category,
                    k -> new XsltFunctionCategoryIndex(null, k, docFilename));
            classesGroup.forEach(annotatedClass -> {
                final String functionName = annotatedClass.annotation().name();
                index.addFunction(functionName);
            });
            final int errorCount = checkDocPage(index);
            errorCounter.addAndGet(errorCount);
        });

        try {
            final String json = JsonUtil.getMapper().writeValueAsString(map);
            LOGGER.info("Index:\n{}", json);

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
            int errorCount = 0;
            // Check the _index.md file contains a link for each func with the appropriate category
            final Path indexDocFilePath = buildDocsFilePath(INDEX_DOC_FILENAME);
            if (!Files.isRegularFile(indexDocFilePath)) {
                throw new RuntimeException(LogUtil.message("File {} does not exist",
                        indexDocFilePath.toAbsolutePath()));
            }
            final String docFileNameWithoutExtension = index.getDocFilename()
                    .replaceAll("\\.md$", "");
            final String indexDocContent = Files.readString(indexDocFilePath);
            Set<String> stringsToFind = index.functionNames
                    .stream()
                    .map(functionName ->
                            "[" + functionName + "]("
                            + docFileNameWithoutExtension
                            + "#" + functionName + ")")
                    .collect(Collectors.toCollection(HashSet::new));

            stringsToFind.removeIf(indexDocContent::contains);

            if (!stringsToFind.isEmpty()) {
                LOGGER.error("File {} is missing content for the following functions in category {}. " +
                             "Each function should have a link a bit like '[hash](conversion#hash)'.\n{}",
                        indexDocFilePath.toAbsolutePath(),
                        index.category,
                        String.join("\n", stringsToFind));
            }
            errorCount += stringsToFind.size();

            // Check the appropriate category file (e.g. conversion.md) contains a shortcode
            // for each of the funcs in that category.
            final Path categoryDocFilePath = buildDocsFilePath(index.getDocFilename());
            if (!Files.isRegularFile(categoryDocFilePath)) {
                throw new RuntimeException(LogUtil.message("File {} does not exist",
                        indexDocFilePath.toAbsolutePath()));
            }

            final String categoryDocContent = Files.readString(categoryDocFilePath);

            stringsToFind = index.functionNames
                    .stream()
                    .map(functionName -> "{{< xslt-func \"" + functionName + "\" >}}")
                    .collect(Collectors.toCollection(HashSet::new));

            stringsToFind.removeIf(categoryDocContent::contains);

            if (!stringsToFind.isEmpty()) {
                LOGGER.error("File {} is missing content for the following functions. " +
                             "Each function should have a shortcode call like '{{< xslt-func \"hash\" >}}'.\n{}",
                        categoryDocFilePath.toAbsolutePath(),
                        String.join("\n", stringsToFind));
            }
            errorCount += stringsToFind.size();
            return errorCount;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generate() {
        StroomDocsUtil.doWithClassScanResult(this::generateAll);
//        final ObjectMapper mapper = JsonUtil.getMapper();
//        final ListXsltFunctionDef functionDefinitions = XsltFunctionFactory.getFunctionDefinitions;
    }

    private List<AnnotatedClass<XsltFunctionDef>> getAllFunctionDefs(final ScanResult scanResult) {
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
                    .map(clazz -> {
                        final XsltFunctionDef anno = clazz.getAnnotation(XsltFunctionDef.class);
                        if (anno == null) {
                            LOGGER.error("XSLT Function {} is missing annotation {}",
                                    clazz.getName(),
                                    XsltFunctionDef.class.getName());
                            return null;
                        } else {
                            return new AnnotatedClass<>(clazz, anno);
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

    private void processFunction(final AnnotatedClass<XsltFunctionDef> annotatedClass) {
        try {
            final XsltFunctionDef functionDef = annotatedClass.annotation();
            final String json = objectMapper.writeValueAsString(functionDef);
            final String filename = annotatedClass.annotation().name() + ".json";
            final Path filePath = buildDataFilePath(filename);
            LOGGER.info("{} - {} - {}\n{}",
                    annotatedClass.clazz().getName(),
                    filename,
                    filePath.toAbsolutePath(),
                    json);
            Files.writeString(filePath, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private record AnnotatedClass<T extends Annotation>(Class<?> clazz, T annotation) {

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
}
