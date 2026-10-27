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

package stroom.ai.impl.extraction;

import stroom.util.shared.NullSafe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Changes the XSLT of a table's existing search extraction to give fields (see [ExtractionField]).
///
/// - One Stroom AI wrote (see [ExtractionXslt#read(String)]) is written again from the fields given, which are all
///   the fields it is to give: so fields can be added, removed, renamed or given another XPath.
/// - One written by hand is kept as it is, with a `data` element added for each field given, which are only the
///   fields to add, just before the end of its one record. Changing what it gives already is left to its author.
public final class ExtractionChange {

    /// The namespace of XSLT's own elements.
    static final String XSL_NAMESPACE = "http://www.w3.org/1999/XSL/Transform";

    // A data element of an XSLT, with its name given literally: <data name="UserId" ...>.
    private static final Pattern DATA_NAME = Pattern.compile(
            "<(?:[A-Za-z_][\\w.-]*:)?data\\s+name\\s*=\\s*[\"']([^\"'{}]+)[\"']");
    // The end of a record element, with or without a prefix.
    private static final Pattern RECORD_END = Pattern.compile("</(?:[A-Za-z_][\\w.-]*:)?record\\s*>");
    // The prefix an XSLT gives XSLT's own namespace.
    private static final Pattern XSL_PREFIX = Pattern.compile(
            "xmlns:([A-Za-z_][\\w.-]*)\\s*=\\s*[\"']" + Pattern.quote(XSL_NAMESPACE) + "[\"']");
    // A data element whose name is made as the XSLT runs: <data name="{@Name}" ...>, or an xsl:element named data.
    private static final Pattern DYNAMIC_DATA_NAME = Pattern.compile(
            "<(?:[A-Za-z_][\\w.-]*:)?data\\s+name\\s*=\\s*[\"'][^\"']*\\{"
            + "|<[A-Za-z_][\\w.-]*:element\\s+name\\s*=\\s*[\"'](?:[A-Za-z_][\\w.-]*:)?data[\"']"
            + "|<(?:[A-Za-z_][\\w.-]*:)?data\\b[^>]*>\\s*<[A-Za-z_][\\w.-]*:attribute\\s+name\\s*=\\s*"
            + "[\"']name[\"']");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern CDATA = Pattern.compile("<!\\[CDATA\\[.*?]]>", Pattern.DOTALL);

    private ExtractionChange() {
        // Static only.
    }

    /// Changes an extraction XSLT to give fields.
    ///
    /// @param code      The XSLT's code now.
    /// @param namespace The events' namespace (e.g. `event-logging:3`), blank for none, or null to keep the one an
    ///                  XSLT Stroom AI wrote was written for.
    /// @param fields    For one Stroom AI wrote, every field it is to give; for one written by hand, the fields to
    ///                  add. Checked (see [ExtractionField#problems(List)]).
    /// @return The changed XSLT.
    /// @throws IllegalArgumentException if it can't be changed so, saying why.
    public static Changed change(final String code, final String namespace, final List<ExtractionField> fields) {
        if (NullSafe.isBlankString(code)) {
            throw new IllegalArgumentException("The extraction's XSLT is empty, so there is nothing to change.");
        }
        final Optional<ExtractionXslt.Generated> generated = ExtractionXslt.read(code);
        if (generated.isPresent()) {
            // Stroom fills a column's field by its exact name.
            final List<String> before = generated.get().fields().stream().map(ExtractionField::name).toList();
            final List<String> after = fields.stream().map(ExtractionField::name).toList();
            final List<ExtractionField> added = fields.stream()
                    .filter(field -> !before.contains(field.name()))
                    .toList();
            final List<String> removed = before.stream()
                    .filter(name -> !after.contains(name))
                    .toList();
            final String ns = namespace == null
                    ? generated.get().namespace()
                    : namespace;
            return new Changed(ExtractionXslt.xslt(ns, fields), true, Set.of(), false, added, removed);
        }
        return new Changed(addTo(code, namespace, fields), false, dataNames(code), namesAsItRuns(code), fields,
                List.of());
    }

    /// @param code An XSLT's code.
    /// @return True if it names some of its `data` elements as it runs (e.g. `name="{@Name}"`), so which fields it
    /// gives can't all be read from its code.
    public static boolean namesAsItRuns(final String code) {
        return DYNAMIC_DATA_NAME.matcher(withoutComments(NullSafe.string(code))).find();
    }

    /// The names of the `data` elements an XSLT gives literally, e.g. `UserId` for `<data name="UserId" .../>`.
    ///
    /// @param code An XSLT's code.
    /// @return The names, in the order given; none if there are none.
    public static Set<String> dataNames(final String code) {
        final Set<String> names = new LinkedHashSet<>();
        final Matcher matcher = DATA_NAME.matcher(withoutComments(NullSafe.string(code)));
        while (matcher.find()) {
            names.add(matcher.group(1).strip());
        }
        return names;
    }

    /// The SHA-256 of some text, e.g. an XSLT's code, so a change is only written to the code it was proposed for.
    ///
    /// @param text The text.
    /// @return Its SHA-256 as lower-case hex.
    public static String sha256(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(NullSafe.string(text).getBytes(StandardCharsets.UTF_8))).toLowerCase(Locale.ROOT);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // An XSLT written by hand, with a data element for each field added just before the end of its record.
    private static String addTo(final String code, final String namespace, final List<ExtractionField> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Give the fields to add to the extraction.");
        }
        final Set<String> existing = names(dataNames(code));
        final List<String> clashes = fields.stream()
                .map(ExtractionField::name)
                .filter(name -> existing.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
        if (!clashes.isEmpty()) {
            throw new IllegalArgumentException("The extraction's XSLT, written by hand, gives "
                                               + String.join(", ", clashes) + " already. Only fields it doesn't give "
                                               + "can be added to it; changing one it gives is for its author, by "
                                               + "hand.");
        }
        // Positions are kept, so a match in the code without its comments is at the same place in the code.
        final String plain = withoutComments(code);
        final Matcher prefix = XSL_PREFIX.matcher(plain);
        if (!prefix.find()) {
            throw new IllegalArgumentException("The extraction's XSLT doesn't give XSLT's namespace a prefix (e.g. "
                                               + "xmlns:xsl), so fields can't be added to it.");
        }
        final String xsl = prefix.group(1);
        final Matcher end = RECORD_END.matcher(plain);
        final List<Integer> ends = new ArrayList<>();
        while (end.find()) {
            ends.add(end.start());
        }
        if (ends.size() != 1) {
            throw new IllegalArgumentException("The extraction's XSLT, written by hand, has "
                                               + (ends.isEmpty()
                    ? "no record element"
                    : ends.size() + " record elements")
                                               + ", so where to add the fields isn't clear. Save it as a new "
                                               + "pipeline with an XSLT Stroom AI writes, or add them by hand.");
        }
        final int at = ends.getFirst();
        final String indent = indentOf(code, at);
        final StringBuilder sb = new StringBuilder();
        for (final ExtractionField field : fields) {
            sb.append("  <data name=\"").append(ExtractionXslt.escape(field.name())).append("\" ")
                    .append(xsl).append(":xpath-default-namespace=\"")
                    .append(ExtractionXslt.escape(NullSafe.string(namespace).strip())).append("\">\n")
                    .append(indent).append("    <").append(xsl).append(":attribute name=\"value\">\n")
                    .append(indent).append("      <").append(xsl).append(":value-of select=\"string((")
                    .append(ExtractionXslt.escape(field.xpath())).append(")[1])\"/>\n")
                    .append(indent).append("    </").append(xsl).append(":attribute>\n")
                    .append(indent).append("  </data>\n")
                    .append(indent);
        }
        return code.substring(0, at) + sb + code.substring(at);
    }

    // The white space before a position on its line, if only white space is before it.
    private static String indentOf(final String code, final int at) {
        final int lineStart = code.lastIndexOf('\n', at - 1) + 1;
        final String before = code.substring(lineStart, at);
        return before.isBlank()
                ? before
                : "";
    }

    // The code with its comments and CDATA sections blanked out, keeping every other character where it was.
    private static String withoutComments(final String code) {
        String plain = code;
        for (final Pattern pattern : List.of(COMMENT, CDATA)) {
            final Matcher matcher = pattern.matcher(plain);
            final StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, " ".repeat(matcher.end() - matcher.start()));
            }
            matcher.appendTail(sb);
            plain = sb.toString();
        }
        return plain;
    }

    private static Set<String> names(final Iterable<String> names) {
        final Set<String> lower = new LinkedHashSet<>();
        names.forEach(name -> lower.add(name.toLowerCase(Locale.ROOT)));
        return lower;
    }


    // --------------------------------------------------------------------------------


    /// An extraction XSLT, changed.
    ///
    /// @param code      Its new code.
    /// @param generated True if Stroom AI wrote it, and wrote it again; false if it was written by hand, and fields
    ///                  were added to it.
    /// @param alsoGiven     For one written by hand, the fields it gave already, as its code names them (besides
    ///                      those added); else none.
    /// @param namesAsItRuns For one written by hand, true if it names some fields as it runs, so they aren't all in
    ///                      [#alsoGiven()] (nor checked against those added); see [#namesAsItRuns(String)].
    /// @param added         The fields it didn't give before.
    /// @param removed       For one Stroom AI wrote, the fields it gave that it won't (which anything else using it
    ///                      loses); else none.
    public record Changed(String code,
                          boolean generated,
                          Set<String> alsoGiven,
                          boolean namesAsItRuns,
                          List<ExtractionField> added,
                          List<String> removed) {

    }
}
