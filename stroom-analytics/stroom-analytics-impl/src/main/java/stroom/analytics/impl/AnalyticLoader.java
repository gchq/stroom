package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.rule.impl.ReportStore;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class AnalyticLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticLoader.class);

    private final AnalyticRuleStore analyticRuleStore;
    private final ReportStore reportStore;

    @Inject
    public AnalyticLoader(final AnalyticRuleStore analyticRuleStore,
                          final ReportStore reportStore) {
        this.analyticRuleStore = analyticRuleStore;
        this.reportStore = reportStore;
    }

    public List<AbstractAnalyticRuleDoc> loadAll() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  so the caller can filter half of them out by type.
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<AbstractAnalyticRuleDoc> currentRules = new ArrayList<>();
        List<DocRef> docRefs = analyticRuleStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    currentRules.add(analyticRuleDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        docRefs = reportStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final ReportDoc reportDoc = reportStore.readDocument(docRef);
                if (reportDoc != null) {
                    currentRules.add(reportDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentRules;
    }

    public AbstractAnalyticRuleDoc load(final DocRef docRef) {
        if (AnalyticRuleDoc.TYPE.equals(docRef.getType())) {
            return analyticRuleStore.readDocument(docRef);
        } else if (ReportDoc.TYPE.equals(docRef.getType())) {
            return reportStore.readDocument(docRef);
        } else {
            throw new RuntimeException("Unexpected type: " + docRef.getType());
        }
    }

    public void disableProcess(final AbstractAnalyticRuleDoc doc) {
        final AnalyticProcessConfig analyticProcessConfig = doc.getAnalyticProcessConfig();
        if (analyticProcessConfig instanceof
                final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
            TableBuilderAnalyticProcessConfig updatedProcessConfig = tableBuilderAnalyticProcessConfig
                    .copy()
                    .enabled(false)
                    .build();
            if (doc instanceof final AnalyticRuleDoc analyticRuleDoc) {
                final AnalyticRuleDoc modified = analyticRuleDoc
                        .copy()
                        .analyticProcessConfig(updatedProcessConfig)
                        .build();
                analyticRuleStore.writeDocument(modified);
            } else if (doc instanceof final ReportDoc reportDoc) {
                final ReportDoc modified = reportDoc
                        .copy()
                        .analyticProcessConfig(updatedProcessConfig)
                        .build();
                reportStore.writeDocument(modified);
            }
        }
    }
}
