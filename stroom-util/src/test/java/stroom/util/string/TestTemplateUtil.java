/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.string.TemplateUtil.ExecutorBuilder;
import stroom.util.string.TemplateUtil.Template;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemplateUtil {

    @TestFactory
    Stream<DynamicTest> testTemplator() {
        final Map<CIKey, String> populatedMap = CIKey.mapOf(
                "number", "123",
                "animal", "cow",
                "food", "cheese",
                "colour", "indigo");
        final Map<CIKey, String> emptyMap = CIKey.mapOf(Map.of());

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Map<CIKey, String>, String>>() {

                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Map<CIKey, String> map = Objects.requireNonNullElse(testCase.getInput()._1, emptyMap);
                    final String templateStr = testCase.getInput()._2;
                    final Template templator = TemplateUtil.parseTemplate(
                            templateStr,
                            String::toUpperCase,
                            String::toLowerCase);
                    final String output1 = templator.buildExecutor()
                            .addCommonReplacementFunction(map::get)
                            .execute();

                    // Check re-use
                    final ExecutorBuilder executorBuilder2 = templator.buildExecutor();
                    map.forEach(executorBuilder2::addReplacement);

                    final String output2 = executorBuilder2.execute();
                    assertThat(output2)
                            .isEqualTo(output1);

                    final ExecutorBuilder executorBuilder3 = templator.buildExecutor();
                    map.forEach((var, value) ->
                            executorBuilder3.addLazyReplacement(var, () -> value));
                    final String output3 = executorBuilder3.execute();
                    assertThat(output3)
                            .isEqualTo(output1);

                    final String output4 = templator.executeWith(testCase.getInput()._1);
                    assertThat(output4)
                            .isEqualTo(output1);

                    return output1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, "${animal}_and_${food}"), "_and_")
                .addCase(Tuple.of(emptyMap, "${animal}_and_${food}"), "_and_")
                .addCase(Tuple.of(populatedMap, "${animal}_and_${food}"), "COW_and_CHEESE")
                .addCase(Tuple.of(populatedMap, "${animal}${food}"), "COWCHEESE")
                .addCase(Tuple.of(populatedMap, "${drink}_and_${food}"), "_and_CHEESE")
                .addCase(Tuple.of(populatedMap, "a ${drink} and a ${snack}"), "a  and a ")
                .addCase(Tuple.of(populatedMap, "all static text"), "all static text")
                .addCase(Tuple.of(populatedMap, "${unknown}"), "")
                .addCase(Tuple.of(populatedMap, "${food}_${food}_${food}"), "CHEESE_CHEESE_CHEESE")
                .addCase(Tuple.of(populatedMap, "${food}${food}${food}"), "CHEESECHEESECHEESE")
                .addCase(Tuple.of(populatedMap, "xxx${food}"), "xxxCHEESE")
                .addCase(Tuple.of(populatedMap, "${food}xxx"), "CHEESExxx")
                .addCase(Tuple.of(populatedMap, "xxx${food}xxx"), "xxxCHEESExxx")
                .addCase(Tuple.of(populatedMap, "   ${food}   "), "   CHEESE   ")
                .addCase(Tuple.of(populatedMap, ""), "")
                .addCase(Tuple.of(populatedMap, " "), " ")
                .addCase(Tuple.of(populatedMap, "    "), "    ")
                .addCase(Tuple.of(populatedMap, null), "")
                .build();
    }

    @Test
    void testFunctionReUse() {
        final AtomicInteger counter = new AtomicInteger(1);
        final Template template = TemplateUtil.parseTemplate("The count is ${count} then ${count} then ${count}");
        assertThat(template.getVarsInTemplate())
                .containsExactlyInAnyOrder(CIKey.ofDynamicKey("count"));
        // The replacement provider func should only be called once to get the replacement,
        // then the replacement reused.
        final String output = template.buildExecutor()
                .addLazyReplacement(CIKey.ofDynamicKey("count"), () ->
                        String.valueOf(counter.getAndIncrement()))
                .execute();
        assertThat(output)
                .isEqualTo("The count is 1 then 1 then 1");
    }

    @Test
    void testNullValues1() {
        final Map<CIKey, String> replacements = new HashMap<>();
        replacements.put(CIKey.ofDynamicKey("food"), null);
        replacements.put(CIKey.ofDynamicKey("drink"), "");
        replacements.put(CIKey.ofDynamicKey("animal"), "cow");

        final Template template = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = template.executeWith(replacements);
        assertThat(output)
                .isEqualTo(",  and cow");
    }

    @Test
    void testNullValues2() {
        final Map<CIKey, String> replacements = new HashMap<>();
        replacements.put(CIKey.ofDynamicKey("food"), null);
        replacements.put(CIKey.ofDynamicKey("drink"), "");
        replacements.put(CIKey.ofDynamicKey("animal"), "cow");

        final Template template = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = template.buildExecutor()
                .addCommonReplacementFunction(replacements::get)
                .execute();
        assertThat(output)
                .isEqualTo(",  and cow");
    }

    @Test
    void testNullMap() {

        final Template template = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");
        final String output = template.executeWith(null);
        assertThat(output)
                .isEqualTo(",  and ");
    }

    @Test
    void testTemplatorReUse1() {
        final Template template = TemplateUtil.parseTemplate("${food}, ${drink} and ${animal}");

        final String output1 = template.buildExecutor()
                .addReplacements(Map.of(
                        CIKey.ofDynamicKey("food"), "cheese",
                        CIKey.ofDynamicKey("drink"), "milk",
                        CIKey.ofDynamicKey("animal"), "toad"))
                .execute();

        final String output2 = template.buildExecutor()
                .addReplacements(Map.of(
                        CIKey.ofDynamicKey("food"), "scampi fries",
                        CIKey.ofDynamicKey("drink"), "beer",
                        CIKey.ofDynamicKey("animal"), "worm"))
                .execute();

        assertThat(output1)
                .isEqualTo("cheese, milk and toad");
        assertThat(output2)
                .isEqualTo("scampi fries, beer and worm");
    }

    @Test
    void testTemplatorReUse2() {
        final Template template = TemplateUtil.parseTemplate("${food}, ${drink} and some more ${food}");

        final AtomicInteger counter1 = new AtomicInteger(100);
        final Map<CIKey, String> map1 = CIKey.mapOf(Map.of(
                "food", "cheese",
                "drink", "milk",
                "animal", "toad"));
        final String output1 = template.buildExecutor()
                .addCommonReplacementFunction(key ->
                        map1.get(key) + "_" + counter1.incrementAndGet())
                .execute();

        final AtomicInteger counter2 = new AtomicInteger(200);
        final Map<CIKey, String> map2 = CIKey.mapOf(Map.of(
                "food", "scampi_fries",
                "drink", "beer",
                "animal", "worm"));
        final String output2 = template.buildExecutor()
                .addCommonReplacementFunction(key ->
                        map2.get(key) + "_" + counter2.incrementAndGet())
                .execute();

        assertThat(output1)
                .isEqualTo("cheese_101, milk_102 and some more cheese_101");
        assertThat(output2)
                .isEqualTo("scampi_fries_201, beer_202 and some more scampi_fries_201");
    }

    @Test
    void testTimeReplacements() {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018,
                8,
                20,
                13,
                17,
                22,
                123456789,
                ZoneOffset.UTC);
        final String templateStr =
                "${foo}__${year}/${year}-${month}/${year}-${month}-${day}/${hour}:${minute}:${second}.${millis}";
        final Template templator = TemplateUtil.parseTemplate(templateStr);
        final String output = templator.buildExecutor()
                .addStandardTimeReplacements(zonedDateTime)
                .execute();
        assertThat(output)
                .isEqualTo("__2018/2018-08/2018-08-20/13:17:22.123");
    }

    @Test
    void testUuidReplacement_reuse() {
        final String templateStr = "${uuid},${uuid}";
        final Template templator = TemplateUtil.parseTemplate(templateStr);
        final String output = templator.buildExecutor()
                .addUuidReplacement(true)
                .execute();

        final String[] parts = output.split(",");
        assertThat(parts)
                .hasSize(2);
        final UUID uuid1 = UUID.fromString(parts[0]);
        final UUID uuid2 = UUID.fromString(parts[1]);
        assertThat(uuid1)
                .isEqualTo(uuid2);
    }

    @Test
    void testUuidReplacement_unique() {
        final String templateStr = "${uuid},${uuid}";
        final Template templator = TemplateUtil.parseTemplate(templateStr);
        final String output = templator.buildExecutor()
                .addUuidReplacement(false)
                .execute();

        final String[] parts = output.split(",");
        assertThat(parts)
                .hasSize(2);
        final UUID uuid1 = UUID.fromString(parts[0]);
        final UUID uuid2 = UUID.fromString(parts[1]);
        assertThat(uuid1)
                .isNotEqualTo(uuid2);
    }

    @Test
    void testDynamicProviders() {
        final Template template = TemplateUtil.parseTemplate("${a},${b},${c}");
        final String output = template.buildExecutor()
                .addReplacement(CIKey.ofDynamicKey("b"), "BBB")
                .addDynamicReplacementProvider(ignored -> Optional.empty())
                .addDynamicReplacementProvider(var ->
                        CIKey.ofDynamicKey("a").equals(var)
                                ? Optional.of("AAA")
                                : Optional.empty())
                .addDynamicReplacementProvider(var -> {
                    final String replacement;
                    if (CIKey.ofDynamicKey("a").equals(var)) {
                        replacement = "aaa"; // 1nd dynamic provider used for this
                    } else if (CIKey.ofDynamicKey("b").equals(var)) {
                        replacement = "bbb"; // Static one is used for ${b}
                    } else if (CIKey.ofDynamicKey("c").equals(var)) {
                        replacement = "ccc";
                    } else {
                        replacement = "";
                    }
                    return Optional.of(replacement);
                })
                .execute();

        assertThat(output)
                .isEqualTo("AAA,BBB,ccc");
    }

    @Test
    void testFileNameReplacement() {
        final Template template = TemplateUtil.parseTemplate("__${fileStem}.${fileExtension} - ${fileName}__");
        final String output = template.buildExecutor()
                .addFileNameReplacement("foo.txt")
                .execute();
        assertThat(output)
                .isEqualTo("__foo.txt - foo.txt__");
    }

    @Test
    void testFileNameReplacement2() {
        final Template template = TemplateUtil.parseTemplate("__${foo}__");
        final String output = template.buildExecutor()
                .addFileNameReplacement("foo.txt")
                .addReplacement(CIKey.ofDynamicKey("foo"), "bar")
                .execute();

        assertThat(output)
                .isEqualTo("__bar__");
    }

    @Test
    void testCaseInsense() {
        final Template template = TemplateUtil.parseTemplate("__${foo}__${Foo}__${FOO}__");
        final String output = template.buildExecutor()
                .addReplacement(CIKey.ofDynamicKey("foo"), "bar")
                .execute();

        assertThat(output)
                .isEqualTo("__bar__bar__bar__");
    }

    @Test
    void testSystemPropReplacement(@TempDir final Path tempDir) throws IOException {
        final String propKey = "stroom.test.29348023984";
        final String propVal = "prop-val";
        System.setProperty(propKey, propVal);
        final Template template = TemplateUtil.parseTemplate("__${stroom.test.29348023984}__${FOO}__");
        final String output = template.buildExecutor()
                .addReplacement(CIKey.ofDynamicKey("foo"), "bar")
                .addSystemPropertyReplacements(
                        () -> tempDir.resolve("home"),
                        () -> tempDir.resolve("temp"))
                .execute();
        assertThat(output)
                .isEqualTo("__prop-val__bar__");
    }

    @Test
    void testAllStatic() {
        assertThat(TemplateUtil.parseTemplate("foo").isStatic())
                .isTrue();
        assertThat(TemplateUtil.parseTemplate("foo${bar}").isStatic())
                .isFalse();
    }
}
