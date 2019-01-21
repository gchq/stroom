package stroom.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docstore.DocumentSerialiser2;
import stroom.docstore.Serialiser2;
import stroom.docstore.Serialiser2Factory;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.util.string.EncodingUtil;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class DashboardSerialiser implements DocumentSerialiser2<DashboardDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardSerialiser.class);

    private static final String XML = "xml";
    private static final String JSON = "json";

    private final Serialiser2<DashboardDoc> delegate;
    private final Serialiser2<DashboardConfig> dashboardConfigSerialiser;

    @Inject
    public DashboardSerialiser(final Serialiser2Factory serialiser2Factory) {
        this.delegate = serialiser2Factory.createSerialiser(DashboardDoc.class);
        this.dashboardConfigSerialiser = serialiser2Factory.createSerialiser(DashboardConfig.class);
    }

    @Override
    public DashboardDoc read(final Map<String, byte[]> data) throws IOException {
        final DashboardDoc document = delegate.read(data);

        // Deal with old XML format data.
        final String xml = EncodingUtil.asString(data.get(XML));
        if (xml != null) {
            document.setDashboardConfig(getDashboardConfigFromLegacyXML(xml));
        }

        final byte[] jsonData = data.get(JSON);
        if (jsonData != null) {
            try {
                final DashboardConfig dashboardConfig = dashboardConfigSerialiser.read(jsonData);
                document.setDashboardConfig(dashboardConfig);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return document;
    }

    @Override
    public Map<String, byte[]> write(final DashboardDoc document) throws IOException {
        final Map<String, byte[]> data = delegate.write(document);

        DashboardConfig dashboardConfig = document.getDashboardConfig();
        if (dashboardConfig != null) {
            final StringWriter stringWriter = new StringWriter();
            dashboardConfigSerialiser.write(stringWriter, dashboardConfig);
            data.put(JSON, EncodingUtil.asBytes(stringWriter.toString()));
        }
        return data;
    }

    public DashboardConfig getDashboardConfigFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(DashboardConfig.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, DashboardConfig.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}