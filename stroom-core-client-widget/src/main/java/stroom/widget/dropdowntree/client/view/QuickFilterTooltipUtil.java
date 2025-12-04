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

package stroom.widget.dropdowntree.client.view;

import stroom.util.shared.NullSafe;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class QuickFilterTooltipUtil {

    public static SafeHtml createTooltip(final String header,
                                         final String quickFilterHelpUrl) {
        return createTooltip(header, Collections.emptyList(), quickFilterHelpUrl);
    }

    public static SafeHtml createTooltip(final String header,
                                         final List<FilterFieldDefinition> fieldDefinitions,
                                         final String quickFilterHelpUrl) {

        return createTooltip(header, null, fieldDefinitions, quickFilterHelpUrl);
    }

    public static SafeHtml createTooltip(final String header,
                                         final Consumer<TableBuilder> preambleBuilder,
                                         final List<FilterFieldDefinition> fieldDefinitions,
                                         final String quickFilterHelpUrl) {

        final String defaultFieldNames = getDefaultFieldNamesInfo(fieldDefinitions);

        final String description = "By default matches on " +
                                   (defaultFieldNames != null
                                           ? defaultFieldNames + " "
                                           : "") +
                                   "where the values contain the text input.";
        // All this help content needs to match what happens in QuickFilterPredicateFactory
        final TableBuilder tb = new TableBuilder();
        tb.row(TableCell.header(header, 2));
        tb.row(TableCell.data(description, 2));
        tb.row(TableCell.data("Space is used to separate multiple terms, unless quoted, see examples.", 2));

        // Add the supplied pre-amble
        if (preambleBuilder != null) {
            preambleBuilder.accept(tb);
        }

        // Adds a break at the end if there are fields
        addFieldInfo(fieldDefinitions, tb);

        tb
                .row(TableCell.header("Example input"), TableCell.header("Match type"))
                .row("abc", "Default contains matching. (matches 'xxabcxx').")
                .row("~abc",
                        "Characters anywhere (in order) matching (matches 'xxaxxbxxcxx').")
                .row("/ab.c",
                        "Regular expression matching (matches 'xxabxcxx').")
                .row("?ABC",
                        "Word boundary matching (matches 'AlphaBravoCharlie').")
                .row("=abc",
                        "Exact match (matches 'abc').")
                .row("$abc",
                        "Suffix match (matches 'xxxabc')")
                .row("^abc",
                        "Prefix match (matches abcxxx').")
                .row("!abc",
                        "Negated match (does not match values containing 'abc'). " +
                        "Can be used with other match types, e.g. '!=abc'.");

        if (NullSafe.hasItems(fieldDefinitions)) {
            //noinspection SimplifyStreamApiCallChains // Cos GWT
            final List<String> qualifiedFields = fieldDefinitions.stream()
                    .filter(filterFieldDefinition -> !filterFieldDefinition.isDefaultField())
                    .map(FilterFieldDefinition::getFilterQualifier)
                    .sorted()
                    .collect(Collectors.toList());

            if (!qualifiedFields.isEmpty()) {
                tb
                        .row()
                        .row(qualifiedFields.get(0)
                             + ":abc",
                                "Named field matching (using above match types on field '"
                                + qualifiedFields.get(0) + "').");
            }
        }

        tb
                .row()
                .row(TableCell.header("Examples", 2));

        addFieldExamples(fieldDefinitions, tb);

        if (NullSafe.isNonBlankString(quickFilterHelpUrl)) {
            final HtmlBuilder hb = new HtmlBuilder();
            hb.appendTrustedString("For more information see the ");
            hb.appendLink(quickFilterHelpUrl, "Help Documentation");
            hb.appendTrustedString(".");

            tb
                    .row()
                    .row(TableCell.data(hb.toSafeHtml(), 2));
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private static String getDefaultFieldNamesInfo(final List<FilterFieldDefinition> fieldDefinitions) {
        if (fieldDefinitions != null) {
            final List<String> fieldNames = fieldDefinitions.stream()
                    .filter(FilterFieldDefinition::isDefaultField)
                    .map(FilterFieldDefinition::getDisplayName)
                    .map(str -> "\"" + str + "\"")
                    .collect(Collectors.toList());
            if (fieldNames.isEmpty()) {
                return null;
            } else if (fieldNames.size() == 1) {
                return "field " + fieldNames.get(0);
            } else if (fieldNames.size() == 2) {
                return "fields " + fieldNames.get(0) + " or " + fieldNames.get(1);
            } else {
                return "fields "
                       + String.join(", ", fieldNames.subList(0, fieldNames.size() - 1))
                       + " or "
                       + fieldNames.get(fieldNames.size() - 1);
            }
        } else {
            return null;
        }
    }

    private static void addFieldInfo(final List<FilterFieldDefinition> fieldDefinitions,
                                     final TableBuilder tb) {
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            tb.row();

            tb.row(TableCell.header("Filterable fields"), TableCell.header("Field qualifier"));

            fieldDefinitions.forEach(fieldDefinition -> {
                final String suffix = fieldDefinition.isDefaultField()
                        ? " (Default)"
                        : "";
                final SafeHtml value = new HtmlBuilder()
                        .code(hb -> hb.append(fieldDefinition.getFilterQualifier()))
                        .italic(suffix)
                        .toSafeHtml();
                tb.row(SafeHtmlUtil.from(fieldDefinition.getDisplayName()), value);
            });
        }
    }

    private static void addFieldExamples(final List<FilterFieldDefinition> fieldDefinitions, final TableBuilder tb) {
        tb
                .row("abc \"d ef\"",
                        "Matches default field(s) with values containing 'abc' AND 'd ef'.");


        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            final List<String> qualifiers = fieldDefinitions.stream()
                    .filter(filterFieldDefinition -> !filterFieldDefinition.isDefaultField())
                    .map(FilterFieldDefinition::getFilterQualifier)
                    .sorted()
                    .collect(Collectors.toList());

            if (qualifiers.size() == 1) {

                tb
                        .row(
                                "/abc " + qualifiers.get(0) + ":^def",
                                "Matches default field(s) with regex 'abc' " +
                                "and " + qualifiers.get(0) + " field values that start with 'def'");
            } else if (qualifiers.size() > 1) {
                tb
                        .row(
                                "/abc " + qualifiers.get(0) + ":^def",
                                "Matches default field(s) with regex 'abc' " +
                                "and " + qualifiers.get(0) + " field values that start with 'def'")
                        .row(

                                "\"" + qualifiers.get(0) + ":ab c\" "
                                + qualifiers.get(1) + ":/(def|xyz)",
                                "Matches " + qualifiers.get(0) + " field values which contain 'ab c' and "
                                + qualifiers.get(1) + " field values which matches " +
                                "regex '(def|xyz)'")
                        .row(
                                "" + qualifiers.get(0) + ":?ABC "
                                + qualifiers.get(1) + ":!/def",
                                "Matches " + qualifiers.get(0)
                                + " field values with word first letters A, B, C " +
                                "and " + qualifiers.get(1) + " field values that " +
                                "don't match regex 'def'");
            }
        }
    }
}
