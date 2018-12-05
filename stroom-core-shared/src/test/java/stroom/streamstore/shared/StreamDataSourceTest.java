package stroom.streamstore.shared;

import org.hibernate.annotations.common.util.impl.LoggerFactory;
import org.jboss.logging.Logger;
import org.junit.Test;
import stroom.datasource.api.v2.DataSourceField;

import java.util.List;

public class StreamDataSourceTest {
    private static final Logger LOGGER = LoggerFactory.logger(StreamDataSourceTest.class);

    @Test
    public void testFieldOrder() {
        List<DataSourceField> fields =  StreamDataSource.getExtendedFields();

        LOGGER.info(String.format("Fields %d", fields.size()));

        // Fatal Error Count - 3 words....sigh
        fields.forEach(f -> LOGGER.info(f.getName()));
    }
}
