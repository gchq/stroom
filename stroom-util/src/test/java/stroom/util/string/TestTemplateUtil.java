package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil.GeneratorBuilder;
import stroom.util.string.TemplateUtil.Templator;

import com.google.common.base.Strings;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemplateUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestTemplateUtil.class);

    @TestFactory
    Stream<DynamicTest> testTemplator() {
        final Map<String, String> populatedMap = Map.of(
                "number", "123",
                "animal", "cow",
                "food", "cheese",
                "colour", "indigo");
        final Map<String, String> emptyMap = Map.of();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Map<String, String>, String>>() {

                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Map<String, String> map = NullSafe.map(testCase.getInput()._1);
                    final String template = testCase.getInput()._2;
                    final Templator templator = TemplateUtil.parseTemplate(
                            template,
                            String::toUpperCase,
                            String::toLowerCase);
                    final String output1 = templator.buildGenerator()
                            .addCommonReplacementFunction(map::get)
                            .generate();

                    // Check re-use
                    final GeneratorBuilder generatorBuilder2 = templator.buildGenerator();
                    map.forEach(generatorBuilder2::addReplacement);

                    final String output2 = generatorBuilder2.generate();
                    assertThat(output2)
                            .isEqualTo(output1);

                    final GeneratorBuilder generatorBuilder3 = templator.buildGenerator();
                    map.forEach((var, value) ->
                            generatorBuilder3.addLazyReplacement(var, () -> value));
                    final String output3 = generatorBuilder3.generate();
                    assertThat(output3)
                            .isEqualTo(output1);

                    final String output4 = templator.generateWith(map);
                    assertThat(output4)
                            .isEqualTo(output1);

                    return output1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, "${animal}_and_${food}"), "_and_")
                .addCase(Tuple.of(emptyMap, "${animal}_and_${food}"), "_and_")
                .addCase(Tuple.of(populatedMap, "${animal}_and_${food}"), "COW_and_CHEESE")
                .addCase(Tuple.of(populatedMap, "${drink}_and_${food}"), "_and_CHEESE")
                .addCase(Tuple.of(populatedMap, "a ${drink} and a ${snack}"), "a  and a ")
                .addCase(Tuple.of(populatedMap, "all static text"), "all static text")
                .addCase(Tuple.of(populatedMap, "${unknown}"), "")
                .addCase(Tuple.of(populatedMap, "${food}_${food}_${food}"), "CHEESE_CHEESE_CHEESE")
                .build();
    }

    @Test
    void testFunctionReUse() {
        final AtomicInteger counter = new AtomicInteger(1);
        final Templator templator = TemplateUtil.parseTemplate("The count is ${count} then ${count} then ${count}");
        assertThat(templator.getVarsInTemplate())
                .containsExactlyInAnyOrder("count");
        // The replacement provider func should only be called once to get the replacement,
        // then the replacement reused.
        final String output = templator.buildGenerator()
                .addLazyReplacement("count", () ->
                        String.valueOf(counter.getAndIncrement()))
                .generate();
        assertThat(output)
                .isEqualTo("The count is 1 then 1 then 1");
    }

    @Test
    void testNullValues1() {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("food", null);
        replacements.put("drink", "");
        replacements.put("animal", "cow");

        final Templator templator = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = templator.generateWith(replacements);
        assertThat(output)
                .isEqualTo(",  and cow");
    }

    @Test
    void testNullValues2() {
        final Map<String, String> replacements = new HashMap<>();
        replacements.put("food", null);
        replacements.put("drink", "");
        replacements.put("animal", "cow");

        final Templator templator = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = templator.buildGenerator()
                .addCommonReplacementFunction(replacements::get)
                .generate();
        assertThat(output)
                .isEqualTo(",  and cow");
    }

    @Test
    void testNullMap() {

        final Templator templator = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = templator.generateWith(null);
        assertThat(output)
                .isEqualTo(",  and ");
    }

    @Test
    void testTemplatorReUse1() {
        final Templator templator = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");

        final String output1 = templator.buildGenerator()
                .addReplacements(Map.of(
                        "food", "cheese",
                        "drink", "milk",
                        "animal", "toad"))
                .generate();

        final String output2 = templator.buildGenerator()
                .addReplacements(Map.of(
                        "food", "scampi fries",
                        "drink", "beer",
                        "animal", "worm"))
                .generate();

        assertThat(output1)
                .isEqualTo("cheese, milk and toad");
        assertThat(output2)
                .isEqualTo("scampi fries, beer and worm");
    }

    @Test
    void testTemplatorReUse2() {
        final Templator templator = TemplateUtil.parseTemplate("${food}, ${drink} and some more ${food}");

        final AtomicInteger counter1 = new AtomicInteger(100);
        final Map<String, String> map1 = Map.of(
                "food", "cheese",
                "drink", "milk",
                "animal", "toad");
        final String output1 = templator.buildGenerator()
                .addCommonReplacementFunction(key ->
                        map1.get(key) + "_" + counter1.incrementAndGet())
                .generate();

        final AtomicInteger counter2 = new AtomicInteger(200);
        final Map<String, String> map2 = Map.of(
                "food", "scampi_fries",
                "drink", "beer",
                "animal", "worm");
        final String output2 = templator.buildGenerator()
                .addCommonReplacementFunction(key ->
                        map2.get(key) + "_" + counter2.incrementAndGet())
                .generate();

        assertThat(output1)
                .isEqualTo("cheese_101, milk_102 and some more cheese_101");
        assertThat(output2)
                .isEqualTo("scampi_fries_201, beer_202 and some more scampi_fries_201");
    }

    @Disabled // Manual perf testing only
    @Test
    void testPerf() {
//        final long cnt = 1_000L;
        final long cnt = 1_000_000L;
        final int rounds = 10;
        final List<String> animals = makeData(cnt, "animal");
        final List<String> drinks = makeData(cnt, "drink");
        final List<String> foods = makeData(cnt, "food");
        assertThat(animals)
                .hasSize((int) cnt);
//        final String template = "Animal: ${animal}, drink: ${drink}, food: ${food}, " +
//                                "unknown: ${unknown} and drink: ${drink}";
        final String template = "Animal: ${animal}, drink: ${drink}, food: ${food} and drink: ${drink}";
        final Templator templator = TemplateUtil.parseTemplate(template);

        final List<String> outputs1 = new ArrayList<>((int) cnt);
        final List<String> outputs2 = new ArrayList<>((int) cnt);
        final List<String> outputs3 = new ArrayList<>((int) cnt);
        final List<String> outputs4 = new ArrayList<>((int) cnt);
        final SimplePathCreator pathCreator = new SimplePathCreator(null, null);

        final TimedCase templatorCase1 = TimedCase.of("Templator1", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final String output = templator.buildGenerator()
                        .addReplacement("animal", animals.get(i))
                        .addReplacement("drink", drinks.get(i))
                        .addReplacement("food", foods.get(i))
                        .generate();
                outputs1.add(output);
            }
        });
        final TimedCase templatorCase2 = TimedCase.of("Templator2", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final String output = templator.generateWith(Map.of(
                        "animal", animals.get(i),
                        "drink", drinks.get(i),
                        "food", foods.get(i)));
                outputs2.add(output);
            }
        });

        final Pattern varPattern = Pattern.compile("\\$\\{.*}");
        final TimedCase replaceAllCase = TimedCase.of("Replace", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                String output = template
                        .replace("${animal}", animals.get(i))
                        .replace("${drink}", drinks.get(i))
                        .replace("${food}", foods.get(i));
//                output = output.replace("${unknown}", "");
//                output = varPattern.matcher(output)
//                        .replaceAll("");
                outputs3.add(output);
            }
        });

        final TimedCase pathCreatorCase = TimedCase.of("PathCreator", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int finalI = i;
                String output = template;
                output = pathCreator.replace(output, "animal", () -> animals.get(finalI));
                output = pathCreator.replace(output, "drink", () -> drinks.get(finalI));
                output = pathCreator.replace(output, "food", () -> foods.get(finalI));
//                output = pathCreator.replace(output, "unknown", () -> "");
                // Clear unused vars
                output = pathCreator.clearVars(output);
                outputs4.add(output);
            }
        });

        TestUtil.comparePerformance(
                rounds,
                cnt,
                LOGGER::info,
                templatorCase1,
                templatorCase2,
                replaceAllCase,
                pathCreatorCase);

        final int listSize = (int) (cnt * rounds);
        assertThat(outputs1)
                .hasSize(listSize);
        assertThat(outputs2)
                .hasSize(listSize);
        assertThat(outputs3)
                .hasSize(listSize);
        assertThat(outputs4)
                .hasSize(listSize);

        assertThat(outputs1.getFirst())
                .isEqualTo(
                        "Animal: animal_0000000001, drink: drink_0000000001, " +
                        "food: food_0000000001 and drink: drink_0000000001");

        for (int i = 0; i < outputs1.size(); i++) {
            assertThat(outputs1.get(i))
                    .isEqualTo(outputs2.get(i));
            assertThat(outputs1.get(i))
                    .isEqualTo(outputs3.get(i));
            assertThat(outputs1.get(i))
                    .isEqualTo(outputs4.get(i));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private List<String> makeData(final long iterations, final String prefix) {
        return LongStream.rangeClosed(1, iterations)
                .boxed()
                .map(i ->
                        prefix
                        + "_"
                        + Strings.padStart(String.valueOf(i), 10, '0'))
                .toList();
    }
}
