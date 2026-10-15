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

package stroom.widget.form.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileUploadUtil {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            // The fake path browsers give
            "C:\\fakepath\\stream.txt     | stream.txt",
            // Real paths, as older browsers may give
            "C:\\Users\\me\\data.zip      | data.zip",
            "/home/me/data.zip            | data.zip",
            "C:\\mixed/separators\\a.txt  | a.txt",
            "C:/mixed\\separators/b.txt   | b.txt",
            // Just a name
            "stream.txt                   | stream.txt",
            "my file.txt                  | my file.txt",
            "no-extension                 | no-extension",
            // A name holding dots is kept whole
            "C:\\fakepath\\a.b.c.txt      | a.b.c.txt",
            // Nothing after the last separator
            "C:\\fakepath\\               | ''",
            "/                            | ''",
            "''                           | ''",
            "NULL                         | NULL"
    })
    void testGetFileName(final String fileInputValue, final String expectedFileName) {
        assertThat(FileUploadUtil.getFileName(fileInputValue))
                .isEqualTo(expectedFileName);
    }
}
