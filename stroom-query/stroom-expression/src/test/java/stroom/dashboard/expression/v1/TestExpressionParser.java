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

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionParser.class);

    private final ExpressionParser parser = new ExpressionParser(new FunctionFactory(), new ParamFactory());

    @Test
    void testBasic() {
        test("${val1}");
        test("min(${val1})");
        test("max(${val1})");
        test("sum(${val1})");
        test("min(round(${val1}, 4))");
        test("min(roundDay(${val1}))");
        test("min(roundMinute(${val1}))");
        test("ceiling(${val1})");
        test("floor(${val1})");
        test("ceiling(floor(min(roundMinute(${val1}))))");
        test("ceiling(floor(min(round(${val1}))))");
        test("max(${val1})-min(${val1})");
        test("max(${val1})/count()");
        test("round(${val1})/(min(${val1})+max(${val1}))");
        test("concat('this is', 'it')");
        test("concat('it''s a string', 'with a quote')");
        test("'it''s a string'");
        test("50");
        test("stringLength('it''s a string')");
        test("upperCase('it''s a string')");
        test("lowerCase('it''s a string')");
        test("encodeUrl('http://www.example.com')");
        test("decodeUrl('http://www.example.com')");
        test("substring('Hello', 0, 1)");
        test("equals(${val1}, ${val1})");
        test("greaterThan(1, 0)");
        test("lessThan(1, 0)");
        test("greaterThanOrEqualTo(1, 0)");
        test("lessThanOrEqualTo(1, 0)");
        test("1=0");
        test("decode('fred', 'fr.+', 'freda', 'freddy')");
        test("extractHostFromUri('http://www.example.com:1234/this/is/a/path')");
        test("link('title', 'http://www.somehost.com/somepath', 'target')");
        test("dashboard('title', 'someuuid', 'param1=value1')");
    }

    private void test(final String expression) {
        createExpression(expression, exp ->
                System.out.println(exp.toString()));
    }

    @Test
    void testMin1() {
        createGenerator("min(${val1})", gen -> {
            gen.set(getVal(300D));
            gen.set(getVal(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(getVal(500D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(180D, Offset.offset(0D));

            gen.set(getVal(600D));
            gen.set(getVal(13D));
            gen.set(getVal(99.3D));
            gen.set(getVal(87D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(13D, Offset.offset(0D));
        });
    }

    private Val[] getVal(final String... str) {
        final Val[] result = new Val[str.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ValString.create(str[i]);
        }
        return result;
    }

    private Val[] getVal(final double... d) {
        final Val[] result = new Val[d.length];
        for (int i = 0; i < d.length; i++) {
            result[i] = ValDouble.create(d[i]);
        }
        return result;
    }

    @Test
    void testMinUngrouped2() {
        createGenerator("min(${val1}, 100, 30, 8)", gen -> {
            gen.set(getVal(300D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMinGrouped2() {
        createGenerator("min(min(${val1}), 100, 30, 8)", gen -> {
            gen.set(getVal(300D));
            gen.set(getVal(180D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testMin3() {
        createGenerator("min(min(${val1}), 100, 30, 8, count(), 55)", gen -> {
            gen.set(getVal(300D));
            gen.set(getVal(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(getVal(300D));
            gen.set(getVal(180D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testMax1() {
        createGenerator("max(${val1})", gen -> {
            gen.set(getVal(300D));
            gen.set(getVal(180D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300D, Offset.offset(0D));

            gen.set(getVal(500D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(500D, Offset.offset(0D));

            gen.set(getVal(600D));
            gen.set(getVal(13D));
            gen.set(getVal(99.3D));
            gen.set(getVal(87D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(600D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxUngrouped2() {
        createGenerator("max(${val1}, 100, 30, 8)", gen -> {
            gen.set(getVal(10D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMaxGrouped2() {
        createGenerator("max(max(${val1}), 100, 30, 8)", gen -> {
            gen.set(getVal(10D));
            gen.set(getVal(40D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
        });
    }

    @Test
    void testMax3() {
        createGenerator("max(max(${val1}), count())", gen -> {
            gen.set(getVal(3D));
            gen.set(getVal(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testSum() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("sum(${val1}, count())", gen -> {
            gen.set(getVal(3D));
            gen.set(getVal(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(5D, Offset.offset(0D));
        });
    }

    @Test
    void testSumOfSum() {
        createGenerator("sum(sum(${val1}), count())", gen -> {
            gen.set(getVal(3D));
            gen.set(getVal(2D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(11D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageUngrouped() {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        createGenerator("average(${val1}, count())", gen -> {
            gen.set(getVal(3D));
            gen.set(getVal(4D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(8D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testAverageGrouped() {
        createGenerator("average(${val1})", gen -> {
            gen.set(getVal(3D));
            gen.set(getVal(4D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.5D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(8D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testMatch1() {
        createGenerator("match('this', 'this')", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isTrue();
        });
    }

    @Test
    void testMatch2() {
        createGenerator("match('this', 'that')", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isFalse();
        });
    }

    @Test
    void testMatch3() {
        createGenerator("match(${val1}, 'this')", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toBoolean()).isTrue();
        });
    }

    @Test
    void testMatch4() {
        createGenerator("match(${val1}, 'that')", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toBoolean()).isFalse();
        });
    }

    @Test
    void testTrue() {
        createGenerator("true()", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isTrue();
        });
    }

    @Test
    void testFalse() {
        createGenerator("false()", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isFalse();
        });
    }

    @Test
    void testNull() {
        createGenerator("null()", gen -> {
            final Val out = gen.eval();
            assertThat(out).isInstanceOf(ValNull.class);
        });
    }

    @Test
    void testErr() {
        createGenerator("err()", gen -> {
            final Val out = gen.eval();
            assertThat(out).isInstanceOf(ValErr.class);
        });
    }

    @Test
    void testNotTrue() {
        createGenerator("not(true())", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isFalse();
        });
    }

    @Test
    void testNotFalse() {
        createGenerator("not(false())", gen -> {
            final Val out = gen.eval();
            assertThat(out.toBoolean()).isTrue();
        });
    }

    @Test
    void testIf1() {
        createGenerator("if(true(), 'this', 'that')", gen -> {
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testIf2() {
        createGenerator("if(false(), 'this', 'that')", gen -> {
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testIf3() {
        createGenerator("if(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("true"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testIf4() {
        createGenerator("if(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("false"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testIf5() {
        createGenerator("if(match(${val1}, 'foo'), 'this', 'that')", gen -> {
            gen.set(getVal("foo"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testIf6() {
        createGenerator("if(match(${val1}, 'foo'), 'this', 'that')", gen -> {
            gen.set(getVal("bar"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testNotIf() {
        createGenerator("if(not(${val1}), 'this', 'that')", gen -> {
            gen.set(getVal("false"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testIf_nullHandling() {
        createGenerator("if(${val1}=null(), true(), false())", gen -> {
            gen.set(new Val[]{ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testReplace1() {
        createGenerator("replace('this', 'is', 'at')", gen -> {
            gen.set(getVal(3D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testReplace2() {
        createGenerator("replace(${val1}, 'is', 'at')", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testConcat1() {
        createGenerator("concat('this', ' is ', 'it')", gen -> {
            gen.set(getVal(3D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this is it");
        });
    }


    @Test
    void testConcat1Plus() {
        createGenerator("'this'+' is '+'it'", gen -> {
            gen.set(getVal(3D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this is it");
        });
    }

    @Test
    void testConcat2() {
        createGenerator("concat(${val1}, ' is ', 'it')", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this is it");
        });
    }

    @Test
    void testConcatSingle1() {
        createGenerator("concat(${val1})", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testConcatSingle2() {
        createGenerator("concat('hello')", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("hello");
        });
    }

    @Test
    void testConcatNUll() {
        createGenerator("concat(${val1}, ${val2})", 2, gen -> {
            gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("");
        });
    }

    @Test
    void testConcatNullPlus1() {
        createGenerator("${val1}+${val2}", 2, gen -> {
            gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out).isEqualTo(ValNull.INSTANCE);
        });
    }

    @Test
    void testConcatNullPlus2() {
        createGenerator("${val1}+${val2}+'test'", 2, gen -> {
            gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("test");
        });
    }

    @Test
    void testConcatNullPlus3() {
        createGenerator("${val1}+${val2}+''", 2, gen -> {
            gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("");
        });
    }

    @Test
    void testConcatBooleanPlus1() {
        createGenerator("${val1}+${val2}", 2, gen -> {
            gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2");
        });
    }

    @Test
    void testConcatBooleanPlus2() {
        createGenerator("${val1}+${val2}+''", 2, gen -> {
            gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2");
        });
    }


    @Test
    void testConcatBooleanPlus3() {
        createGenerator("${val1}+${val2}+'test'", 2, gen -> {
            gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2test");
        });
    }

    @Test
    void testConcatBooleanPlus4() {
        createGenerator("${val1}+'test'+${val2}", 2, gen -> {
            gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("truetesttrue");
        });
    }

    @Test
    void testLink1() {
        createGenerator("link('Title', 'http://www.somehost.com/somepath')", gen -> {
            final String expectedText = "Title";
            final String expectedUrl = "http://www.somehost.com/somepath";

            gen.set(getVal("this"));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[Title](http%3A%2F%2Fwww.somehost.com%2Fsomepath)");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
        });
    }

    @Test
    void testLink2() {
        createGenerator("link('Title', 'http://www.somehost.com/somepath', 'browser')", gen -> {
            final String expectedText = "Title";
            final String expectedUrl = "http://www.somehost.com/somepath";
            final String expectedType = "browser";

            gen.set(getVal("this"));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[Title](http%3A%2F%2Fwww.somehost.com%2Fsomepath){browser}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
            final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
            assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
        });
    }

    @Test
    void testLink3() {
        createGenerator("link(${val1}, ${val2}, 'browser')", 2, gen -> {
            final String expectedText = "t}his [is] a tit(le w{it}h (brack[ets)";
            final String expectedUrl = "http://www.somehost.com/somepath?k1=v1&k[2]={v2}";
            final String expectedType = "browser";

            gen.set(getVal(expectedText, expectedUrl));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[t%7Dhis+%5Bis%5D+a+tit%28le+w%7Bit%7Dh+%28brack%5Bets%29](http%3A%2F%2Fwww.somehost.com%2Fsomepath%3Fk1%3Dv1%26k%5B2%5D%3D%7Bv2%7D){browser}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
            final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
            assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
        });
    }

    @Test
    void testDashboard() {
        createGenerator("dashboard('blah', 'abcdefg', 'dt='+formatDate(roundDay(${val1}))+'+1h')", gen -> {
            final String expectedText = "blah";
            final String expectedUrl = "?uuid=abcdefg&params=dt%3D2014-02-23T00%3A00%3A00.000Z%2B1h";

            gen.set(getVal("2014-02-22T12:12:12.000Z"));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[blah](%3Fuuid%3Dabcdefg%26params%3Ddt%253D2014-02-23T00%253A00%253A00.000Z%252B1h){dashboard}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
        });
    }

    @Test
    void testLink4() {
        createGenerator("link('blah', '?annotationId=1&streamId=2&eventId=3&title='+encodeUrl('this is a title')+'&subject='+encodeUrl('this is a subject')+'&status=New&assignedTo='+encodeUrl('test user')+'&comment='+encodeUrl('new comment'), 'annotation')", 2, gen -> {
            final String expectedText = "blah";
            final String expectedUrl = "?annotationId=1&streamId=2&eventId=3&title=this+is+a+title&subject=this+is+a+subject&status=New&assignedTo=test+user&comment=new+comment";
            final String expectedType = "annotation";

            gen.set(getVal(expectedText, expectedUrl));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[blah](%3FannotationId%3D1%26streamId%3D2%26eventId%3D3%26title%3Dthis%2Bis%2Ba%2Btitle%26subject%3Dthis%2Bis%2Ba%2Bsubject%26status%3DNew%26assignedTo%3Dtest%2Buser%26comment%3Dnew%2Bcomment){annotation}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
            final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
            assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
        });
    }

    @Test
    void testAnnotation() {
        createGenerator("annotation('blah', '1', '2', '3', 'this is a title', 'this is a subject', 'New', 'test user', 'new comment')", gen -> {
            final String expectedText = "blah";
            final String expectedUrl = "?annotationId=1&streamId=2&eventId=3&title=this+is+a+title&subject=this+is+a+subject&status=New&assignedTo=test+user&comment=new+comment";
            final String expectedType = "annotation";

            gen.set(getVal(expectedText, expectedUrl));

            final Val out = gen.eval();
            final String str = out.toString();
            assertThat(str).isEqualTo("[blah](%3FannotationId%3D1%26streamId%3D2%26eventId%3D3%26title%3Dthis%2Bis%2Ba%2Btitle%26subject%3Dthis%2Bis%2Ba%2Bsubject%26status%3DNew%26assignedTo%3Dtest%2Buser%26comment%3Dnew%2Bcomment){annotation}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
            final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
            assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
        });
    }

    @Test
    void testStaticString1() {
        createGenerator("'hello'", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("hello");
        });
    }

    @Test
    void testStaticString2() {
        createGenerator("'[Click Here](http://www.somehost.com/somepath){DIALOG}'", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("[Click Here](http://www.somehost.com/somepath){DIALOG}");
        });
    }

    @Test
    void testStaticNumber() {
        createGenerator("50", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("50");
        });
    }

    @Test
    void testStringLength1() {
        createGenerator("stringLength(${val1})", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testSubstring1() {
        createGenerator("substring(${val1}, 1, 2)", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("h");
        });
    }

    @Test
    void testSubstring3() {
        createGenerator("substring(${val1}, 2, 99)", gen -> {
            gen.set(getVal("his"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("s");
        });
    }

    @Test
    void testSubstring4() {
        createGenerator("substring(${val1}, 1+1, 99-1)", gen -> {
            gen.set(getVal("his"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("s");
        });
    }

    @Test
    void testSubstring5() {
        createGenerator("substring(${val1}, 2+5, 99-1)", gen -> {
            gen.set(getVal("his"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEmpty();
        });
    }

    @Test
    void testSubstringBefore1() {
        createGenerator("substringBefore(${val1}, '-')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("aa");
        });
    }

    @Test
    void testSubstringBefore2() {
        createGenerator("substringBefore(${val1}, 'a')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEmpty();
        });
    }

    @Test
    void testSubstringBefore3() {
        createGenerator("substringBefore(${val1}, 'b')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("aa-");
        });
    }

    @Test
    void testSubstringBefore4() {
        createGenerator("substringBefore(${val1}, 'q')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEmpty();
        });
    }

    @Test
    void testSubstringAfter1() {
        createGenerator("substringAfter(${val1}, '-')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("bb");
        });
    }

    @Test
    void testSubstringAfter2() {
        createGenerator("substringAfter(${val1}, 'a')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("a-bb");
        });
    }

    @Test
    void testSubstringAfter3() {
        createGenerator("substringAfter(${val1}, 'b')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("b");
        });
    }

    @Test
    void testSubstringAfter4() {
        createGenerator("substringAfter(${val1}, 'q')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEmpty();
        });
    }

    @Test
    void testIndexOf() {
        createGenerator("indexOf(${val1}, '-')", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toInteger().intValue()).isEqualTo(2);
        });
    }

    @Test
    void testIndexOf1() {
        createGenerator("substring(${val1}, indexOf(${val1}, '-'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("-bb");
        });
    }

    @Test
    void testIndexOf2() {
        createGenerator("substring(${val1}, indexOf(${val1}, 'a'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("aa-bb");
        });
    }

    @Test
    void testIndexOf3() {
        createGenerator("substring(${val1}, indexOf(${val1}, 'b'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("bb");
        });
    }

    @Test
    void testIndexOf4() {
        createGenerator("substring(${val1}, indexOf(${val1}, 'q'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testLastIndexOf1() {
        createGenerator("substring(${val1}, lastIndexOf(${val1}, '-'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("-bb");
        });
    }

    @Test
    void testLastIndexOf2() {
        createGenerator("substring(${val1}, lastIndexOf(${val1}, 'a'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("a-bb");
        });
    }

    @Test
    void testLastIndexOf3() {
        createGenerator("substring(${val1}, lastIndexOf(${val1}, 'b'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("b");
        });
    }

    @Test
    void testLastIndexOf4() {
        createGenerator("substring(${val1}, lastIndexOf(${val1}, 'q'), stringLength(${val1}))", gen -> {
            gen.set(getVal("aa-bb"));

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testDecode1() {
        createGenerator("decode(${val1}, 'hullo', 'hello', 'goodbye')", gen -> {
            gen.set(getVal("hullo"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("hello");
        });
    }

    @Test
    void testDecode2() {
        createGenerator("decode(${val1}, 'h.+o', 'hello', 'goodbye')", gen -> {
            gen.set(getVal("hullo"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("hello");
        });
    }

    @Test
    void testInclude1() {
        createGenerator("include(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("this"));
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testInclude2() {
        createGenerator("include(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("that"));
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("that");
        });
    }

    @Test
    void testInclude3() {
        createGenerator("include(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("other"));
            final Val out = gen.eval();
            assertThat(out.toString()).isNull();
        });
    }

    @Test
    void testExclude1() {
        createGenerator("exclude(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("this"));
            final Val out = gen.eval();
            assertThat(out.toString()).isNull();
        });
    }

    @Test
    void testExclude2() {
        createGenerator("exclude(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("that"));
            final Val out = gen.eval();
            assertThat(out.toString()).isNull();
        });
    }

    @Test
    void testExclude3() {
        createGenerator("exclude(${val1}, 'this', 'that')", gen -> {
            gen.set(getVal("other"));
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("other");
        });
    }

    @Test
    void testEncodeUrl() {
        createGenerator("encodeUrl('https://www.somesite.com:8080/this/path?query=string')", gen -> {
            gen.set(getVal(""));
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring");
        });
    }

    @Test
    void testDecodeUrl() {
        createGenerator("decodeUrl('https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring')", gen -> {
            gen.set(getVal(""));
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("https://www.somesite.com:8080/this/path?query=string");
        });
    }

    @Test
    void testEquals1() {
        createGenerator("equals(${val1}, 'plop')", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testEquals2() {
        createGenerator("equals(${val1}, ${val1})", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testEquals3() {
        createGenerator("equals(${val1}, 'plip')", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testEquals4() {
        createGenerator("equals(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("plop", "plip"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testEquals5() {
        createGenerator("equals(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("plop", "plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testEquals6() {
        createGenerator("${val1}=${val2}", 2, gen -> {
            gen.set(getVal("plop", "plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testEqualsNull1() {
        createGenerator("${val1}=null()", gen -> {
            gen.set(new Val[]{ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testEqualsNull2() {
        createGenerator("${val1}=null()", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testEqualsNull3() {
        createGenerator("null()=null()", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testEqualsNull4() {
        createGenerator("if(${val1}=null(), true(), false())", gen -> {
            gen.set(new Val[]{ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.type().isError()).isTrue();
        });
    }

    @Test
    void testIsNull1() {
        createGenerator("isNull(${val1})", gen -> {
            gen.set(new Val[]{ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testIsNull2() {
        createGenerator("isNull(${val1})", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testIsNull3() {
        createGenerator("isNull(null())", gen -> {
            gen.set(getVal("plop"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testIsNull4() {
        createGenerator("if(isNull(${val1}), true(), false())", gen -> {
            gen.set(new Val[]{ValNull.INSTANCE});

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThan1() {
        createGenerator("lessThan(1, 0)", 2, gen -> {

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testLessThan2() {
        createGenerator("lessThan(1, 1)", 2, gen -> {
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testLessThan3() {
        createGenerator("lessThan(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal(1D, 2D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThan4() {
        createGenerator("lessThan(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred", "fred"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testLessThan5() {
        createGenerator("lessThan(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred", "fred1"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThan6() {
        createGenerator("lessThan(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred1", "fred"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testLessThanOrEqualTo1() {
        createGenerator("lessThanOrEqualTo(1, 0)", 2, gen -> {
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testLessThanOrEqualTo2() {
        createGenerator("lessThanOrEqualTo(1, 1)", 2, gen -> {
            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThanOrEqualTo3() {
        createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal(1D, 2D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThanOrEqualTo3_mk2() {
        createGenerator("(${val1}<=${val2})", 2, gen -> {
            gen.set(getVal(1D, 2D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThanOrEqualTo4() {
        createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred", "fred"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThanOrEqualTo5() {
        createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred", "fred1"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testLessThanOrEqualTo6() {
        createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal("fred1", "fred"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("false");
        });
    }

    @Test
    void testGreaterThanOrEqualTo1() {
        createGenerator("greaterThanOrEqualTo(${val1}, ${val2})", 2, gen -> {
            gen.set(getVal(2D, 1D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testGreaterThanOrEqualTo1_mk2() {
        createGenerator("(${val1}>=${val2})", 2, gen -> {
            gen.set(getVal(2D, 1D));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("true");
        });
    }

    @Test
    void testBooleanExpressions() {
        ValBoolean vTrue = ValBoolean.TRUE;
        ValBoolean vFals = ValBoolean.FALSE; // intentional typo to keep var name length consistent
        ValNull vNull = ValNull.INSTANCE;
        ValErr vEror = ValErr.create("Expecting an error"); // intentional typo to keep var name length consistent

        ValLong vLng0 = ValLong.create(0L);
        ValLong vLng1 = ValLong.create(1L);
        ValLong vLng2 = ValLong.create(2L);

        ValInteger vInt0 = ValInteger.create(0);
        ValInteger vInt1 = ValInteger.create(1);
        ValInteger vInt2 = ValInteger.create(2);

        ValDouble vDbl0 = ValDouble.create(0);
        ValDouble vDbl1 = ValDouble.create(1);
        ValDouble vDbl2 = ValDouble.create(2);

        ValString vStr1 = ValString.create("1");
        ValString vStr2 = ValString.create("2");
        ValString vStrA = ValString.create("AAA");
        ValString vStrB = ValString.create("BBB");
        ValString vStra = ValString.create("aaa");
        ValString vStrT = ValString.create("true");
        ValString vStrF = ValString.create("false");
        ValString vStr_ = ValString.EMPTY;

        // null/error, equals
        assertBooleanExpression(vNull, "=", vNull, vEror);
        assertBooleanExpression(vNull, "=", vEror, vEror);
        assertBooleanExpression(vEror, "=", vEror, vEror);

        // booleans, equals
        assertBooleanExpression(vTrue, "=", vTrue, vTrue);
        assertBooleanExpression(vFals, "=", vFals, vTrue);
        assertBooleanExpression(vTrue, "=", vFals, vFals);

        // longs, equals
        assertBooleanExpression(vLng1, "=", vNull, vEror);
        assertBooleanExpression(vNull, "=", vLng1, vEror);
        assertBooleanExpression(vLng1, "=", vLng1, vTrue);
        assertBooleanExpression(vLng1, "=", vLng2, vFals);
        assertBooleanExpression(vLng1, "=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vLng1, "=", vFals, vFals);

        // integers, equals
        assertBooleanExpression(vInt1, "=", vNull, vEror);
        assertBooleanExpression(vNull, "=", vInt1, vEror);
        assertBooleanExpression(vInt1, "=", vInt1, vTrue);
        assertBooleanExpression(vInt1, "=", vInt2, vFals);
        assertBooleanExpression(vInt1, "=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vInt1, "=", vFals, vFals);

        // doubles, equals
        assertBooleanExpression(vDbl1, "=", vNull, vEror);
        assertBooleanExpression(vNull, "=", vDbl1, vEror);
        assertBooleanExpression(vDbl1, "=", vDbl1, vTrue);
        assertBooleanExpression(vDbl1, "=", vDbl2, vFals);
        assertBooleanExpression(vDbl1, "=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vDbl1, "=", vFals, vFals);

        // strings, equals
        assertBooleanExpression(vStrA, "=", vNull, vEror);
        assertBooleanExpression(vNull, "=", vStrA, vEror);
        assertBooleanExpression(vStrA, "=", vStrA, vTrue);
        assertBooleanExpression(vStrA, "=", vStrB, vFals);
        assertBooleanExpression(vStrA, "=", vTrue, vFals);
        assertBooleanExpression(vStrA, "=", vFals, vFals);
        assertBooleanExpression(vStrA, "=", vStra, vFals);

        // mixed types, equals
        assertBooleanExpression(vLng1, "=", vStr1, vTrue);
        assertBooleanExpression(vDbl1, "=", vStr1, vTrue);
        assertBooleanExpression(vLng1, "=", vTrue, vTrue); //true cast to 1
        assertBooleanExpression(vInt1, "=", vTrue, vTrue); //true cast to 1
        assertBooleanExpression(vDbl1, "=", vTrue, vTrue);
        assertBooleanExpression(vLng0, "=", vFals, vTrue); // false() cast to 0
        assertBooleanExpression(vInt0, "=", vFals, vTrue); // false() cast to 0
        assertBooleanExpression(vDbl0, "=", vFals, vTrue); // false() cast to 0
        assertBooleanExpression(vDbl1, "=", vLng1, vTrue);
        assertBooleanExpression(vStrT, "=", vTrue, vTrue); // true() cast to "true"
        assertBooleanExpression(vStrF, "=", vFals, vTrue); // false() cast to "false"


        // booleans, greater than
        assertBooleanExpression(vTrue, ">", vTrue, vFals);
        assertBooleanExpression(vFals, ">", vFals, vFals);
        assertBooleanExpression(vTrue, ">", vFals, vTrue);

        // longs, greater than
        assertBooleanExpression(vLng1, ">", vNull, vEror);
        assertBooleanExpression(vNull, ">", vLng1, vEror);
        assertBooleanExpression(vLng1, ">", vLng1, vFals);
        assertBooleanExpression(vLng1, ">", vLng2, vFals);
        assertBooleanExpression(vLng2, ">", vLng1, vTrue);
        assertBooleanExpression(vLng1, ">", vTrue, vFals); //true cast to 1
        assertBooleanExpression(vLng2, ">", vDbl1, vTrue);
        assertBooleanExpression(vLng2, ">", vStr1, vTrue);

        // longs, greater than
        assertBooleanExpression(vInt1, ">", vNull, vEror);
        assertBooleanExpression(vNull, ">", vInt1, vEror);
        assertBooleanExpression(vInt1, ">", vInt1, vFals);
        assertBooleanExpression(vInt1, ">", vInt2, vFals);
        assertBooleanExpression(vInt2, ">", vInt1, vTrue);
        assertBooleanExpression(vInt1, ">", vTrue, vFals); // true cast to 1
        assertBooleanExpression(vInt2, ">", vDbl1, vTrue);
        assertBooleanExpression(vInt2, ">", vStr1, vTrue);

        // doubles, greater than
        assertBooleanExpression(vDbl1, ">", vNull, vEror);
        assertBooleanExpression(vNull, ">", vDbl1, vEror);
        assertBooleanExpression(vDbl1, ">", vDbl1, vFals);
        assertBooleanExpression(vDbl1, ">", vDbl2, vFals);
        assertBooleanExpression(vDbl2, ">", vDbl1, vTrue);
        assertBooleanExpression(vDbl1, ">", vTrue, vFals); //true() cast to 1
        assertBooleanExpression(vDbl2, ">", vDbl1, vTrue);
        assertBooleanExpression(vDbl2, ">", vStr1, vTrue);

        // strings, greater than
        assertBooleanExpression(vStrA, ">", vStrA, vFals);
        assertBooleanExpression(vStrA, ">", vStrB, vFals);
        assertBooleanExpression(vStrB, ">", vStrA, vTrue);
        assertBooleanExpression(vStrA, ">", vStr_, vTrue);
        assertBooleanExpression(vStrA, ">", vStr1, vTrue);
        assertBooleanExpression(vStrA, ">", vNull, vEror);
        assertBooleanExpression(vStrA, ">", vStra, vFals);
        assertBooleanExpression(vStra, ">", vStrA, vTrue);


        // booleans, greater than or equal to
        assertBooleanExpression(vTrue, ">=", vTrue, vTrue);
        assertBooleanExpression(vFals, ">=", vFals, vTrue);
        assertBooleanExpression(vTrue, ">=", vFals, vTrue);
        assertBooleanExpression(vFals, ">=", vTrue, vFals);

        // longs, greater than or equal to
        assertBooleanExpression(vLng1, ">=", vNull, vEror);
        assertBooleanExpression(vNull, ">=", vLng1, vEror);
        assertBooleanExpression(vLng1, ">=", vLng1, vTrue);
        assertBooleanExpression(vLng1, ">=", vLng2, vFals);
        assertBooleanExpression(vLng2, ">=", vLng1, vTrue);
        assertBooleanExpression(vLng1, ">=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vLng2, ">=", vDbl1, vTrue);
        assertBooleanExpression(vLng2, ">=", vStr1, vTrue);

        // integers, greater than or equal to
        assertBooleanExpression(vInt1, ">=", vNull, vEror);
        assertBooleanExpression(vNull, ">=", vInt1, vEror);
        assertBooleanExpression(vInt1, ">=", vInt1, vTrue);
        assertBooleanExpression(vInt1, ">=", vInt2, vFals);
        assertBooleanExpression(vInt2, ">=", vInt1, vTrue);
        assertBooleanExpression(vInt1, ">=", vTrue, vTrue); //true() cast to 1
        assertBooleanExpression(vInt2, ">=", vDbl1, vTrue);
        assertBooleanExpression(vInt2, ">=", vStr1, vTrue);

        // doubles, greater than or equal to
        assertBooleanExpression(vDbl1, ">=", vNull, vEror);
        assertBooleanExpression(vNull, ">=", vDbl1, vEror);
        assertBooleanExpression(vDbl1, ">=", vDbl1, vTrue);
        assertBooleanExpression(vDbl1, ">=", vDbl2, vFals);
        assertBooleanExpression(vDbl2, ">=", vDbl1, vTrue);
        assertBooleanExpression(vDbl1, ">=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vDbl2, ">=", vDbl1, vTrue);
        assertBooleanExpression(vDbl2, ">=", vStr1, vTrue);

        // strings, greater than or equal to
        assertBooleanExpression(vStrA, ">=", vStrA, vTrue);
        assertBooleanExpression(vStrA, ">=", vStrB, vFals);
        assertBooleanExpression(vStrB, ">=", vStrA, vTrue);
        assertBooleanExpression(vStrA, ">=", vStr_, vTrue);
        assertBooleanExpression(vStrA, ">=", vStr1, vTrue);
        assertBooleanExpression(vStrA, ">=", vNull, vEror);


        // booleans, less than
        assertBooleanExpression(vTrue, "<", vTrue, vFals);
        assertBooleanExpression(vFals, "<", vFals, vFals);
        assertBooleanExpression(vTrue, "<", vFals, vFals);
        assertBooleanExpression(vFals, "<", vTrue, vTrue);

        // longs, less than
        assertBooleanExpression(vLng1, "<", vNull, vEror);
        assertBooleanExpression(vNull, "<", vLng1, vEror);
        assertBooleanExpression(vLng1, "<", vLng1, vFals);
        assertBooleanExpression(vLng1, "<", vLng2, vTrue);
        assertBooleanExpression(vLng2, "<", vLng1, vFals);
        assertBooleanExpression(vLng1, "<", vTrue, vFals); // true() cast to 1
        assertBooleanExpression(vLng2, "<", vDbl1, vFals);
        assertBooleanExpression(vLng2, "<", vStr1, vFals);

        // integers, less than
        assertBooleanExpression(vInt1, "<", vNull, vEror);
        assertBooleanExpression(vNull, "<", vInt1, vEror);
        assertBooleanExpression(vInt1, "<", vInt1, vFals);
        assertBooleanExpression(vInt1, "<", vInt2, vTrue);
        assertBooleanExpression(vInt2, "<", vInt1, vFals);
        assertBooleanExpression(vInt1, "<", vTrue, vFals); // true() cast to 1
        assertBooleanExpression(vInt2, "<", vDbl1, vFals);
        assertBooleanExpression(vInt2, "<", vStr1, vFals);

        // doubles, less than
        assertBooleanExpression(vDbl1, "<", vNull, vEror);
        assertBooleanExpression(vNull, "<", vDbl1, vEror);
        assertBooleanExpression(vDbl1, "<", vDbl1, vFals);
        assertBooleanExpression(vDbl1, "<", vDbl2, vTrue);
        assertBooleanExpression(vDbl2, "<", vDbl1, vFals);
        assertBooleanExpression(vDbl1, "<", vTrue, vFals); // true() cast to 1
        assertBooleanExpression(vDbl2, "<", vDbl1, vFals);
        assertBooleanExpression(vDbl2, "<", vStr1, vFals);

        // strings, less than
        assertBooleanExpression(vStrA, "<", vStrA, vFals);
        assertBooleanExpression(vStrA, "<", vStrB, vTrue);
        assertBooleanExpression(vStrB, "<", vStrA, vFals);
        assertBooleanExpression(vStrA, "<", vStr_, vFals);
        assertBooleanExpression(vStrA, "<", vStr1, vFals);
        assertBooleanExpression(vStrA, "<", vNull, vEror);


        // booleans, less than or equal to
        assertBooleanExpression(vTrue, "<=", vTrue, vTrue);
        assertBooleanExpression(vFals, "<=", vFals, vTrue);
        assertBooleanExpression(vTrue, "<=", vFals, vFals);
        assertBooleanExpression(vFals, "<=", vTrue, vTrue);

        // longs, less than or equal to
        assertBooleanExpression(vLng1, "<=", vNull, vEror);
        assertBooleanExpression(vNull, "<=", vLng1, vEror);
        assertBooleanExpression(vLng1, "<=", vLng1, vTrue);
        assertBooleanExpression(vLng1, "<=", vLng2, vTrue);
        assertBooleanExpression(vLng2, "<=", vLng1, vFals);
        assertBooleanExpression(vLng1, "<=", vTrue, vTrue); // true() cast to 1
        assertBooleanExpression(vLng2, "<=", vDbl1, vFals);
        assertBooleanExpression(vDbl1, "<=", vLng2, vTrue);
        assertBooleanExpression(vLng2, "<=", vStr1, vFals);

        // integers, less than or equal to
        assertBooleanExpression(vInt1, "<=", vNull, vEror);
        assertBooleanExpression(vNull, "<=", vInt1, vEror);
        assertBooleanExpression(vInt1, "<=", vInt1, vTrue);
        assertBooleanExpression(vInt1, "<=", vInt2, vTrue);
        assertBooleanExpression(vInt2, "<=", vInt1, vFals);
        assertBooleanExpression(vInt1, "<=", vTrue, vTrue); //true() cast to 1
        assertBooleanExpression(vInt2, "<=", vDbl1, vFals);
        assertBooleanExpression(vInt1, "<=", vDbl2, vTrue);
        assertBooleanExpression(vInt2, "<=", vStr1, vFals);
        assertBooleanExpression(vInt1, "<=", vStr2, vTrue);

        // doubles, less than or equal to
        assertBooleanExpression(vDbl1, "<=", vNull, vEror);
        assertBooleanExpression(vNull, "<=", vDbl1, vEror);
        assertBooleanExpression(vDbl1, "<=", vDbl1, vTrue);
        assertBooleanExpression(vDbl1, "<=", vDbl2, vTrue);
        assertBooleanExpression(vDbl2, "<=", vDbl1, vFals);
        assertBooleanExpression(vDbl1, "<=", vTrue, vTrue); // true() caste to 1
        assertBooleanExpression(vDbl2, "<=", vStr1, vFals);
        assertBooleanExpression(vDbl1, "<=", vStr2, vTrue);

        // strings, less than or equal to
        assertBooleanExpression(vStrA, "<=", vStrA, vTrue);
        assertBooleanExpression(vStrA, "<=", vStrB, vTrue);
        assertBooleanExpression(vStrB, "<=", vStrA, vFals);
        assertBooleanExpression(vStrA, "<=", vStr_, vFals);
        assertBooleanExpression(vStrA, "<=", vStr1, vFals);
        assertBooleanExpression(vStrA, "<=", vNull, vEror);
    }

    @Test
    void testSubstring2() {
        createGenerator("substring(${val1}, 0, 99)", gen -> {
            gen.set(getVal("this"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this");
        });
    }

    @Test
    void testHash1() {
        createGenerator("hash(${val1})", gen -> {
            gen.set(getVal("test"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        });
    }

    @Test
    void testHash2() {
        createGenerator("hash(${val1}, 'SHA-512')", gen -> {
            gen.set(getVal("test"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff");
        });
    }

    @Test
    void testHash3() {
        createGenerator("hash(${val1}, 'SHA-512', 'mysalt')", gen -> {
            gen.set(getVal("test"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("af2910d4d8acf3fcf9683d3ca4425327cb1b4b48bc690f566e27b0e0144c17af82066cf6af14d3a30312ed9df671e0e24b1c66ed3973d1a7836899d75c4d6bb8");
        });
    }

    @Test
    void testJoining1() {
        createGenerator("joining(${val1}, ',')", gen -> {
            gen.set(getVal("one"));
            gen.set(getVal("two"));
            gen.set(getVal("three"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("one,two,three");
        });
    }

    @Test
    void testJoining2() {
        createGenerator("joining(${val1})", gen -> {
            gen.set(getVal("one"));
            gen.set(getVal("two"));
            gen.set(getVal("three"));

            final Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("onetwothree");
        });
    }

    @Test
    void testCount() {
        createGenerator("count()", gen -> {
            gen.set(getVal(122D));
            gen.set(getVal(133D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(getVal(11D));
            gen.set(getVal(122D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testCountGroups() {
        createGenerator("countGroups()", gen -> {
            gen.set(getVal(122D));
            gen.set(getVal(133D));

            final GroupKey parent = new GroupKey(0, null, new Val[]{ValString.create("parent")});
            gen.addChildKey(new GroupKey(1, parent, new Val[]{ValString.create("val1")}));
            gen.addChildKey(new GroupKey(1, parent, new Val[]{ValString.create("val1")}));
            gen.addChildKey(new GroupKey(1, parent, new Val[]{ValString.create("val2")}));
            gen.addChildKey(new GroupKey(1, parent, new Val[]{ValString.create("val2")}));

            Val out = gen.eval();
            assertThat(out.toInteger()).isEqualTo(2);
        });
    }

    @Test
    void testCountUnique() {
        createGenerator("countUnique(${val1})", gen -> {
            gen.set(getVal(122D));
            gen.set(getVal(133D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

            gen.set(getVal(11D));
            gen.set(getVal(122D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
        });
    }

    @Test
    void testCountUniqueStaticValue() {
        createGenerator("countUnique('test')", gen -> {
            gen.set(getVal(122D));
            gen.set(getVal(133D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));

            gen.set(getVal(11D));
            gen.set(getVal(122D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
        });
    }

    @Test
    void testAdd1() {
        createGenerator("3+4", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));
        });
    }

    @Test
    void testAdd2() {
        createGenerator("3+4+5", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
        });
    }

    @Test
    void testAdd3() {
        createGenerator("2+count()", gen -> {
            gen.set(getVal(1D));
            gen.set(getVal(1D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testSubtract1() {
        createGenerator("3-4", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(-1D, Offset.offset(0D));
        });
    }

    @Test
    void testSubtract2() {
        createGenerator("2-count()", gen -> {
            gen.set(getVal(1D));
            gen.set(getVal(1D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(0D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(-2D, Offset.offset(0D));
        });
    }

    @Test
    void testMultiply1() {
        createGenerator("3*4", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
        });
    }

    @Test
    void testMultiply2() {
        createGenerator("2*count()", gen -> {
            gen.set(getVal(1D));
            gen.set(getVal(1D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testDivide1() {
        createGenerator("8/4", gen -> {
//        gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testDivide2() {
        createGenerator("8/count()", gen -> {
            gen.set(getVal(1D));
            gen.set(getVal(1D));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

            gen.set(getVal(1D));
            gen.set(getVal(1D));

            out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testDivide_byZero() {
        createGenerator("8/0", gen -> {
            final Val out = gen.eval();
            assertThat(out instanceof ValErr).isTrue();
            System.out.println("Error message: " + ((ValErr) out).getMessage());
        });
    }

    @Test
    void testFloorNum1() {
        createGenerator("floor(8.4234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum2() {
        createGenerator("floor(8.5234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum3() {
        createGenerator("floor(${val1})", gen -> {
            gen.set(getVal(1.34D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum4() {
        createGenerator("floor(${val1}+count())", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum5() {
        createGenerator("floor(${val1}+count(), 1)", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.8D, Offset.offset(0D));
        });
    }

    @Test
    void testFloorNum6() {
        createGenerator("floor(${val1}+count(), 2)", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.86D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum1() {
        createGenerator("ceiling(8.4234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum2() {
        createGenerator("ceiling(8.5234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum3() {
        createGenerator("ceiling(${val1})", gen -> {
            gen.set(getVal(1.34D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum4() {
        createGenerator("ceiling(${val1}+count())", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum5() {
        createGenerator("ceiling(${val1}+count(), 1)", gen -> {

            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
        });
    }

    @Test
    void testCeilNum6() {
        createGenerator("ceiling(${val1}+count(), 2)", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum1() {
        createGenerator("round(8.4234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum2() {
        createGenerator("round(8.5234)", gen -> {
            gen.set(getVal(1D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum3() {
        createGenerator("round(${val1})", gen -> {

            gen.set(getVal(1.34D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum4() {
        createGenerator("round(${val1}+count())", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum5() {
        createGenerator("round(${val1}+count(), 1)", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
        });
    }

    @Test
    void testRoundNum6() {
        createGenerator("round(${val1}+count(), 2)", gen -> {
            gen.set(getVal(1.34D));
            gen.set(getVal(1.8655D));

            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
        });
    }

    @Test
    void testTime() {
        testTime("floorSecond", "2014-02-22T12:12:12.888Z", "2014-02-22T12:12:12.000Z");
        testTime("floorMinute", "2014-02-22T12:12:12.888Z", "2014-02-22T12:12:00.000Z");
        testTime("floorHour", "2014-02-22T12:12:12.888Z", "2014-02-22T12:00:00.000Z");
        testTime("floorDay", "2014-02-22T12:12:12.888Z", "2014-02-22T00:00:00.000Z");
        testTime("floorMonth", "2014-02-22T12:12:12.888Z", "2014-02-01T00:00:00.000Z");
        testTime("floorYear", "2014-02-22T12:12:12.888Z", "2014-01-01T00:00:00.000Z");

        testTime("ceilingSecond", "2014-02-22T12:12:12.888Z", "2014-02-22T12:12:13.000Z");
        testTime("ceilingMinute", "2014-02-22T12:12:12.888Z", "2014-02-22T12:13:00.000Z");
        testTime("ceilingHour", "2014-02-22T12:12:12.888Z", "2014-02-22T13:00:00.000Z");
        testTime("ceilingDay", "2014-02-22T12:12:12.888Z", "2014-02-23T00:00:00.000Z");
        testTime("ceilingMonth", "2014-02-22T12:12:12.888Z", "2014-03-01T00:00:00.000Z");
        testTime("ceilingYear", "2014-02-22T12:12:12.888Z", "2015-01-01T00:00:00.000Z");

        testTime("roundSecond", "2014-02-22T12:12:12.888Z", "2014-02-22T12:12:13.000Z");
        testTime("roundMinute", "2014-02-22T12:12:12.888Z", "2014-02-22T12:12:00.000Z");
        testTime("roundHour", "2014-02-22T12:12:12.888Z", "2014-02-22T12:00:00.000Z");
        testTime("roundDay", "2014-02-22T12:12:12.888Z", "2014-02-23T00:00:00.000Z");
        testTime("roundMonth", "2014-02-22T12:12:12.888Z", "2014-03-01T00:00:00.000Z");
        testTime("roundYear", "2014-02-22T12:12:12.888Z", "2014-01-01T00:00:00.000Z");
    }

    private void testTime(final String function, final String in, final String expected) {
        final double expectedMs = DateUtil.parseNormalDateTimeString(expected);
        final String expression = function + "(${val1})";
        createGenerator(expression, gen -> {
            gen.set(getVal(in));
            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(expectedMs, Offset.offset(0D));
        });
    }

    @Test
    void testBODMAS1() {
        createGenerator("4+4/2+2", gen -> {
            final Val out = gen.eval();

            // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 8.
            assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
        });
    }

    @Test
    void testBODMAS2() {
        createGenerator("(4+4)/2+2", gen -> {
            final Val out = gen.eval();

            // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 6.
            assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
        });
    }

    @Test
    void testBODMAS3() {
        createGenerator("(4+4)/(2+2)", gen -> {
            final Val out = gen.eval();

            // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 2.
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testBODMAS4() {
        createGenerator("4+4/2+2*3", gen -> {
            final Val out = gen.eval();

            // Non BODMAS would evaluate as 18 - BODMAS should be 12.
            assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
        });
    }

    @Test
    void testBODMAS5() {
        createGenerator("8%3", gen -> {
            final Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
        });
    }

    @Test
    void testExtractAuthorityFromUri() {
        createGenerator("extractAuthorityFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("www.example.com:1234");
        });
    }

    @Test
    void testExtractFragmentFromUri() {
        createGenerator("extractFragmentFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path#frag"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("frag");
        });
    }

    @Test
    void testExtractHostFromUri() {
        createGenerator("extractHostFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("www.example.com");
        });
    }

    @Test
    void testExtractPathFromUri() {
        createGenerator("extractPathFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("/this/is/a/path");
        });
    }

    @Test
    void testExtractPortFromUri() {
        createGenerator("extractPortFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("1234");
        });
    }

    @Test
    void testExtractQueryFromUri() {
        createGenerator("extractQueryFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path?this=that&foo=bar"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("this=that&foo=bar");
        });
    }

    @Test
    void testExtractSchemeFromUri() {
        createGenerator("extractSchemeFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("http");
        });
    }

    @Test
    void testExtractSchemeSpecificPartFromUri() {
        createGenerator("extractSchemeSpecificPartFromUri(${val1})", gen -> {
            gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("//www.example.com:1234/this/is/a/path");
        });
    }

    @Test
    void testExtractUserInfoFromUri() {
        createGenerator("extractUserInfoFromUri(${val1})", gen -> {
            gen.set(getVal("http://john:doe@example.com:81/"));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("john:doe");
        });
    }

    @Test
    void testParseDate1() {
        createGenerator("parseDate(${val1})", gen -> {
            gen.set(getVal("2014-02-22T12:12:12.888Z"));
            Val out = gen.eval();
            assertThat(out.toLong().longValue()).isEqualTo(1393071132888L);
        });
    }

    @Test
    void testParseDate2() {
        createGenerator("parseDate(${val1}, 'yyyy MM dd')", gen -> {
            gen.set(getVal("2014 02 22"));
            Val out = gen.eval();
            assertThat(out.toLong().longValue()).isEqualTo(1393027200000L);
        });
    }

    @Test
    void testParseDate3() {
        createGenerator("parseDate(${val1}, 'yyyy MM dd', '+0400')", gen -> {
            gen.set(getVal("2014 02 22"));
            Val out = gen.eval();
            assertThat(out.toLong().longValue()).isEqualTo(1393012800000L);
        });
    }

    @Test
    void testFormatDate1() {
        createGenerator("formatDate(${val1})", gen -> {
            gen.set(getVal(1393071132888L));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2014-02-22T12:12:12.888Z");
        });
    }

    @Test
    void testFormatDate2() {
        createGenerator("formatDate(${val1}, 'yyyy MM dd')", gen -> {
            gen.set(getVal(1393071132888L));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2014 02 22");
        });
    }

    @Test
    void testFormatDate3() {
        createGenerator("formatDate(${val1}, 'yyyy MM dd', '+1200')", gen -> {
            gen.set(getVal(1393071132888L));
            Val out = gen.eval();
            assertThat(out.toString()).isEqualTo("2014 02 23");
        });
    }

    @Test
    void testVariance1() {
        createGenerator("variance(600, 470, 170, 430, 300)", gen -> {
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testVariance2() {
        createGenerator("variance(${val1})", gen -> {
            gen.set(getVal(600));
            gen.set(getVal(470));
            gen.set(getVal(170));
            gen.set(getVal(430));
            gen.set(getVal(300));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
        });
    }

    @Test
    void testStDev1() {
        createGenerator("round(stDev(600, 470, 170, 430, 300))", gen -> {
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }

    @Test
    void testStDev2() {
        createGenerator("round(stDev(${val1}))", gen -> {
            gen.set(getVal(600));
            gen.set(getVal(470));
            gen.set(getVal(170));
            gen.set(getVal(430));
            gen.set(getVal(300));

            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
        });
    }

    @Test
    void testAny() {
        createGenerator("any(${val1})", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("any(${val1})", child -> {
                    child.set(getVal(300));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toDouble()).isEqualTo(300, Offset.offset(0D));
        });
    }

    @Test
    void testFirst() {
        createGenerator("first(${val1})", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("first(${val1})", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toDouble()).isEqualTo(1, Offset.offset(0D));
        });
    }

    @Test
    void testLast() {
        createGenerator("last(${val1})", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("last(${val1})", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toDouble()).isEqualTo(10, Offset.offset(0D));
        });
    }

    @Test
    void testNth() {
        createGenerator("nth(${val1}, 7)", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("nth(${val1}, 7)", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toDouble()).isEqualTo(7, Offset.offset(0D));
        });
    }

    @Test
    void testTop() {
        createGenerator("top(${val1}, ',', 3)", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("top(${val1}, ',', 3)", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toString()).isEqualTo("1,2,3");
        });
    }

    @Test
    void testTopSmall() {
        createGenerator("top(${val1}, ',', 3)", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[2];
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                createGenerator("top(${val1}, ',', 3)", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toString()).isEqualTo("1,2");
        });
    }

    @Test
    void testBottom() {
        createGenerator("bottom(${val1}, ',', 3)", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                createGenerator("bottom(${val1}, ',', 3)", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toString()).isEqualTo("8,9,10");
        });
    }

    @Test
    void testBottomSmall() {
        createGenerator("bottom(${val1}, ',', 3)", gen -> {
            gen.set(getVal(300));
            Val out = gen.eval();
            assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

            final Generator[] children = new Generator[2];
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                createGenerator("bottom(${val1}, ',', 3)", child -> {
                    child.set(getVal(idx + 1));
                    children[idx] = child;
                });
            }

            final Selector selector = (Selector) gen;
            final Val selected = selector.select(children);
            assertThat(selected.toString()).isEqualTo("1,2");
        });
    }

    @Test
    void testToBoolean1() {
        createGenerator("toBoolean('true')", gen ->
                assertThat(gen.eval()).isEqualTo(ValBoolean.TRUE));
    }

    @Test
    void testToBoolean2() {
        createGenerator("toBoolean(${val1})", gen -> {
            gen.set(getVal("true"));
            assertThat(gen.eval()).isEqualTo(ValBoolean.TRUE);
        });
    }

    @Test
    void testToDouble1() {
        createGenerator("toDouble('100')", gen ->
                assertThat(gen.eval()).isEqualTo(ValDouble.create(100)));
    }

    @Test
    void testToDouble2() {
        createGenerator("toDouble(${val1})", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValDouble.create(100));
        });
    }

    @Test
    void testToInteger1() {
        createGenerator("toInteger('100')", gen ->
                assertThat(gen.eval()).isEqualTo(ValInteger.create(100)));
    }

    @Test
    void testToInteger2() {
        createGenerator("toInteger(${val1})", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValInteger.create(100));
        });
    }

    @Test
    void testToLong1() {
        createGenerator("toLong('100')", gen ->
                assertThat(gen.eval()).isEqualTo(ValLong.create(100)));
    }

    @Test
    void testToLong2() {
        createGenerator("toLong(${val1})", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValLong.create(100));
        });
    }

    @Test
    void testToString1() {
        createGenerator("toString('100')", gen ->
                assertThat(gen.eval()).isEqualTo(ValString.create("100")));
    }

    @Test
    void testToString2() {
        createGenerator("toString(${val1})", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValString.create("100"));
        });
    }

    @Test
    void testMappedValues1() {
        createGenerator("param('testkey')", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValString.create("testvalue"));
        });
    }

    @Test
    void testMappedValues2() {
        createGenerator("params()", gen -> {
            gen.set(getVal("100"));
            assertThat(gen.eval()).isEqualTo(ValString.create("testkey=\"testvalue\""));
        });
    }

    @Test
    void testErrorHandling1() {
        ValLong valLong = ValLong.create(10);
        assertThatItEvaluatesToValErr("(${val1}=err())", valLong);
        assertThatItEvaluatesToValErr("(err()=${val1})", valLong);
        assertThatItEvaluatesToValErr("(err()=null())", valLong);
        assertThatItEvaluatesToValErr("(null()=err())", valLong);
        assertThatItEvaluatesToValErr("(null()=${val1})", valLong);
        assertThatItEvaluatesToValErr("(${val1}=null())", valLong);

        assertThatItEvaluatesToValErr("(${val1}>=err())", valLong);
        assertThatItEvaluatesToValErr("(err()>=${val1})", valLong);
        assertThatItEvaluatesToValErr("(err()>=null())", valLong);
        assertThatItEvaluatesToValErr("(null()>=err())", valLong);
        assertThatItEvaluatesToValErr("(null()>=${val1})", valLong);
        assertThatItEvaluatesToValErr("(${val1}>=null())", valLong);

        assertThatItEvaluatesToValErr("(${val1}>err())", valLong);
        assertThatItEvaluatesToValErr("(err()>${val1})", valLong);
        assertThatItEvaluatesToValErr("(err()>null())", valLong);
        assertThatItEvaluatesToValErr("(null()>err())", valLong);
        assertThatItEvaluatesToValErr("(null()>${val1})", valLong);
        assertThatItEvaluatesToValErr("(${val1}>null())", valLong);

        assertThatItEvaluatesToValErr("(${val1}<=err())", valLong);
        assertThatItEvaluatesToValErr("(err()<=${val1})", valLong);
        assertThatItEvaluatesToValErr("(err()<=null())", valLong);
        assertThatItEvaluatesToValErr("(null()<=err())", valLong);
        assertThatItEvaluatesToValErr("(null()<=${val1})", valLong);
        assertThatItEvaluatesToValErr("(${val1}<=null())", valLong);

        assertThatItEvaluatesToValErr("(${val1}<err())", valLong);
        assertThatItEvaluatesToValErr("(err()<${val1})", valLong);
        assertThatItEvaluatesToValErr("(err()<null())", valLong);
        assertThatItEvaluatesToValErr("(null()<err())", valLong);
        assertThatItEvaluatesToValErr("(null()<${val1})", valLong);
        assertThatItEvaluatesToValErr("(${val1}<null())", valLong);
    }

    private void assertThatItEvaluatesToValErr(final String expression, final Val... values) {
        createGenerator(expression, gen -> {
            gen.set(values);
            Val out = gen.eval();
            System.out.println(expression + " - " +
                    out.getClass().getSimpleName() + ": " +
                    out.toString() +
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));
            assertThat(out).isInstanceOf(ValErr.class);
        });
    }

    @Test
    void testTypeOf() {
        ValBoolean vTrue = ValBoolean.TRUE;
        ValBoolean vFals = ValBoolean.FALSE; // intentional typo to keep var name length consistent
        ValNull vNull = ValNull.INSTANCE;
        ValErr vEror = ValErr.create("Expecting an error"); // intentional typo to keep var name length consistent
        ValLong vLng0 = ValLong.create(0L);
        ValInteger vInt0 = ValInteger.create(1);
        ValDouble vDbl0 = ValDouble.create(1.1);
        ValString vStr1 = ValString.create("abc");

        assertTypeOf(vTrue, "boolean");
        assertTypeOf(vFals, "boolean");
        assertTypeOf(vNull, "null");
        assertTypeOf(vEror, "error");
        assertTypeOf(vLng0, "long");
        assertTypeOf(vInt0, "integer");
        assertTypeOf(vDbl0, "double");
        assertTypeOf(vStr1, "string");

        assertTypeOf("typeOf(err())", "error");
        assertTypeOf("typeOf(null())", "null");
        assertTypeOf("typeOf(true())", "boolean");
        assertTypeOf("typeOf(1+2)", "double");
        assertTypeOf("typeOf(concat('a', 'b'))", "string");
        assertTypeOf("typeOf('xxx')", "string");
        assertTypeOf("typeOf(1.234)", "double");
        assertTypeOf("typeOf(2>=1)", "boolean");
    }

    @Test
    void testIsExpressions() {
        final ValBoolean vTrue = ValBoolean.TRUE;
        final ValBoolean vFals = ValBoolean.FALSE; // intentional typo to keep var name length consistent
        final ValNull vNull = ValNull.INSTANCE;
        final ValErr vError = ValErr.create("Expecting an error"); // intentional typo to keep var name length consistent
        final ValLong vLong = ValLong.create(0L);
        final ValInteger vInt = ValInteger.create(0);
        final ValDouble vDbl = ValDouble.create(0);
        final ValString vString = ValString.create("1");

        final Map<String, Set<Val>> testMap = new HashMap<>();
        testMap.computeIfAbsent("isBoolean", k -> new HashSet<>(Arrays.asList(vFals, vTrue)));
        testMap.computeIfAbsent("isDouble", k -> new HashSet<>(Collections.singletonList(vDbl)));
        testMap.computeIfAbsent("isInteger", k -> new HashSet<>(Collections.singletonList(vInt)));
        testMap.computeIfAbsent("isLong", k -> new HashSet<>(Collections.singletonList(vLong)));
        testMap.computeIfAbsent("isString", k -> new HashSet<>(Collections.singletonList(vString)));
        testMap.computeIfAbsent("isNumber", k -> new HashSet<>(Arrays.asList(vDbl, vInt, vLong)));
        testMap.computeIfAbsent("isValue", k -> new HashSet<>(Arrays.asList(vFals, vTrue, vDbl, vInt, vLong, vString)));
        testMap.computeIfAbsent("isNull", k -> new HashSet<>(Collections.singletonList(vNull)));
        testMap.computeIfAbsent("isError", k -> new HashSet<>(Collections.singletonList(vError)));

        final List<Val> types = Arrays.asList(vTrue, vFals, vNull, vError, vLong, vInt, vDbl, vString);
        testMap.forEach((k, v) -> types.forEach(type -> assertIsExpression(type, k, ValBoolean.create(v.contains(type)))));
    }

    private void createGenerator(final String expression, final Consumer<Generator> consumer) {
        createGenerator(expression, 1, consumer);
    }

    private void createExpression(final String expression, final Consumer<Expression> consumer) {
        createExpression(expression, 1, consumer);
    }

    private void createGenerator(final String expression, final int valueCount, final Consumer<Generator> consumer) {
        createExpression(expression, valueCount, exp -> {
            final Generator gen = exp.createGenerator();
            consumer.accept(gen);

            final Generator generator2 = exp.createGenerator();
            testKryo(gen, generator2);
        });
    }

    private void createExpression(final String expression, final int valueCount, final Consumer<Expression> consumer) {
        final FieldIndex fieldIndex = new FieldIndex();
        for (int i = 1; i <= valueCount; i++) {
            fieldIndex.create("val" + i);
        }

        Expression exp;
        try {
            exp = parser.parse(fieldIndex, expression);
        } catch (final ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final Map<String, String> mappedValues = new HashMap<>();
        mappedValues.put("testkey", "testvalue");
        exp.setStaticMappedValues(mappedValues);

        final String actual = exp.toString();
        assertThat(actual).isEqualTo(expression);

        consumer.accept(exp);
    }

    private void assertBooleanExpression(final Val val1, final String operator, final Val val2, final Val expectedOutput) {
        final String expression = String.format("(${val1}%s${val2})", operator);
        createGenerator(expression, 2, gen -> {
            gen.set(new Val[]{val1, val2});
            Val out = gen.eval();

            System.out.printf("[%s: %s] %s [%s: %s] => [%s: %s%s]%n",
                    val1.getClass().getSimpleName(), val1.toString(),
                    operator,
                    val2.getClass().getSimpleName(), val2.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        });
    }

    private void assertTypeOf(final String expression, final String expectedType) {
        createGenerator(expression, gen -> {
            Val out = gen.eval();

            System.out.printf("%s => [%s:%s%s]%n",
                    expression,
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));

            // The output type is always wrapped in a ValString
            assertThat(out.type().toString()).isEqualTo("string");

            assertThat(out).isInstanceOf(ValString.class);
            assertThat(out.toString()).isEqualTo(expectedType);
        });
    }

    private void assertTypeOf(final Val val1, final String expectedType) {
        final String expression = "typeOf(${val1})";
        createGenerator(expression, gen -> {
            gen.set(new Val[]{val1});
            Val out = gen.eval();

            System.out.printf("%s - [%s:%s] => [%s:%s%s]%n",
                    expression,
                    val1.getClass().getSimpleName(), val1.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));

            // The output type is always wrapped in a ValString
            assertThat(out.type().toString()).isEqualTo("string");

            assertThat(out).isInstanceOf(ValString.class);
            assertThat(out.toString()).isEqualTo(expectedType);
        });
    }

    private void assertIsExpression(final Val val1, final String function, final Val expectedOutput) {
        final String expression = String.format("%s(${val1})", function);
        createGenerator(expression, 2, gen -> {
            gen.set(new Val[]{val1});
            Val out = gen.eval();

            System.out.printf("%s([%s: %s]) => [%s: %s%s]%n",
                    function,
                    val1.getClass().getSimpleName(), val1.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        });
    }

    private void testKryo(final Generator inputGenerator, final Generator outputGenerator) {
        final Val val = inputGenerator.eval();

        ByteBuffer buffer = ByteBuffer.allocateDirect(1000);

        try (final Output output = new Output(new ByteBufferOutputStream(buffer))) {
            inputGenerator.write(output);
        }

        buffer.flip();
        print(buffer);

        try (final Input input = new Input(new ByteBufferInputStream(buffer))) {
            outputGenerator.read(input);
        }

        final Val newVal = outputGenerator.eval();

        assertThat(newVal).isEqualTo(val);
    }

    private void print(final ByteBuffer byteBuffer) {
        final ByteBuffer copy = byteBuffer.duplicate();
        byte[] bytes = new byte[copy.limit()];
        for (int i = 0; i < copy.limit(); i++) {
            bytes[i] = copy.get();
        }
        LOGGER.info(Arrays.toString(bytes));
    }
}
