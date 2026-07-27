/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRedirectUrlUtil {

    @Test
    void allowsPlainRootRelativePaths() {
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/")).isTrue();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/stroom")).isTrue();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/stroom/ui?name=a&back=/x#frag")).isTrue();
    }

    @Test
    void rejectsProtocolRelative() {
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("//evil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("//evil.com/path")).isFalse();
    }

    @Test
    void rejectsBackslashAuthorityBypass() {
        // A browser normalises '\' to '/', so these would resolve to a protocol-relative "//evil.com".
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/\\evil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/\\/evil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/path\\evil.com")).isFalse();
    }

    @Test
    void rejectsControlCharacters() {
        // A browser strips tab/newline/CR from a URL before parsing, which can change the effective origin.
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/\tevil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/\nevil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("/\revil.com")).isFalse();
    }

    @Test
    void rejectsNonRootRelativeAndNull() {
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("https://evil.com")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("javascript:alert(1)")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("relative/path")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath("")).isFalse();
        assertThat(RedirectUrlUtil.isSafeRootRelativePath(null)).isFalse();
    }
}
