/*
 * Copyright 2024 Crown Copyright
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

import stroom.docs.shared.Description;
import stroom.docs.shared.NotDocumented;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.svg.shared.SvgImage;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateDocumentReferenceDoc implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateDocumentReferenceDoc.class);

    public static final String DOCUMENT_TYPE_FIELD_NAME = "DOCUMENT_TYPE";

    private static final Path DATA_SUB_PATH = Paths.get("assets/data/doc-reference");
    private static final Path DOC_FILE = Paths.get(
            "content/en/docs/reference-section/documents.md");

    private final JsonMapper objectMapper;

    /// Creates a document reference data generator.
    public GenerateDocumentReferenceDoc() {
        this.objectMapper = JsonUtil.getMapper();
    }

    static void main(final String[] ignoredArgs) {
        final GenerateDocumentReferenceDoc generateDocumentReferenceDoc = new GenerateDocumentReferenceDoc();
        generateDocumentReferenceDoc.generateDocumentsReference();
    }

    @Override
    public void generateAll(final ScanResult scanResult) {
        generateDocumentsReference(scanResult);
    }

    @Disabled // manual testing only
    @Test
    void generateDocumentsReference() {
        StroomDocsUtil.doWithClassScanResult(this::generateDocumentsReference);
    }

    @Test
    void testDocTypes() {
        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            final List<DocInfo> docInfoList = fetchDocInfo(scanResult);

            docInfoList.forEach(docInfo -> {
                LOGGER.debug("docInfo");
                assertThat(docInfo.type)
                        .withFailMessage(() -> "Blank type for " + docInfo)
                        .isNotBlank();
                assertThat(docInfo.typeDisplayName)
                        .withFailMessage(() -> "Blank typeDisplayName for " + docInfo)
                        .isNotBlank();
                assertThat(docInfo.iconClassName)
                        .withFailMessage(() -> "Blank iconClassName for " + docInfo)
                        .isNotBlank();
                assertThat(docInfo.icon)
                        .withFailMessage(() -> "Null icon for " + docInfo)
                        .isNotNull();
                // We use doc type for our json file names so having spaces in there is a pain,
                assertThat(docInfo.type)
                        .withFailMessage(() -> LogUtil.message(
                                "Document type '{}' should not contain spaces. " +
                                "We use doc type for our json file names in stroom-docs, so having spaces in the " +
                                "type is not helpful.", docInfo.type))
                        .doesNotContain(" ");
            });
        });
    }

    void generateDocumentsReference(final ScanResult scanResult) {

        final List<DocInfo> docInfoList = fetchDocInfo(scanResult);
        writeDataFiles(docInfoList);
        validateDocPage(docInfoList);
    }

    private void validateDocPage(final List<DocInfo> docInfoList) {

        final Path docFile = StroomDocsUtil.resolveStroomDocsFile(DOC_FILE, true);

        // TreeMap is used to ensure the groups are in a consistent order
        final Map<DocumentTypeGroup, List<DocInfo>> docInfoByGroup = docInfoList.stream()
                .collect(Collectors.groupingBy(
                        docInfo -> docInfo.group,
                        TreeMap::new,
                        Collectors.toList()));

        final String pageContents = FileUtil.readString(docFile);
        final String shortCodeTemplate = "{{< doc-detail \"{}\" >}}";

        final AtomicInteger totalMissing = new AtomicInteger(0);

        docInfoByGroup.forEach((group, docInfos) -> {
            final List<DocInfo> missingItems = new ArrayList<>(docInfos.size());
            docInfos.forEach(docInfo -> {
                final String shortCode = LogUtil.message(shortCodeTemplate, docInfo.type);
                if (!pageContents.contains(shortCode)) {
                    missingItems.add(docInfo);
                    totalMissing.incrementAndGet();
                }
            });

            if (!missingItems.isEmpty()) {
                final StringBuilder sb = new StringBuilder()
                        .append("## ")
                        .append(group.getDisplayName());
                missingItems.forEach(docInfo ->
                        sb.append("\n\n")
                                .append("### ")
                                .append(docInfo.typeDisplayName)
                                .append("\n\n")
                                .append(LogUtil.message(shortCodeTemplate, docInfo.type)));

                LOGGER.error("Document {} is missing the following content:\n{}", docFile, sb);
            }
        });

        if (totalMissing.get() > 0) {
            throw new RuntimeException(LogUtil.message("Document {} is missing {} doc-detail shortcodes",
                    docFile, totalMissing.get()));
        }
    }

    private @NonNull List<DocInfo> fetchDocInfo(final ScanResult scanResult) {
        return scanResult
                .getSubclasses(AbstractDoc.class)
                .parallelStream()
                // Not visible in UI currently
                .filter(Predicate.not(ClassInfo::isInterface))
                .filter(classInfo ->
                        !classInfo.hasAnnotation(NotDocumented.class))
                .map(this::mapClass)
                .filter(Objects::nonNull)
                .filter(docInfo ->
                        DocumentTypeGroup.SYSTEM != docInfo.group
                        && DocumentTypeGroup.STRUCTURE != docInfo.group)
                .toList();
    }

    private void writeDataFiles(final List<DocInfo> docInfoList) {
        final Path outputPath = StroomDocsUtil.resolveStroomDocsFile(DATA_SUB_PATH, false);
        try {
            Files.createDirectories(outputPath);

            final List<Path> jsonFiles = FileUtil.listChildPaths(outputPath, path ->
                    Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json"));

            // Delete any existing JSON files so that we can regenerate them.
            // Ensures any docs that have been deleted/renamed are removed from the docs.
            jsonFiles.forEach(GenerateDocumentReferenceDoc::deleteFile);

            docInfoList.stream()
                    .sorted(Comparator.comparing(DocInfo::getLowerType))
                    .forEach(this::writeDataFile);

        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error writing document reference data to {}", outputPath), e);
        }
    }

    private void writeDataFile(final DocInfo docInfo) {
        final String filename = docInfo.type().replace(' ', '-') + ".json";
        final Path filePath = DATA_SUB_PATH.resolve(filename);
        final Path outputFile = StroomDocsUtil.resolveStroomDocsFile(filePath, false);
        try {
            final String json = objectMapper.writeValueAsString(docInfo);
            Files.writeString(outputFile, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            LOGGER.info("Written JSON data to {}", outputFile);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "Error writing document reference data to {}", outputFile), e);
        }
    }

    private static void deleteFile(final Path path) {
        try {
            Files.delete(path);
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error deleting {}", path), e);
        }
    }

    private DocInfo mapClass(final ClassInfo classInfo) {
        final Class<?> clazz = classInfo.loadClass();

        if (!classInfo.isInterface() && classInfo.hasField(DOCUMENT_TYPE_FIELD_NAME)) {
            final DocumentType docType;
            try {
                final Field field = clazz.getField(DOCUMENT_TYPE_FIELD_NAME);
                field.setAccessible(true);
                docType = (DocumentType) field.get(null);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("Error reading field {} on {}: {}",
                        DOCUMENT_TYPE_FIELD_NAME, classInfo.getName(), e.getMessage(), e));
            }

            Objects.requireNonNull(classInfo, () -> LogUtil.message(
                    "No Doc subclass found for {}", classInfo.getName()));

            String description = null;
            if (classInfo.hasAnnotation(Description.class)) {
                final Class<?> docClazz = classInfo.loadClass();
                final Description descriptionAnno = docClazz.getAnnotation(Description.class);
                description = NullSafe.trim(descriptionAnno.value());
            } else {
                LOGGER.warn("No {} annotation for class {}",
                        Description.class.getSimpleName(), classInfo.getName());
//                    throw new RuntimeException(LogUtil.message("No {} annotation for class {}",
//                            Description.class.getSimpleName(), classInfo.getName()));
            }

            final SvgImage icon = docType.getIcon();
            final DocumentTypeGroup group = docType.getGroup();

            return new DocInfo(
                    docType.getType(),
                    description,
                    docType.getDisplayType(),
                    icon,
                    icon.getRelativePathStr(),
                    icon.getClassName(),
                    group,
                    group.getPriority(),
                    group.getDisplayName(),
                    group.getDescription());
        } else {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    private static final class DocInfo {

        @JsonProperty
        private final String type;
        @JsonProperty
        private final String description;
        @JsonProperty
        private final String typeDisplayName;
        @JsonProperty
        private final SvgImage icon;
        @JsonProperty
        private final String iconRelativePathStr;
        @JsonProperty
        private final String iconClassName;
        @JsonProperty
        private final DocumentTypeGroup group;
        @JsonProperty
        private final int groupPriority;
        @JsonProperty
        private final String groupDisplayName;
        @JsonProperty
        private final String groupDescription;

        @JsonCreator
        private DocInfo(
                @JsonProperty("type") String type,
                @JsonProperty("description") String description,
                @JsonProperty("typeDisplayName") String typeDisplayName,
                @JsonProperty("icon") SvgImage icon,
                @JsonProperty("iconRelativePathStr") String iconRelativePathStr,
                @JsonProperty("iconClassName") String iconClassName,
                @JsonProperty("group") DocumentTypeGroup group,
                @JsonProperty("groupPriority") int groupPriority,
                @JsonProperty("groupDisplayName") String groupDisplayName,
                @JsonProperty("groupDescription") String groupDescription) {
            this.type = type;
            this.description = description;
            this.typeDisplayName = typeDisplayName;
            this.icon = icon;
            this.iconRelativePathStr = iconRelativePathStr;
            this.iconClassName = iconClassName;
            this.group = group;
            this.groupPriority = groupPriority;
            this.groupDisplayName = groupDisplayName;
            this.groupDescription = groupDescription;
        }

        String getLowerType() {
            return type.toLowerCase(Locale.ROOT);
        }

        public String type() {
            return type;
        }

        public String description() {
            return description;
        }

        public String typeDisplayName() {
            return typeDisplayName;
        }

        public SvgImage icon() {
            return icon;
        }

        public String iconRelativePathStr() {
            return iconRelativePathStr;
        }

        public String iconClassName() {
            return iconClassName;
        }

        public DocumentTypeGroup group() {
            return group;
        }

        public int groupPriority() {
            return groupPriority;
        }

        public String groupDisplayName() {
            return groupDisplayName;
        }

        public String groupDescription() {
            return groupDescription;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final DocInfo that = (DocInfo) obj;
            return Objects.equals(this.type, that.type) &&
                   Objects.equals(this.description, that.description) &&
                   Objects.equals(this.typeDisplayName, that.typeDisplayName) &&
                   Objects.equals(this.icon, that.icon) &&
                   Objects.equals(this.iconRelativePathStr, that.iconRelativePathStr) &&
                   Objects.equals(this.iconClassName, that.iconClassName) &&
                   Objects.equals(this.group, that.group) &&
                   this.groupPriority == that.groupPriority &&
                   Objects.equals(this.groupDisplayName, that.groupDisplayName) &&
                   Objects.equals(this.groupDescription, that.groupDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type,
                    description,
                    typeDisplayName,
                    icon,
                    iconRelativePathStr,
                    iconClassName,
                    group,
                    groupPriority,
                    groupDisplayName,
                    groupDescription);
        }

        @Override
        public String toString() {
            return "DocInfo[" +
                   "type=" + type + ", " +
                   "description=" + description + ", " +
                   "typeDisplayName=" + typeDisplayName + ", " +
                   "icon=" + icon + ", " +
                   "iconRelativePathStr=" + iconRelativePathStr + ", " +
                   "iconClassName=" + iconClassName + ", " +
                   "group=" + group + ", " +
                   "groupPriority=" + groupPriority + ", " +
                   "groupDisplayName=" + groupDisplayName + ", " +
                   "groupDescription=" + groupDescription + ']';
        }

    }
}
