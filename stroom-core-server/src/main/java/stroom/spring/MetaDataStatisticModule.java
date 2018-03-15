package stroom.spring;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.internalstatistics.MetaDataStatisticImpl;
import stroom.internalstatistics.MetaDataStatisticTemplate;
import stroom.statistics.internal.InternalStatisticsReceiver;

import javax.inject.Provider;
import java.util.Arrays;

public class MetaDataStatisticModule extends AbstractModule {
    /**
     * This bean must be returned as a class and not an interface otherwise annotation scanning will not work.
     */
    @Provides
    public MetaDataStatistic metaDataStatistic(final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider) {
        final MetaDataStatisticImpl metaDataStatistic = new MetaDataStatisticImpl(internalStatisticsReceiverProvider);
        metaDataStatistic.setTemplates(Arrays.asList(
                new MetaDataStatisticTemplate(
                        "Meta Data-Streams Received",
                        "receivedTime",
                        Arrays.asList("Feed")),
                new MetaDataStatisticTemplate(
                        "Meta Data-Stream Size",
                        "receivedTime",
                        "StreamSize",
                        Arrays.asList("Feed"))));
        return metaDataStatistic;
    }

}
