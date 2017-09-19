package stroom.dashboard.client.table;

import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class UrlDetectorTest {
    private final UrlDetector urlDetector = new UrlDetector();

    @Test
    public void testValidUrl() {
        // Given
        final Map<String, UrlDetector.Hyperlink> testLinks = new HashMap<>();
        testLinks.put("[Orange](http://colors/orange)", new UrlDetector.HyperlinkBuilder()
                .title("Orange")
                .href("http://colors/orange")
                .build());
        testLinks.put("[Blue](http://colors/get?id=blue)", new UrlDetector.HyperlinkBuilder()
                .title("Blue")
                .href("http://colors/get?id=blue")
                .build());
        testLinks.put("[Magenta](http://shades.com/shop/item?id=kinda%20purple)", new UrlDetector.HyperlinkBuilder()
                .title("Magenta")
                .href("http://shades.com/shop/item?id=kinda%20purple")
                .build());
        testLinks.put("[First](http//some-url/First)", new UrlDetector.HyperlinkBuilder()
                .title("First")
                .href("http//some-url/First")
                .build());

        // When
        final Map<String, UrlDetector.Hyperlink> mappedLinks =
                testLinks.keySet().stream().collect(Collectors.toMap(r -> r, urlDetector::detect));

        // Then
        assertEquals(testLinks, mappedLinks);
    }

    @Test
    public void testInvalidUrls() {
        // Given
        final List<String> testLinks = new ArrayList<>();
        testLinks.add("Orange](http://colors/orange)(whats this?)");
        testLinks.add("[Blue](http://co(wrapped parenth)lors/get?id=blue");
        testLinks.add("(Magenta)[http://shades.com/shop/item?id=kinda%20purple]");

        // When, url detection applied
        // Then, all should have returned null hyperlinks
        final long countNonNull = testLinks.stream()
                .map(urlDetector::detect)
                .filter(Objects::nonNull)
                .peek(System.out::println)
                .count();
        assertEquals(0, countNonNull);
    }
}
