/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import io.vavr.Tuple;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:localvariablename")
class TestExpressionParser extends AbstractExpressionParserTest {

    @TestFactory
    Stream<DynamicTest> testBasic() {
        return Stream.of(
                        "1+1",
                        "-1+2",
                        "${val1}",
                        "max(${val1})",
                        "sum(${val1})",
                        "min(round(${val1}, 4))",
                        "min(roundDay(${val1}))",
                        "min(roundMinute(${val1}))",
                        "ceiling(${val1})",
                        "floor(${val1})",
                        "ceiling(floor(min(roundMinute(${val1}))))",
                        "ceiling(floor(min(round(${val1}))))",
                        "max(${val1})-min(${val1})",
                        "max(${val1})/count()",
                        "round(${val1})/(min(${val1})+max(${val1}))",
                        "concat('this is', 'it')",
                        "concat('it''s a string', 'with a quote')",
                        "'it''s a string'",
                        "50",
                        "stringLength('it''s a string')",
                        "upperCase('it''s a string')",
                        "lowerCase('it''s a string')",
                        "encodeUrl('http://www.example.com')",
                        "decodeUrl('http://www.example.com')",
                        "substring('Hello', 0, 1)",
                        "equals(${val1}, ${val1})",
                        "greaterThan(1, 0)",
                        "lessThan(1, 0)",
                        "greaterThanOrEqualTo(1, 0)",
                        "lessThanOrEqualTo(1, 0)",
                        "1=0",
                        "decode('fred', 'fr.+', 'freda', 'freddy')",
                        "extractHostFromUri('http://www.example.com:1234/this/is/a/path')",
                        "link('title', 'http://www.somehost.com/somepath', 'target')",
                        "dashboard('title', 'someuuid', 'param1=value1')")
                .map(expr ->
                        DynamicTest.dynamicTest(expr, () ->
                                test(expr)));
    }

    @Test
    void testMatch1() {
        createGenerator("match('this', 'this')", (gen, storedValues) -> {
            final Val out = gen.eval(storedValues, null);
            assertThat(out.toBoolean()).isTrue();
        });
    }

    @Test
    void testMatch2() {
        createGenerator("match('this', 'that')", (gen, storedValues) -> {
            final Val out = gen.eval(storedValues, null);
            assertThat(out.toBoolean()).isFalse();
        });
    }

    @Test
    void testMatch3() {
        compute("match(${val1}, 'this')",
                Val.of("this"),
                out -> assertThat(out.toBoolean()).isTrue());
    }

    @Test
    void testMatch4() {
        compute("match(${val1}, 'that')",
                Val.of("this"),
                out -> assertThat(out.toBoolean()).isFalse());
    }

    @Test
    void testTrue() {
        compute("true()",
                out -> assertThat(out.toBoolean()).isTrue());
    }

    @Test
    void testFalse() {
        compute("false()",
                out -> assertThat(out.toBoolean()).isFalse());
    }

    @Test
    void testNull() {
        compute("null()",
                out -> assertThat(out).isInstanceOf(ValNull.class));
    }

    @Test
    void testErr() {
        compute("err()",
                out -> assertThat(out).isInstanceOf(ValErr.class));
    }

    @Test
    void testNotTrue() {
        compute("not(true())",
                out -> assertThat(out.toBoolean()).isFalse());
    }

    @Test
    void testNotFalse() {
        compute("not(false())",
                out -> assertThat(out.toBoolean()).isTrue());
    }

    @Test
    void testIf1() {
        compute("if(true(), 'this', 'that')",
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testIf2() {
        compute("if(false(), 'this', 'that')",
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testIf3() {
        compute("if(${val1}, 'this', 'that')",
                Val.of("true"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testIf4() {
        compute("if(${val1}, 'this', 'that')",
                Val.of("false"),
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testIf5() {
        compute("if(match(${val1}, 'foo'), 'this', 'that')",
                Val.of("foo"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testIf6() {
        compute("if(match(${val1}, 'foo'), 'this', 'that')",
                Val.of("bar"),
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testNotIf() {
        compute("if(not(${val1}), 'this', 'that')",
                Val.of("false"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testIf_nullHandling() {
        compute("if(${val1}=null(), true(), false())",
                Val.of(ValNull.INSTANCE),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testReplace1() {
        compute("replace('this', 'is', 'at')",
                Val.of(3D),
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testReplace2() {
        compute("replace(${val1}, 'is', 'at')",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testConcat1() {
        compute("concat('this', ' is ', 'it')",
                Val.of(3D),
                out -> assertThat(out.toString()).isEqualTo("this is it"));
    }


    @Test
    void testConcat1Plus() {
        compute("'this'+' is '+'it'",
                Val.of(3D),
                out -> assertThat(out.toString()).isEqualTo("this is it"));
    }

    @Test
    void testConcat2() {
        compute("concat(${val1}, ' is ', 'it')",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("this is it"));
    }

    @Test
    void testConcatSingle1() {
        compute("concat(${val1})",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testConcatSingle2() {
        compute("concat('hello')",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("hello"));
    }

    @Test
    void testConcatNUll() {
        compute("concat(${val1}, ${val2})",
                2,
                Val.of(ValNull.INSTANCE, ValNull.INSTANCE),
                out -> assertThat(out.toString()).isEqualTo(""));
    }

    @Test
    void testConcatNullPlus1() {
        compute("${val1}+${val2}",
                2,
                Val.of(ValNull.INSTANCE, ValNull.INSTANCE),
                out -> assertThat(out).isEqualTo(ValNull.INSTANCE));
    }

    @Test
    void testConcatNullPlus2() {
        compute("${val1}+${val2}+'test'",
                2,
                Val.of(ValNull.INSTANCE, ValNull.INSTANCE),
                out -> assertThat(out.toString()).isEqualTo("test"));
    }

    @Test
    void testConcatNullPlus3() {
        compute("${val1}+${val2}+''",
                2,
                Val.of(ValNull.INSTANCE, ValNull.INSTANCE),
                out -> assertThat(out.toString()).isEqualTo(""));
    }

    @Test
    void testConcatBooleanPlus1() {
        compute("${val1}+${val2}",
                2,
                Val.of(ValBoolean.TRUE, ValBoolean.TRUE),
                out -> assertThat(out.toString()).isEqualTo("2"));
    }

    @Test
    void testConcatBooleanPlus2() {
        compute("${val1}+${val2}+''",
                2,
                Val.of(ValBoolean.TRUE, ValBoolean.TRUE),
                out -> assertThat(out.toString()).isEqualTo("2"));
    }


    @Test
    void testConcatBooleanPlus3() {
        compute("${val1}+${val2}+'test'",
                2,
                Val.of(ValBoolean.TRUE, ValBoolean.TRUE),
                out -> assertThat(out.toString()).isEqualTo("2test"));
    }

    @Test
    void testConcatBooleanPlus4() {
        compute("${val1}+'test'+${val2}",
                2,
                Val.of(ValBoolean.TRUE, ValBoolean.TRUE),
                out -> assertThat(out.toString()).isEqualTo("truetesttrue"));
    }


    @Test
    void testStaticString1() {
        compute("'hello'",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("hello"));
    }

    @Test
    void testStaticString2() {
        compute("'[Click Here](http://www.somehost.com/somepath){DIALOG}'",
                Val.of("this"),
                out -> assertThat(out.toString())
                        .isEqualTo("[Click Here](http://www.somehost.com/somepath){DIALOG}"));
    }

    @Test
    void testStaticNumber() {
        compute("50",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("50"));
    }

    @Test
    void testStringLength1() {
        compute("stringLength(${val1})",
                Val.of("this"),
                out -> assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D)));
    }

    @Test
    void testSubstring1() {
        compute("substring(${val1}, 1, 2)",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("h"));
    }

    @Test
    void testSubstring3() {
        compute("substring(${val1}, 2, 99)",
                Val.of("his"),
                out -> assertThat(out.toString()).isEqualTo("s"));
    }

    @Test
    void testSubstring4() {
        compute("substring(${val1}, 1+1, 99-1)",
                Val.of("his"),
                out -> assertThat(out.toString()).isEqualTo("s"));
    }

    @Test
    void testSubstring5() {
        compute("substring(${val1}, 2+5, 99-1)",
                Val.of("his"),
                out -> assertThat(out.toString()).isEmpty());
    }

    @Test
    void testSubstringBefore1() {
        compute("substringBefore(${val1}, '-')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("aa"));
    }

    @Test
    void testSubstringBefore2() {
        compute("substringBefore(${val1}, 'a')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEmpty());
    }

    @Test
    void testSubstringBefore3() {
        compute("substringBefore(${val1}, 'b')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("aa-"));
    }

    @Test
    void testSubstringBefore4() {
        compute("substringBefore(${val1}, 'q')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEmpty());
    }

    @Test
    void testSubstringAfter1() {
        compute("substringAfter(${val1}, '-')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("bb"));
    }

    @Test
    void testSubstringAfter2() {
        compute("substringAfter(${val1}, 'a')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("a-bb"));
    }

    @Test
    void testSubstringAfter3() {
        compute("substringAfter(${val1}, 'b')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("b"));
    }

    @Test
    void testSubstringAfter4() {
        compute("substringAfter(${val1}, 'q')",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEmpty());
    }

    @Test
    void testIndexOf() {
        compute("indexOf(${val1}, '-')",
                Val.of("aa-bb"),
                out -> assertThat(out.toInteger().intValue()).isEqualTo(2));
    }

    @Test
    void testIndexOf1() {
        compute("substring(${val1}, indexOf(${val1}, '-'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("-bb"));
    }

    @Test
    void testIndexOf2() {
        compute("substring(${val1}, indexOf(${val1}, 'a'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("aa-bb"));
    }

    @Test
    void testIndexOf3() {
        compute("substring(${val1}, indexOf(${val1}, 'b'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("bb"));
    }

    @Test
    void testIndexOf4() {
        compute("substring(${val1}, indexOf(${val1}, 'q'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testLastIndexOf1() {
        compute("substring(${val1}, lastIndexOf(${val1}, '-'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("-bb"));
    }

    @Test
    void testLastIndexOf2() {
        compute("substring(${val1}, lastIndexOf(${val1}, 'a'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("a-bb"));
    }

    @Test
    void testLastIndexOf3() {
        compute("substring(${val1}, lastIndexOf(${val1}, 'b'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.toString()).isEqualTo("b"));
    }

    @Test
    void testLastIndexOf4() {
        compute("substring(${val1}, lastIndexOf(${val1}, 'q'), stringLength(${val1}))",
                Val.of("aa-bb"),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testDecode1() {
        compute("decode(${val1}, 'hullo', 'hello', 'goodbye')",
                Val.of("hullo"),
                out -> assertThat(out.toString()).isEqualTo("hello"));
    }

    @Test
    void testDecode2() {
        compute("decode(${val1}, 'h.+o', 'hello', 'goodbye')",
                Val.of("hullo"),
                out -> assertThat(out.toString()).isEqualTo("hello"));
    }

    @Test
    void testInclude1() {
        compute("include(${val1}, 'this', 'that')",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testInclude2() {
        compute("include(${val1}, 'this', 'that')",
                Val.of("that"),
                out -> assertThat(out.toString()).isEqualTo("that"));
    }

    @Test
    void testInclude3() {
        compute("include(${val1}, 'this', 'that')",
                Val.of("other"),
                out -> assertThat(out.toString()).isNull());
    }

    @Test
    void testExclude1() {
        compute("exclude(${val1}, 'this', 'that')",
                Val.of("this"),
                out -> assertThat(out.toString()).isNull());
    }

    @Test
    void testExclude2() {
        compute("exclude(${val1}, 'this', 'that')",
                Val.of("that"),
                out -> assertThat(out.toString()).isNull());
    }

    @Test
    void testExclude3() {
        compute("exclude(${val1}, 'this', 'that')",
                Val.of("other"),
                out -> assertThat(out.toString()).isEqualTo("other"));
    }

    @Test
    void testEncodeUrl() {
        compute("encodeUrl('https://www.somesite.com:8080/this/path?query=string')",
                Val.of(""),
                out -> assertThat(out.toString()).isEqualTo(
                        "https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring"))
        ;
    }

    @Test
    void testDecodeUrl() {
        compute("decodeUrl('https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring')",
                Val.of(""),
                out -> assertThat(out.toString())
                        .isEqualTo("https://www.somesite.com:8080/this/path?query=string"));
    }

    @Test
    void testEquals1() {
        compute("equals(${val1}, 'plop')",
                Val.of("plop"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testEquals2() {
        compute("equals(${val1}, ${val1})",
                Val.of("plop"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testEquals3() {
        compute("equals(${val1}, 'plip')",
                Val.of("plop"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testEquals4() {
        compute("equals(${val1}, ${val2})", 2, Val.of("plop", "plip"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testEquals5() {
        compute("equals(${val1}, ${val2})", 2, Val.of("plop", "plop"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testEquals6() {
        compute("${val1}=${val2}", 2, Val.of("plop", "plop"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testEqualsNull1() {
        compute("${val1}=null()",
                Val.of(ValNull.INSTANCE),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testEqualsNull2() {
        compute("${val1}=null()",
                Val.of("plop"),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testEqualsNull3() {
        compute("null()=null()",
                Val.of("plop"),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testEqualsNull4() {
        compute("if(${val1}=null(), true(), false())",
                Val.of(ValNull.INSTANCE),
                out -> assertThat(out.type().isError()).isTrue());
    }

    @Test
    void testIsNull1() {
        compute("isNull(${val1})",
                Val.of(ValNull.INSTANCE),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testIsNull2() {
        compute("isNull(${val1})",
                Val.of("plop"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testIsNull3() {
        compute("isNull(null())",
                Val.of("plop"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testIsNull4() {
        compute("if(isNull(${val1}), true(), false())",
                Val.of(ValNull.INSTANCE),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThan1() {
        compute("lessThan(1, 0)",
                2,
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testLessThan2() {
        compute("lessThan(1, 1)",
                2,
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testLessThan3() {
        compute("lessThan(${val1}, ${val2})",
                2,
                Val.of(1D, 2D),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThan4() {
        compute("lessThan(${val1}, ${val2})",
                2,
                Val.of("fred", "fred"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testLessThan5() {
        compute("lessThan(${val1}, ${val2})",
                2,
                Val.of("fred", "fred1"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThan6() {
        compute("lessThan(${val1}, ${val2})",
                2,
                Val.of("fred1", "fred"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testLessThanOrEqualTo1() {
        compute("lessThanOrEqualTo(1, 0)",
                2,
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testLessThanOrEqualTo2() {
        compute("lessThanOrEqualTo(1, 1)",
                2,
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThanOrEqualTo3() {
        compute("lessThanOrEqualTo(${val1}, ${val2})",
                2,
                Val.of(1D, 2D),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThanOrEqualTo3_mk2() {
        compute("(${val1}<=${val2})",
                2,
                Val.of(1D, 2D),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThanOrEqualTo4() {
        compute("lessThanOrEqualTo(${val1}, ${val2})",
                2,
                Val.of("fred", "fred"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThanOrEqualTo5() {
        compute("lessThanOrEqualTo(${val1}, ${val2})",
                2,
                Val.of("fred", "fred1"),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testLessThanOrEqualTo6() {
        compute("lessThanOrEqualTo(${val1}, ${val2})",
                2,
                Val.of("fred1", "fred"),
                out -> assertThat(out.toString()).isEqualTo("false"));
    }

    @Test
    void testGreaterThanOrEqualTo1() {
        compute("greaterThanOrEqualTo(${val1}, ${val2})",
                2,
                Val.of(2D, 1D),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @Test
    void testGreaterThanOrEqualTo1_mk2() {
        compute("(${val1}>=${val2})",
                2,
                Val.of(2D, 1D),
                out -> assertThat(out.toString()).isEqualTo("true"));
    }

    @TestFactory
    Stream<DynamicTest> testBooleanExpressions() {
        final ValBoolean vTrue = ValBoolean.TRUE;
        final ValBoolean vFals = ValBoolean.FALSE; // intentional typo to keep var name length consistent
        final ValNull vNull = ValNull.INSTANCE;
        final ValErr vEror = ValErr.create("Expecting an error"); // intentional typo to keep var name length consistent

        final ValLong vLng0 = ValLong.create(0L);
        final ValLong vLng1 = ValLong.create(1L);
        final ValLong vLng2 = ValLong.create(2L);

        final ValInteger vInt0 = ValInteger.create(0);
        final ValInteger vInt1 = ValInteger.create(1);
        final ValInteger vInt2 = ValInteger.create(2);

        final ValDouble vDbl0 = ValDouble.create(0);
        final ValDouble vDbl1 = ValDouble.create(1);
        final ValDouble vDbl2 = ValDouble.create(2);

        final ValString vStr1 = ValString.create("1");
        final ValString vStr2 = ValString.create("2");
        final ValString vStrA = ValString.create("AAA");
        final ValString vStrB = ValString.create("BBB");
        final ValString vStra = ValString.create("aaa");
        final ValString vStrT = ValString.create("true");
        final ValString vStrF = ValString.create("false");
        final ValString vStr_ = ValString.EMPTY;

        // Don't use List.of() as there are so many varargs it kills IJ performance
        final QuadList<Val, String, Val, Val> testCases = new QuadList<>();

        // null/error, equals
        testCases.add(vNull, "=", vNull, vEror);
        testCases.add(vNull, "=", vEror, vEror);
        testCases.add(vEror, "=", vEror, vEror);

        // booleans, equals
        testCases.add(vTrue, "=", vTrue, vTrue);
        testCases.add(vFals, "=", vFals, vTrue);
        testCases.add(vTrue, "=", vFals, vFals);

        // longs, equals
        testCases.add(vLng1, "=", vNull, vEror);
        testCases.add(vNull, "=", vLng1, vEror);
        testCases.add(vLng1, "=", vLng1, vTrue);
        testCases.add(vLng1, "=", vLng2, vFals);
        testCases.add(vLng1, "=", vTrue, vTrue); // true() cast to 1
        testCases.add(vLng1, "=", vFals, vFals);

        // integers, equals
        testCases.add(vInt1, "=", vNull, vEror);
        testCases.add(vNull, "=", vInt1, vEror);
        testCases.add(vInt1, "=", vInt1, vTrue);
        testCases.add(vInt1, "=", vInt2, vFals);
        testCases.add(vInt1, "=", vTrue, vTrue); // true() cast to 1
        testCases.add(vInt1, "=", vFals, vFals);

        // doubles, equals
        testCases.add(vDbl1, "=", vNull, vEror);
        testCases.add(vNull, "=", vDbl1, vEror);
        testCases.add(vDbl1, "=", vDbl1, vTrue);
        testCases.add(vDbl1, "=", vDbl2, vFals);
        testCases.add(vDbl1, "=", vTrue, vTrue); // true() cast to 1
        testCases.add(vDbl1, "=", vFals, vFals);

        // strings, equals
        testCases.add(vStrA, "=", vNull, vEror);
        testCases.add(vNull, "=", vStrA, vEror);
        testCases.add(vStrA, "=", vStrA, vTrue);
        testCases.add(vStrA, "=", vStrB, vFals);
        testCases.add(vStrA, "=", vTrue, vFals);
        testCases.add(vStrA, "=", vFals, vFals);
        testCases.add(vStrA, "=", vStra, vFals);

        // mixed types, equals
        testCases.add(vLng1, "=", vStr1, vTrue);
        testCases.add(vDbl1, "=", vStr1, vTrue);
        testCases.add(vLng1, "=", vTrue, vTrue); //true cast to 1
        testCases.add(vInt1, "=", vTrue, vTrue); //true cast to 1
        testCases.add(vDbl1, "=", vTrue, vTrue);
        testCases.add(vLng0, "=", vFals, vTrue); // false() cast to 0
        testCases.add(vInt0, "=", vFals, vTrue); // false() cast to 0
        testCases.add(vDbl0, "=", vFals, vTrue); // false() cast to 0
        testCases.add(vDbl1, "=", vLng1, vTrue);
        testCases.add(vStrT, "=", vTrue, vTrue); // true() cast to "true"
        testCases.add(vStrF, "=", vFals, vTrue); // false() cast to "false"

        // booleans, greater than
        testCases.add(vTrue, ">", vTrue, vFals);
        testCases.add(vFals, ">", vFals, vFals);
        testCases.add(vTrue, ">", vFals, vTrue);

        // longs, greater than
        testCases.add(vLng1, ">", vNull, vEror);
        testCases.add(vNull, ">", vLng1, vEror);
        testCases.add(vLng1, ">", vLng1, vFals);
        testCases.add(vLng1, ">", vLng2, vFals);
        testCases.add(vLng2, ">", vLng1, vTrue);
        testCases.add(vLng1, ">", vTrue, vFals); //true cast to 1
        testCases.add(vLng2, ">", vDbl1, vTrue);
        testCases.add(vLng2, ">", vStr1, vTrue);

        // longs, greater than
        testCases.add(vInt1, ">", vNull, vEror);
        testCases.add(vNull, ">", vInt1, vEror);
        testCases.add(vInt1, ">", vInt1, vFals);
        testCases.add(vInt1, ">", vInt2, vFals);
        testCases.add(vInt2, ">", vInt1, vTrue);
        testCases.add(vInt1, ">", vTrue, vFals); // true cast to 1
        testCases.add(vInt2, ">", vDbl1, vTrue);
        testCases.add(vInt2, ">", vStr1, vTrue);

        // doubles, greater than
        testCases.add(vDbl1, ">", vNull, vEror);
        testCases.add(vNull, ">", vDbl1, vEror);
        testCases.add(vDbl1, ">", vDbl1, vFals);
        testCases.add(vDbl1, ">", vDbl2, vFals);
        testCases.add(vDbl2, ">", vDbl1, vTrue);
        testCases.add(vDbl1, ">", vTrue, vFals); //true() cast to 1
        testCases.add(vDbl2, ">", vDbl1, vTrue);
        testCases.add(vDbl2, ">", vStr1, vTrue);

        // strings, greater than
        testCases.add(vStrA, ">", vStrA, vFals);
        testCases.add(vStrA, ">", vStrB, vFals);
        testCases.add(vStrB, ">", vStrA, vTrue);
        testCases.add(vStrA, ">", vStr_, vTrue);
        testCases.add(vStrA, ">", vStr1, vTrue);
        testCases.add(vStrA, ">", vNull, vEror);
        testCases.add(vStrA, ">", vStra, vFals);
        testCases.add(vStra, ">", vStrA, vTrue);

        // booleans, greater than or equal to
        testCases.add(vTrue, ">=", vTrue, vTrue);
        testCases.add(vFals, ">=", vFals, vTrue);
        testCases.add(vTrue, ">=", vFals, vTrue);
        testCases.add(vFals, ">=", vTrue, vFals);

        // longs, greater than or equal to
        testCases.add(vLng1, ">=", vNull, vEror);
        testCases.add(vNull, ">=", vLng1, vEror);
        testCases.add(vLng1, ">=", vLng1, vTrue);
        testCases.add(vLng1, ">=", vLng2, vFals);
        testCases.add(vLng2, ">=", vLng1, vTrue);
        testCases.add(vLng1, ">=", vTrue, vTrue); // true() cast to 1
        testCases.add(vLng2, ">=", vDbl1, vTrue);
        testCases.add(vLng2, ">=", vStr1, vTrue);

        // integers, greater than or equal to
        testCases.add(vInt1, ">=", vNull, vEror);
        testCases.add(vNull, ">=", vInt1, vEror);
        testCases.add(vInt1, ">=", vInt1, vTrue);
        testCases.add(vInt1, ">=", vInt2, vFals);
        testCases.add(vInt2, ">=", vInt1, vTrue);
        testCases.add(vInt1, ">=", vTrue, vTrue); //true() cast to 1
        testCases.add(vInt2, ">=", vDbl1, vTrue);
        testCases.add(vInt2, ">=", vStr1, vTrue);

        // doubles, greater than or equal to
        testCases.add(vDbl1, ">=", vNull, vEror);
        testCases.add(vNull, ">=", vDbl1, vEror);
        testCases.add(vDbl1, ">=", vDbl1, vTrue);
        testCases.add(vDbl1, ">=", vDbl2, vFals);
        testCases.add(vDbl2, ">=", vDbl1, vTrue);
        testCases.add(vDbl1, ">=", vTrue, vTrue); // true() cast to 1
        testCases.add(vDbl2, ">=", vDbl1, vTrue);
        testCases.add(vDbl2, ">=", vStr1, vTrue);

        // strings, greater than or equal to
        testCases.add(vStrA, ">=", vStrA, vTrue);
        testCases.add(vStrA, ">=", vStrB, vFals);
        testCases.add(vStrB, ">=", vStrA, vTrue);
        testCases.add(vStrA, ">=", vStr_, vTrue);
        testCases.add(vStrA, ">=", vStr1, vTrue);
        testCases.add(vStrA, ">=", vNull, vEror);


        // booleans, less than
        testCases.add(vTrue, "<", vTrue, vFals);
        testCases.add(vFals, "<", vFals, vFals);
        testCases.add(vTrue, "<", vFals, vFals);
        testCases.add(vFals, "<", vTrue, vTrue);

        // longs, less than
        testCases.add(vLng1, "<", vNull, vEror);
        testCases.add(vNull, "<", vLng1, vEror);
        testCases.add(vLng1, "<", vLng1, vFals);
        testCases.add(vLng1, "<", vLng2, vTrue);
        testCases.add(vLng2, "<", vLng1, vFals);
        testCases.add(vLng1, "<", vTrue, vFals); // true() cast to 1
        testCases.add(vLng2, "<", vDbl1, vFals);
        testCases.add(vLng2, "<", vStr1, vFals);

        // integers, less than
        testCases.add(vInt1, "<", vNull, vEror);
        testCases.add(vNull, "<", vInt1, vEror);
        testCases.add(vInt1, "<", vInt1, vFals);
        testCases.add(vInt1, "<", vInt2, vTrue);
        testCases.add(vInt2, "<", vInt1, vFals);
        testCases.add(vInt1, "<", vTrue, vFals); // true() cast to 1
        testCases.add(vInt2, "<", vDbl1, vFals);
        testCases.add(vInt2, "<", vStr1, vFals);

        // doubles, less than
        testCases.add(vDbl1, "<", vNull, vEror);
        testCases.add(vNull, "<", vDbl1, vEror);
        testCases.add(vDbl1, "<", vDbl1, vFals);
        testCases.add(vDbl1, "<", vDbl2, vTrue);
        testCases.add(vDbl2, "<", vDbl1, vFals);
        testCases.add(vDbl1, "<", vTrue, vFals); // true() cast to 1
        testCases.add(vDbl2, "<", vDbl1, vFals);
        testCases.add(vDbl2, "<", vStr1, vFals);

        // strings, less than
        testCases.add(vStrA, "<", vStrA, vFals);
        testCases.add(vStrA, "<", vStrB, vTrue);
        testCases.add(vStrB, "<", vStrA, vFals);
        testCases.add(vStrA, "<", vStr_, vFals);
        testCases.add(vStrA, "<", vStr1, vFals);
        testCases.add(vStrA, "<", vNull, vEror);

        // booleans, less than or equal to
        testCases.add(vTrue, "<=", vTrue, vTrue);
        testCases.add(vFals, "<=", vFals, vTrue);
        testCases.add(vTrue, "<=", vFals, vFals);
        testCases.add(vFals, "<=", vTrue, vTrue);

        // longs, less than or equal to
        testCases.add(vLng1, "<=", vNull, vEror);
        testCases.add(vNull, "<=", vLng1, vEror);
        testCases.add(vLng1, "<=", vLng1, vTrue);
        testCases.add(vLng1, "<=", vLng2, vTrue);
        testCases.add(vLng2, "<=", vLng1, vFals);
        testCases.add(vLng1, "<=", vTrue, vTrue); // true() cast to 1
        testCases.add(vLng2, "<=", vDbl1, vFals);
        testCases.add(vDbl1, "<=", vLng2, vTrue);
        testCases.add(vLng2, "<=", vStr1, vFals);

        // integers, less than or equal to
        testCases.add(vInt1, "<=", vNull, vEror);
        testCases.add(vNull, "<=", vInt1, vEror);
        testCases.add(vInt1, "<=", vInt1, vTrue);
        testCases.add(vInt1, "<=", vInt2, vTrue);
        testCases.add(vInt2, "<=", vInt1, vFals);
        testCases.add(vInt1, "<=", vTrue, vTrue); //true() cast to 1
        testCases.add(vInt2, "<=", vDbl1, vFals);
        testCases.add(vInt1, "<=", vDbl2, vTrue);
        testCases.add(vInt2, "<=", vStr1, vFals);
        testCases.add(vInt1, "<=", vStr2, vTrue);

        // doubles, less than or equal to
        testCases.add(vDbl1, "<=", vNull, vEror);
        testCases.add(vNull, "<=", vDbl1, vEror);
        testCases.add(vDbl1, "<=", vDbl1, vTrue);
        testCases.add(vDbl1, "<=", vDbl2, vTrue);
        testCases.add(vDbl2, "<=", vDbl1, vFals);
        testCases.add(vDbl1, "<=", vTrue, vTrue); // true() caste to 1
        testCases.add(vDbl2, "<=", vStr1, vFals);
        testCases.add(vDbl1, "<=", vStr2, vTrue);

        // strings, less than or equal to
        testCases.add(vStrA, "<=", vStrA, vTrue);
        testCases.add(vStrA, "<=", vStrB, vTrue);
        testCases.add(vStrB, "<=", vStrA, vFals);
        testCases.add(vStrA, "<=", vStr_, vFals);
        testCases.add(vStrA, "<=", vStr1, vFals);
        testCases.add(vStrA, "<=", vNull, vEror);

        return testCases.stream()
                .map(tuple4 -> {
                    final Val val1 = tuple4._1;
                    final String operator = tuple4._2;
                    final Val val2 = tuple4._3;
                    final Val expectedOutput = tuple4._4;

                    return DynamicTest.dynamicTest(
                            String.join(
                                    ", ",
                                    valToString(val1),
                                    operator,
                                    valToString(val2),
                                    valToString(expectedOutput)),
                            () ->
                                    assertBooleanExpression(val1, operator, val2, expectedOutput));
                });
    }

    @Test
    void testSubstring2() {
        compute("substring(${val1}, 0, 99)",
                Val.of("this"),
                out -> assertThat(out.toString()).isEqualTo("this"));
    }

    @Test
    void testHash1() {
        compute("hash(${val1})",
                Val.of("test"),
                out -> assertThat(out.toString()).isEqualTo(
                        "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"));
    }

    @Test
    void testHash2() {
        compute("hash(${val1}, 'SHA-512')",
                Val.of("test"),
                out -> assertThat(out.toString()).isEqualTo(
                        "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff"));
    }

    @Test
    void testHash3() {
        compute("hash(${val1}, 'SHA-512', 'mysalt')",
                Val.of("test"),
                out -> assertThat(out.toString()).isEqualTo(
                        "af2910d4d8acf3fcf9683d3ca4425327cb1b4b48bc690f566e27b0e0144c17af82066cf6af14d3a30312ed9df671e0e24b1c66ed3973d1a7836899d75c4d6bb8"));
    }

    @Test
    void testJoining1() {
        createGenerator("joining(${val1}, ',')", (gen, storedValues) -> {
            gen.set(Val.of("one"), storedValues);
            gen.set(Val.of("two"), storedValues);
            gen.set(Val.of("three"), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toString()).isEqualTo("one,two,three");
        });
    }

    @Test
    void testJoining2() {
        createGenerator("joining(${val1})", (gen, storedValues) -> {
            gen.set(Val.of("one"), storedValues);
            gen.set(Val.of("two"), storedValues);
            gen.set(Val.of("three"), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toString()).isEqualTo("onetwothree");
        });
    }

    @Test
    void testCount() {
        createGenerator("count()", (gen, storedValues) -> {
            gen.set(Val.of(122D), storedValues);
            gen.set(Val.of(133D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(Val.of(11D), storedValues);
            gen.set(Val.of(122D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testCountGroups() {
        createGenerator("countGroups()", (gen, storedValues) -> {
            gen.set(Val.of(122D), storedValues);
            gen.set(Val.of(133D), storedValues);

            final Supplier<ChildData> childDataSupplier =
                    createChildDataSupplier(List.of(storedValues, storedValues));

            Val out = gen.eval(storedValues, childDataSupplier);
            assertThat(out.toInteger()).isEqualTo(2);
        });
    }

    @Test
    void testCountUnique() {
        createGenerator("countUnique(${val1})", (gen, storedValues) -> {
            gen.set(Val.of(122D), storedValues);
            gen.set(Val.of(133D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(Val.of(11D), storedValues);
            gen.set(Val.of(122D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
        });
    }

    @Test
    void testCountUniqueStaticValue() {
        createGenerator("countUnique('test')", (gen, storedValues) -> {
            gen.set(Val.of(122D), storedValues);
            gen.set(Val.of(133D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));

            gen.set(Val.of(11D), storedValues);
            gen.set(Val.of(122D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
        });
    }

    @Test
    void testAdd1() {
        compute("3+4",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D)));
    }

    @Test
    void testAdd2() {
        compute("3+4+5",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D)));
    }

    @Test
    void testAdd3() {
        createGenerator("2+count()", (gen, storedValues) -> {
            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testSubtract1() {
        compute("3-4",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(-1D, Offset.offset(0D)));
    }

    @Test
    void testSubtract2() {
        createGenerator("2-count()", (gen, storedValues) -> {
            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(0D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(-2D, Offset.offset(0D));
        });
    }

    @Test
    void testMultiply1() {
        compute("3*4",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D)));
    }

    @Test
    void testMultiply2() {
        createGenerator("2*count()", (gen, storedValues) -> {
            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testDivide1() {
        compute("8/4",
                out -> {
//        gen.set(getVal(1D));
                    assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
                });
    }

    @Test
    void testDivide2() {
        createGenerator("8/count()", (gen, storedValues) -> {
            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(Val.of(1D), storedValues);
            gen.set(Val.of(1D), storedValues);

            out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testDivide_byZero() {
        compute("8/0", out -> {
            assertThat(out instanceof ValErr).isTrue();
            System.out.println("Error message: " + ((ValErr) out).getMessage());
        });
    }

    @Test
    void testFloorNum1() {
        compute("floor(8.4234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D)));
    }

    @Test
    void testFloorNum2() {
        compute("floor(8.5234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D)));
    }

    @Test
    void testFloorNum3() {
        compute("floor(${val1})",
                Val.of(1.34D),
                out -> assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D)));
    }

    @Test
    void testFloorNum4() {
        createGenerator("floor(${val1}+count())", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum5() {
        createGenerator("floor(${val1}+count(), 1)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.8D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum6() {
        createGenerator("floor(${val1}+count(), 2)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.86D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum1() {
        compute("ceiling(8.4234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D)));
    }

    @Test
    void testCeilNum2() {
        compute("ceiling(8.5234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D)));
    }

    @Test
    void testCeilNum3() {
        compute("ceiling(${val1})",
                Val.of(1.34D),
                out -> assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D)));
    }

    @Test
    void testCeilNum4() {
        createGenerator("ceiling(${val1}+count())", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum5() {
        createGenerator("ceiling(${val1}+count(), 1)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum6() {
        createGenerator("ceiling(${val1}+count(), 2)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum1() {
        compute("round(8.4234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D)));
    }

    @Test
    void testRoundNum2() {
        compute("round(8.5234)",
                Val.of(1D),
                out -> assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D)));
    }

    @Test
    void testRoundNum3() {
        compute("round(${val1})",
                Val.of(1.34D),
                out -> assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D)));
    }

    @Test
    void testRoundNum4() {
        createGenerator("round(${val1}+count())", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum5() {
        createGenerator("round(${val1}+count(), 1)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum6() {
        createGenerator("round(${val1}+count(), 2)", (gen, storedValues) -> {
            gen.set(Val.of(1.34D), storedValues);
            gen.set(Val.of(1.8655D), storedValues);

            final Val out = gen.eval(storedValues, null);
            assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
        });
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            expr, input, expectedResult
            floorSecond, 2014-02-22T12:12:12.888Z, 2014-02-22T12:12:12.000Z
            floorMinute, 2014-02-22T12:12:12.888Z, 2014-02-22T12:12:00.000Z
            floorHour, 2014-02-22T12:12:12.888Z, 2014-02-22T12:00:00.000Z
            floorDay, 2014-02-22T12:12:12.888Z, 2014-02-22T00:00:00.000Z
            floorMonth, 2014-02-22T12:12:12.888Z, 2014-02-01T00:00:00.000Z
            floorYear, 2014-02-22T12:12:12.888Z, 2014-01-01T00:00:00.000Z

            ceilingSecond, 2014-02-22T12:12:12.888Z, 2014-02-22T12:12:13.000Z
            ceilingMinute, 2014-02-22T12:12:12.888Z, 2014-02-22T12:13:00.000Z
            ceilingHour, 2014-02-22T12:12:12.888Z, 2014-02-22T13:00:00.000Z
            ceilingDay, 2014-02-22T12:12:12.888Z, 2014-02-23T00:00:00.000Z
            ceilingMonth, 2014-02-22T12:12:12.888Z, 2014-03-01T00:00:00.000Z
            ceilingYear, 2014-02-22T12:12:12.888Z, 2015-01-01T00:00:00.000Z

            roundSecond, 2014-02-22T12:12:12.888Z, 2014-02-22T12:12:13.000Z
            roundMinute, 2014-02-22T12:12:12.888Z, 2014-02-22T12:12:00.000Z
            roundHour, 2014-02-22T12:12:12.888Z, 2014-02-22T12:00:00.000Z
            roundDay, 2014-02-22T12:12:12.888Z, 2014-02-23T00:00:00.000Z
            roundMonth, 2014-02-22T12:12:12.888Z, 2014-03-01T00:00:00.000Z
            roundYear, 2014-02-22T12:12:12.888Z, 2014-01-01T00:00:00.000Z
            """)
    void testTime(final String expr, final String input, final String expectedResult) {
        final double expectedMs = DateUtil.parseNormalDateTimeString(expectedResult);
        final String expression = expr + "(${val1})";
        compute(expression,
                Val.of(input),
                out -> assertThat(out.toDouble()).isEqualTo(expectedMs, Offset.offset(0D)));
    }

    @TestFactory
    Stream<DynamicTest> testBODMAS() {
        return Stream.of(
                        TestCase.of("4+4/2+2", ValDouble.create(8)),
                        TestCase.of("(4+4)/2+2", ValDouble.create(6)),
                        TestCase.of("(4+4)/(2+2)", ValDouble.create(2)),
                        TestCase.of("4+4/2+2*3", ValDouble.create(12)),
                        TestCase.of("8%3", ValDouble.create(2))
                )
                .map(testCase -> DynamicTest.dynamicTest(testCase.toString(), () ->
                        compute(testCase.expression,
                                testCase.inputValues,
                                out -> assertThat(out.toDouble())
                                        .isEqualTo(testCase.expectedResult.toDouble(),
                                                Offset.offset(0D)))));
    }

    @TestFactory
    Stream<DynamicTest> testParseDate() {
        return Stream.of(
                        TestCase.of(
                                "parseDate(${val1})",
                                ValDate.create(1393071132888L),
                                Val.of("2014-02-22T12:12:12.888Z")),
                        TestCase.of(
                                "parseDate(${val1}, 'yyyy MM dd')",
                                ValDate.create(1393027200000L),
                                Val.of("2014 02 22")),
                        TestCase.of(
                                "parseDate(${val1}, 'yyyy MM dd', '+0400')",
                                ValDate.create(1393012800000L),
                                Val.of("2014 02 22"))
                )
                .map(testCase -> DynamicTest.dynamicTest(testCase.toString(), () ->
                        compute(testCase.expression,
                                testCase.inputValues,
                                out -> assertThat(out)
                                        .isEqualTo(testCase.expectedResult))));
    }

    @TestFactory
    Stream<DynamicTest> testFormatDate() {
        return Stream.of(
                        TestCase.of(
                                "formatDate(${val1})",
                                ValString.create("2014-02-22T12:12:12.888Z"),
                                ValLong.create(1393071132888L)),
                        TestCase.of(
                                "formatDate(${val1}, 'yyyy MM dd')",
                                ValString.create("2014 02 22"),
                                ValLong.create(1393071132888L)),
                        TestCase.of(
                                "formatDate(${val1}, 'yyyy MM dd', '+1200')",
                                ValString.create("2014 02 23"),
                                ValLong.create(1393071132888L))
                )
                .map(testCase -> DynamicTest.dynamicTest(testCase.toString(), () ->
                        compute(testCase.expression,
                                testCase.inputValues,
                                out -> assertThat(out)
                                        .isEqualTo(testCase.expectedResult))));
    }

    @TestFactory
    Stream<DynamicTest> testCasts() {
        return Stream.of(
                        TestCase.of("toBoolean('true')", ValBoolean.TRUE),
                        TestCase.of("toBoolean(${val1})", ValBoolean.TRUE, Val.of("true")),

                        TestCase.of("toDouble('100')", ValDouble.create(100)),
                        TestCase.of("toDouble(${val1})", ValDouble.create(100), Val.of("100")),

                        TestCase.of("toInteger('100')", ValInteger.create(100)),
                        TestCase.of("toInteger(${val1})", ValInteger.create(100), Val.of("100")),

                        TestCase.of("toLong('100')", ValLong.create(100)),
                        TestCase.of("toLong(${val1})", ValLong.create(100), Val.of("100")),

                        TestCase.of("toString('100')", ValString.create("100")),
                        TestCase.of("toString(100)", ValString.create("100")),
                        TestCase.of("toString(${val1})", ValString.create("100"), Val.of("100"))
                )
                .map(testCase -> DynamicTest.dynamicTest(testCase.toString(), () ->
                        compute(testCase.expression,
                                testCase.inputValues,
                                out -> assertThat(out)
                                        .isEqualTo(testCase.expectedResult))));
    }

    @Test
    void testMappedValues1() {
        compute("param('testkey')",
                Val.of("100"),
                out -> assertThat(out).isEqualTo(ValString.create("testvalue")));
    }

    @Test
    void testMappedValues2() {
        compute("params()",
                Val.of("100"),
                out -> assertThat(out).isEqualTo(ValString.create("testkey=\"testvalue\"")));
    }

    @TestFactory
    Stream<DynamicTest> testErrorHandling1() {
        final ValLong valLong = ValLong.create(10);
        return Stream.of(
                        "(${val1}=err())",
                        "(err()=${val1})",
                        "(err()=null())",
                        "(null()=err())",
                        "(null()=${val1})",
                        "(${val1}=null())",

                        "(${val1}>=err())",
                        "(err()>=${val1})",
                        "(err()>=null())",
                        "(null()>=err())",
                        "(null()>=${val1})",
                        "(${val1}>=null())",

                        "(${val1}>err())",
                        "(err()>${val1})",
                        "(err()>null())",
                        "(null()>err())",
                        "(null()>${val1})",
                        "(${val1}>null())",

                        "(${val1}<=err())",
                        "(err()<=${val1})",
                        "(err()<=null())",
                        "(null()<=err())",
                        "(null()<=${val1})",
                        "(${val1}<=null())",

                        "(${val1}<err())",
                        "(err()<${val1})",
                        "(err()<null())",
                        "(null()<err())",
                        "(null()<${val1})",
                        "(${val1}<null())")
                .map(expr ->
                        DynamicTest.dynamicTest(expr, () ->
                                assertThatItEvaluatesToValErr(expr, valLong)));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, quoteCharacter = '"', textBlock = """
            expr, expectedType
            "typeOf(err())", error
            "typeOf(null())", null
            "typeOf(true())", boolean
            "typeOf(1+2)", double
            "typeOf(concat('a', 'b'))", string
            "typeOf('xxx')", string
            "typeOf(1.234)", double
            "typeOf(2>=1)", boolean
            """)
    void testTypeOf_1(final String expr, final String expectedType) {
        assertTypeOf(expr, expectedType);
    }

    @TestFactory
    Stream<DynamicTest> testTypeOf_2() {
        return Stream.of(
                        Tuple.of(ValBoolean.TRUE, "boolean"),
                        Tuple.of(ValBoolean.FALSE, "boolean"),
                        Tuple.of(ValNull.INSTANCE, "null"),
                        Tuple.of(ValErr.create("Expecting an error"), "error"),
                        Tuple.of(ValLong.create(0L), "long"),
                        Tuple.of(ValInteger.create(1), "integer"),
                        Tuple.of(ValDouble.create(1.1), "double"),
                        Tuple.of(ValString.create("abc"), "string"))
                .map(tuple2 -> {
                    final Val inputVal = tuple2._1;
                    final String expectedType = tuple2._2;
                    return DynamicTest.dynamicTest(
                            valToString(inputVal) + " - " + expectedType,
                            () -> assertTypeOf(inputVal, expectedType));
                });
    }

    @TestFactory
    Stream<DynamicTest> testIsExpressions() {
        final ValBoolean vTrue = ValBoolean.TRUE;
        final ValBoolean vFals = ValBoolean.FALSE; // intentional typo to keep var name length consistent
        final ValNull vNull = ValNull.INSTANCE;
        final ValErr vError = ValErr.create("Expecting an error"); // intentional typo to keep var name length consistent
        final ValLong vLong = ValLong.create(0L);
        final ValInteger vInt = ValInteger.create(0);
        final ValDouble vDbl = ValDouble.create(0);
        final ValString vString = ValString.create("1");

        final Map<String, Set<Val>> testMap = new HashMap<>();
        testMap.computeIfAbsent("isBoolean",
                k -> new HashSet<>(Arrays.asList(vFals, vTrue)));
        testMap.computeIfAbsent("isDouble",
                k -> new HashSet<>(Collections.singletonList(vDbl)));
        testMap.computeIfAbsent("isInteger",
                k -> new HashSet<>(Collections.singletonList(vInt)));
        testMap.computeIfAbsent("isLong",
                k -> new HashSet<>(Collections.singletonList(vLong)));
        testMap.computeIfAbsent("isString",
                k -> new HashSet<>(Collections.singletonList(vString)));
        testMap.computeIfAbsent("isNumber",
                k -> new HashSet<>(Arrays.asList(vDbl, vInt, vLong)));
        testMap.computeIfAbsent("isValue",
                k -> new HashSet<>(Arrays.asList(vFals,
                        vTrue,
                        vDbl,
                        vInt,
                        vLong,
                        vString)));
        testMap.computeIfAbsent("isNull",
                k -> new HashSet<>(Collections.singletonList(vNull)));
        testMap.computeIfAbsent("isError",
                k -> new HashSet<>(Collections.singletonList(vError)));

        final List<Val> types = Arrays.asList(
                vTrue,
                vFals,
                vNull,
                vError,
                vLong,
                vInt,
                vDbl,
                vString);

        return testMap.entrySet()
                .stream()
                .flatMap(entry -> {
                    final String expr = entry.getKey();
                    final Set<Val> vals = entry.getValue();
                    return types.stream()
                            .map(type ->
                                    DynamicTest.dynamicTest(
                                            String.join(", ", expr, valToString(type)),
                                            () -> assertIsExpression(
                                                    type,
                                                    expr,
                                                    ValBoolean.create(vals.contains(type)))));
                });
    }

    /**
     * Depending on context, `-X` should be turned into negate(X)
     */
    @TestFactory
    Stream<DynamicTest> testNegation() {
        final ValLong val1 = ValLong.create(10);
        final ValLong val2 = ValLong.create(5);

        return Stream.of(
                        TestCase.of("20-10", ValDouble.create(10)),
                        TestCase.of("-20-10", ValDouble.create(-30)),
                        TestCase.of("-20--10", ValDouble.create(-10)),
                        TestCase.of("-20+-10", ValDouble.create(-30)),
                        TestCase.of("-10", ValDouble.create(-10)),
                        TestCase.of("${val1}", val1, val1),
                        TestCase.of("-${val1}", ValDouble.create(-val1.toDouble()), val1),
                        TestCase.of("-'text'", ValErr.INSTANCE),
                        TestCase.of("add(-10, 20)", ValDouble.create(10)),
                        TestCase.of(
                                "add(${val1}, ${val2})",
                                ValDouble.create(val1.toDouble() + val2.toDouble()),
                                val1, val2),
                        TestCase.of(
                                "add(${val1}, -${val2})",
                                ValDouble.create(val1.toDouble() - val2.toDouble()),
                                val1, val2),
                        TestCase.of(
                                "-add(${val1}, ${val2})",
                                ValDouble.create((val1.toDouble() + val2.toDouble()) * -1),
                                val1, val2)
                )
                .map(testCase -> DynamicTest.dynamicTest(testCase.toString(), () ->
                        assertThatItEvaluatesTo(
                                testCase.expression,
                                testCase.expectedResult,
                                testCase.inputValues)));
    }
}
