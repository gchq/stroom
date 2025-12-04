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

package stroom.util.user;

import stroom.test.common.TestUtil;
import stroom.util.shared.UserDesc;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

class TestUserDescUtil {

    @TestFactory
    Stream<DynamicTest> testParseUsersCsvData() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<UserDesc>>() {
                })
                .withTestFunction(testCase ->
                        UserDescUtil.parseUsersCsvData(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null,
                        Collections.emptyList())
                .addCase("",
                        Collections.emptyList())
                .addCase(" ",
                        Collections.emptyList())
                .addCase(" 123 ",
                        List.of(user("123")))
                .addCase("123",
                        List.of(user("123")))
                .addCase("\"123\"",
                        List.of(user("123")))
                .addCase("123,jbloggs",
                        List.of(user("123", "jbloggs")))
                .addCase("\"123\",\"jbloggs\"",
                        List.of(user("123", "jbloggs")))
                .addCase("123,jbloggs,Joe Bloggs",
                        List.of(user("123", "jbloggs", "Joe Bloggs")))
                .addCase(" 123 , jbloggs , Joe Bloggs ",
                        List.of(user("123", "jbloggs", "Joe Bloggs")))
                .addCase("\"123\",\"jbloggs\",\"Joe Bloggs\"",
                        List.of(user("123", "jbloggs", "Joe Bloggs")))
                .addCase("\"1,23\",\"j,bloggs\",\"Joe Bloggs with a ,\"",
                        List.of(user("1,23", "j,bloggs", "Joe Bloggs with a ,")))
                .addCase("""
                                123,jbloggs,Joe Bloggs
                                456,jdoe,John Doe""",
                        List.of(
                                user("123", "jbloggs", "Joe Bloggs"),
                                user("456", "jdoe", "John Doe")))
                .addCase("""

                                123,jbloggs,Joe Bloggs

                                456,jdoe,John Doe
                                """,
                        List.of(
                                user("123", "jbloggs", "Joe Bloggs"),
                                user("456", "jdoe", "John Doe")))
                .build();
    }

    private UserDesc user(final String uniqueIdentity) {
        return UserDesc.forSubjectId(uniqueIdentity);
    }

    private UserDesc user(final String uniqueIdentity, final String displayName) {
        return UserDesc.builder(uniqueIdentity)
                .displayName(displayName)
                .build();
    }

    private UserDesc user(final String uniqueIdentity, final String displayName, final String fullName) {
        return UserDesc.builder(uniqueIdentity)
                .displayName(displayName)
                .fullName(fullName)
                .build();
    }
}
