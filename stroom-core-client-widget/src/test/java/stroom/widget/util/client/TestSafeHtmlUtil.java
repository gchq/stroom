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

package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSafeHtmlUtil {

    @Test
    void toPlainText_decodesWhatEscapingAdded() {
        // A menu item's tooltip, escaped once when made; its title must show it as typed
        assertThat(SafeHtmlUtil.toPlainText(SafeHtmlUtil.from("Your browser didn't let \"Stroom\" <read> it & more")))
                .isEqualTo("Your browser didn't let \"Stroom\" <read> it & more");
    }

    @Test
    void toPlainText_dropsTags() {
        assertThat(SafeHtmlUtil.toPlainText(SafeHtmlUtils.fromSafeConstant("<b>Bold</b> and <i>italic</i>")))
                .isEqualTo("Bold and italic");
    }

    @Test
    void toPlainText_decodesNamedAndNumberedReferences() {
        assertThat(SafeHtmlUtil.toPlainText(SafeHtmlUtils.fromSafeConstant("a&nbsp;b&#39;c&#x41;&apos;")))
                .isEqualTo("a b'cA'");
    }

    @Test
    void toPlainText_leavesUnknownOrBrokenReferences() {
        assertThat(SafeHtmlUtil.toPlainText(SafeHtmlUtils.fromSafeConstant("a &bogus; b & c &#zz;")))
                .isEqualTo("a &bogus; b & c &#zz;");
    }

    @Test
    void toPlainText_ofNullIsEmpty() {
        assertThat(SafeHtmlUtil.toPlainText(null)).isEmpty();
    }
}
