/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.cache;

import stroom.docref.DocRef;
import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xslt.XsltStore;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Reproductions for the reported `xsl:import` problems: an XSLT that imports another, by name or by uuid, must
/// pick up a change to the imported XSLT, and must resolve an import whose target is created after a failed
/// compile.
///
/// Each test makes its own uniquely named documents, so the tests don't depend on each other.
///
/// Renames aren't covered here: these tests run on `MemoryPersistence`, whose name lookup doesn't follow a
/// rename (unlike the DB persistence). See `TestDocRefFromNameCache` for renames.
class TestXsltPoolImpl extends AbstractProcessIntegrationTest {

    private static final String COMPILE_FAILED = "COMPILE FAILED";

    @Inject
    private XsltStore xsltStore;
    @Inject
    private XsltPool xsltPool;

    /// The imported XSLT is changed and saved. The importing XSLT, served from the pool, must show the change.
    /// `nameDoubleQuoted` is how existing content references an import and is the control case.
    @ParameterizedTest(name = "href form = {0}")
    @ValueSource(strings = {"nameDoubleQuoted", "nameSingleQuoted", "uuid", "docRefString"})
    void changeToImportedXsltIsPickedUp(final String hrefForm) {
        final String importedName = uniqueName("imported");
        final DocRef imported = createXslt(importedName, outputting("Before"));
        final DocRef importer = createXslt(uniqueName("importer"), importing(imported, hrefForm));

        assertThat(transformPooled(importer)).as("output before the change").isEqualTo("<Before/>");

        saveXslt(imported, outputting("After"));

        assertThat(transformPooled(importer))
                .as("output after the imported XSLT was saved (href form %s)", hrefForm)
                .isEqualTo("<After/>");
    }

    /// A imports B, which imports C, all by name. Changing C must reach A.
    @Test
    void changeToIndirectlyImportedXsltIsPickedUp() {
        final DocRef c = createXslt(uniqueName("c"), outputting("Before"));
        final DocRef b = createXslt(uniqueName("b"), importing(c, "nameDoubleQuoted"));
        final DocRef a = createXslt(uniqueName("a"), importing(b, "nameDoubleQuoted"));

        assertThat(transformPooled(a)).as("output before the change").isEqualTo("<Before/>");

        saveXslt(c, outputting("After"));

        assertThat(transformPooled(b)).as("B, which imports C directly").isEqualTo("<After/>");
        assertThat(transformPooled(a)).as("A, which imports C through B").isEqualTo("<After/>");
    }

    /// The importing XSLT is compiled (e.g. by stepping or by processing) before the XSLT it imports exists.
    /// Once that XSLT is created and saved, the importing XSLT must compile.
    @Test
    void importResolvesOnceTheImportedXsltIsCreated() {
        final String importedName = uniqueName("later");
        final DocRef importer = createXslt(uniqueName("importer"),
                importingHref(importedName));

        assertThat(transformPooled(importer)).as("before the imported XSLT exists").startsWith(COMPILE_FAILED);

        createXslt(importedName, outputting("Created"));

        assertThat(transformPooled(importer))
                .as("pooled compile after the imported XSLT was created")
                .isEqualTo("<Created/>");
    }

    /// As [#importResolvesOnceTheImportedXsltIsCreated()], but the importing XSLT is compiled afresh each time
    /// (as stepping does for an XSLT with unsaved edits), so only the name lookup can be stale.
    @Test
    void importResolvesOnceTheImportedXsltIsCreatedWithoutThePool() {
        final String importedName = uniqueName("later");
        final DocRef importer = createXslt(uniqueName("importer"),
                importingHref(importedName));

        assertThat(transformUnpooled(importer)).as("before the imported XSLT exists").startsWith(COMPILE_FAILED);

        createXslt(importedName, outputting("Created"));

        assertThat(transformUnpooled(importer))
                .as("fresh compile after the imported XSLT was created")
                .isEqualTo("<Created/>");
    }

    /// An import of a name that doesn't exist must say so, rather than report a uuid lookup of the name.
    @Test
    void importOfAMissingNameSaysTheNameWasNotFound() {
        final String missingName = uniqueName("missing");
        final DocRef importer = createXslt(uniqueName("importer"), importingHref(missingName));

        final String result = transformUnpooled(importer);

        assertThat(result)
                .as("error for a missing import")
                .startsWith(COMPILE_FAILED + ":")
                .contains("No XSLT found with name or UUID \"" + missingName + "\"");
    }

    private static String uniqueName(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String outputting(final String elementName) {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <%s/>
                  </xsl:template>
                </xsl:stylesheet>
                """.formatted(elementName);
    }

    private String importing(final DocRef imported, final String hrefForm) {
        final String href = switch (hrefForm) {
            case "nameDoubleQuoted", "nameSingleQuoted" -> imported.getName();
            case "uuid" -> imported.getUuid();
            case "docRefString" -> "uuid='" + imported.getUuid() + "', name='" + imported.getName() + "'";
            default -> throw new IllegalArgumentException(hrefForm);
        };
        if ("nameSingleQuoted".equals(hrefForm)) {
            return importingXml("<xsl:import href='" + href + "'/>");
        }
        return importingHref(href);
    }

    private static String importingHref(final String href) {
        return importingXml("<xsl:import href=\"" + href + "\"/>");
    }

    private static String importingXml(final String importElement) {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  %s
                </xsl:stylesheet>
                """.formatted(importElement);
    }

    private DocRef createXslt(final String name, final String data) {
        final DocRef docRef = xsltStore.createDocument(name);
        saveXslt(docRef, data);
        return docRef;
    }

    private void saveXslt(final DocRef docRef, final String data) {
        final XsltDoc current = xsltStore.readDocument(docRef);
        xsltStore.writeDocument(current.copy().data(data).build());
    }

    private String transformPooled(final DocRef xsltRef) {
        return transform(xsltRef, true);
    }

    private String transformUnpooled(final DocRef xsltRef) {
        return transform(xsltRef, false);
    }

    // Borrows the XSLT as XsltFilter does and runs it over a trivial document. Returns the output, or
    // COMPILE_FAILED and the compile errors.
    private String transform(final DocRef xsltRef, final boolean usePool) {
        final XsltDoc xsltDoc = xsltStore.readDocument(xsltRef);
        final PoolItem<StoredXsltExecutable> poolItem;
        try {
            poolItem = xsltPool.borrowConfiguredTemplate(
                    xsltDoc,
                    new StoredErrorReceiver(),
                    new DefaultLocationFactory(),
                    List.of(),
                    usePool);
        } catch (final RuntimeException e) {
            // A failed import lookup escapes the compile as an exception rather than a compile error.
            // XsltFilter logs this exception's message as the element's fatal error.
            return COMPILE_FAILED + " (exception): " + e.getMessage();
        }
        try {
            final StoredXsltExecutable stored = poolItem.getValue();
            final XsltExecutable executable = stored.getXsltExecutable();
            if (executable == null) {
                final String errors = stored.getErrorReceiver().getList().stream()
                        .map(StoredError::toString)
                        .collect(Collectors.joining("\n"));
                return COMPILE_FAILED + ": " + errors;
            }
            final StringWriter writer = new StringWriter();
            final Serializer serializer = executable.getProcessor().newSerializer(writer);
            serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
            final XsltTransformer transformer = executable.load();
            transformer.setSource(new StreamSource(new StringReader("<in/>")));
            transformer.setDestination(serializer);
            transformer.transform();
            return writer.toString().trim();
        } catch (final SaxonApiException e) {
            throw new RuntimeException(e);
        } finally {
            xsltPool.returnObject(poolItem, usePool);
        }
    }
}
