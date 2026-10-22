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

package stroom.ai.impl.tools.generation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/// The functions the XPath parser knows (the Stroom MCP server's parser, elementpath, knows them): the standard
/// functions called without a prefix, with how many arguments each takes; the map, array and math functions; the
/// XML Schema types that have constructor functions; and the kind tests. Taken from elementpath 5.1.4 by parsing a
/// call of each with up to seven arguments.
final class XPathFunctions {

    /// Takes any number of arguments from its least.
    static final int ANY = Integer.MAX_VALUE;

    /// The array functions namespace.
    static final String ARRAY_NS = "http://www.w3.org/2005/xpath-functions/array";
    /// The math functions namespace.
    static final String MATH_NS = "http://www.w3.org/2005/xpath-functions/math";

    /// The prefixes the parser binds itself, as elementpath does.
    static final Map<String, String> DEFAULT_NAMESPACES = Map.of(
            "map", MappingFacts.MAP_NS,
            "array", ARRAY_NS,
            "math", MATH_NS,
            "xml", "http://www.w3.org/XML/1998/namespace",
            "xs", MappingFacts.XS,
            "fn", MappingFacts.FN,
            "err", "http://www.w3.org/2005/xqt-errors");

    /// The kind tests: called like functions, but their arguments are names and types.
    static final Set<String> KIND_TESTS = Set.of("node", "text", "comment", "element", "document-node",
            "processing-instruction", "namespace-node", "schema-element", "schema-attribute");

    /// The sequence types only allowed after `instance of` and `treat as`.
    static final Set<String> SEQUENCE_TYPES = Set.of("item", "empty-sequence");

    /// The axes.
    static final Set<String> AXES = Set.of("ancestor", "ancestor-or-self", "attribute", "child", "descendant",
            "descendant-or-self", "following", "following-sibling", "namespace", "parent", "preceding",
            "preceding-sibling", "self");

    /// The XML Schema types a constructor function makes (`xs:integer(...)`).
    static final Set<String> CONSTRUCTORS = Set.of("ENTITIES", "ENTITY", "ID", "IDREF", "IDREFS", "NCName",
            "NMTOKEN", "NMTOKENS", "NOTATION", "Name", "QName", "anyURI", "base64Binary", "boolean", "byte", "date",
            "dateTime", "dateTimeStamp", "dayTimeDuration", "decimal", "double", "duration", "error", "float",
            "gDay", "gMonth", "gMonthDay", "gYear", "gYearMonth", "hexBinary", "int", "integer", "language", "long",
            "negativeInteger", "nonNegativeInteger", "nonPositiveInteger", "normalizedString", "numeric",
            "positiveInteger", "short", "string", "time", "token", "unsignedByte", "unsignedInt", "unsignedLong",
            "unsignedShort", "untypedAtomic", "yearMonthDuration");

    private static final Map<String, int[]> STANDARD = standard();
    private static final Map<String, Map<String, int[]>> BY_NAMESPACE = Map.of(
            MappingFacts.MAP_NS, arities("contains 2 2;entry 2 2;find 2 2;for-each 2 2;get 2 2;keys 1 1;"
                                         + "merge 1 2;put 3 3;remove 2 2;size 1 1"),
            ARRAY_NS, arities("append 2 2;filter 2 2;flatten 1 1;fold-left 3 3;fold-right 3 3;for-each 2 2;"
                              + "for-each-pair 3 3;get 2 2;head 1 1;insert-before 3 3;join 1 1;put 3 3;"
                              + "remove 2 2;reverse 1 1;size 1 1;sort 1 3;subarray 2 3;tail 1 1"),
            MATH_NS, arities("acos 1 1;asin 1 1;atan 1 1;atan2 2 2;cos 1 1;exp 1 1;exp10 1 1;log 1 1;log10 1 1;"
                             + "pi 0 0;pow 2 2;sin 1 1;sqrt 1 1;tan 1 1"));

    private XPathFunctions() {
        // Static only.
    }

    /// @param name A function's name, without a prefix.
    /// @return Whether it is a standard function called without a prefix.
    static boolean isStandard(final String name) {
        return STANDARD.containsKey(name);
    }

    /// @param name  A standard function's name.
    /// @param count How many arguments it is called with.
    /// @return Whether it takes that many.
    static boolean takes(final String name, final int count) {
        return takes(STANDARD.get(name), count);
    }

    /// @param namespace A namespace: the map, array or math functions', or the standard functions'.
    /// @param name      A function's local name.
    /// @return Whether the namespace has a function of that name.
    static boolean has(final String namespace, final String name) {
        return MappingFacts.FN.equals(namespace)
                ? STANDARD.containsKey(name)
                : BY_NAMESPACE.getOrDefault(namespace, Map.of()).containsKey(name);
    }

    /// @param namespace A namespace, as for [#has(String, String)].
    /// @param name      A function's local name, one it has.
    /// @param count     How many arguments it is called with.
    /// @return Whether it takes that many.
    static boolean takes(final String namespace, final String name, final int count) {
        return MappingFacts.FN.equals(namespace)
                ? takes(STANDARD.get(name), count)
                : takes(BY_NAMESPACE.getOrDefault(namespace, Map.of()).get(name), count);
    }

    /// @param namespace A namespace.
    /// @return Whether elementpath has functions in it of its own (the standard, map, array and math functions).
    static boolean isBuiltIn(final String namespace) {
        return MappingFacts.FN.equals(namespace) || BY_NAMESPACE.containsKey(namespace);
    }

    private static boolean takes(final int[] range, final int count) {
        return range != null && count >= range[0] && count <= range[1];
    }

    private static Map<String, int[]> standard() {
        final Map<String, int[]> out = new HashMap<>(arities(
                "abs 1 1;adjust-date-to-timezone 1 2;adjust-dateTime-to-timezone 1 2;adjust-time-to-timezone 1 2;"
                + "analyze-string 2 3;apply 2 2;available-environment-variables 0 0;avg 1 1;base-uri 0 1;"
                + "ceiling 1 1;codepoint-equal 2 2;codepoints-to-string 1 1;collation-key 1 2;collection 0 1;"
                + "compare 2 3;contains 2 3;contains-token 2 3;count 1 1;current-date 0 0;current-dateTime 0 0;"
                + "current-time 0 0;data 0 1;day-from-date 1 1;day-from-dateTime 1 1;days-from-duration 1 1;"
                + "deep-equal 2 3;default-collation 0 0;default-language 0 0;distinct-values 1 2;doc 1 1;"
                + "doc-available 1 1;document-uri 0 1;element-with-id 1 2;empty 1 1;encode-for-uri 1 1;"
                + "ends-with 2 3;environment-variable 1 1;error 1 3;escape-html-uri 1 1;exactly-one 1 1;"
                + "exists 1 1;false 0 0;filter 2 2;floor 1 1;fold-left 3 3;fold-right 3 3;for-each 2 2;"
                + "for-each-pair 3 3;format-date 2 5;format-dateTime 2 5;format-integer 2 3;format-number 2 3;"
                + "format-time 2 5;function-arity 1 1;function-lookup 2 2;function-name 1 1;generate-id 0 1;"
                + "has-children 0 1;head 1 1;hours-from-dateTime 1 1;hours-from-duration 1 1;"
                + "hours-from-time 1 1;id 1 2;idref 1 2;implicit-timezone 0 0;in-scope-prefixes 1 1;"
                + "index-of 2 3;innermost 1 1;insert-before 3 3;iri-to-uri 1 1;json-doc 1 2;json-to-xml 1 2;"
                + "lang 1 2;last 0 0;load-xquery-module 1 2;local-name 0 1;local-name-from-QName 1 1;"
                + "lower-case 1 1;matches 2 3;max 1 2;min 1 2;minutes-from-dateTime 1 1;"
                + "minutes-from-duration 1 1;minutes-from-time 1 1;month-from-date 1 1;month-from-dateTime 1 1;"
                + "months-from-duration 1 1;name 0 1;namespace-uri 0 1;namespace-uri-for-prefix 2 2;"
                + "namespace-uri-from-QName 1 1;nilled 0 1;node-name 0 1;normalize-space 0 1;"
                + "normalize-unicode 1 2;not 1 1;number 0 1;one-or-more 1 1;outermost 1 1;parse-ietf-date 1 1;"
                + "parse-json 1 2;parse-xml 1 1;parse-xml-fragment 1 1;path 0 1;position 0 0;"
                + "prefix-from-QName 1 1;random-number-generator 0 1;remove 2 2;replace 3 4;resolve-QName 2 2;"
                + "resolve-uri 1 2;reverse 1 1;root 0 1;round 1 2;round-half-to-even 1 2;"
                + "seconds-from-dateTime 1 1;seconds-from-duration 1 1;seconds-from-time 1 1;serialize 1 2;"
                + "sort 1 3;starts-with 2 3;static-base-uri 0 0;string-join 1 2;string-length 0 1;"
                + "string-to-codepoints 1 1;subsequence 2 3;substring 2 3;substring-after 2 3;"
                + "substring-before 2 3;sum 1 2;tail 1 1;timezone-from-date 1 1;timezone-from-dateTime 1 1;"
                + "timezone-from-time 1 1;tokenize 1 3;trace 1 2;transform 1 1;translate 3 3;true 0 0;"
                + "unordered 1 1;unparsed-text 1 2;unparsed-text-available 1 2;unparsed-text-lines 1 2;"
                + "upper-case 1 1;uri-collection 0 1;xml-to-json 1 2;year-from-date 1 1;"
                + "year-from-dateTime 1 1;years-from-duration 1 1;zero-or-one 1 1;QName 2 2;dateTime 2 2;"
                + "boolean 1 1;string 0 1"));
        out.put("concat", new int[]{2, ANY});
        // The constructor functions are called without a prefix too (integer(x)), with one argument; not NOTATION
        // or dateTimeStamp.
        for (final String type : CONSTRUCTORS) {
            if (!"NOTATION".equals(type) && !"dateTimeStamp".equals(type)) {
                out.putIfAbsent(type, new int[]{1, 1});
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, int[]> arities(final String table) {
        final Map<String, int[]> out = new HashMap<>();
        for (final String row : table.split(";")) {
            final String[] parts = row.trim().split(" ");
            out.put(parts[0], new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
        }
        return Map.copyOf(out);
    }
}
