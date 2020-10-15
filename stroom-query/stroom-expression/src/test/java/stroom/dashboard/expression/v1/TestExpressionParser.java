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

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParser {
    private final ExpressionParser parser = new ExpressionParser(new FunctionFactory(), new ParamFactory());

    @Test
    void testBasic() throws ParseException {
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

    private void test(final String expression) throws ParseException {
        final Expression exp = createExpression(expression);
        System.out.println(exp.toString());
    }

    @Test
    void testMin1() throws ParseException {
        final Generator gen = createGenerator("min(${val1})");

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
    void testMinUngrouped2() throws ParseException {
        final Generator gen = createGenerator("min(${val1}, 100, 30, 8)");

        gen.set(getVal(300D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testMinGrouped2() throws ParseException {
        final Generator gen = createGenerator("min(min(${val1}), 100, 30, 8)");

        gen.set(getVal(300D));
        gen.set(getVal(180D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testMin3() throws ParseException {
        final Generator gen = createGenerator("min(min(${val1}), 100, 30, 8, count(), 55)");

        gen.set(getVal(300D));
        gen.set(getVal(180D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

        gen.set(getVal(300D));
        gen.set(getVal(180D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testMax1() throws ParseException {
        final Generator gen = createGenerator("max(${val1})");

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
    }

    @Test
    void testMaxUngrouped2() throws ParseException {
        final Generator gen = createGenerator("max(${val1}, 100, 30, 8)");

        gen.set(getVal(10D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
    }

    @Test
    void testMaxGrouped2() throws ParseException {
        final Generator gen = createGenerator("max(max(${val1}), 100, 30, 8)");

        gen.set(getVal(10D));
        gen.set(getVal(40D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(100D, Offset.offset(0D));
    }

    @Test
    void testMax3() throws ParseException {
        final Generator gen = createGenerator("max(max(${val1}), count())");

        gen.set(getVal(3D));
        gen.set(getVal(2D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testSum() throws ParseException {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        final Generator gen = createGenerator("sum(${val1}, count())");

        gen.set(getVal(3D));
        gen.set(getVal(2D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(5D, Offset.offset(0D));
    }

    @Test
    void testSumOfSum() throws ParseException {
        final Generator gen = createGenerator("sum(sum(${val1}), count())");

        gen.set(getVal(3D));
        gen.set(getVal(2D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(11D, Offset.offset(0D));
    }

    @Test
    void testAverageUngrouped() throws ParseException {
        // This is a bad usage of functions as ${val1} will produce the last set
        // value when we evaluate the sum. As we are effectively grouping and we
        // don't have any control over the order that cell values are inserted
        // we will end up with indeterminate behaviour.
        final Generator gen = createGenerator("average(${val1}, count())");

        gen.set(getVal(3D));
        gen.set(getVal(4D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(8D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
    }

    @Test
    void testAverageGrouped() throws ParseException {
        final Generator gen = createGenerator("average(${val1})");

        gen.set(getVal(3D));
        gen.set(getVal(4D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.5D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(8D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testMatch1() throws ParseException {
        final Generator gen = createGenerator("match('this', 'this')");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isTrue();
    }

    @Test
    void testMatch2() throws ParseException {
        final Generator gen = createGenerator("match('this', 'that')");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isFalse();
    }

    @Test
    void testMatch3() throws ParseException {
        final Generator gen = createGenerator("match(${val1}, 'this')");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isTrue();
    }

    @Test
    void testMatch4() throws ParseException {
        final Generator gen = createGenerator("match(${val1}, 'that')");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isFalse();
    }

    @Test
    void testTrue() throws ParseException {
        final Generator gen = createGenerator("true()");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isTrue();
    }

    @Test
    void testFalse() throws ParseException {
        final Generator gen = createGenerator("false()");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isFalse();
    }

    @Test
    void testNull() throws ParseException {
        final Generator gen = createGenerator("null()");

        final Val out = gen.eval();
        assertThat(out).isInstanceOf(ValNull.class);
    }

    @Test
    void testErr() throws ParseException {
        final Generator gen = createGenerator("err()");

        final Val out = gen.eval();
        assertThat(out).isInstanceOf(ValErr.class);
    }

    @Test
    void testNotTrue() throws ParseException {
        final Generator gen = createGenerator("not(true())");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isFalse();
    }

    @Test
    void testNotFalse() throws ParseException {
        final Generator gen = createGenerator("not(false())");

        final Val out = gen.eval();
        assertThat(out.toBoolean()).isTrue();
    }

    @Test
    void testIf1() throws ParseException {
        final Generator gen = createGenerator("if(true(), 'this', 'that')");

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testIf2() throws ParseException {
        final Generator gen = createGenerator("if(false(), 'this', 'that')");

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testIf3() throws ParseException {
        final Generator gen = createGenerator("if(${val1}, 'this', 'that')");

        gen.set(getVal("true"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testIf4() throws ParseException {
        final Generator gen = createGenerator("if(${val1}, 'this', 'that')");

        gen.set(getVal("false"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testIf5() throws ParseException {
        final Generator gen = createGenerator("if(match(${val1}, 'foo'), 'this', 'that')");

        gen.set(getVal("foo"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testIf6() throws ParseException {
        final Generator gen = createGenerator("if(match(${val1}, 'foo'), 'this', 'that')");

        gen.set(getVal("bar"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testNotIf() throws ParseException {
        final Generator gen = createGenerator("if(not(${val1}), 'this', 'that')");

        gen.set(getVal("false"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testIf_nullHandling() throws ParseException {
        final Generator gen = createGenerator("if(${val1}=null(), true(), false())");

        gen.set(new Val[]{ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testReplace1() throws ParseException {
        final Generator gen = createGenerator("replace('this', 'is', 'at')");

        gen.set(getVal(3D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testReplace2() throws ParseException {
        final Generator gen = createGenerator("replace(${val1}, 'is', 'at')");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testConcat1() throws ParseException {
        final Generator gen = createGenerator("concat('this', ' is ', 'it')");

        gen.set(getVal(3D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this is it");
    }


    @Test
    void testConcat1Plus() throws ParseException {
        final Generator gen = createGenerator("'this'+' is '+'it'");

        gen.set(getVal(3D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this is it");
    }

    @Test
    void testConcat2() throws ParseException {
        final Generator gen = createGenerator("concat(${val1}, ' is ', 'it')");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this is it");
    }

    @Test
    void testConcatSingle1() throws ParseException {
        final Generator gen = createGenerator("concat(${val1})");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testConcatSingle2() throws ParseException {
        final Generator gen = createGenerator("concat('hello')");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("hello");
    }

    @Test
    void testConcatNUll() throws ParseException {
        final Generator gen = createGenerator("concat(${val1}, ${val2})", 2);

        gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("");
    }

    @Test
    void testConcatNullPlus1() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}", 2);

        gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void testConcatNullPlus2() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}+'test'", 2);

        gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("test");
    }

    @Test
    void testConcatNullPlus3() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}+''", 2);

        gen.set(new Val[]{ValNull.INSTANCE, ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("");
    }

    @Test
    void testConcatBooleanPlus1() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}", 2);

        gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2");
    }

    @Test
    void testConcatBooleanPlus2() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}+''", 2);

        gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2");
    }


    @Test
    void testConcatBooleanPlus3() throws ParseException {
        final Generator gen = createGenerator("${val1}+${val2}+'test'", 2);

        gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2test");
    }

    @Test
    void testConcatBooleanPlus4() throws ParseException {
        final Generator gen = createGenerator("${val1}+'test'+${val2}", 2);

        gen.set(new Val[]{ValBoolean.TRUE, ValBoolean.TRUE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("truetesttrue");
    }

    @Test
    void testLink1() throws ParseException {
        final Generator gen = createGenerator("link('Title', 'http://www.somehost.com/somepath')");

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
    }

    @Test
    void testLink2() throws ParseException {
        final Generator gen = createGenerator("link('Title', 'http://www.somehost.com/somepath', 'browser')");

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
    }

    @Test
    void testLink3() throws ParseException {
        final Generator gen = createGenerator("link(${val1}, ${val2}, 'browser')", 2);

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
    }

    @Test
    void testDashboard() throws ParseException {
        final Generator gen = createGenerator("dashboard('blah', 'abcdefg', 'dt='+formatDate(roundDay(${val1}))+'+1h')");

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
    }

    @Test
    void testLink4() throws ParseException {
        final Generator gen = createGenerator("link('blah', '?annotationId=1&streamId=2&eventId=3&title='+encodeUrl('this is a title')+'&subject='+encodeUrl('this is a subject')+'&status=New&assignedTo='+encodeUrl('test user')+'&comment='+encodeUrl('new comment'), 'annotation')", 2);

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
    }

    @Test
    void testAnnotation() throws ParseException {
        final Generator gen = createGenerator("annotation('blah', '1', '2', '3', 'this is a title', 'this is a subject', 'New', 'test user', 'new comment')");

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
    }

    @Test
    void testStaticString1() throws ParseException {
        final Generator gen = createGenerator("'hello'");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("hello");
    }

    @Test
    void testStaticString2() throws ParseException {
        final Generator gen = createGenerator("'[Click Here](http://www.somehost.com/somepath){DIALOG}'");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("[Click Here](http://www.somehost.com/somepath){DIALOG}");
    }

    @Test
    void testStaticNumber() throws ParseException {
        final Generator gen = createGenerator("50");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("50");
    }

    @Test
    void testStringLength1() throws ParseException {
        final Generator gen = createGenerator("stringLength(${val1})");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testSubstring1() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, 1, 2)");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("h");
    }

    @Test
    void testSubstring3() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, 2, 99)");

        gen.set(getVal("his"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("s");
    }

    @Test
    void testSubstring4() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, 1+1, 99-1)");

        gen.set(getVal("his"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("s");
    }

    @Test
    void testSubstring5() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, 2+5, 99-1)");

        gen.set(getVal("his"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void testSubstringBefore1() throws ParseException {
        final Generator gen = createGenerator("substringBefore(${val1}, '-')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("aa");
    }

    @Test
    void testSubstringBefore2() throws ParseException {
        final Generator gen = createGenerator("substringBefore(${val1}, 'a')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void testSubstringBefore3() throws ParseException {
        final Generator gen = createGenerator("substringBefore(${val1}, 'b')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("aa-");
    }

    @Test
    void testSubstringBefore4() throws ParseException {
        final Generator gen = createGenerator("substringBefore(${val1}, 'q')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void testSubstringAfter1() throws ParseException {
        final Generator gen = createGenerator("substringAfter(${val1}, '-')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("bb");
    }

    @Test
    void testSubstringAfter2() throws ParseException {
        final Generator gen = createGenerator("substringAfter(${val1}, 'a')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("a-bb");
    }

    @Test
    void testSubstringAfter3() throws ParseException {
        final Generator gen = createGenerator("substringAfter(${val1}, 'b')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("b");
    }

    @Test
    void testSubstringAfter4() throws ParseException {
        final Generator gen = createGenerator("substringAfter(${val1}, 'q')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void testIndexOf() throws ParseException {
        final Generator gen = createGenerator("indexOf(${val1}, '-')");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toInteger().intValue()).isEqualTo(2);
    }

    @Test
    void testIndexOf1() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, indexOf(${val1}, '-'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("-bb");
    }

    @Test
    void testIndexOf2() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, indexOf(${val1}, 'a'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("aa-bb");
    }

    @Test
    void testIndexOf3() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, indexOf(${val1}, 'b'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("bb");
    }

    @Test
    void testIndexOf4() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, indexOf(${val1}, 'q'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testLastIndexOf1() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, lastIndexOf(${val1}, '-'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("-bb");
    }

    @Test
    void testLastIndexOf2() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, lastIndexOf(${val1}, 'a'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("a-bb");
    }

    @Test
    void testLastIndexOf3() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, lastIndexOf(${val1}, 'b'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("b");
    }

    @Test
    void testLastIndexOf4() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, lastIndexOf(${val1}, 'q'), stringLength(${val1}))");

        gen.set(getVal("aa-bb"));

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testDecode1() throws ParseException {
        final Generator gen = createGenerator("decode(${val1}, 'hullo', 'hello', 'goodbye')");

        gen.set(getVal("hullo"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("hello");
    }

    @Test
    void testDecode2() throws ParseException {
        final Generator gen = createGenerator("decode(${val1}, 'h.+o', 'hello', 'goodbye')");

        gen.set(getVal("hullo"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("hello");
    }

    @Test
    void testInclude1() throws ParseException {
        final Generator gen = createGenerator("include(${val1}, 'this', 'that')");
        gen.set(getVal("this"));
        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testInclude2() throws ParseException {
        final Generator gen = createGenerator("include(${val1}, 'this', 'that')");
        gen.set(getVal("that"));
        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("that");
    }

    @Test
    void testInclude3() throws ParseException {
        final Generator gen = createGenerator("include(${val1}, 'this', 'that')");
        gen.set(getVal("other"));
        final Val out = gen.eval();
        assertThat(out.toString()).isNull();
    }

    @Test
    void testExclude1() throws ParseException {
        final Generator gen = createGenerator("exclude(${val1}, 'this', 'that')");
        gen.set(getVal("this"));
        final Val out = gen.eval();
        assertThat(out.toString()).isNull();
    }

    @Test
    void testExclude2() throws ParseException {
        final Generator gen = createGenerator("exclude(${val1}, 'this', 'that')");
        gen.set(getVal("that"));
        final Val out = gen.eval();
        assertThat(out.toString()).isNull();
    }

    @Test
    void testExclude3() throws ParseException {
        final Generator gen = createGenerator("exclude(${val1}, 'this', 'that')");
        gen.set(getVal("other"));
        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("other");
    }

    @Test
    void testEncodeUrl() throws ParseException {
        final Generator gen = createGenerator("encodeUrl('https://www.somesite.com:8080/this/path?query=string')");
        gen.set(getVal(""));
        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring");
    }

    @Test
    void testDecodeUrl() throws ParseException {
        final Generator gen = createGenerator("decodeUrl('https%3A%2F%2Fwww.somesite.com%3A8080%2Fthis%2Fpath%3Fquery%3Dstring')");
        gen.set(getVal(""));
        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("https://www.somesite.com:8080/this/path?query=string");
    }

    @Test
    void testEquals1() throws ParseException {
        final Generator gen = createGenerator("equals(${val1}, 'plop')");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testEquals2() throws ParseException {
        final Generator gen = createGenerator("equals(${val1}, ${val1})");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testEquals3() throws ParseException {
        final Generator gen = createGenerator("equals(${val1}, 'plip')");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testEquals4() throws ParseException {
        final Generator gen = createGenerator("equals(${val1}, ${val2})", 2);

        gen.set(getVal("plop", "plip"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testEquals5() throws ParseException {
        final Generator gen = createGenerator("equals(${val1}, ${val2})", 2);

        gen.set(getVal("plop", "plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testEquals6() throws ParseException {
        final Generator gen = createGenerator("${val1}=${val2}", 2);

        gen.set(getVal("plop", "plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testEqualsNull1() throws ParseException {
        final Generator gen = createGenerator("${val1}=null()");

        gen.set(new Val[]{ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testEqualsNull2() throws ParseException {
        final Generator gen = createGenerator("${val1}=null()");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testEqualsNull3() throws ParseException {
        final Generator gen = createGenerator("null()=null()");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testEqualsNull4() throws ParseException {
        final Generator gen = createGenerator("if(${val1}=null(), true(), false())");

        gen.set(new Val[]{ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.type().isError()).isTrue();
    }

    @Test
    void testIsNull1() throws ParseException {
        final Generator gen = createGenerator("isNull(${val1})");

        gen.set(new Val[]{ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testIsNull2() throws ParseException {
        final Generator gen = createGenerator("isNull(${val1})");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testIsNull3() throws ParseException {
        final Generator gen = createGenerator("isNull(null())");

        gen.set(getVal("plop"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testIsNull4() throws ParseException {
        final Generator gen = createGenerator("if(isNull(${val1}), true(), false())");

        gen.set(new Val[]{ValNull.INSTANCE});

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThan1() throws ParseException {
        final Generator gen = createGenerator("lessThan(1, 0)", 2);

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testLessThan2() throws ParseException {
        final Generator gen = createGenerator("lessThan(1, 1)", 2);

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testLessThan3() throws ParseException {
        final Generator gen = createGenerator("lessThan(${val1}, ${val2})", 2);

        gen.set(getVal(1D, 2D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThan4() throws ParseException {
        final Generator gen = createGenerator("lessThan(${val1}, ${val2})", 2);

        gen.set(getVal("fred", "fred"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testLessThan5() throws ParseException {
        final Generator gen = createGenerator("lessThan(${val1}, ${val2})", 2);

        gen.set(getVal("fred", "fred1"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThan6() throws ParseException {
        final Generator gen = createGenerator("lessThan(${val1}, ${val2})", 2);

        gen.set(getVal("fred1", "fred"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testLessThanOrEqualTo1() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(1, 0)", 2);

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testLessThanOrEqualTo2() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(1, 1)", 2);

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThanOrEqualTo3() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2);

        gen.set(getVal(1D, 2D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThanOrEqualTo3_mk2() throws ParseException {
        final Generator gen = createGenerator("(${val1}<=${val2})", 2);

        gen.set(getVal(1D, 2D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThanOrEqualTo4() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2);

        gen.set(getVal("fred", "fred"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThanOrEqualTo5() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2);

        gen.set(getVal("fred", "fred1"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testLessThanOrEqualTo6() throws ParseException {
        final Generator gen = createGenerator("lessThanOrEqualTo(${val1}, ${val2})", 2);

        gen.set(getVal("fred1", "fred"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("false");
    }

    @Test
    void testGreaterThanOrEqualTo1() throws ParseException {
        final Generator gen = createGenerator("greaterThanOrEqualTo(${val1}, ${val2})", 2);

        gen.set(getVal(2D, 1D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testGreaterThanOrEqualTo1_mk2() throws ParseException {
        final Generator gen = createGenerator("(${val1}>=${val2})", 2);

        gen.set(getVal(2D, 1D));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("true");
    }

    @Test
    void testBooleanExpressions() throws ParseException {
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
    void testSubstring2() throws ParseException {
        final Generator gen = createGenerator("substring(${val1}, 0, 99)");

        gen.set(getVal("this"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this");
    }

    @Test
    void testHash1() throws ParseException {
        final Generator gen = createGenerator("hash(${val1})");

        gen.set(getVal("test"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    void testHash2() throws ParseException {
        final Generator gen = createGenerator("hash(${val1}, 'SHA-512')");

        gen.set(getVal("test"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff");
    }

    @Test
    void testHash3() throws ParseException {
        final Generator gen = createGenerator("hash(${val1}, 'SHA-512', 'mysalt')");

        gen.set(getVal("test"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("af2910d4d8acf3fcf9683d3ca4425327cb1b4b48bc690f566e27b0e0144c17af82066cf6af14d3a30312ed9df671e0e24b1c66ed3973d1a7836899d75c4d6bb8");
    }

    @Test
    void testJoining1() throws ParseException {
        final Generator gen = createGenerator("joining(${val1}, ',')");

        gen.set(getVal("one"));
        gen.set(getVal("two"));
        gen.set(getVal("three"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("one,two,three");
    }

    @Test
    void testJoining2() throws ParseException {
        final Generator gen = createGenerator("joining(${val1})");

        gen.set(getVal("one"));
        gen.set(getVal("two"));
        gen.set(getVal("three"));

        final Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("onetwothree");
    }

    @Test
    void testCount() throws ParseException {
        final Generator gen = createGenerator("count()");

        gen.set(getVal(122D));
        gen.set(getVal(133D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

        gen.set(getVal(11D));
        gen.set(getVal(122D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testCountUnique() throws ParseException {
        final Generator gen = createGenerator("countUnique(${val1})");

        gen.set(getVal(122D));
        gen.set(getVal(133D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));

        gen.set(getVal(11D));
        gen.set(getVal(122D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
    }

    @Test
    void testCountUniqueStaticValue() throws ParseException {
        final Generator gen = createGenerator("countUnique('test')");

        gen.set(getVal(122D));
        gen.set(getVal(133D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));

        gen.set(getVal(11D));
        gen.set(getVal(122D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
    }

    @Test
    void testAdd1() throws ParseException {
        final Generator gen = createGenerator("3+4");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(7D, Offset.offset(0D));
    }

    @Test
    void testAdd2() throws ParseException {
        final Generator gen = createGenerator("3+4+5");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
    }

    @Test
    void testAdd3() throws ParseException {
        final Generator gen = createGenerator("2+count()");

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
    }

    @Test
    void testSubtract1() throws ParseException {
        final Generator gen = createGenerator("3-4");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(-1D, Offset.offset(0D));
    }

    @Test
    void testSubtract2() throws ParseException {
        final Generator gen = createGenerator("2-count()");

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(0D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(-2D, Offset.offset(0D));
    }

    @Test
    void testMultiply1() throws ParseException {
        final Generator gen = createGenerator("3*4");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
    }

    @Test
    void testMultiply2() throws ParseException {
        final Generator gen = createGenerator("2*count()");

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testDivide1() throws ParseException {
        final Generator gen = createGenerator("8/4");

//        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
    }

    @Test
    void testDivide2() throws ParseException {
        final Generator gen = createGenerator("8/count()");

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));

        gen.set(getVal(1D));
        gen.set(getVal(1D));

        out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
    }

    @Test
    void testDivide_byZero() throws ParseException {
        final Generator gen = createGenerator("8/0");

        final Val out = gen.eval();
        assertThat(out instanceof ValErr).isTrue();
        System.out.println("Error message: " + ((ValErr) out).getMessage());
    }

    @Test
    void testFloorNum1() throws ParseException {
        final Generator gen = createGenerator("floor(8.4234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testFloorNum2() throws ParseException {
        final Generator gen = createGenerator("floor(8.5234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testFloorNum3() throws ParseException {
        final Generator gen = createGenerator("floor(${val1})");

        gen.set(getVal(1.34D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
    }

    @Test
    void testFloorNum4() throws ParseException {
        final Generator gen = createGenerator("floor(${val1}+count())");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3D, Offset.offset(0D));
    }

    @Test
    void testFloorNum5() throws ParseException {
        final Generator gen = createGenerator("floor(${val1}+count(), 1)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.8D, Offset.offset(0D));
    }

    @Test
    void testFloorNum6() throws ParseException {
        final Generator gen = createGenerator("floor(${val1}+count(), 2)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.86D, Offset.offset(0D));
    }

    @Test
    void testCeilNum1() throws ParseException {
        final Generator gen = createGenerator("ceiling(8.4234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
    }

    @Test
    void testCeilNum2() throws ParseException {
        final Generator gen = createGenerator("ceiling(8.5234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
    }

    @Test
    void testCeilNum3() throws ParseException {
        final Generator gen = createGenerator("ceiling(${val1})");

        gen.set(getVal(1.34D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
    }

    @Test
    void testCeilNum4() throws ParseException {
        final Generator gen = createGenerator("ceiling(${val1}+count())");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testCeilNum5() throws ParseException {
        final Generator gen = createGenerator("ceiling(${val1}+count(), 1)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
    }

    @Test
    void testCeilNum6() throws ParseException {
        final Generator gen = createGenerator("ceiling(${val1}+count(), 2)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
    }

    @Test
    void testRoundNum1() throws ParseException {
        final Generator gen = createGenerator("round(8.4234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testRoundNum2() throws ParseException {
        final Generator gen = createGenerator("round(8.5234)");

        gen.set(getVal(1D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(9D, Offset.offset(0D));
    }

    @Test
    void testRoundNum3() throws ParseException {
        final Generator gen = createGenerator("round(${val1})");

        gen.set(getVal(1.34D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(1D, Offset.offset(0D));
    }

    @Test
    void testRoundNum4() throws ParseException {
        final Generator gen = createGenerator("round(${val1}+count())");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(4D, Offset.offset(0D));
    }

    @Test
    void testRoundNum5() throws ParseException {
        final Generator gen = createGenerator("round(${val1}+count(), 1)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.9D, Offset.offset(0D));
    }

    @Test
    void testRoundNum6() throws ParseException {
        final Generator gen = createGenerator("round(${val1}+count(), 2)");

        gen.set(getVal(1.34D));
        gen.set(getVal(1.8655D));

        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(3.87D, Offset.offset(0D));
    }

    @Test
    void testTime() throws ParseException {
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

    private void testTime(final String function, final String in, final String expected) throws ParseException {
        final double expectedMs = DateUtil.parseNormalDateTimeString(expected);
        final String expression = function + "(${val1})";
        final Generator gen = createGenerator(expression);

        gen.set(getVal(in));
        final Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(expectedMs, Offset.offset(0D));
    }

    @Test
    void testBODMAS1() throws ParseException {
        final Generator gen = createGenerator("4+4/2+2");

        final Val out = gen.eval();

        // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 8.
        assertThat(out.toDouble()).isEqualTo(8D, Offset.offset(0D));
    }

    @Test
    void testBODMAS2() throws ParseException {
        final Generator gen = createGenerator("(4+4)/2+2");

        final Val out = gen.eval();

        // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 6.
        assertThat(out.toDouble()).isEqualTo(6D, Offset.offset(0D));
    }

    @Test
    void testBODMAS3() throws ParseException {
        final Generator gen = createGenerator("(4+4)/(2+2)");

        final Val out = gen.eval();

        // Non BODMAS would evaluate as 6 or even 4 - BODMAS should be 2.
        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
    }

    @Test
    void testBODMAS4() throws ParseException {
        final Generator gen = createGenerator("4+4/2+2*3");

        final Val out = gen.eval();

        // Non BODMAS would evaluate as 18 - BODMAS should be 12.
        assertThat(out.toDouble()).isEqualTo(12D, Offset.offset(0D));
    }

    @Test
    void testBODMAS5() throws ParseException {
        final Generator gen = createGenerator("8%3");

        final Val out = gen.eval();

        assertThat(out.toDouble()).isEqualTo(2D, Offset.offset(0D));
    }

    @Test
    void testExtractAuthorityFromUri() throws ParseException {
        final Generator gen = createGenerator("extractAuthorityFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("www.example.com:1234");
    }

    @Test
    void testExtractFragmentFromUri() throws ParseException {
        final Generator gen = createGenerator("extractFragmentFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path#frag"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("frag");
    }

    @Test
    void testExtractHostFromUri() throws ParseException {
        final Generator gen = createGenerator("extractHostFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("www.example.com");
    }

    @Test
    void testExtractPathFromUri() throws ParseException {
        final Generator gen = createGenerator("extractPathFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("/this/is/a/path");
    }

    @Test
    void testExtractPortFromUri() throws ParseException {
        final Generator gen = createGenerator("extractPortFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("1234");
    }

    @Test
    void testExtractQueryFromUri() throws ParseException {
        final Generator gen = createGenerator("extractQueryFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path?this=that&foo=bar"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("this=that&foo=bar");
    }

    @Test
    void testExtractSchemeFromUri() throws ParseException {
        final Generator gen = createGenerator("extractSchemeFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("http");
    }

    @Test
    void testExtractSchemeSpecificPartFromUri() throws ParseException {
        final Generator gen = createGenerator("extractSchemeSpecificPartFromUri(${val1})");

        gen.set(getVal("http://www.example.com:1234/this/is/a/path"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("//www.example.com:1234/this/is/a/path");
    }

    @Test
    void testExtractUserInfoFromUri() throws ParseException {
        final Generator gen = createGenerator("extractUserInfoFromUri(${val1})");

        gen.set(getVal("http://john:doe@example.com:81/"));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("john:doe");
    }

    @Test
    void testParseDate1() throws ParseException {
        final Generator gen = createGenerator("parseDate(${val1})");

        gen.set(getVal("2014-02-22T12:12:12.888Z"));
        Val out = gen.eval();
        assertThat(out.toLong().longValue()).isEqualTo(1393071132888L);
    }

    @Test
    void testParseDate2() throws ParseException {
        final Generator gen = createGenerator("parseDate(${val1}, 'yyyy MM dd')");

        gen.set(getVal("2014 02 22"));
        Val out = gen.eval();
        assertThat(out.toLong().longValue()).isEqualTo(1393027200000L);
    }

    @Test
    void testParseDate3() throws ParseException {
        final Generator gen = createGenerator("parseDate(${val1}, 'yyyy MM dd', '+0400')");

        gen.set(getVal("2014 02 22"));
        Val out = gen.eval();
        assertThat(out.toLong().longValue()).isEqualTo(1393012800000L);
    }

    @Test
    void testFormatDate1() throws ParseException {
        final Generator gen = createGenerator("formatDate(${val1})");

        gen.set(getVal(1393071132888L));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2014-02-22T12:12:12.888Z");
    }

    @Test
    void testFormatDate2() throws ParseException {
        final Generator gen = createGenerator("formatDate(${val1}, 'yyyy MM dd')");

        gen.set(getVal(1393071132888L));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2014 02 22");
    }

    @Test
    void testFormatDate3() throws ParseException {
        final Generator gen = createGenerator("formatDate(${val1}, 'yyyy MM dd', '+1200')");

        gen.set(getVal(1393071132888L));
        Val out = gen.eval();
        assertThat(out.toString()).isEqualTo("2014 02 23");
    }

    @Test
    void testVariance1() throws ParseException {
        final Generator gen = createGenerator("variance(600, 470, 170, 430, 300)");

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
    }

    @Test
    void testVariance2() throws ParseException {
        final Generator gen = createGenerator("variance(${val1})");

        gen.set(getVal(600));
        gen.set(getVal(470));
        gen.set(getVal(170));
        gen.set(getVal(430));
        gen.set(getVal(300));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(21704D, Offset.offset(0D));
    }

    @Test
    void testStDev1() throws ParseException {
        final Generator gen = createGenerator("round(stDev(600, 470, 170, 430, 300))");

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
    }

    @Test
    void testStDev2() throws ParseException {
        final Generator gen = createGenerator("round(stDev(${val1}))");

        gen.set(getVal(600));
        gen.set(getVal(470));
        gen.set(getVal(170));
        gen.set(getVal(430));
        gen.set(getVal(300));

        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(147, Offset.offset(0D));
    }

    @Test
    void testAny() throws ParseException {
        final Generator gen = createGenerator("any(${val1})");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("any(${val1})");
            child.set(getVal(300));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toDouble()).isEqualTo(300, Offset.offset(0D));
    }

    @Test
    void testFirst() throws ParseException {
        final Generator gen = createGenerator("first(${val1})");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("first(${val1})");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toDouble()).isEqualTo(1, Offset.offset(0D));
    }

    @Test
    void testLast() throws ParseException {
        final Generator gen = createGenerator("last(${val1})");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("last(${val1})");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toDouble()).isEqualTo(10, Offset.offset(0D));
    }

    @Test
    void testNth() throws ParseException {
        final Generator gen = createGenerator("nth(${val1}, 7)");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("nth(${val1}, 7)");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toDouble()).isEqualTo(7, Offset.offset(0D));
    }

    @Test
    void testTop() throws ParseException {
        final Generator gen = createGenerator("top(${val1}, ',', 3)");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("top(${val1}, ',', 3)");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toString()).isEqualTo("1,2,3");
    }

    @Test
    void testTopSmall() throws ParseException {
        final Generator gen = createGenerator("top(${val1}, ',', 3)");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[2];
        for (int i = 0; i < 2; i++) {
            final Generator child = createGenerator("top(${val1}, ',', 3)");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toString()).isEqualTo("1,2");
    }

    @Test
    void testBottom() throws ParseException {
        final Generator gen = createGenerator("bottom(${val1}, ',', 3)");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[10];
        for (int i = 0; i < 10; i++) {
            final Generator child = createGenerator("bottom(${val1}, ',', 3)");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toString()).isEqualTo("8,9,10");
    }

    @Test
    void testBottomSmall() throws ParseException {
        final Generator gen = createGenerator("bottom(${val1}, ',', 3)");
        gen.set(getVal(300));
        Val out = gen.eval();
        assertThat(out.toDouble()).isEqualTo(300, Offset.offset(0D));

        final Generator[] children = new Generator[2];
        for (int i = 0; i < 2; i++) {
            final Generator child = createGenerator("bottom(${val1}, ',', 3)");
            child.set(getVal(i + 1));
            children[i] = child;
        }

        final Selector selector = (Selector) gen;
        final Val selected = selector.select(children);
        assertThat(selected.toString()).isEqualTo("1,2");
    }

    @Test
    void testToBoolean1() throws ParseException {
        final Generator gen = createGenerator("toBoolean('true')");
        assertThat(gen.eval()).isEqualTo(ValBoolean.TRUE);
    }

    @Test
    void testToBoolean2() throws ParseException {
        final Generator gen = createGenerator("toBoolean(${val1})");
        gen.set(getVal("true"));
        assertThat(gen.eval()).isEqualTo(ValBoolean.TRUE);
    }

    @Test
    void testToDouble1() throws ParseException {
        final Generator gen = createGenerator("toDouble('100')");
        assertThat(gen.eval()).isEqualTo(ValDouble.create(100));
    }

    @Test
    void testToDouble2() throws ParseException {
        final Generator gen = createGenerator("toDouble(${val1})");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValDouble.create(100));
    }

    @Test
    void testToInteger1() throws ParseException {
        final Generator gen = createGenerator("toInteger('100')");
        assertThat(gen.eval()).isEqualTo(ValInteger.create(100));
    }

    @Test
    void testToInteger2() throws ParseException {
        final Generator gen = createGenerator("toInteger(${val1})");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValInteger.create(100));
    }

    @Test
    void testToLong1() throws ParseException {
        final Generator gen = createGenerator("toLong('100')");
        assertThat(gen.eval()).isEqualTo(ValLong.create(100));
    }

    @Test
    void testToLong2() throws ParseException {
        final Generator gen = createGenerator("toLong(${val1})");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValLong.create(100));
    }

    @Test
    void testToString1() throws ParseException {
        final Generator gen = createGenerator("toString('100')");
        assertThat(gen.eval()).isEqualTo(ValString.create("100"));
    }

    @Test
    void testToString2() throws ParseException {
        final Generator gen = createGenerator("toString(${val1})");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValString.create("100"));
    }

    @Test
    void testMappedValues1() throws ParseException {
        final Generator gen = createGenerator("param('testkey')");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValString.create("testvalue"));
    }

    @Test
    void testMappedValues2() throws ParseException {
        final Generator gen = createGenerator("params()");
        gen.set(getVal("100"));
        assertThat(gen.eval()).isEqualTo(ValString.create("testkey=\"testvalue\""));
    }

    @Test
    void testErrorHandling1() throws ParseException {
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

    private void assertThatItEvaluatesToValErr(final String expression, final Val... values) throws ParseException {
        Generator gen = createGenerator(expression);
        gen.set(values);
        Val out = gen.eval();
        System.out.println(expression + " - " +
                out.getClass().getSimpleName() + ": " +
                out.toString() +
                (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : ""));
        assertThat(out).isInstanceOf(ValErr.class);
    }

    @Test
    void testTypeOf() throws ParseException {
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

    private Generator createGenerator(final String expression) throws ParseException {
        return createGenerator(expression, 1);
    }

    private Expression createExpression(final String expression) throws ParseException {
        return createExpression(expression, 1);
    }

    private Generator createGenerator(final String expression, final int valueCount) throws ParseException {
        final Expression exp = createExpression(expression, valueCount);
        final Generator gen = exp.createGenerator();
        return gen;
    }

    private Expression createExpression(final String expression, final int valueCount) throws ParseException {
        final FieldIndexMap fieldIndexMap = new FieldIndexMap();
        for (int i = 1; i <= valueCount; i++) {
            fieldIndexMap.create("val" + i, true);
        }

        final Expression exp = parser.parse(fieldIndexMap, expression);

        final Map<String, String> mappedValues = new HashMap<>();
        mappedValues.put("testkey", "testvalue");
        exp.setStaticMappedValues(mappedValues);

        final String actual = exp.toString();
        assertThat(actual).isEqualTo(expression);

        return exp;
    }

    private void assertBooleanExpression(final Val val1, final String operator, final Val val2, final Val expectedOutput)
            throws ParseException {

        final String expression = String.format("(${val1}%s${val2})", operator);
        final Expression exp = createExpression(expression, 2);
        final Generator gen = exp.createGenerator();
        gen.set(new Val[]{val1, val2});
        Val out = gen.eval();

        System.out.println(String.format("[%s: %s] %s [%s: %s] => [%s: %s%s]",
                val1.getClass().getSimpleName(), val1.toString(),
                operator,
                val2.getClass().getSimpleName(), val2.toString(),
                out.getClass().getSimpleName(), out.toString(),
                (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : "")));

        if (!(expectedOutput instanceof ValErr)) {
            assertThat(out).isEqualTo(expectedOutput);
        }
        assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
    }

    private void assertTypeOf(final String expression, final String expectedType) throws ParseException {
        final Expression exp = createExpression(expression);
        final Generator gen = exp.createGenerator();
        Val out = gen.eval();

        System.out.println(String.format("%s => [%s:%s%s]",
                expression,
                out.getClass().getSimpleName(), out.toString(),
                (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : "")));

        // The output type is always wrapped in a ValString
        assertThat(out.type().toString()).isEqualTo("string");

        assertThat(out).isInstanceOf(ValString.class);
        assertThat(out.toString()).isEqualTo(expectedType);
    }

    private void assertTypeOf(final Val val1, final String expectedType) throws ParseException {

        final String expression = "typeOf(${val1})";
        final Expression exp = createExpression(expression);
        final Generator gen = exp.createGenerator();
        gen.set(new Val[]{val1});
        Val out = gen.eval();

        System.out.println(String.format("%s - [%s:%s] => [%s:%s%s]",
                expression,
                val1.getClass().getSimpleName(), val1.toString(),
                out.getClass().getSimpleName(), out.toString(),
                (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : "")));

        // The output type is always wrapped in a ValString
        assertThat(out.type().toString()).isEqualTo("string");

        assertThat(out).isInstanceOf(ValString.class);
        assertThat(out.toString()).isEqualTo(expectedType);
    }

    private void assertIsExpression(final Val val1, final String function, final Val expectedOutput) {
        try {
            final String expression = String.format("%s(${val1})", function);
            final Expression exp = createExpression(expression, 2);
            final Generator gen = exp.createGenerator();
            gen.set(new Val[]{val1});
            Val out = gen.eval();

            System.out.println(String.format("%s([%s: %s]) => [%s: %s%s]",
                    function,
                    val1.getClass().getSimpleName(), val1.toString(),
                    out.getClass().getSimpleName(), out.toString(),
                    (out instanceof ValErr ? (" - " + ((ValErr) out).getMessage()) : "")));

            if (!(expectedOutput instanceof ValErr)) {
                assertThat(out).isEqualTo(expectedOutput);
            }
            assertThat(out.getClass()).isEqualTo(expectedOutput.getClass());
        } catch (final ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
