/*
 * Copyright 2026 Crown Copyright
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
        final String namespacedXml = "<root xmlns=\"ns1\" xmlns:b=\"nsb\"><element>value</element>"
                                     + "<b:element>bvalue</b:element></root>";
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

                // Multiple matches are concatenated.
                TestCase.of(
                        "Multiple matches - concatenated",
                        ValString.create("item1item2"),
                        ValString.create(xml),
                        ValString.create("/root/nested/item")),
                TestCase.of(
                        "Multiple matches - delimited",
                        ValString.create("item1, item2"),
                        ValString.create(xml),
                        ValString.create("/root/nested/item"),
                        ValString.create(""),
                        ValString.create(", ")),
                TestCase.of(
                        "Multiple matches - attributes delimited",
                        ValString.create("1|2"),
                        ValString.create(xml),
                        ValString.create("//item/@id"),
                        ValString.create(""),
                        ValString.create("|")),
                TestCase.of(
                        "Single match with delimiter",
                        ValString.create("value"),
                        ValString.create(xml),
                        ValString.create("/root/element"),
                        ValString.create(""),
                        ValString.create(",")),

                // XPath 2.0+ expressions.
                TestCase.of(
                        "XPath 2.0 - name() as a step",
                        ValString.create("element,nested"),
                        ValString.create(xml),
                        ValString.create("/root/*[name() != 'foo']/name()"),
                        ValString.create(""),
                        ValString.create(",")),
                TestCase.of(
                        "XPath 2.0 - string-join",
                        ValString.create("item1-item2"),
                        ValString.create(xml),
                        ValString.create("string-join(/root/nested/item, '-')")),
                TestCase.of(
                        "XPath 2.0 - upper-case",
                        ValString.create("VALUE"),
                        ValString.create(xml),
                        ValString.create("upper-case(/root/element)")),

                // Namespaces are ignored if no prefix mappings are supplied.
                TestCase.of(
                        "Namespace unaware - default namespace ignored",
                        ValString.create("value"),
                        ValString.create("<root xmlns=\"ns1\"><element>value</element></root>"),
                        ValString.create("/root/element")),
                TestCase.of(
                        "Namespace unaware - prefixes ignored",
                        ValString.create("valuebvalue"),
                        ValString.create(namespacedXml),
                        ValString.create("/root/element")),
                TestCase.of(
                        "Namespace unaware - attribute prefixes ignored",
                        ValString.create("1"),
                        ValString.create("<root xmlns:a=\"nsa\"><item a:id=\"1\"/></root>"),
                        ValString.create("/root/item/@id")),

                // Namespaces are honoured if prefix mappings are supplied.
                TestCase.of(
                        "Namespace - single",
                        ValString.create("value"),
                        ValString.create("<root xmlns=\"ns1\"><element>value</element></root>"),
                        ValString.create("/ns:root/ns:element"),
                        ValString.create("ns:ns1")),
                TestCase.of(
                        "Namespace - multiple",
                        ValString.create("item2"),
                        ValString.create("<root xmlns:a=\"nsa\" "
                                         + "xmlns:b=\"nsb\"><a:item>item1</a:item><b:item>item2</b:item></root>"),
                        ValString.create("/root/b:item"),
                        ValString.create("a:nsa b:nsb")),
                TestCase.of(
                        "Namespace - URI containing colons",
                        ValString.create("value"),
                        ValString.create("<root xmlns=\"event-logging:3\"><element>value</element></root>"),
                        ValString.create("/e:root/e:element"),
                        ValString.create("e:event-logging:3")),
                TestCase.of(
                        "Namespace - default element namespace",
                        ValString.create("value"),
                        ValString.create("<root xmlns=\"ns1\"><element>value</element></root>"),
                        ValString.create("/root/element"),
                        ValString.create(":ns1")),
                TestCase.of(
                        "Namespace - only matches the declared namespace",
                        ValString.create("value"),
                        ValString.create(namespacedXml),
                        ValString.create("/ns:root/ns:element"),
                        ValString.create("ns:ns1")),

                // Errors.
                TestCase.of(
                        "Invalid XML",
                        ValString.create("XML document structures must start and end within the same entity."),
                        ValString.create("<root>unclosed"),
                        ValString.create("/root")),
                TestCase.of(
                        "Empty XPath",
                        ValString.create(
                                "An empty XPath expression has been defined for second argument of 'XPath' function"),
                        ValString.create(xml),
                        ValString.create("")),
                TestCase.of(
                        "Invalid XPath",
                        ValString.create("Error in XPath expression"),
                        ValString.create(xml),
                        ValString.create("/root/[")),
                TestCase.of(
                        "Namespace - missing URI",
                        ValString.create("Namespaces must be provided as space delimited 'prefix:uri' mappings"),
                        ValString.create("<root/>"),
                        ValString.create("/root"),
                        ValString.create("ns")),
                TestCase.of(
                        "Namespace - undeclared prefix",
                        ValString.create("Error in XPath expression"),
                        ValString.create(namespacedXml),
                        ValString.create("/ns:root/ns:element"),
                        ValString.create("other:nsb"))
        );
    }
}
