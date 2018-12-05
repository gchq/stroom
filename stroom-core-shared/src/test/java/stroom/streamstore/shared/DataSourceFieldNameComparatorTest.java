package stroom.streamstore.shared;

import org.junit.Test;

import stroom.streamstore.shared.DataSourceFieldNameComparator.WordCountComparator;
import stroom.streamstore.shared.DataSourceFieldNameComparator.NameComparator;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DataSourceFieldNameComparatorTest {

    @Test
    public void testWordCountComparator() {
        final WordCountComparator instance = WordCountComparator.getInstance();

        int firstLonger = instance.compare("more words", "less");
        int secondLonger = instance.compare("less", "than this");
        int firstEqualSecond = instance.compare("the same", "as eachother");

        assertTrue(firstLonger > 0);
        assertTrue(secondLonger < 0);
        assertEquals(firstEqualSecond, 0);
    }

    @Test
    public void testWordCountComparatorSort() {
        final List<String> strings = Arrays.asList("Two Words", "One", "Four Words of Wisdom", "Three Word Sentence");
        strings.sort(WordCountComparator.getInstance());

        assertEquals(Arrays.asList("One", "Two Words", "Three Word Sentence", "Four Words of Wisdom"), strings);
    }

    @Test
    public void testNameComparator() {
        final NameComparator instance = NameComparator.getInstance();

        int firstAfter = instance.compare("Sent Count", "Received Count");
        int secondAfter = instance.compare("Received Count", "Sent Count");
        int firstEqualSecond = instance.compare("Received Count", "Received Count");

        assertTrue(firstAfter > 0);
        assertTrue(secondAfter < 0);
        assertEquals(firstEqualSecond, 0);
    }

    @Test
    public void testNameComparatorSort() {
        final List<String> strings = Arrays.asList("Created Time", "Stream Count", "Byte Count", "Received Time", "Word Count", "A Time");
        strings.sort(NameComparator.getInstance());

        assertEquals(Arrays.asList("Byte Count", "Stream Count", "Word Count", "A Time", "Created Time", "Received Time"), strings);

    }
}
