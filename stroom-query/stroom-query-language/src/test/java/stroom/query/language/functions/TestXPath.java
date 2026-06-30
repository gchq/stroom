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

package stroom.query.language.functions;

import java.util.stream.Stream;

class TestXPath extends AbstractFunctionTest<XPath> {

    @Override
    Class<XPath> getFunctionType() {
        return XPath.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final String xml = "<root><element>value</element><nested><item id=\"1\">item1</item>"
                + "<item id=\"2\">item2</item></nested></root>";
        return Stream.of(
                TestCase.of(
                        "Simple element",
                        ValString.create("value"),
                        ValString.create(xml),
                        ValString.create("/root/element")),
                TestCase.of(
                        "Nested element",
                        ValString.create("item1"),
                        ValString.create(xml),
                        ValString.create("/root/nested/item[1]")),
                TestCase.of(
                        "Attribute extraction",
                        ValString.create("2"),
                        ValString.create(xml),
                        ValString.create("/root/nested/item[2]/@id")),
                TestCase.of(
                        "No match",
                        ValString.create(""),
                        ValString.create(xml),
                        ValString.create("/root/missing")),
                TestCase.of(
                        "Invalid XML",
                        ValString.create("XML document structures must start and end within the same entity."),
                        ValString.create("<root>unclosed"),
                        ValString.create("/root")),
                TestCase.of(
                        "Invalid XPath",
                        ValString.create(
                                "An empty XPath expression has been defined for second argument of 'XPath' function"),
                        ValString.create(xml),
                        ValString.create("")),
                TestCase.of(
                        "Namespace - single",
                        ValString.create("value"),
                        ValString.create("<root xmlns=\"ns1\"><element>value</element></root>"),
                        ValString.create("/ns:root/ns:element"),
                        ValString.create("ns"),
                        ValString.create("ns1")),
                TestCase.of(
                        "Namespace - multiple",
                        ValString.create("item2"),
                        ValString.create("<root xmlns:a=\"nsa\" "
                                + "xmlns:b=\"nsb\"><a:item>item1</a:item><b:item>item2</b:item></root>"),
                        ValString.create("/root/b:item"),
                        ValString.create("a"),
                        ValString.create("nsa"),
                        ValString.create("b"),
                        ValString.create("nsb")),
                TestCase.of(
                        "Namespace - missing URI",
                        ValString.create("Namespaces must be provided as prefix-URI pairs in 'xpath' function"),
                        ValString.create("<root/>"),
                        ValString.create("/root"),
                        ValString.create("ns"))
        );
    }
}
