package stroom.dashboard.client.table;

import org.junit.Test;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class HyperlinkTest {
    @Test
    public void testValidUrl() {
        final Map<String, Hyperlink> testLinks = new HashMap<>();
        testLinks.put("[Orange](http://colors/orange){dialog}", new Hyperlink.HyperlinkBuilder()
                .title("Orange")
                .href("http://colors/orange")
                .type(HyperlinkType.DIALOG)
                .build());
        testLinks.put("[Blue](http://colors/get?id=blue){tab}", new Hyperlink.HyperlinkBuilder()
                .title("Blue")
                .href("http://colors/get?id=blue")
                .type(HyperlinkType.TAB)
                .build());
        testLinks.put("[Magenta](http://shades.com/shop/item?id=kinda%20purple){tab}", new Hyperlink.HyperlinkBuilder()
                .title("Magenta")
                .href("http://shades.com/shop/item?id=kinda%20purple")
                .type(HyperlinkType.TAB)
                .build());
        testLinks.put("[First](http//some-url/First){browser}", new Hyperlink.HyperlinkBuilder()
                .title("First")
                .href("http//some-url/First")
                .type(HyperlinkType.BROWSER)
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
        testLinks.add("[Blue](http://colors/get?id=blue){tab}");
        testLinks.add("Orange](http://colors/orange)(whats this?){tab}");
        testLinks.add("Orange](http://colors/orange)(whats this?){tab}");
        testLinks.add("[Blue](http://co(wrapped parenth)lors/get?id=blue{tab}");
        testLinks.add("(Magenta)[http://shades.com/shop/item?id=kinda%20purple]{tab}");

        // When, url detection applied
        // Then, all should have returned null hyperlinks
        final long countNonNull = testLinks.stream()
                .map(Hyperlink::detect)
                .filter(Objects::nonNull)
                .peek(System.out::println)
                .count();
        assertEquals(2, countNonNull);
    }
}
