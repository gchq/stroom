package stroom.analytics.impl;

import stroom.analytics.api.AlertDefinition;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.CompiledFields;
import stroom.search.extraction.ExtractionStateHolder;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class MultiValuesReceiverFactory {

    private final Provider<ExtractionStateHolder> extractionStateHolderProvider;
    private final Provider<AlertWriter> alertWriterProvider;
    private final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory;

    @Inject
    public MultiValuesReceiverFactory(final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                                      final Provider<AlertWriter> alertWriterProvider,
                                      final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory) {
        this.extractionStateHolderProvider = extractionStateHolderProvider;
        this.alertWriterProvider = alertWriterProvider;
        this.aggregateRuleValuesConsumerFactory = aggregateRuleValuesConsumerFactory;
    }

    public MultiValuesConsumer create(final RuleConfig ruleConfig,
                                      final RecordConsumer recordConsumer) {
        final FieldIndex fieldIndexMap = FieldIndex.forFields();
        final List<Field> fields = ruleConfig.getAlertDefinitions().stream().map(a -> a.getTableSettings().getFields())
                .reduce(new ArrayList<>(), (a, b) -> {
                    a.addAll(b);
                    return a;
                });
        CompiledFields.create(fields, fieldIndexMap, ruleConfig.getParams());

        final List<ValuesConsumer> subConsumers = new ArrayList<>();
        final List<AlertDefinition> eventAlertDefinitions = new ArrayList<>();
        if (ruleConfig instanceof DashboardRuleConfig) {
            for (AlertDefinition alertDefinition : ruleConfig.getAlertDefinitions()) {
                if (!alertDefinition.isDisabled()) {
                    eventAlertDefinitions.add(alertDefinition);
                }
            }
        }

//        else if (ruleConfig instanceof AlertRuleConfig) {
//            final AlertRuleConfig alertRuleConfig = (AlertRuleConfig) ruleConfig;
//            if (AlertRuleType.EVENT.equals(alertRuleConfig.getAlertRuleDoc().getAlertRuleType())) {
//                for (AlertDefinition alertDefinition : ruleConfig.getAlertDefinitions()) {
//                    if (!alertDefinition.isDisabled()) {
//                        eventAlertDefinitions.add(alertDefinition);
//                    }
//                }
//            } else {
//                // Add a receiver that will put data into a result store.
//                subConsumers.add(aggregateRuleValuesConsumerFactory.create(alertRuleConfig));
//            }
//        }

        // If we have some alerts that only care about single events then create a handler for these.
        if (eventAlertDefinitions.size() > 0) {
            final AlertWriter alertWriter = alertWriterProvider.get();
            alertWriter.setAlertDefinitions(eventAlertDefinitions);
            alertWriter.setParamMapForAlerting(ruleConfig.getParams());
            alertWriter.setFieldIndex(fieldIndexMap);
            alertWriter.setRecordConsumer(recordConsumer);
            subConsumers.add(alertWriter);
        }

        final MultiValuesConsumer receiver = new MultiValuesConsumer(subConsumers);
        final ExtractionStateHolder extractionStateHolder = extractionStateHolderProvider.get();
        extractionStateHolder.setFieldIndex(fieldIndexMap);
        extractionStateHolder.setQueryKey(new QueryKey("alert"));
        extractionStateHolder.setReceiver(receiver);

        return receiver;
    }
}
