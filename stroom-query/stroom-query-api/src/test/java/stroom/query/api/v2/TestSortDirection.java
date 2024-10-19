/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.api.v2;

import stroom.query.api.v2.Sort.SortDirection;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Optional;
import java.util.stream.Stream;

public class TestSortDirection {

    @TestFactory
    Stream<DynamicTest> testFromString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<SortDirection>>() {
                })
                .withSingleArgTestFunction(SortDirection::fromString)
                .withSimpleEqualityAssertion()
                .addCase(null, Optional.empty())
                .addCase("", Optional.empty())
                .addCase(" ", Optional.empty())
                .addCase("foo", Optional.empty())
                .addCase("asc", Optional.of(SortDirection.ASCENDING))
                .addCase("Asc", Optional.of(SortDirection.ASCENDING))
                .addCase("ASC", Optional.of(SortDirection.ASCENDING))
                .addCase("ascending", Optional.of(SortDirection.ASCENDING))
                .addCase("Ascending", Optional.of(SortDirection.ASCENDING))
                .addCase("ASCENDING", Optional.of(SortDirection.ASCENDING))
                .addCase("desc", Optional.of(SortDirection.DESCENDING))
                .addCase("Desc", Optional.of(SortDirection.DESCENDING))
                .addCase("DESC", Optional.of(SortDirection.DESCENDING))
                .addCase("descending", Optional.of(SortDirection.DESCENDING))
                .addCase("Descending", Optional.of(SortDirection.DESCENDING))
                .addCase("DESCENDING", Optional.of(SortDirection.DESCENDING))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFromShortForm() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<SortDirection>>() {
                })
                .withSingleArgTestFunction(SortDirection::fromShortForm)
                .withSimpleEqualityAssertion()
                .addCase(null, Optional.empty())
                .addCase("", Optional.empty())
                .addCase(" ", Optional.empty())
                .addCase("foo", Optional.empty())
                .addCase("asc", Optional.of(SortDirection.ASCENDING))
                .addCase("Asc", Optional.of(SortDirection.ASCENDING))
                .addCase("ASC", Optional.of(SortDirection.ASCENDING))
                .addCase("ascending", Optional.empty())
                .addCase("Ascending", Optional.empty())
                .addCase("ASCENDING", Optional.empty())
                .addCase("desc", Optional.of(SortDirection.DESCENDING))
                .addCase("Desc", Optional.of(SortDirection.DESCENDING))
                .addCase("DESC", Optional.of(SortDirection.DESCENDING))
                .addCase("descending", Optional.empty())
                .addCase("Descending", Optional.empty())
                .addCase("DESCENDING", Optional.empty())
                .build();
    }
}
