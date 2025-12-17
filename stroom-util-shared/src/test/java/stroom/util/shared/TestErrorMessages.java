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

package stroom.util.shared;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TestErrorMessages {

    ErrorMessage error = new ErrorMessage(Severity.ERROR, "error");
    ErrorMessage warning = new ErrorMessage(Severity.WARNING, "warning");
    ErrorMessage info = new ErrorMessage(Severity.INFO, "info");

    @Test
    void getHighestSeverity() {
        assertThat(new ErrorMessages(null).getHighestSeverity()).isNull();
        assertThat(errorMessages().getHighestSeverity()).isNull();
        assertThat(errorMessages(error).getHighestSeverity()).isEqualTo(Severity.ERROR);
        assertThat(errorMessages(info, warning, error).getHighestSeverity()).isEqualTo(Severity.ERROR);
        assertThat(errorMessages(warning, info).getHighestSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void containsAny() {
        assertThat(new ErrorMessages(null).containsAny()).isFalse();
        assertThat(errorMessages().containsAny()).isFalse();
        assertThat(errorMessages(error).containsAny(Severity.ERROR)).isTrue();
        assertThat(errorMessages(info, warning, error).containsAny(Severity.ERROR)).isTrue();
        assertThat(errorMessages(warning, info).containsAny(Severity.ERROR)).isFalse();
        assertThat(errorMessages(warning, info).containsAny(Severity.ERROR, Severity.WARNING)).isTrue();
    }

    @Test
    void isEmpty() {
        assertThat(new ErrorMessages(null).isEmpty()).isTrue();
        assertThat(errorMessages().isEmpty()).isTrue();
        assertThat(errorMessages(error).isEmpty()).isFalse();
        assertThat(errorMessages(info, warning, error).isEmpty()).isFalse();
    }

    @Test
    void get() {
        assertThat(new ErrorMessages(null).get()).isEmpty();
        assertThat(errorMessages().get()).isEmpty();
        assertThat(errorMessages(error).get(Severity.ERROR)).containsExactly("error");
        assertThat(errorMessages(info, warning, error).get(Severity.ERROR)).containsExactly("error");
        assertThat(errorMessages(info, warning, error).get(Severity.ERROR, Severity.WARNING))
                .containsExactly("error", "warning");
        assertThat(errorMessages(warning, info).get(Severity.ERROR)).isEmpty();
        assertThat(errorMessages(warning, info).get(Severity.ERROR, Severity.WARNING)).containsExactly("warning");
    }

    private ErrorMessages errorMessages(final ErrorMessage...errorMessages) {
        return new ErrorMessages(Arrays.asList(errorMessages));
    }
}
