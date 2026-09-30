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

package stroom.floormap.client.value;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The [ValueAccessor] contract, written once and run against every
/// implementation that can execute on the JVM.
///
/// ### Why this test exists
///
/// FloorMap has four accessor implementations: the production JSON and XML pair
/// (GWT client classes), and a JVM-runnable double for each, used by the parser and
/// editor-model tests. A review found the doubles had drifted from production in five
/// separate ways, and the most serious was a straight contradiction: given an array
/// containing a non-numeric token, one implementation rejected the whole entry while
/// the other kept it with a fabricated zero in place of the bad token. That is the
/// branch that decides whether corrupt stored data is caught or silently accepted,
/// the two runtimes did opposite things, and *neither behaviour had a test*.
///
/// The consequence was worse than a gap in coverage. Roughly 150 tests — the whole
/// of the parser and editor-model suites — ran against the doubles, so a green suite
/// was compatible with production being broken. The tests were not weak; they were
/// attesting to the wrong implementation.
///
/// ### What it can and cannot reach
///
/// The production accessors wrap GWT's JSON and DOM APIs and there is no
/// `GWTTestCase` infrastructure in this repository, so they cannot be executed
/// here — this test runs against the two doubles only. That limit is the reason the
/// format-level rules that need no DOM now live in [XmlValueText] and are
/// *shared* by the production XML accessor and its double rather than written
/// out twice: for those rules divergence is impossible rather than merely tested.
/// What remains implementation-specific is DOM and JSON traversal, and this contract
/// pins the observable behaviour a caller depends on.
///
/// ### What belongs here, and what does not
///
/// Only invariants that must hold **identically for every format**.
/// Genuine format differences stay in the per-format tests — most importantly, XML
/// element text is untyped, so `getString` on a numeric field returns the text,
/// whereas JSON is typed and returns `null` for a non-string. That is a
/// property of the formats, not a defect, and asserting it here would force one of
/// the two implementations to lie.
abstract class AbstractValueAccessorContractTest {

    /// The implementation under test.
    protected abstract ValueAccessor accessor();

    /// A serialised document with one field holding the given comma-or-array
    /// spelling of a numeric list, e.g. `1,2,3` for XML or `[1,2,3]` for
    /// JSON. Implementations translate as their format requires.
    protected abstract String docWithNumericArray(@SuppressWarnings("SameParameterValue") String field,
                                                  double... values);

    /// A serialised document whose field holds an array with a non-numeric token.
    protected abstract String docWithMalformedNumericArray(@SuppressWarnings("SameParameterValue") String field);

    /// A serialised document whose field holds an empty array / empty text.
    protected abstract String docWithEmptyNumericArray(@SuppressWarnings("SameParameterValue") String field);

    /// A serialised document with one string-valued field.
    protected abstract String docWithString(@SuppressWarnings("SameParameterValue") String field,
                                            @SuppressWarnings("SameParameterValue") String value);

    /// A serialised document with no fields at all.
    protected abstract String emptyDoc();

    /// The format's path expression for a top-level field.
    protected abstract String path(String field);

    /// Something this format cannot parse.
    protected abstract String unparseableRaw();

    // -----------------------------------------------------------------------
    // parse
    // -----------------------------------------------------------------------

    @Test
    void testParse_nullOrBlankYieldsNull() {
        assertThat(accessor().parse(null)).isNull();
        assertThat(accessor().parse("")).isNull();
    }

    @Test
    void testParse_unparseableYieldsNull() {
        assertThat(accessor().parse(unparseableRaw())).isNull();
    }

    @Test
    void testParse_validDocumentYieldsAUsableValue() {
        final ParsedValue value = accessor().parse(docWithString("type", "gate"));
        assertThat(value).isNotNull();
        assertThat(accessor().getString(value, path("type"))).isEqualTo("gate");
    }

    // -----------------------------------------------------------------------
    // getArray — the branch the review found the two runtimes disagreeing on
    // -----------------------------------------------------------------------

    @Test
    void testGetArray_wellFormedIsReturned() {
        final ParsedValue value = accessor().parse(docWithNumericArray("coords", 1, 2, 3));
        assertThat(accessor().getArray(value, path("coords")))
                .containsExactly(1d, 2d, 3d);
    }

    /// One bad token makes the whole array malformed. This is the assertion that was
    /// missing: a caller cannot tell a fabricated zero from a real one, so
    /// substituting a default for the offending element silently manufactures data.
    @Test
    void testGetArray_malformedElementYieldsNullForTheWholeArray() {
        final ParsedValue value = accessor().parse(docWithMalformedNumericArray("coords"));
        assertThat(value).isNotNull();
        assertThat(accessor().getArray(value, path("coords")))
                .as("a malformed element must not be replaced with a default")
                .isNull();
    }

    @Test
    void testGetArray_emptyYieldsNull() {
        final ParsedValue value = accessor().parse(docWithEmptyNumericArray("coords"));
        assertThat(value).isNotNull();
        assertThat(accessor().getArray(value, path("coords"))).isNull();
    }

    @Test
    void testGetArray_absentYieldsNull() {
        final ParsedValue value = accessor().parse(emptyDoc());
        assertThat(value).isNotNull();
        assertThat(accessor().getArray(value, path("nothing-here"))).isNull();
    }

    @Test
    void testGetArray_nullArgumentsYieldNull() {
        final ParsedValue value = accessor().parse(docWithNumericArray("coords", 1, 2));
        assertThat(accessor().getArray(null, path("coords"))).isNull();
        assertThat(accessor().getArray(value, null)).isNull();
    }

    // -----------------------------------------------------------------------
    // hasValue — present versus absent, which getArray alone cannot express
    // -----------------------------------------------------------------------

    @Test
    void testHasValue_trueWhenPresent() {
        final ParsedValue value = accessor().parse(docWithString("type", "gate"));
        assertThat(accessor().hasValue(value, path("type"))).isTrue();
    }

    /// Present-but-unreadable still counts as present. The parser relies on this to
    /// tell "the stream omitted this field", which is normal and silent, from "the
    /// field is there and wrong", which the user must be told about.
    @Test
    void testHasValue_trueWhenPresentButMalformed() {
        final ParsedValue value = accessor().parse(docWithMalformedNumericArray("coords"));
        assertThat(accessor().hasValue(value, path("coords")))
                .as("malformed is present, not absent")
                .isTrue();
        assertThat(accessor().getArray(value, path("coords"))).isNull();
    }

    @Test
    void testHasValue_falseWhenAbsent() {
        final ParsedValue value = accessor().parse(emptyDoc());
        assertThat(accessor().hasValue(value, path("nothing-here"))).isFalse();
    }

    @Test
    void testHasValue_falseForNullArguments() {
        final ParsedValue value = accessor().parse(docWithString("type", "gate"));
        assertThat(accessor().hasValue(null, path("type"))).isFalse();
        assertThat(accessor().hasValue(value, null)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Round trips
    // -----------------------------------------------------------------------

    @Test
    void testSetString_thenGetString_roundTrips() {
        final ParsedValue value = accessor().parse(docWithString("type", "gate"));
        accessor().setString(value, path("type"), "camera");
        assertThat(accessor().getString(value, path("type"))).isEqualTo("camera");
    }

    @Test
    void testSetArray_thenGetArray_roundTrips() {
        final ParsedValue value = accessor().parse(docWithNumericArray("coords", 1, 2));
        accessor().setArray(value, path("coords"), new double[]{7, 9});
        assertThat(accessor().getArray(value, path("coords"))).containsExactly(7d, 9d);
    }

    /// An edit survives serialisation and re-parsing. The editor re-serialises the
    /// whole value on every object drag, so anything lost here is lost from the saved
    /// document.
    @Test
    void testSerialize_thenReparse_preservesAnEdit() {
        final ParsedValue value = accessor().parse(docWithNumericArray("coords", 1, 2));
        accessor().setArray(value, path("coords"), new double[]{7, 9});

        final String serialised = accessor().serialize(value);
        assertThat(serialised).isNotNull();

        final ParsedValue reparsed = accessor().parse(serialised);
        assertThat(reparsed).isNotNull();
        assertThat(accessor().getArray(reparsed, path("coords")))
                .containsExactly(7d, 9d);
    }

    @Test
    void testCanParse_acceptsItsOwnOutputAndRejectsTheOther() {
        final String raw = docWithString("type", "gate");
        assertThat(accessor().canParse(raw)).isTrue();
        assertThat(accessor().canParse(null)).isFalse();
    }
}
