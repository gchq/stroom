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

package stroom.pipeline.xml.bug;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test exists to highlight a bug in the JDK XML parser related to corrupting characters when parsing XML 1.1.
 * We will leave this test in place to ensure that we don't accidentally switch back to using the JDK XML parser.
 */
public class TestXml11ParserBug {

    @BeforeAll
    static void setup() throws Exception {
        new DataCreator().create();
    }

    @Test
    void testFullXml10() throws Exception {
        testFull("sample-small-10.xml");
    }

    @Test
    void testFullRewriteXml10() throws Exception {
        testFull("sample-small-10-rewrite.xml");
    }

    @Test
    void testFragmentXml10() throws Exception {
        testFragment("fragment-wrapper_1_0.xml", "sample-fragment-small.xml");
    }

    @Test
    void testFullXml11() throws Exception {
        testFull("sample-small-11.xml");
    }

    @Test
    void testFullRewriteXml11() throws Exception {
        testFull("sample-small-11-rewrite.xml");
    }

    @Test
    void testFragmentXml11() throws Exception {
        testFragment("fragment-wrapper_1_1.xml", "sample-fragment-small.xml");
    }

    private void testFull(final String fileName) throws Exception {
        final Path dir = DataCreator.getDir();
        final Path inputPath = dir.resolve(fileName);

        final JsonValidator jsonValidator = new JsonValidator();
        try (final Reader input = Files.newBufferedReader(inputPath)) {
            final EntityResolver resolver = new NullEntityResolver();
            SaxUtil.parse(input, jsonValidator, resolver);
        }
        assertThat(jsonValidator.getErrorCount()).isZero();
    }

    private void testFragment(final String wrapperFileName,
                              final String fragmentFileName) throws Exception {
        final Path dir = DataCreator.getDir();
        final Path wrapperPath = dir.resolve(wrapperFileName);
        final Path fragmentPath = dir.resolve(fragmentFileName);

        final JsonValidator jsonValidator = new JsonValidator();
        try (final Reader wrapper = Files.newBufferedReader(wrapperPath)) {
            try (final Reader fragment = Files.newBufferedReader(fragmentPath)) {
                final EntityResolver resolver = new FragmentEntity(new InputSource(fragment));
                SaxUtil.parse(wrapper, jsonValidator, resolver);
            }
        }
        assertThat(jsonValidator.getErrorCount()).isZero();
    }
}
