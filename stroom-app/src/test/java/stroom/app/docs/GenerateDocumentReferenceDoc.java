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

import stroom.data.retention.shared.DataRetentionRules;
import stroom.docs.shared.Description;
import stroom.docs.shared.NotDocumented;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.svg.shared.SvgImage;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GenerateDocumentReferenceDoc implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateDocumentReferenceDoc.class);

    private static final String PACKAGE_NAME = "stroom";

    public static final String DOCUMENT_TYPE_FIELD_NAME = "DOCUMENT_TYPE";

    private static final Path DOCUMENT_SUB_PATH = Paths.get(
            "content/en/docs/reference-section/documents.md");

    private static final Set<String> DOC_TYPE_DENY_LIST = Set.of(
            DataRetentionRules.TYPE,
            ReceiveDataRules.TYPE);

    public static void main(final String[] args) {
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

    void generateDocumentsReference(final ScanResult scanResult) {

        final String generatedContent = scanResult
                .getSubclasses(AbstractDoc.class)
                .parallelStream()
                // Not visible in UI currently
                .filter(Predicate.not(ClassInfo::isInterface))
                .filter(classInfo -> !classInfo.hasAnnotation(NotDocumented.class))
                .map(this::mapClass)
                .filter(Objects::nonNull)
                .filter(docInfo ->
                        DocumentTypeGroup.SYSTEM != docInfo.group
                        && DocumentTypeGroup.STRUCTURE != docInfo.group)
                .sequential()
                .collect(Collectors.groupingBy(DocInfo::group))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey(
                        Comparator.comparing(DocumentTypeGroup::getDisplayName)))
                .map(this::mapGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        final Path file = StroomDocsUtil.resolveStroomDocsFile(DOCUMENT_SUB_PATH);

        final boolean didReplace = StroomDocsUtil.replaceGeneratedContent(file, generatedContent);

        if (didReplace) {
            LOGGER.info("Replaced generated content in file: {}", file);
        } else {
            LOGGER.info("No change made to file: {}", file);
        }
    }

    private DocInfo mapClass(final ClassInfo classInfo) {
        final Class<?> clazz = classInfo.loadClass();

        if (!classInfo.isInterface() && classInfo.hasField(DOCUMENT_TYPE_FIELD_NAME)) {
            DocumentType docType = null;
            try {
                final Field field = clazz.getField(DOCUMENT_TYPE_FIELD_NAME);
                field.setAccessible(true);
                docType = (DocumentType) field.get(null);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("Error reading field {} on {}: {}",
                        DOCUMENT_TYPE_FIELD_NAME, classInfo.getName(), e.getMessage(), e));
            }

            if (DOC_TYPE_DENY_LIST.contains(docType.getType())) {
                return null;
            } else {
                Objects.requireNonNull(classInfo, () -> LogUtil.message(
                        "No Doc subclass found for {}", classInfo.getName()));

                String description = null;
                if (classInfo.hasAnnotation(Description.class)) {
                    final Class<?> docClazz = classInfo.loadClass();
                    final Description descriptionAnno = docClazz.getAnnotation(Description.class);
                    description = descriptionAnno.value();
                } else {
                    LOGGER.warn("No {} annotation for class {}",
                            Description.class.getSimpleName(), classInfo.getName());
                }

                return new DocInfo(
                        docType.getType(),
                        description,
                        docType.getDisplayType(),
                        docType.getIcon(),
                        docType.getGroup());
            }
        } else {
            return null;
        }
    }

    private String mapGroup(final Entry<DocumentTypeGroup, List<DocInfo>> entry) {
        final DocumentTypeGroup documentTypeGroup = entry.getKey();
        final List<DocInfo> docInfoList = entry.getValue();

        if (!docInfoList.isEmpty()) {
            final String docsText = convertDocsToText(docInfoList);

            return LogUtil.message("""
                            ## {}

                            {}


                            {}
                            """,
                    documentTypeGroup.getDisplayName(),
                    documentTypeGroup.getDescription(),
                    docsText);
        } else {
            return null;
        }
    }

    private String convertDocsToText(final List<DocInfo> docInfoList) {
        return docInfoList.stream()
                .sorted(Comparator.comparing(DocInfo::typeDisplayName))
                .map(this::convertDocToText)
                .collect(Collectors.joining("\n"));
    }

    private String convertDocToText(final DocInfo docInfo) {
        final String template = """
                ### {}

                * Icon: {{< stroom-icon "{}" >}}
                * Type: `{}`

                {}

                """;

        final String description = Objects.requireNonNullElse(
                docInfo.description(),
                "> TODO - Add description");

        return LogUtil.message(
                template,
                docInfo.typeDisplayName,
                docInfo.icon().getRelativePathStr(),
                docInfo.type,
                description);
    }


    // --------------------------------------------------------------------------------


    private record DocInfo(
            String type,
            String description,
            String typeDisplayName,
            SvgImage icon,
            DocumentTypeGroup group) {

    }
}
