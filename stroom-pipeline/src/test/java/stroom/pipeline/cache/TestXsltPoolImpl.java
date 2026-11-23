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

import stroom.cache.impl.CacheManagerImpl;
import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.xsltfunctions.StroomXsltFunctionLibrary;
import stroom.security.mock.MockSecurityContext;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests that pooled XSLT follows the XSLT security settings. See also the integration test of the same name in
/// stroom-app, which covers imports.
@ExtendWith(MockitoExtension.class)
class TestXsltPoolImpl {

    private static final String FILE_CONTENT = "file-content";

    @Mock
    private DocumentPermissionCache documentPermissionCache;
    @Mock
    private StroomXsltFunctionLibrary functionLibrary;

    @TempDir
    Path tempDir;

    private final AtomicReference<XsltConfig> xsltConfig = new AtomicReference<>(new XsltConfig());
    private final AtomicInteger compileCount = new AtomicInteger();
    private XsltPoolImpl xsltPool;
    private XsltDoc xsltDoc;

    @BeforeEach
    void setUp() throws IOException {
        Mockito.when(documentPermissionCache.canUseDocument(Mockito.any())).thenReturn(true);
        xsltPool = new XsltPoolImpl(
                new CacheManagerImpl(),
                xsltConfig::get,
                documentPermissionCache,
                new MockSecurityContext(),
                (href, base) -> {
                    throw new TransformerException("No XSLT found for " + href);
                },
                () -> {
                    // Asked for once for each compile.
                    compileCount.incrementAndGet();
                    return functionLibrary;
                });

        final Path dataFile = tempDir.resolve("data.txt");
        Files.writeString(dataFile, FILE_CONTENT);
        xsltDoc = XsltDoc.builder()
                .uuid("test-uuid")
                .name("test")
                .data("""
                        <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                          <xsl:output method="text"/>
                          <xsl:template match="/">
                            <xsl:value-of select="unparsed-text-available('%s')"/>
                          </xsl:template>
                        </xsl:stylesheet>
                        """.formatted(dataFile.toUri()))
                .build();
    }

    @Test
    void testSecureByDefault() {
        assertThat(canReadFile()).isFalse();
    }

    @Test
    void testAllowedPrefix() {
        setConfig(true, List.of(tempDir.toString()));
        assertThat(canReadFile()).isTrue();
    }

    /// A pooled XSLT compiled while secure mode was off must not stay unsecured once it is back on.
    @Test
    void testRecompiledWhenSettingsChange() {
        assertThat(canReadFile()).isFalse();

        setConfig(false, List.of());
        assertThat(canReadFile()).isTrue();

        setConfig(true, List.of());
        assertThat(canReadFile()).isFalse();
    }

    @Test
    void testRecompiledWhenAllowedPrefixesChange() {
        setConfig(true, List.of(tempDir.toString()));
        assertThat(canReadFile()).isTrue();

        setConfig(true, List.of());
        assertThat(canReadFile()).isFalse();
    }

    @Test
    void testNotRecompiledWhenSettingsUnchanged() {
        final StoredXsltExecutable first = borrowAndReturn();
        final StoredXsltExecutable second = borrowAndReturn();
        assertThat(second).isSameAs(first);
    }

    /// The allowed URI prefixes do nothing while secure mode is off, so changing them then doesn't recompile.
    @Test
    void testNotRecompiledWhenPrefixesChangeWithSecureModeOff() {
        setConfig(false, List.of());
        final StoredXsltExecutable first = borrowAndReturn();

        setConfig(false, List.of(tempDir.toString()));
        final StoredXsltExecutable second = borrowAndReturn();
        assertThat(second).isSameAs(first);
    }

    /// Stale copies are dropped one by one, so a copy compiled with the current settings is kept and reused.
    @Test
    void testStaleCopiesDroppedAndNewCopiesKept() {
        // Three copies in the pool.
        final List<PoolItem<StoredXsltExecutable>> items = List.of(borrow(), borrow(), borrow());
        items.forEach(item -> xsltPool.returnObject(item, true));
        assertThat(compileCount).hasValue(3);

        setConfig(false, List.of());

        // All three stale copies are dropped and one new copy compiled.
        final StoredXsltExecutable fresh = borrowAndReturn();
        assertThat(compileCount).hasValue(4);
        assertThat(fresh.getSecuritySettings().secureMode()).isFalse();

        // The new copy is reused, not dropped.
        assertThat(borrowAndReturn()).isSameAs(fresh);
        assertThat(compileCount).hasValue(4);
    }

    private void setConfig(final boolean secureMode, final List<String> allowedUriPrefixes) {
        final XsltConfig current = xsltConfig.get();
        xsltConfig.set(new XsltConfig(
                current.getCacheConfig(),
                current.getMaxElements(),
                secureMode,
                allowedUriPrefixes,
                current.getAllowedEmailRecipients(),
                current.getMaxEmailsPerRun()));
    }

    private StoredXsltExecutable borrowAndReturn() {
        final PoolItem<StoredXsltExecutable> poolItem = borrow();
        xsltPool.returnObject(poolItem, true);
        return poolItem.getValue();
    }

    private PoolItem<StoredXsltExecutable> borrow() {
        return xsltPool.borrowConfiguredTemplate(
                xsltDoc,
                new StoredErrorReceiver(),
                new DefaultLocationFactory(),
                List.of(),
                true);
    }

    // Runs the pooled XSLT, which reports whether it can read the file.
    private boolean canReadFile() {
        final PoolItem<StoredXsltExecutable> poolItem = borrow();
        try {
            assertThat(poolItem.getValue().getXsltExecutable())
                    .as("Compile errors: %s", poolItem.getValue().getErrorReceiver().getList())
                    .isNotNull();
            final StringWriter writer = new StringWriter();
            final Serializer serializer = poolItem.getValue().getXsltExecutable().getProcessor()
                    .newSerializer(writer);
            final XsltTransformer transformer = poolItem.getValue().getXsltExecutable().load();
            transformer.setSource(new StreamSource(new StringReader("<a/>")));
            transformer.setDestination(serializer);
            transformer.transform();
            return Boolean.parseBoolean(writer.toString().trim());
        } catch (final SaxonApiException e) {
            throw new RuntimeException(e);
        } finally {
            xsltPool.returnObject(poolItem, true);
        }
    }
}
