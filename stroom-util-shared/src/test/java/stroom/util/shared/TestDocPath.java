/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.shared;

import stroom.test.common.TestUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocPath {

    @Test
    void blank() {
        final DocPath docPath = DocPath.blank();

        assertThat(docPath.toString())
                .isEqualTo("/");
        assertThat(docPath.toRelativePath().toString())
                .isEqualTo("");

        assertThat(docPath.getParts())
                .isEmpty();

        assertThat(docPath.isBlank())
                .isTrue();

        assertThat(DocPath.blank().toRelativePath())
                .isNotEqualTo(DocPath.blank());
    }

    @Test
    void testRoot() {
        final DocPath root = DocPath.blank();
        assertThat(root.isAbsolute())
                .isTrue();
        assertThat(root.toString())
                .isEqualTo("/");
    }

    @Test
    void testAbsolute() {
        final DocPath docPath = DocPath.fromPathString("/stroom/node/name");
        Assertions.assertThat(docPath.isAbsolute())
                .isTrue();
        Assertions.assertThat(docPath.isRelative())
                .isFalse();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void testAbsolute2() {
        final DocPath docPath = DocPath.fromPathString("/stroom/node/name/");
        Assertions.assertThat(docPath.isAbsolute())
                .isTrue();
        Assertions.assertThat(docPath.isRelative())
                .isFalse();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void testRelative() {
        final DocPath docPath = DocPath.fromPathString("stroom/node/name");
        Assertions.assertThat(docPath.isAbsolute())
                .isFalse();
        Assertions.assertThat(docPath.isRelative())
                .isTrue();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("stroom/node/name");
    }

    @Test
    void testRelative2() {
        final DocPath docPath = DocPath.fromPathString("stroom/node/name/");
        Assertions.assertThat(docPath.isAbsolute())
                .isFalse();
        Assertions.assertThat(docPath.isRelative())
                .isTrue();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("stroom/node/name");
    }

    @Test
    void testWhitespace() {
        final DocPath docPath = DocPath.fromPathString(" / stroom / node / name / ");
        Assertions.assertThat(docPath.isAbsolute())
                .isTrue();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void testWhitespace2() {
        final DocPath docPath = DocPath.fromPathString("  stroom / node / name /");
        Assertions.assertThat(docPath.isAbsolute())
                .isEqualTo(!docPath.isRelative())
                .isFalse();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("stroom/node/name");
    }

    @Test
    void testWhitespace3() {
        final DocPath docPath = DocPath.fromPathString("  stroom / has a few spaces / name /");
        Assertions.assertThat(docPath.isAbsolute())
                .isEqualTo(!docPath.isRelative())
                .isFalse();
        Assertions.assertThat(docPath.toString())
                .isEqualTo("stroom/has a few spaces/name");
    }

    @Test
    void getDocPath() {
        final DocPath docPath = DocPath.fromParts("stroom", "node", "name");

        assertThat(docPath.toString())
                .isEqualTo("/stroom/node/name");

        assertThat(docPath.getParts())
                .containsExactly("stroom", "node", "name");
    }

    @Test
    void merge() {
        final DocPath docPath1 = DocPath.fromParts("stroom", "node");
        final DocPath docPath2 = DocPath.fromParts("name").toRelativePath();
        assertThat(docPath1.append(docPath2).toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void merge2() {
        final DocPath docPath1 = DocPath.fromParts("stroom", "node");
        final String part2 = "name";
        Assertions.assertThat(docPath1.append(part2).toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void merge3() {
        final DocPath docPath1 = DocPath.fromParts("stroom", "node");
        Assertions.assertThat(docPath1.append("name", "other").toString())
                .isEqualTo("/stroom/node/name/other");
    }

    @Test
    void merge4() {
        final DocPath docPath1 = DocPath.fromParts("stroom", "node");
        final DocPath docPath2 = DocPath.fromParts("name");

        Assertions.assertThatThrownBy(
                        () -> {
                            assertThat(docPath1.append(docPath2).toString())
                                    .isEqualTo("/stroom/node/name");
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsPart1() {
        final DocPath docPath = DocPath.fromParts("stroom", "node", "name");
        docPath.getParts()
                .forEach(part -> {
                    assertThat(docPath.containsPart(part))
                            .isTrue();
                });
    }

    @Test
    void containsPart2() {
        final DocPath docPath = DocPath.fromParts("stroom", "node", "name");
        assertThat(docPath.containsPart("not_found"))
                .isFalse();
    }

    @Test
    void builder() {
        final DocPath docPath = DocPath.builder()
                .add("stroom")
                .add("node")
                .add("name")
                .build();

        assertThat(docPath.toString())
                .isEqualTo("/stroom/node/name");
    }

    @Test
    void testEqualsIgnoreCase1() {
        doEqualsIgnoreCaseTest("/stroom/node/name", "/stroom/node/name", true);
    }

    @Test
    void testEqualsIgnoreCase2() {
        doEqualsIgnoreCaseTest("/stroom/NODE/name", "/stroom/node/name", true);
    }

    @Test
    void testEqualsIgnoreCase3() {
        doEqualsIgnoreCaseTest("/stroom/NODE/name", "/STROOM/node/NAME", true);
    }

    @Test
    void testEqualsIgnoreCase4() {
        doEqualsIgnoreCaseTest("/stroom/node/name", "/stroom/xxxxxxx/name", false);
    }

    @Test
    void testEqualsIgnoreCase5() {
        doEqualsIgnoreCaseTest("/stroom/node/name", "/stroom/node", false);
    }

    private void doEqualsIgnoreCaseTest(final String pathString1,
                                        final String pathString2,
                                        final boolean expectedResult) {
        final DocPath path1 = DocPath.fromPathString(pathString1);
        final DocPath path2 = DocPath.fromPathString(pathString2);

        final boolean result = path1.equalsIgnoreCase(path2);

        assertThat(result)
                .isEqualTo(expectedResult);
    }

    @TestFactory
    Stream<DynamicTest> testGetParentPropertyName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(DocPath.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        testCase.getInput().getParentName()
                                .orElse(null))
                .withSimpleEqualityAssertion()
                .addCase(DocPath.blank(), null)
                .addCase(DocPath.fromParts("root"), null)
                .addCase(DocPath.fromParts("root", "child"), "root")
                .addCase(DocPath.fromParts("root", "child", "grandchild"), "child")
                .build();
    }

    @Test
    void testGetParent1() {
        final DocPath docPath = DocPath.fromPathString("/stroom/node/name");
        assertThat(docPath.getParent())
                .hasValue(DocPath.fromPathString("/stroom/node"));
    }

    @Test
    void testGetParent2() {
        final DocPath docPath = DocPath.fromPathString("/stroom");
        assertThat(docPath.getParent())
                .isEmpty();
    }

    @Test
    void testGetParent3() {
        final DocPath docPath = DocPath.fromPathString("");
    }

    @Test
    void testSerde() throws IOException {
        doSerdeTest(DocPath.fromPathString("/stroom/node/name"), DocPath.class);
    }

    @Test
    void testSerde_empty() throws IOException {
        doSerdeTest(DocPath.blank(), DocPath.class);
    }

    private <T> void doSerdeTest(final T entity,
                                 final Class<T> clazz) throws IOException {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);


        assertThat(mapper.canSerialize(entity.getClass()))
                .isTrue();

        final String json = mapper.writeValueAsString(entity);
        System.out.println("\n" + json);

        final T entity2 = mapper.readValue(json, clazz);

        assertThat(entity2)
                .isEqualTo(entity);
    }
}
