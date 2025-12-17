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

package stroom.hyperlink.client;


import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class TestHyperlink {

    @Disabled // test is not doing anything, assume due detect() not being a thing any more
    @Test
    void testValidUrl() {
        // Given
        final Map<String, String> namedUrls = new HashMap<>();
        namedUrls.put("orange-ui", "http://colors/orange");
        namedUrls.put("colors-ui", "http://colors");
        namedUrls.put("shades-shop", "http://shades.com/shop/item");

        final Map<String, Hyperlink> testLinks = new HashMap<>();
        testLinks.put("[Orange](__orange-ui__){DIALOG}", Hyperlink.builder()
                .text("Orange")
                .href("http://colors/orange")
                .type(HyperlinkType.DIALOG.name().toLowerCase())
                .build());
        testLinks.put("[Blue](__colors-ui__/get?id=blue){STROOM_TAB}", Hyperlink.builder()
                .text("Blue")
                .href("http://colors/get?id=blue")
                .type(HyperlinkType.TAB.name().toLowerCase())
                .build());
        testLinks.put("[Magenta](__shades-shop__?id=kinda%20purple){STROOM_TAB}", Hyperlink.builder()
                .text("Magenta")
                .href("http://shades.com/shop/item?id=kinda%20purple")
                .type(HyperlinkType.TAB.name().toLowerCase())
                .build());
        testLinks.put("[First](http//some-url/First){BROWSER_TAB}", Hyperlink.builder()
                .text("First")
                .href("http//some-url/First")
                .type(HyperlinkType.BROWSER.name().toLowerCase())
                .build());

//        // When
//        final Map<String, Hyperlink> mappedLinks =
//                testLinks.keySet().stream().collect(Collectors.toMap(r -> r, r -> Hyperlink.detect(r, namedUrls)));
//
//        // Then
//        assertThat(mappedLinks).isEqualTo(testLinks);
    }

    @Disabled // test is not doing anything, assume due detect() not being a thing any more
    @Test
    void testInvalidUrls() {
        // Given
        final List<String> testLinks = new ArrayList<>();
        testLinks.add("[Blue](http://colors/get?id=blue){STROOM_TABULATION}");
        testLinks.add("Orange](http://colors/orange)(whats this?){STROOM_TAB}");
        testLinks.add("Orange](http://colors/orange)(whats this?){STROOM_TAB}");
        testLinks.add("[Blue](http://co(wrapped parenth)lors/get?id=blue{STROOM_TAB}");
        testLinks.add("(Magenta)[http://shades.com/shop/item?id=kinda%20purple]{STROOM_TAB}");

//        // When, url detection applied
//        // Then, all should have returned null hyperlinks
//        final long countNonNull = testLinks.stream()
//                .map(r -> Hyperlink.detect(r, Collections.emptyMap()))
//                .filter(Objects::nonNull)
//                .peek(System.out::println)
//                .count();
//        assertThat(countNonNull).isEqualTo(0);
    }

    @TestFactory
    Stream<DynamicTest> testCreate() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Hyperlink.class)
                .withSingleArgTestFunction(Hyperlink::create)
                .withSimpleEqualityAssertion()
                .addCase("[aaaa](bbbb){cccc}<dddd>", new Hyperlink("aaaa", "bbbb", "cccc", null, "dddd"))
                .addCase("[a](b){c}", new Hyperlink("a", "b", "c", null, null))
                .addCase("[a](b)", new Hyperlink("a", "b", null, null, null))
                .addCase("[a](b){", new Hyperlink("a", "b", null, null, null))
                .addCase("Orange](http://colors/orange)(whats this?){STROOM_TAB}", null)
                .addCase("[Blue](http://co(wrapped parenth)lors/get?id=blue{STROOM_TAB}", null)
                .addCase("(Magenta)[http://shades.com/shop/item?id=kinda%20purple]{STROOM_TAB}", null)
                .addCase("[foo]", null)
                .addCase("[foo](", null)
                .addCase("[foo", null)
                .addCase("", null)
                .addCase(null, null)
                .addCase("[a](b) xxx", new Hyperlink("a", "b", null, null, null))
                .addCase("[aaaaa](bbbbb)<zzzzz>", new Hyperlink("aaaaa", "bbbbb", null, null, "zzzzz"))
                .build();
    }
}
