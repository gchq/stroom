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
        final Map<String, Hyperlink> testLinks = new HashMap<>();
        testLinks.put("[Orange](http://colors/orange){DIALOG}", new Hyperlink.HyperlinkBuilder()
                .title("Orange")
                .href("http://colors/orange")
                .openType(HyperlinkTarget.DIALOG)
                .build());
        testLinks.put("[Blue](http://colors/get?id=blue){STROOM_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("Blue")
                .href("http://colors/get?id=blue")
                .openType(HyperlinkTarget.STROOM_TAB)
                .build());
        testLinks.put("[Magenta](http://shades.com/shop/item?id=kinda%20purple){STROOM_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("Magenta")
                .href("http://shades.com/shop/item?id=kinda%20purple")
                .openType(HyperlinkTarget.STROOM_TAB)
                .build());
        testLinks.put("[First](http//some-url/First){BROWSER_TAB}", new Hyperlink.HyperlinkBuilder()
                .title("First")
                .href("http//some-url/First")
                .openType(HyperlinkTarget.BROWSER_TAB)
                .build());

        // When
        final Map<String, Hyperlink> mappedLinks =
                testLinks.keySet().stream().collect(Collectors.toMap(r -> r, Hyperlink::detect));

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
                .map(Hyperlink::detect)
                .filter(Objects::nonNull)
                .peek(System.out::println)
                .count();
        assertEquals(0, countNonNull);
    }
}
