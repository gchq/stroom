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

package stroom.mcp.impl.e2e;

import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A shared translation (V1.0) selecting two feeds, and a build of its next version for one of them (decided by the
/// user, 2026-11-25), on a real Stroom through the REAL tools: the promotion moves only the build's feed; its new
/// filter selects it by reference, made disabled as the old filter already selects it; the next steps give the
/// switch-over in order, each step linked (remove the feed from the old filter, or from the dictionary it selects
/// by; let the old filter's tasks finish; enable the new filter), and the other feed stays with V1.0, which no step
/// retires; the old filter and dictionary are left as they were. `build_status` then reports the feed selected
/// twice, by none once the old selection is changed, and once each when the new filter is enabled: report only.
@SuppressWarnings("unchecked")
class TestE2ePromotionSwitchOver extends AbstractE2eTest {

    private static final String XSLT = textResource("hand_written_vpn.xsl");
    // Every kind of record the translation writes, so the catalogue read back from it is checked whole.
    private static final String SAMPLE = "["
            + "{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"}, "
            + "{\"ts\": \"2026-11-03T08:02:45.000Z\", \"action\": \"login\", \"user\": \"asmith\", "
            + "\"src_ip\": \"198.51.100.9\", \"host\": \"vpn-gw-1\", \"result\": \"failure\"}, "
            + "{\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\"}, "
            + "{\"ts\": \"2026-11-03T09:30:00.000Z\", \"action\": \"config_change\", \"user\": \"admin\", "
            + "\"host\": \"vpn-gw-1\", \"setting\": \"idle_timeout\", \"new_value\": \"900\"}]";
    private static final String RAW = "Raw Events";

    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private ProcessorService processorService;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private MetaService metaService;

    @Test
    void aSharedTranslationSelectingTwoFeedsByFeedTermsHandsOnlyTheBuildsFeedOver() {
        final Shared shared = shared("SHAREDVPN");
        final ProcessorFilter old = replaceFilter(shared, either(feedIs(shared.feedA()), feedIs(shared.feedB())));

        final Promoted promoted = promoteForA(shared, "sharedvpn-v1-1");

        final String steps = String.valueOf(promoted.result().get("next_steps"));
        assertThat(steps).contains("Remove " + shared.feedA().getName() + " from filter " + old.getId()
                                   + " of pipeline [" + shared.original() + "](",
                "?action=open-doc&docType=Pipeline&docUuid=" + shared.pipeline() + ") (its Processors tab); it "
                + "goes on selecting " + shared.feedB().getName());
        assertSwitchOver(promoted, old, shared);
        // The old filter as it was: enabled, selecting both feeds.
        assertThat(filter(old.getId()).isEnabled()).isTrue();
        assertThat(termsOf(filter(old.getId()))).containsExactly(
                "OR(Feed IS_DOC_REF " + shared.feedA().getName() + ", Feed IS_DOC_REF " + shared.feedB().getName()
                + ")", "Type EQUALS " + RAW);

        // The user removes the feed from the old filter (here, as Stroom's UI would leave it: a filter of B only).
        deleteFilter(old.getId());
        createFilter(shared.pipeline(), all(feedIs(shared.feedB()), type()), true);
        assertNoEnabledFilterThenOnce(promoted);
    }

    @Test
    void aSharedTranslationSelectingTwoFeedsByADictionaryHandsOverThroughTheDictionary() {
        final Shared shared = shared("DICTVPN");
        final DocRef dictionary = create(DictionaryDoc.TYPE, "DICTVPN feeds", "System/Feeds/DICTVPN").getDocRef();
        writeDictionary(dictionary, shared.feedA().getName() + "\n" + shared.feedB().getName() + "\n");
        final ProcessorFilter old = replaceFilter(shared, ExpressionTerm.builder().field(MetaFields.FIELD_FEED)
                .condition(Condition.IN_DICTIONARY).docRef(dictionary).build());

        final Promoted promoted = promoteForA(shared, "dictvpn-v1-1");

        final String steps = String.valueOf(promoted.result().get("next_steps"));
        assertThat(steps).contains("Remove " + shared.feedA().getName() + " from dictionary [DICTVPN feeds](",
                "?action=open-doc&docType=Dictionary&docUuid=" + dictionary.getUuid() + "), which filter "
                + old.getId() + " of pipeline '" + shared.original() + "' selects them by; nothing else uses it");
        assertSwitchOver(promoted, old, shared);
        assertThat(asUser(admin, () -> dictionaryStore.readDocument(dictionary)).getData())
                .as("the dictionary as it was")
                .contains(shared.feedA().getName());

        // The user removes the feed from the dictionary.
        writeDictionary(dictionary, shared.feedB().getName() + "\n");
        assertNoEnabledFilterThenOnce(promoted);
    }

    @Test
    void aFeedNoOldFilterSelectsIsReadWholeWhateverTimeTheNewVersionsFilterReadsFrom() {
        // Seen in eval case 104 on Sonnet: a dormant feed added to the new version beside the live one, the user
        // choosing streams from promotion on; the filter-wide bound left the dormant feed's history unread, so the
        // agent made a second version for it alone.
        final Shared shared = shared("DORMVPN");

        final Promoted promoted = promote(shared, "dormvpn-v1-1", List.of(shared.feedA().getName(),
                shared.feedB().getName()));

        final List<Map<String, Object>> made = list(promoted.result(), "processing_filters");
        assertThat(made).singleElement().satisfies(filter -> assertThat(filter)
                .containsEntry("reads_from_feeds", List.of(shared.feedA().getName()))
                .containsEntry("reads_whole", List.of(shared.feedB().getName())));
        final ProcessorFilter created = filter((int) number(made.getFirst().get("filter_id")));
        assertThat(created.getMinMetaCreateTimeMs()).as("no bound on the whole filter").isNull();
        final List<String> terms = termsOf(created);
        assertThat(terms).hasSize(2);
        assertThat(terms.getFirst()).startsWith("OR(AND(Feed IS_DOC_REF " + shared.feedA().getName()
                                                + ", Create Time GREATER_THAN_OR_EQUAL_TO ")
                .endsWith("), Feed IS_DOC_REF " + shared.feedB().getName() + ")");
        assertThat(terms.get(1)).isEqualTo("Type EQUALS " + RAW);
        // Stroom selects B's stream, made before promotion; not A's, which V1.0 processes.
        final List<String> selected = asUser(admin, () -> metaService.find(new FindMetaCriteria(
                created.getQueryData().getExpression())).getValues().stream()
                .map(Meta::getFeedName)
                .toList());
        assertThat(selected).containsExactly(shared.feedB().getName());
    }

    // What both forms of the old selection share: the new filter for A only, by reference, disabled, its switch-over
    // in order and linked; B stays with V1.0; A reported selected twice.
    private void assertSwitchOver(final Promoted promoted, final ProcessorFilter old, final Shared shared) {
        final Map<String, Object> review = promoted.review();
        assertThat(String.valueOf(review.get("switch_over"))).contains("filter " + old.getId() + " of pipeline '"
                                                                       + shared.original() + "'",
                shared.feedA().getName(), "which moves", shared.feedB().getName() + " stays with it");
        assertThat(String.valueOf(review.get("earlier_versions_keep"))).contains("'" + shared.original()
                                                                                 + "' keeps filter " + old.getId()
                                                                                 + " (" + shared.feedB().getName()
                                                                                 + ")");
        assertThat(String.valueOf(review.get("feed_streams"))).contains(shared.feedA().getName() + " (" + RAW
                                                                        + "): 1 stream");
        final List<Map<String, Object>> made = list(promoted.result(), "processing_filters");
        assertThat(made).singleElement().satisfies(filter -> assertThat(filter)
                .containsEntry("feed", shared.feedA().getName())
                .containsEntry("enabled", false)
                .containsKey("switch_over"));
        final ProcessorFilter created = filter((int) number(made.getFirst().get("filter_id")));
        assertThat(created.isEnabled()).isFalse();
        // By reference, the build's feed only (B stays with V1.0).
        assertThat(termsOf(created)).containsExactly("Feed IS_DOC_REF "
                                                                            + shared.feedA().getName(),
                "Type EQUALS " + RAW);
        final String steps = String.valueOf(promoted.result().get("next_steps"));
        assertThat(steps).contains("has been drafted and is disabled", "Let filter " + old.getId()
                                                                       + "'s in-flight tasks finish",
                "Enable the new filter " + created.getId(), "'" + shared.original() + "' stays for the feeds this "
                                                            + "build doesn't move: filter " + old.getId() + " ("
                                                            + shared.feedB().getName() + ")")
                .doesNotContain("disables or removes");
        // The buttons: open the old selection to change, then enable the new filter after it.
        final List<Map<String, Object>> actions = list(promoted.result(), "user_actions");
        assertThat(actions).extracting(action -> action.get("action")).containsExactly("open_document",
                "enable_processor_filter");
        assertThat(String.valueOf(actions.getLast().get("after"))).contains("in-flight tasks have finished");
        assertThat(String.valueOf(promoted.result().get("feed_selection"))).contains(shared.feedA().getName()
                                                                                    + " (" + RAW + ") is selected "
                                                                                    + "twice");
        final Map<String, Object> status = json("build_status", args("build", promoted.build()));
        dump("build_status after promotion", status);
        assertThat(status).containsEntry("status", "promoted");
        assertThat(String.valueOf(status.get("feed_selection"))).contains(shared.feedA().getName() + " (" + RAW
                                                                          + ") is selected twice");
    }

    // Once the old selection no longer has A: selected by no enabled filter until the new one is enabled; then once.
    private void assertNoEnabledFilterThenOnce(final Promoted promoted) {
        final int created = (int) number(list(promoted.result(), "processing_filters").getFirst().get("filter_id"));
        final Map<String, Object> changed = json("build_status", args("build", promoted.build()));
        dump("build_status once changed", changed);
        assertThat(String.valueOf(changed.get("feed_selection"))).contains("is selected by no enabled filter")
                .doesNotContain("selected twice");
        asUser(admin, () -> processorFilterService.setEnabled(created, true));
        final Map<String, Object> switched = json("build_status", args("build", promoted.build()));
        assertThat(String.valueOf(switched.get("feed_selection"))).contains("selected by one pipeline's enabled "
                                                                            + "filter");
    }

    // A hand-written translation V1.0 of feed A, and a feed B beside it with a stream of its own.
    private Shared shared(final String name) {
        final String folder = "System/Feeds/" + name;
        final String feedA = name + "-A";
        final String feedB = name + "-B";
        final String original = name + "-Events";
        final HandWritten written = handWritten(folder, feedA, XSLT, original, "Event Data (JSON)",
                List.of(PipelineProperty.builder().element("jsonParser").name("addRootObject")
                        .value(new PipelinePropertyValue(false)).build()), RAW, SAMPLE);
        create(FeedDoc.TYPE, feedB, folder);
        storeParts(feedB, List.of(Map.entry(Map.of("Feed", feedB), SAMPLE)));
        return new Shared(written.pipeline(), original, feed(folder, feedA), feed(folder, feedB));
    }

    // A build of V1.1 for feed A only, promoted: the review and the result.
    private Promoted promoteForA(final Shared shared, final String build) {
        return promote(shared, build, List.of(shared.feedA().getName()));
    }

    // A build of V1.1 for the feeds given, promoted: the review and the result. Started from V1.0 as the user
    // chooses after the survey (copy_pipeline waits for the choice), copied, its XSLT read back and the catalogue
    // read back confirmed, as promotion asks of a copy of a hand-written translation.
    private Promoted promote(final Shared shared, final String build, final List<String> feeds) {
        json("start_build", args("build", build, "feeds", feeds));
        feeds.forEach(feed -> json("survey_feed", args("feed", feed, "build", build)));
        startFromExisting(build);
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", build, "source_uuid",
                shared.pipeline(), "new_name", shared.original() + "-V1.1"));
        assertThat(uuidOf(copy)).isNotNull();
        final String copyXslt = list(copy, "copied_documents").stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .map(doc -> (String) doc.get("uuid"))
                .findFirst()
                .orElseThrow();
        final List<Long> raws = new ArrayList<>();
        feeds.forEach(feed -> raws.addAll(streamIdsOf(feed)));
        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", copyXslt, "pipeline_uuid",
                uuidOf(copy), "stream_ids", raws, "change", "Read back from the hand-written translation"));
        assertThat(rebuilt.get("saved")).as("read back: %s", rebuilt).isNotNull();
        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", raws));
        // The copy started the build from V1.0: the catalogue is the one read back from it, its TypeIds kept.
        assertThat(String.valueOf(listed.get("origin"))).startsWith("4 event types from '" + shared.original()
                                                                    + "'");
        confirmedCatalogue(build);
        final String folder = folderOf(shared.pipeline()).substring("System/".length());
        final Map<String, Object> destinations = Map.of("XSLT", folder, "Pipeline", folder);
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        dump("promote_build review", review);
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> result = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build", result);
        assertThat(result).as("promoted: %s", result).containsKey("promoted");
        return new Promoted(build, review, result);
    }

    // V1.0's filter replaced by one selecting as given (with its Type term), enabled.
    private ProcessorFilter replaceFilter(final Shared shared, final ExpressionItem feeds) {
        for (final ProcessorFilter filter : filtersOf(shared.pipeline())) {
            deleteFilter(filter.getId());
        }
        return createFilter(shared.pipeline(), all(feeds, type()), true);
    }

    private ProcessorFilter createFilter(final String pipeline,
                                         final ExpressionOperator expression,
                                         final boolean enabled) {
        return asUser(admin, () -> {
            final ProcessorFilter filter = processorFilterService.create(CreateProcessFilterRequest.builder()
                    .processorType(ProcessorType.PIPELINE)
                    .pipeline(new DocRef(PipelineDoc.TYPE, pipeline))
                    .queryData(QueryData.builder().dataSource(MetaFields.STREAM_STORE_DOC_REF).expression(expression)
                            .build())
                    .enabled(enabled)
                    .priority(10)
                    .maxProcessingTasks(2)
                    .build());
            processorService.setEnabled(filter.getProcessor().getId(), true);
            return filter;
        });
    }

    private void deleteFilter(final int id) {
        asUser(admin, () -> processorFilterService.delete(id));
    }

    private List<ProcessorFilter> filtersOf(final String pipeline) {
        return asUser(admin, () -> processorFilterService.find(new DocRef(PipelineDoc.TYPE, pipeline)).getValues()
                .stream()
                .filter(filter -> !filter.isDeleted())
                .toList());
    }

    private ProcessorFilter filter(final int id) {
        return asUser(admin, () -> processorFilterService.fetch(id).orElseThrow());
    }

    private void writeDictionary(final DocRef dictionary, final String lines) {
        asUser(admin, () -> dictionaryStore.writeDocument(dictionaryStore.readDocument(dictionary).copy()
                .data(lines).build()));
    }

    private DocRef feed(final String folder, final String name) {
        return documentsIn(folder, FeedDoc.TYPE, name).getFirst().getDocRef();
    }

    // A filter's top-level conditions in words, e.g. `Feed IS_DOC_REF ACME` or `OR(Feed IS_DOC_REF A, ...)`.
    private static List<String> termsOf(final ProcessorFilter filter) {
        return TermsSaid.of(filter.getQueryData().getExpression());
    }

    private static ExpressionTerm feedIs(final DocRef feed) {
        return ExpressionTerm.builder().field(MetaFields.FIELD_FEED).condition(Condition.IS_DOC_REF).docRef(feed)
                .build();
    }

    private static ExpressionTerm type() {
        return ExpressionTerm.builder().field(MetaFields.FIELD_TYPE).condition(Condition.EQUALS).value(RAW).build();
    }

    private static ExpressionOperator all(final ExpressionItem... items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        for (final ExpressionItem item : items) {
            builder.addItem(item);
        }
        return builder.build();
    }

    private static ExpressionOperator either(final ExpressionItem... items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        for (final ExpressionItem item : items) {
            builder.addItem(item);
        }
        return builder.build();
    }


    // --------------------------------------------------------------------------------


    // Conditions in words, as processing_status shows them.
    private static final class TermsSaid {

        private TermsSaid() {
            // Static only.
        }

        static List<String> of(final ExpressionOperator expression) {
            final List<String> out = new ArrayList<>();
            for (final ExpressionItem child : expression.getChildren()) {
                if (child instanceof final ExpressionOperator operator) {
                    out.add(operator.op().name() + "(" + String.join(", ", of(operator)) + ")");
                } else if (child instanceof final ExpressionTerm term) {
                    out.add(term.getField() + " " + term.getCondition().name() + " " + (term.getValue() != null
                            ? term.getValue()
                            : term.getDocRef().getName()));
                }
            }
            return out;
        }
    }

    // The shared translation: V1.0's pipeline, its name, and its two feeds.
    private record Shared(String pipeline, String original, DocRef feedA, DocRef feedB) {

    }

    // A promotion: the build, its review, its result.
    private record Promoted(String build, Map<String, Object> review, Map<String, Object> result) {

    }
}
