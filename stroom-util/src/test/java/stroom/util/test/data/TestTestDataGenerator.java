package stroom.util.test.data;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestTestDataGenerator {

    @Test
    public void testCsv_default() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(FlatDataWriterBuilder.defaultCsvFormat())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //addition of header row
        Assert.assertEquals(recCount + 1, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testCsv_custom() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(FlatDataWriterBuilder.builder()
                        .delimitedBy("|")
                        .enclosedBy("\"")
                        .outputHeaderRow(false)
                        .build())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //no header row so lines == recCount
        Assert.assertEquals(recCount, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testXmlElements_default() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(XmlElementsDataWriterBuilder.defaultXmlElementFormat())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //addition of xml declaration, plus opening/closing root elements
        Assert.assertEquals(recCount + 3, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testXmlElements_custom() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(XmlElementsDataWriterBuilder.builder()
                        .namespace("myNamespace")
                        .rootElementName("myRootElm")
                        .recordElementName("myRecordElm")
                        .build())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //addition of xml declaration, plus opening/closing root elements
        Assert.assertEquals(recCount + 3, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testXmlAttributes_default() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(XmlAttributesDataWriterBuilder.defaultXmlElementFormat())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //addition of xml declaration, plus opening/closing root elements
        Assert.assertEquals(recCount + 3, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testXmlAttributes_custom() {
        TestDataGenerator.DefinitionBuilder builder = buildBasicDefinition();

        int recCount = 10;
        Queue<String> lines = new ConcurrentLinkedQueue<>();
        builder
                .setDataWriter(XmlAttributesDataWriterBuilder.builder()
                        .namespace("myNamespace")
                        .rootElementName("myRootElm")
                        .recordElementName("myRecordElm")
                        .fieldValueElementName("myFieldValue")
                        .build())
                .rowCount(recCount)
                .consumedBy(stringStream ->
                        stringStream.forEach(lines::add))
                .generate();

        //addition of xml declaration, plus opening/closing root elements
        Assert.assertEquals(recCount + 3, lines.size());
        lines.forEach(System.out::println);
    }

    @Test
    public void testRandomWordsField_singleItem() {
        Field field = TestDataGenerator.randomWordsField(
                "myField",
                2,
                4,
                Collections.singletonList("MY_SINGLE_VALUE"));

        String val = field.getNext();

        System.out.println("val = " + val);
    }

    @Test (expected = RuntimeException.class)
    public void testRandomWordsField_emptyList() {
        Field field = TestDataGenerator.randomWordsField(
                "myField",
                2,
                4,
                Collections.emptyList());

        String val = field.getNext();
    }

    private TestDataGenerator.DefinitionBuilder buildBasicDefinition() {
        //start building a definition that uses all field types
        return TestDataGenerator.buildDefinition()
                .multiThreaded()
                .addFieldDefinition(TestDataGenerator.sequentialValueField(
                        "sequentialValueField",
                        Arrays.asList("One", "Two", "Three")))
                .addFieldDefinition(TestDataGenerator.randomValueField(
                        "randomValueField",
                        Arrays.asList("Red", "Green", "Blue")))
                .addFieldDefinition(TestDataGenerator.randomNumberedValueField(
                        "randomNumberedValueField",
                        "user-%s",
                        3))
                .addFieldDefinition(TestDataGenerator.sequentiallyNumberedValueField(
                        "sequentiallyNumberedValueField",
                        "user-%s",
                        5,
                        10))
                .addFieldDefinition(TestDataGenerator.sequentialNumberField(
                        "sequentialNumberField",
                        5,
                        10))
                .addFieldDefinition(TestDataGenerator.randomNumberField(
                        "randomNumberField",
                        1,
                        5))
                .addFieldDefinition(TestDataGenerator.randomIpV4Field("randomIpV4Field"))
                .addFieldDefinition(TestDataGenerator.randomDateTimeField(
                        "randomDateTimeField",
                        LocalDateTime.of(2016, 1, 1, 0, 0, 0),
                        LocalDateTime.of(2018, 1, 1, 0, 0, 0),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .addFieldDefinition(TestDataGenerator.randomDateTimeField(
                        "randomDateTimeField2",
                        LocalDateTime.of(2016, 1, 1, 0, 0, 0),
                        LocalDateTime.of(2018, 1, 1, 0, 0, 0),
                        "yyyyMMdd"))
                .addFieldDefinition(TestDataGenerator.sequentialDateTimeField(
                        "sequentialDateTimeField",
                        LocalDateTime.of(2016, 1, 1, 0, 0, 0),
                        Duration.ofDays(1),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .addFieldDefinition(TestDataGenerator.sequentialDateTimeField(
                        "sequentialDateTimeField2",
                        LocalDateTime.of(2016, 1, 1, 0, 0, 0),
                        Duration.ofDays(1),
                        "yyyyMMdd"))
                .addFieldDefinition(TestDataGenerator.uuidField("uuidField"))
                .addFieldDefinition(TestDataGenerator.randomClassNamesField(
                        "randomClassNamesField",
                        0,
                        3))
                .addFieldDefinition(TestDataGenerator.randomWordsField(
                        "randomWordsField",
                        0,
                        3,
                        Arrays.asList("attractive", "bald", "beautiful", "chubby", "drab", "elegant", "scruffy", "fit", "glamorous", "handsome", "unkempt")));
    }

}