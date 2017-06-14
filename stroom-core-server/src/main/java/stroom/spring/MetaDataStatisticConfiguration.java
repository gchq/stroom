package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.internalstatistics.MetaDataStatisticImpl;
import stroom.internalstatistics.MetaDataStatisticTemplate;

import java.util.Arrays;

@Configuration
public class MetaDataStatisticConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaDataStatisticConfiguration.class);

    public MetaDataStatisticConfiguration() {
        LOGGER.info("MetaDataStatisticConfiguration loading...");
    }

    /**
     * This bean must be returned as a class and not an interface otherwise annotation scanning will not work.
     */
    @Bean
    public MetaDataStatisticImpl metaDataStatistic() {
        final MetaDataStatisticImpl metaDataStatistic = new MetaDataStatisticImpl();
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
