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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentTypes {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "index.html|text/html; charset=utf-8",
            "workbench.nocache.js|text/javascript; charset=utf-8",
            "ui/css/app.css|text/css; charset=utf-8",
            "IMAGE.SVG|image/svg+xml",
            "font.woff2|font/woff2",
            "file.unknown|application/octet-stream",
            "no-extension|application/octet-stream",
            "trailing-dot.|application/octet-stream",
    })
    void testForFileName(final String fileName, final String expected) {
        assertThat(ContentTypes.forFileName(Path.of(fileName))).isEqualTo(expected);
    }

    @Test
    void testForFileName_root() {
        assertThat(ContentTypes.forFileName(Path.of("/"))).isEqualTo(ContentTypes.DEFAULT_CONTENT_TYPE);
    }
}
