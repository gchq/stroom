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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionParserLinks extends AbstractExpressionParserTest {

    @Test
    void testLink1() {
        createGenerator("link('Title', 'http://www.somehost.com/somepath')", (gen, storedValues) -> {
            final String expectedText = "Title";
            final String expectedUrl = "http://www.somehost.com/somepath";

            gen.set(Val.of("this"), storedValues);

            final Val out = gen.eval(storedValues, null);
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
        createGenerator("link('Title', 'http://www.somehost.com/somepath', 'browser')", (gen, storedValues) -> {
            final String expectedText = "Title";
            final String expectedUrl = "http://www.somehost.com/somepath";
            final String expectedType = "browser";

            gen.set(Val.of("this"), storedValues);

            final Val out = gen.eval(storedValues, null);
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
    @SuppressWarnings("checkstyle:lineLength")
    void testLink3() {
        createGenerator("link(${val1}, ${val2}, 'browser')", 2, (gen, storedValues) -> {
            final String expectedText = "t}his [is] a tit(le w{it}h (brack[ets)";
            final String expectedUrl = "http://www.somehost.com/somepath?k1=v1&k[2]={v2}";
            final String expectedType = "browser";

            gen.set(Val.of(expectedText, expectedUrl), storedValues);

            final Val out = gen.eval(storedValues, null);
            final String str = out.toString();
            assertThat(str).isEqualTo(
                    "[t%7Dhis+%5Bis%5D+a+tit%28le+w%7Bit%7Dh+%28brack%5Bets%29](http%3A%2F%2Fwww.somehost.com%2Fsomepath%3Fk1%3Dv1%26k%5B2%5D%3D%7Bv2%7D){browser}");

            final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
            final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
            final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

            assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
            assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
            assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
        });
    }

    @Test
    @SuppressWarnings("checkstyle:lineLength")
    void testDashboard() {
        createGenerator("dashboard('blah', 'abcdefg', 'dt='+formatDate(roundDay(${val1}))+'+1h')",
                (gen, storedValues) -> {
                    final String expectedText = "blah";
                    final String expectedUrl = "?uuid=abcdefg&params=dt%3D2014-02-23T00%3A00%3A00.000Z%2B1h";

                    gen.set(Val.of("2014-02-22T12:12:12.000Z"), storedValues);

                    final Val out = gen.eval(storedValues, null);
                    final String str = out.toString();
                    assertThat(str).isEqualTo(
                            "[blah](%3Fuuid%3Dabcdefg%26params%3Ddt%253D2014-02-23T00%253A00%253A00.000Z%252B1h){dashboard}");

                    final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
                    final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));

                    assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
                    assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
                });
    }

    @Test
    @SuppressWarnings("checkstyle:lineLength")
    void testLink4() {
        createGenerator(
                "link('blah', '?annotationId=1&streamId=2&eventId=3&title='+encodeUrl('this is a title')+'&subject='+encodeUrl('this is a subject')+'&status=New&assignedTo='+encodeUrl('test user')+'&comment='+encodeUrl('new comment'), 'annotation')",
                2,
                (gen, storedValues) -> {
                    final String expectedText = "blah";
                    final String expectedUrl = "?annotationId=1&streamId=2&eventId=3&title=this+is+a+title&subject=this+is+a+subject&status=New&assignedTo=test+user&comment=new+comment";
                    final String expectedType = "annotation";

                    gen.set(Val.of(expectedText, expectedUrl), storedValues);

                    final Val out = gen.eval(storedValues, null);
                    final String str = out.toString();
                    assertThat(str).isEqualTo(
                            "[blah](%3FannotationId%3D1%26streamId%3D2%26eventId%3D3%26title%3Dthis%2Bis%2Ba%2Btitle%26subject%3Dthis%2Bis%2Ba%2Bsubject%26status%3DNew%26assignedTo%3Dtest%2Buser%26comment%3Dnew%2Bcomment){annotation}");

                    final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
                    final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
                    final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

                    assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
                    assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
                    assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
                });
    }

    @Test
    @SuppressWarnings("checkstyle:lineLength")
    void testAnnotation() {
        createGenerator(
                "annotation('blah', '1', '2', '3', 'this is a title', 'this is a subject', 'New', 'test user', 'new comment')",
                (gen, storedValues) -> {
                    final String expectedText = "blah";
                    final String expectedUrl = "?annotationId=1&StreamId=2&EventId=3&title=this+is+a+title&subject=this+is+a+subject&status=New&assignedTo=test+user&comment=new+comment";
                    final String expectedType = "annotation";

                    gen.set(Val.of(expectedText, expectedUrl), storedValues);

                    final Val out = gen.eval(storedValues, null);
                    final String str = out.toString();
                    assertThat(str).isEqualTo(
                            "[blah](%3FannotationId%3D1%26StreamId%3D2%26EventId%3D3%26title%3Dthis%2Bis%2Ba%2Btitle%26subject%3Dthis%2Bis%2Ba%2Bsubject%26status%3DNew%26assignedTo%3Dtest%2Buser%26comment%3Dnew%2Bcomment){annotation}");

                    final String text = str.substring(str.indexOf("[") + 1, str.indexOf("]"));
                    final String url = str.substring(str.indexOf("(") + 1, str.indexOf(")"));
                    final String type = str.substring(str.indexOf("{") + 1, str.indexOf("}"));

                    assertThat(EncodingUtil.decodeUrl(text)).isEqualTo(expectedText);
                    assertThat(EncodingUtil.decodeUrl(url)).isEqualTo(expectedUrl);
                    assertThat(EncodingUtil.decodeUrl(type)).isEqualTo(expectedType);
                });
    }
}
