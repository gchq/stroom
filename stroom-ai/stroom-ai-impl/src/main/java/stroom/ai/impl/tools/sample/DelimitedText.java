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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Delimited text read for the profile of a sample: its records split into cells as the Data Splitter the tools
/// generate splits them, and the sniffer, which guesses the delimiter and whether the first row is a header.
///
/// Cells are read as Stroom reads them, so the profile shows the values a mapping will see (asked for by the user:
/// they were read by other CSV rules, which turned a doubled quote into one and took a quote for a quote only at a
/// value's start). A quote opens or closes a quoted stretch wherever it is, and a doubled quote inside one stays
/// doubled, for the mapping's `unescape_quotes` to make one ([SplitterDryRun#splitQuoted(String, String, String)]).
///
/// A field may be of any length: there is no field size limit. The text read is already cut to the caller's
/// budget, and Stroom's Data Splitter reads longer fields, so a limit only lost records (seen in testing: one
/// 250,000-character URL query stopped a whole stream part being surveyed).
final class DelimitedText {

    /// The quote the generated Data Splitter takes as a cell's container.
    static final String QUOTE = "\"";

    // The type has_header gives a column of numbers.
    private static final Object COMPLEX = new Object();
    private static final List<Character> PREFERRED = List.of(',', '\t', ';', ' ', ':');
    private static final String QUOTED_BODY = "(?:\\k<quote>{2}|(?!\\k<quote>).)*+";
    private static final List<Pattern> QUOTE_AND_DELIMITER = List.of(
            // ,"...",
            ToolText.re("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"'])" + QUOTED_BODY + "\\k<quote>\\k<delim>",
                    Pattern.DOTALL | Pattern.MULTILINE),
            //  "...",
            ToolText.re("(?:^|\\n)(?<quote>[\"'])" + QUOTED_BODY + "\\k<quote>(?<delim>[^\\w\\n\"'])(?<space> ?)",
                    Pattern.DOTALL | Pattern.MULTILINE),
            // ,"..."
            ToolText.re("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"'])" + QUOTED_BODY + "\\k<quote>(?:$|\\n)",
                    Pattern.DOTALL | Pattern.MULTILINE),
            //  "..." (no delim, no space)
            ToolText.re("(?:^|\\n)(?<quote>[\"'])" + QUOTED_BODY + "\\k<quote>(?:$|\\n)",
                    Pattern.DOTALL | Pattern.MULTILINE));

    private DelimitedText() {
        // Static methods only.
    }

    /// Splits records into cells as the generated Data Splitter does: at the delimiter outside quotes, the quotes
    /// taken out, a doubled quote kept doubled.
    ///
    /// @param records   The records: a line each, or, for quoted values holding new lines, the lines
    ///                  [SplitterDryRun#quotedLines(String, String)] joins.
    /// @param delimiter What separates cells.
    /// @return Each record's cells.
    static List<List<String>> cells(final List<String> records, final char delimiter) {
        final String delimiterText = String.valueOf(delimiter);
        final List<List<String>> rows = new ArrayList<>();
        records.forEach(record -> rows.add(SplitterDryRun.splitQuoted(record, delimiterText, QUOTE)));
        return rows;
    }

    /// Guesses how a sample is delimited and quoted, as `csv.Sniffer().sniff(sample, delimiters)` does.
    ///
    /// @param sample     The sample.
    /// @param delimiters The delimiters to choose from, or null for any.
    /// @return The dialect.
    /// @throws CsvException if no delimiter can be told ("Could not determine delimiter").
    static Dialect sniff(final String sample, final String delimiters) {
        final String data = sample.replace("\r\n", "\n").replace('\r', '\n');
        Guess guess = guessQuoteAndDelimiter(data, delimiters);
        if (guess.delimiter() == null || guess.delimiter().isEmpty()) {
            guess = guessDelimiter(data, delimiters, guess.quote(), guess.doubleQuote());
        }
        if (guess.delimiter() == null || guess.delimiter().isEmpty()) {
            throw new CsvException("Could not determine delimiter");
        }
        final char quote = guess.quote() == null || guess.quote().isEmpty()
                ? '"'
                : guess.quote().charAt(0);
        return new Dialect(guess.delimiter().charAt(0), quote, guess.doubleQuote(), guess.skipInitialSpace());
    }

    private static Guess guessQuoteAndDelimiter(final String data, final String delimiters) {
        // Each match's quote, delimiter and space ("" where the pattern has no such group).
        final List<String[]> matches = new ArrayList<>();
        boolean hasDelimiter = false;
        boolean hasSpace = false;
        for (final Pattern pattern : QUOTE_AND_DELIMITER) {
            hasDelimiter = pattern.pattern().contains("(?<delim>");
            hasSpace = pattern.pattern().contains("(?<space>");
            final Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                matches.add(new String[]{
                        matcher.group("quote"),
                        hasDelimiter
                                ? matcher.group("delim")
                                : "",
                        hasSpace
                                ? matcher.group("space")
                                : ""});
            }
            if (!matches.isEmpty()) {
                break;
            }
        }
        if (matches.isEmpty()) {
            return new Guess("", false, null, false);
        }
        final Map<String, Integer> quotes = new LinkedHashMap<>();
        final Map<String, Integer> delims = new LinkedHashMap<>();
        int spaces = 0;
        for (final String[] match : matches) {
            if (!match[0].isEmpty()) {
                quotes.merge(match[0], 1, Integer::sum);
            }
            if (!hasDelimiter) {
                continue;
            }
            if (!match[1].isEmpty() && (delimiters == null || delimiters.contains(match[1]))) {
                delims.merge(match[1], 1, Integer::sum);
            }
            if (hasSpace && !match[2].isEmpty()) {
                spaces++;
            }
        }
        final String quoteChar = firstMax(quotes);
        String delim;
        final boolean skipInitialSpace;
        if (!delims.isEmpty()) {
            delim = firstMax(delims);
            skipInitialSpace = delims.get(delim) == spaces;
            if ("\n".equals(delim)) {
                // Most likely a file with a single column.
                delim = "";
            }
        } else {
            // There is no delimiter: a single column of quoted data.
            delim = "";
            skipInitialSpace = false;
        }
        // A doubled quote character inside a quoted field means a double quoted format. Whole fields are matched,
        // so that a match can't slide across field boundaries.
        boolean doubleQuote = false;
        if (!delim.isEmpty()) {
            final String quotedDelim = Pattern.quote(delim);
            final Pattern doubled = ToolText.re("(?:(?<=" + quotedDelim + ")|^)" + (" ".equals(delim)
                    ? ""
                    : " *+") + quoteChar + "((?:" + quoteChar + quoteChar + "|[^" + quoteChar + "]++)*+)" + quoteChar
                    + "(?:" + quotedDelim + "|$)", Pattern.MULTILINE);
            final Matcher matcher = doubled.matcher(data);
            while (matcher.find()) {
                if (matcher.group(1).contains(quoteChar + quoteChar)) {
                    doubleQuote = true;
                    break;
                }
            }
        }
        return new Guess(quoteChar, doubleQuote, delim, skipInitialSpace);
    }

    private static <K> K firstMax(final Map<K, Integer> counts) {
        K best = null;
        int most = Integer.MIN_VALUE;
        for (final Map.Entry<K, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    // The delimiter should occur the same number of times on each row; small variations are allowed. The mode of
    // each character's frequency per line is its expected frequency; the character that best meets it is the
    // delimiter. The data is read in chunks of ten lines, so that as little as possible is read.
    private static Guess guessDelimiter(final String text,
                                        final String delimiters,
                                        final String quote,
                                        final boolean doubleQuote) {
        final List<String> data = new ArrayList<>();
        for (final String line : text.split("\n", -1)) {
            if (!line.isEmpty()) {
                data.add(line);
            }
        }
        final int chunkLength = Math.min(10, data.size());
        int iteration = 0;
        final Map<Character, Map<Integer, Integer>> charFrequency = new LinkedHashMap<>();
        final Map<Character, int[]> modes = new LinkedHashMap<>();
        final Map<Character, int[]> delims = new LinkedHashMap<>();
        int start = 0;
        int end = chunkLength;
        while (start < data.size()) {
            iteration++;
            for (final String line : data.subList(start, Math.min(end, data.size()))) {
                for (char c = 0; c < 127; c++) {
                    final Map<Integer, Integer> metaFrequency = charFrequency.computeIfAbsent(c,
                            k -> new LinkedHashMap<>());
                    metaFrequency.merge(count(line, c), 1, Integer::sum);
                }
            }
            for (final Map.Entry<Character, Map<Integer, Integer>> entry : charFrequency.entrySet()) {
                final List<int[]> items = new ArrayList<>();
                entry.getValue().forEach((freq, n) -> items.add(new int[]{freq, n}));
                if (items.size() == 1 && items.getFirst()[0] == 0) {
                    continue;
                }
                if (items.size() > 1) {
                    int[] mode = items.getFirst();
                    for (final int[] item : items) {
                        if (item[1] > mode[1]) {
                            mode = item;
                        }
                    }
                    items.remove(mode);
                    final int others = items.stream().mapToInt(item -> item[1]).sum();
                    modes.put(entry.getKey(), new int[]{mode[0], mode[1] - others});
                } else {
                    modes.put(entry.getKey(), items.getFirst());
                }
            }
            final double total = Math.min(chunkLength * iteration, data.size());
            double consistency = 1.0;
            final double threshold = 0.9;
            while (delims.isEmpty() && consistency >= threshold) {
                for (final Map.Entry<Character, int[]> entry : modes.entrySet()) {
                    final int[] v = entry.getValue();
                    if (v[0] > 0 && v[1] > 0 && v[1] / total >= consistency
                        && (delimiters == null || delimiters.indexOf(entry.getKey()) >= 0)) {
                        delims.put(entry.getKey(), v);
                    }
                }
                consistency -= 0.01;
            }
            if (delims.size() == 1) {
                final char delim = delims.keySet().iterator().next();
                return guessed(delim, data, quote, doubleQuote);
            }
            start = end;
            end += chunkLength;
        }
        if (delims.isEmpty()) {
            return new Guess(quote, doubleQuote, "", false);
        }
        if (delims.size() > 1) {
            for (final char preferred : PREFERRED) {
                if (delims.containsKey(preferred)) {
                    return guessed(preferred, data, quote, doubleQuote);
                }
            }
        }
        // Nothing else indicates a preference: the character that dominates.
        Character best = null;
        int[] bestValue = null;
        for (final Map.Entry<Character, int[]> entry : delims.entrySet()) {
            final int[] v = entry.getValue();
            if (bestValue == null || v[0] > bestValue[0] || (v[0] == bestValue[0] && v[1] > bestValue[1])
                || (v[0] == bestValue[0] && v[1] == bestValue[1] && entry.getKey() > best)) {
                best = entry.getKey();
                bestValue = v;
            }
        }
        return guessed(best, data, quote, doubleQuote);
    }

    private static Guess guessed(final char delim, final List<String> data, final String quote,
                                 final boolean doubleQuote) {
        final String first = data.getFirst();
        final boolean skipInitialSpace = count(first, delim) == ToolText.count(first, delim + " ");
        return new Guess(quote, doubleQuote, String.valueOf(delim), skipInitialSpace);
    }

    private static int count(final String line, final char c) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /// Whether the first row of a sample is a header, as `csv.Sniffer().has_header(sample)` decides: a column whose
    /// values below are all numbers, or all one length, while its first row's isn't, votes for a header.
    ///
    /// @param sample The sample.
    /// @return True if it looks like it has a header row.
    /// @throws CsvException if the sample's delimiter can't be told.
    static boolean hasHeader(final String sample) {
        final List<List<String>> rows = cells(ToolText.nonBlankLines(sample), sniff(sample, null).delimiter());
        if (rows.isEmpty()) {
            return false;
        }
        final List<String> header = rows.getFirst();
        final int columns = header.size();
        // A column's type: null for none yet, COMPLEX for a number, or an Integer, the values' length.
        final Map<Integer, Object> columnTypes = new LinkedHashMap<>();
        for (int i = 0; i < columns; i++) {
            columnTypes.put(i, null);
        }
        int checked = 0;
        for (final List<String> row : rows.subList(1, rows.size())) {
            // An arbitrary number of rows to check, to keep it sane.
            if (checked > 20) {
                break;
            }
            checked++;
            if (row.size() != columns) {
                continue;
            }
            for (final Integer col : new ArrayList<>(columnTypes.keySet())) {
                final Object thisType = isComplex(row.get(col))
                        ? COMPLEX
                        : (Object) ToolText.len(row.get(col));
                final Object current = columnTypes.get(col);
                if (!thisType.equals(current)) {
                    if (current == null) {
                        columnTypes.put(col, thisType);
                    } else {
                        // The type is inconsistent: the column isn't considered.
                        columnTypes.remove(col);
                    }
                }
            }
        }
        int hasHeader = 0;
        for (final Map.Entry<Integer, Object> entry : columnTypes.entrySet()) {
            final String cell = header.get(entry.getKey());
            if (entry.getValue() instanceof final Integer length) {
                hasHeader += ToolText.len(cell) != length
                        ? 1
                        : -1;
            } else if (entry.getValue() == null) {
                // A column no row typed: counted as a header.
                hasHeader++;
            } else {
                hasHeader += isComplex(cell)
                        ? -1
                        : 1;
            }
        }
        return hasHeader > 0;
    }

    /// @return True if the text is a number, complex numbers included: such as `1`, `-2.5e3`, `1_000`, `inf`,
    /// `nan`, `3j` or `(1+2j)`, with any whitespace around it.
    static boolean isComplex(final String text) {
        String s = text;
        if (s.indexOf('_') >= 0) {
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '_' && (i == 0 || i == s.length() - 1 || !isAsciiDigit(s.charAt(i - 1))
                                           || !isAsciiDigit(s.charAt(i + 1)))) {
                    return false;
                }
            }
            s = s.replace("_", "");
        }
        s = ToolText.strip(s);
        if (s.startsWith("(")) {
            if (!s.endsWith(")") || s.length() < 2) {
                return false;
            }
            s = ToolText.strip(s.substring(1, s.length() - 1));
        }
        if (s.isEmpty()) {
            return false;
        }
        final int realEnd = floatPrefix(s, 0);
        if (realEnd == 0) {
            // <sign>j or j alone.
            if (s.charAt(0) == '+' || s.charAt(0) == '-') {
                return s.length() == 2 && (s.charAt(1) == 'j' || s.charAt(1) == 'J');
            }
            return s.length() == 1 && (s.charAt(0) == 'j' || s.charAt(0) == 'J');
        }
        if (realEnd == s.length()) {
            return true;
        }
        final char next = s.charAt(realEnd);
        if (next == 'j' || next == 'J') {
            return realEnd + 1 == s.length();
        }
        if (next == '+' || next == '-') {
            final int imagEnd = floatPrefix(s, realEnd);
            final int at = imagEnd == realEnd
                    ? realEnd + 1
                    : imagEnd;
            return at < s.length() && (s.charAt(at) == 'j' || s.charAt(at) == 'J') && at + 1 == s.length();
        }
        return false;
    }

    private static boolean isAsciiDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    // The end of the float that starts at `from` (a sign, digits with a point and an exponent, or inf, infinity or
    // nan), or `from` when none does.
    private static int floatPrefix(final String s, final int from) {
        int i = from;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            i++;
        }
        final String rest = s.substring(i).toLowerCase(Locale.ROOT);
        if (rest.startsWith("infinity")) {
            return i + 8;
        }
        if (rest.startsWith("inf") || rest.startsWith("nan")) {
            return i + 3;
        }
        final int digitsStart = i;
        while (i < s.length() && isAsciiDigit(s.charAt(i))) {
            i++;
        }
        boolean digits = i > digitsStart;
        if (i < s.length() && s.charAt(i) == '.') {
            final int fractionStart = i + 1;
            int j = fractionStart;
            while (j < s.length() && isAsciiDigit(s.charAt(j))) {
                j++;
            }
            if (digits || j > fractionStart) {
                digits = true;
                i = j;
            }
        }
        if (!digits) {
            return from;
        }
        if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            int j = i + 1;
            if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                j++;
            }
            final int exponentStart = j;
            while (j < s.length() && isAsciiDigit(s.charAt(j))) {
                j++;
            }
            if (j > exponentStart) {
                i = j;
            }
        }
        return i;
    }


    // --------------------------------------------------------------------------------


    /// How the sniffer guesses delimited text is written.
    ///
    /// @param delimiter        What separates values.
    /// @param quote            What quotes a value.
    /// @param doubleQuote      True if a doubled quote inside a quoted value is a quote.
    /// @param skipInitialSpace True if spaces after a delimiter are left out.
    record Dialect(char delimiter, char quote, boolean doubleQuote, boolean skipInitialSpace) {

    }

    /// Delimited text whose delimiter can't be told.
    static final class CsvException extends RuntimeException {

        /// @param message What the sniffer says.
        CsvException(final String message) {
            super(message);
        }
    }

    private record Guess(String quote, boolean doubleQuote, String delimiter, boolean skipInitialSpace) {

    }
}
