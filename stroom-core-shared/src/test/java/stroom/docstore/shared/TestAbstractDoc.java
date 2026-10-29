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

package stroom.docstore.shared;

import stroom.docs.shared.Description;
import stroom.docs.shared.NotDocumented;
import stroom.test.common.docs.StroomDocsUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;
import java.util.stream.Stream;

class TestAbstractDoc {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAbstractDoc.class);
    private static final Class<Description> DOCUMENTATION_CLASS = Description.class;
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\[]+]([^(]+)");

    @Disabled // Enable this once all the docs have documentation
    @Test
    void checkDocDocumentation() {
        SoftAssertions.assertSoftly(softly -> {
            StroomDocsUtil.doWithClassScanResult(scanResult -> {
                streamAbstractDocSubClasses(scanResult)
                        .forEach(classInfo -> {
                            final Class<?> clazz = classInfo.loadClass();
                            checkDoc(softly, classInfo, clazz);
                        });
            });
        });
    }

    private void checkDoc(final SoftAssertions softly, final ClassInfo classInfo, final Class<?> clazz) {
        LOGGER.debug("Checking document {}", clazz.getSimpleName());
        final Description descriptionAnno = clazz.getAnnotation(DOCUMENTATION_CLASS);
        softly.assertThat(descriptionAnno)
                .withFailMessage(LogUtil.message(
                        "Document class {} is missing annotation {}. It's time to write some documentation.",
                        clazz.getName(),
                        DOCUMENTATION_CLASS.getName()))
                .isNotNull();
        if (descriptionAnno != null) {
            final String description = descriptionAnno.value();

            softly.assertThat(description)
                    .withFailMessage(LogUtil.message(
                            "Document class {} has annotation {} but no description.",
                            clazz.getName(),
                            DOCUMENTATION_CLASS.getName()))
                    .isNotBlank();

            softly.assertThat(description)
                    .withFailMessage(LogUtil.message(
                            "Documentation for class {} contains Hugo shortcodes. " +
                            "Shortcodes are not supported in the description.",
                            clazz.getName(),
                            DOCUMENTATION_CLASS.getName()))
                    .doesNotContain("{{<", "{{%", ">}}", "%}}");

            softly.assertThat(description)
                    .withFailMessage("Documentation for class {} contains markdown links. " +
                                     "Markdown links are not supported in the description.",
                            clazz.getName(),
                            DOCUMENTATION_CLASS.getName())
                    .doesNotMatch(MARKDOWN_LINK);
        }
    }

    private Stream<ClassInfo> streamAbstractDocSubClasses(final ScanResult scanResult) {
        return scanResult.getSubclasses(AbstractDoc.class)
                .stream()
                .filter(classInfo -> !classInfo.isInterface())
                .filter(classInfo -> !classInfo.isAbstract())
                .filter(classInfo -> !classInfo.hasAnnotation(NotDocumented.class));
    }
}
