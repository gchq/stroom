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

package stroom.query.impl;

import stroom.dashboard.impl.vis.VisSettings;
import stroom.dashboard.impl.vis.VisSettings.Control;
import stroom.dashboard.impl.vis.VisSettings.Tab;
import stroom.dashboard.impl.visualisation.VisualisationDocCache;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.docref.DocRef;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionSnippet;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpDocument;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.util.collections.TrimmedSortedList;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.string.AceStringMatcher;
import stroom.util.string.AceStringMatcher.AceMatchResult;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Singleton
public class Visualisations {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Visualisations.class);

    private static final String VISUALISATION_ID = "visualisation";
    private static final QueryHelpRow ROOT = QueryHelpRow
            .builder()
            .type(QueryHelpType.TITLE)
            .id(VISUALISATION_ID)
            .hasChildren(true)
            .title("Visualisations")
            .build();
    public static final int INITIAL_SCORE = 200;

    private final VisualisationStore visualisationStore;
    private final VisualisationDocCache visualisationDocCache;

    @Inject
    public Visualisations(final VisualisationStore visualisationStore,
                          final VisualisationDocCache visualisationDocCache) {
        this.visualisationStore = visualisationStore;
        this.visualisationDocCache = visualisationDocCache;
    }

    private List<VisualisationDoc> getVisualisationDocs() {
        // TODO not ideal having to hit the DB to list all the docRefs then load
        //  each doc (albeit the load will probably be a cache hit)
        return visualisationStore.list()
                .stream()
                .filter(Objects::nonNull)
                .map(visualisationDocCache::get)
                .toList();
    }

    public void addRows(final PageRequest pageRequest,
                        final String parentPath,
                        final Predicate<String> predicate,
                        final ResultPageBuilder<QueryHelpRow> resultPageBuilder) {
        final List<VisualisationDoc> docs = getVisualisationDocs();
        if (parentPath.isBlank()) {
            final boolean hasChildren = hasChildren(docs, predicate);
            if (hasChildren ||
                predicate.test(ROOT.getTitle())) {
                resultPageBuilder.add(ROOT.copy().hasChildren(hasChildren).build());
            }
        } else if (parentPath.startsWith(VISUALISATION_ID + ".")) {
            final TrimmedSortedList<QueryHelpRow> trimmedSortedList =
                    new TrimmedSortedList<>(pageRequest, Comparator.comparing(QueryHelpRow::getTitle));

            for (final VisualisationDoc doc : docs) {
                final DocRef docRef = doc.asDocRef();
                if (predicate.test(docRef.getDisplayValue())) {
                    final QueryHelpRow row = QueryHelpRow
                            .builder()
                            .type(QueryHelpType.VISUALISATION)
                            .id(VISUALISATION_ID + "." + docRef.getUuid())
                            .documentType(VisualisationDoc.TYPE)
                            .iconTooltip(docRef.getType() + " - " + docRef.getDisplayValue())
                            .title(docRef.getDisplayValue())
                            .data(new QueryHelpDocument(docRef))
                            .build();
                    trimmedSortedList.add(row);
                }
            }

            final List<QueryHelpRow> list = trimmedSortedList.getList();
            for (final QueryHelpRow row : list) {
                resultPageBuilder.add(row);
            }
        }
    }

    public void addCompletions(final CompletionsRequest request,
                               final int maxCompletions,
                               final List<CompletionItem> resultList) {

        try {
            // Get all visualisations
            final List<VisualisationDoc> visualisationDocs = getVisualisationDocs();

            if (visualisationDocs.size() > maxCompletions) {
                final List<AceMatchResult<VisualisationDoc>> matchResults = AceStringMatcher.filterCompletions(
                                visualisationDocs,
                                request.getPattern(),
                                INITIAL_SCORE,
                                VisualisationDoc::getName)
                        .stream()
                        .sorted(AceStringMatcher.SCORE_DESC_THEN_NAME_COMPARATOR)
                        .toList();

                LOGGER.debug(() -> LogUtil.message("Found {} match results, from {} items, maxCompletions {}",
                        matchResults.size(), visualisationDocs.size(), maxCompletions));

                matchResults.stream()
                        .limit(maxCompletions)
                        .map(matchResult -> createCompletionSnippet(matchResult.item(), matchResult.score()))
                        .forEach(resultList::add);
            } else {
                LOGGER.debug(() -> LogUtil.message("Found {} match results using offset {}, maxCompletions {}",
                        visualisationDocs.size(), maxCompletions));
                visualisationDocs.stream()
                        .map(doc -> new AceMatchResult<>(doc, doc.getName(), INITIAL_SCORE, false))
                        .map(matchResult -> createCompletionSnippet(matchResult.item(), matchResult.score()))
                        .forEach(resultList::add);
            }
        } catch (final Exception e) {
            LOGGER.error("Error adding visualisation completions: {}", e.getMessage(), e);
        }
    }

    public Optional<QueryHelpDetail> fetchDetail(final QueryHelpRow row) {
        if (VISUALISATION_ID.equals(row.getId())) {
            final InsertType insertType = InsertType.NOT_INSERTABLE;
            final String insertText = null;
            final String documentation =
                    "A list of data sources that can be queried by specifying them in the 'from' clause.";
            return Optional.of(new QueryHelpDetail(insertType, insertText, documentation));

        } else if (QueryHelpType.VISUALISATION.equals(row.getType()) &&
                   row.getId().startsWith(VISUALISATION_ID + ".")) {
            final QueryHelpDocument queryHelpDocument = (QueryHelpDocument) row.getData();
            final DocRef docRef = queryHelpDocument.getDocRef();
            final VisualisationDoc doc = visualisationDocCache.get(docRef);
            final InsertType insertType = InsertType.snippet(getSnippetText(doc));
            final String insertText = getSnippetText(doc);
            final String documentation = getDetail(doc);
            return Optional.of(new QueryHelpDetail(insertType, insertText, documentation));
        }

        return Optional.empty();
    }

    private boolean hasChildren(final List<VisualisationDoc> docs, final Predicate<String> predicate) {
        return docs.stream()
                .map(VisualisationDoc::asDocRef)
                .anyMatch(docRef -> predicate.test(docRef.getDisplayValue()));
    }

    private CompletionItem createCompletionSnippet(final VisualisationDoc doc, final int score) {
        final String caption = doc.getName();

        String snippetText;
        try {
            snippetText = getSnippetText(doc);
        } catch (final Exception e) {
            LOGGER.debug(() -> "Error getting vis settings: " + e.getMessage(), e);
            // Fall back to a CompletionValue
            snippetText = getInsertText(doc.asDocRef());
        }

        final String tooltip = getDetail(doc);
        return new CompletionSnippet(
                caption,
                snippetText,
                score,
                "Visualisation",
                tooltip);
    }

    private String getInsertText(final DocRef docRef) {
        return docRef.getName().contains(" ")
                ? "\"" + docRef.getName() + "\""
                : docRef.getName();
    }

    private String getSnippetText(final VisualisationDoc doc) {

        final String visName = doc.getName().contains(" ")
                ? "\"" + doc.getName() + "\""
                : doc.getName();
        final VisSettings visSettings = JsonUtil.readValue(doc.getSettings(), VisSettings.class);
        final Tab dataTab = NullSafe.stream(visSettings.getTabs())
                .filter(tab -> "data".equalsIgnoreCase(tab.getName()))
                .findFirst()
                .orElse(null);
        final List<Control> dataControls = NullSafe.asList(
                NullSafe.get(dataTab, Tab::getControls));

        final StringBuilder sb = new StringBuilder(visName)
                .append("(");

        int tabStop = 1;
        for (final Control control : dataControls) {
            if ("field".equalsIgnoreCase(control.getType())) {
                if (tabStop > 1) {
                    sb.append(", ");
                }

                sb.append(control.getId())
                        .append(" = ")
                        .append("${")
                        .append(tabStop++)
                        .append(":")
                        .append(control.getLabel())
                        .append("}");
            }
        }

        sb.append(")$0");

        return sb.toString();
    }

    private String getDetail(final VisualisationDoc doc) {
        final DetailBuilder detail = new DetailBuilder();
        detail.title(doc.getName());
        detail.description(description -> description
                .table(table -> table
                        .appendKVRow("Name:", doc.getName())
                        .appendKVRow("Type:", doc.getType())
                        .appendKVRow("UUID:", doc.getUuid())));

        NullSafe.consume(doc.getDescription(), detail::append);

        return detail.build();
    }
}
