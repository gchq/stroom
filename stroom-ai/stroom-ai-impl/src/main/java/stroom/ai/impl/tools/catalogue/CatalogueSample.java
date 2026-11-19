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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SplitterSpec;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/// The records of a catalogue's scope as the check reads them: as processing will read them (strings cut at the
/// pipeline's `stringTruncateLength`), with the reading the conditions are evaluated by (the mapping's input, its
/// namespace, nil values, held fields and extractions), and what else the check needs of them.
///
/// @param reading              The mapping the conditions are evaluated by ([LocalCheck#holds]): the draft's, or one
///                             made by [#reading]; its rules are not used.
/// @param records              The scope's records as processing reads them
///                             ([LocalCheck#sampleRecords(TranslationMapping, Object, SplitterSpec, int)]).
/// @param whole                The same records read with no string cut, one for one; null when none is cut (or
///                             not known).
/// @param texts                Each record's text, for excerpts; null to write them from the records.
/// @param variantField         The field a record's line format (its variant) is written in, when the records are
///                             in several; else null.
/// @param stringTruncateLength The pipeline's stringTruncateLength the records were cut at
///                             ([LocalCheck#JSON_PARSER_CUT] by default); 0 when nothing is cut.
/// @param regexBudget          The longest one condition's regex may take over the sample's records.
public record CatalogueSample(TranslationMapping reading,
                              List<Object> records,
                              List<Object> whole,
                              List<String> texts,
                              String variantField,
                              int stringTruncateLength,
                              Duration regexBudget) {

    /// The longest one condition's regex may take over the sample's records, by default: a way of recognising an
    /// event type tells records by a short pattern, which runs over a sample in a few milliseconds; one that takes
    /// longer backtracks, and in processing would take that again for every stream (lessons: no regex over whole long
    /// values, 2026-11-12; a key=value regex's StackOverflow, 2026-11-06).
    public static final Duration REGEX_BUDGET = Duration.ofMillis(200);

    /// Checks the parts given and fills in the defaults.
    public CatalogueSample {
        Objects.requireNonNull(reading, "reading");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        whole = whole == null || whole.size() != records.size()
                ? null
                : List.copyOf(whole);
        texts = texts == null || texts.size() != records.size()
                ? null
                : List.copyOf(texts);
        regexBudget = Objects.requireNonNullElse(regexBudget, REGEX_BUDGET);
    }

    /// The scope's records read from a sample as processing will read them, and read whole too where processing
    /// cuts strings (a JSON input), so a condition that depends on the cut is found.
    ///
    /// @param reading              The mapping the conditions are evaluated by.
    /// @param sample               The scope's sample: a text, or a list of texts (one a file).
    /// @param splitter             The Data Splitter a data_splitter input is read with, or null.
    /// @param stringTruncateLength The pipeline's stringTruncateLength ([LocalCheck#JSON_PARSER_CUT] by default); 0
    ///                             or less to read every string whole.
    /// @param variantField         The field a record's line format is written in, or null.
    /// @return The sample.
    public static CatalogueSample read(final TranslationMapping reading,
                                       final Object sample,
                                       final SplitterSpec splitter,
                                       final int stringTruncateLength,
                                       final String variantField) {
        final int cut = Math.max(0, stringTruncateLength);
        final List<Object> records = LocalCheck.sampleRecords(reading, sample, splitter, cut).records();
        final List<Object> whole = cut > 0 && "json".equals(reading.input())
                ? LocalCheck.sampleRecords(reading, sample, splitter, 0).records()
                : null;
        return new CatalogueSample(reading, records, whole, null, variantField, cut, null);
    }

    /// A mapping to evaluate conditions by, for a draft whose own mapping isn't finished (its rules and time not yet
    /// chosen): only the parts that say how a record is read.
    ///
    /// @param input        `data_splitter`, `json`, `xml` or `xml_fragments`.
    /// @param record       An XML input's record element, or null.
    /// @param xmlNamespace An XML input's namespace, or null.
    /// @param jsonFields   The fields holding JSON text, or null.
    /// @param xmlFields    The fields holding XML text, or null.
    /// @param nilValues    The values that count as none, or null.
    /// @param extract      The extractions (fields read out of text), or null.
    /// @return The mapping.
    public static TranslationMapping reading(final String input,
                                             final String record,
                                             final String xmlNamespace,
                                             final List<String> jsonFields,
                                             final List<String> xmlFields,
                                             final List<String> nilValues,
                                             final List<Extraction> extract) {
        return new TranslationMapping(input, null, record, xmlNamespace, null, jsonFields, xmlFields, null, extract,
                null, nilValues, null, null, null, null, null, false, null, null, null, null, null, null);
    }

    /// @param budget The longest one condition's regex may take over the sample's records.
    /// @return It with that budget.
    public CatalogueSample withRegexBudget(final Duration budget) {
        return new CatalogueSample(reading, records, whole, texts, variantField, stringTruncateLength, budget);
    }

    /// @param texts Each record's text, one for one.
    /// @return It with those texts for excerpts.
    public CatalogueSample withTexts(final List<String> texts) {
        return new CatalogueSample(reading, records, whole, texts, variantField, stringTruncateLength, regexBudget);
    }
}
