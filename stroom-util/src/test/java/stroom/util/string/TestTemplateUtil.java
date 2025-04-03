package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.string.TemplateUtil.Templator;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
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
                    final Map<String, String> map = testCase.getInput()._1;
                    final String template = testCase.getInput()._2;
                    final Templator templator = TemplateUtil.parseTemplate(
                            template,
                            String::toUpperCase,
                            String::toLowerCase);
                    final String output1 = templator.apply(map);
                    // Check re-use
                    final String output2 = templator.apply(map);
                    assertThat(output2)
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
                .build();
    }
}
