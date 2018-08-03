package stroom.dashboard.client.table;

import org.junit.Test;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class HyperlinkTest {
    @Test
    public void testValidUrl() {
        // Given
        final Map<String, String> namedUrls = new HashMap<>();
        namedUrls.put("orange-ui", "http://colors/orange");
        namedUrls.put("colors-ui", "http://colors");
        namedUrls.put("shades-shop", "http://shades.com/shop/item");

        final Map<String, Hyperlink> testLinks = new HashMap<>();
        testLinks.put("[Orange](__orange-ui__){DIALOG}", new Hyperlink.HyperlinkBuilder()
                .title("Orange")
                .href("http://colors/orange")
                .target(HyperlinkTarget.DIALOG)
                .build());
        testLinks.put("[Blue](__colors-ui__/get?id=blue){STROOM_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("Blue")
                .href("http://colors/get?id=blue")
                .target(HyperlinkTarget.STROOM_TAB)
                .build());
        testLinks.put("[Magenta](__shades-shop__?id=kinda%20purple){STROOM_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("Magenta")
                .href("http://shades.com/shop/item?id=kinda%20purple")
                .target(HyperlinkTarget.STROOM_TAB)
                .build());
        testLinks.put("[First](http//some-url/First){BROWSER_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("First")
                .href("http//some-url/First")
                .target(HyperlinkTarget.BROWSER_TAB)
                .build());

        // When
        final Map<String, Hyperlink> mappedLinks =
                testLinks.keySet().stream().collect(Collectors.toMap(r -> r, r -> Hyperlink.detect(r, namedUrls)));

        // Then
        assertEquals(testLinks, mappedLinks);
    }

    @Test
    public void testInvalidUrls() {
        // Given
        final List<String> testLinks = new ArrayList<>();
        testLinks.add("[Blue](http://colors/get?id=blue){STROOM_TABULATION}");
        testLinks.add("Orange](http://colors/orange)(whats this?){STROOM_TAB}");
        testLinks.add("Orange](http://colors/orange)(whats this?){STROOM_TAB}");
        testLinks.add("[Blue](http://co(wrapped parenth)lors/get?id=blue{STROOM_TAB}");
        testLinks.add("(Magenta)[http://shades.com/shop/item?id=kinda%20purple]{STROOM_TAB}");

        // When, url detection applied
        // Then, all should have returned null hyperlinks
        final long countNonNull = testLinks.stream()
                .map(r -> Hyperlink.detect(r, Collections.emptyMap()))
                .filter(Objects::nonNull)
                .peek(System.out::println)
                .count();
        assertEquals(0, countNonNull);
    }
}
