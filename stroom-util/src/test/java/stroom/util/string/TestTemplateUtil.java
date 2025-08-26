package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil.GeneratorBuilder;
import stroom.util.string.TemplateUtil.Templator;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemplateUtil {

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
                            .addCommonReplacements(map::get)
                            .generate();

                    // Check re-use
                    final GeneratorBuilder generatorBuilder2 = templator.buildGenerator();
                    map.forEach(generatorBuilder2::addStaticReplacement);

                    final String output2 = generatorBuilder2.generate();
                    assertThat(output2)
                            .isEqualTo(output1);

                    final GeneratorBuilder generatorBuilder3 = templator.buildGenerator();
                    map.forEach((var, value) ->
                            generatorBuilder2.addLazyReplacement(var, aVar -> value));
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
        final Templator templator = TemplateUtil.parseTemplate("The count is ${count} and ${count}");
        assertThat(templator.getVarsInTemplate())
                .containsExactlyInAnyOrder("count");
        // The replacement provider func should only be called once to get the replacement,
        // then the replacement reused.
        final String output = templator.buildGenerator()
                .addLazyReplacement("count", aVar ->
                        String.valueOf(counter.getAndIncrement()))
                .generate();
        assertThat(output)
                .isEqualTo("The count is 1 and 1");
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
                .addCommonReplacements(replacements::get)
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
}
