package stroom.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docstore.EncodingUtil;
import stroom.docstore.JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class DashboardSerialiser extends JsonSerialiser2<DashboardDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardSerialiser.class);

    private static final String XML = "xml";
    private static final String JSON = "json";

    private final ObjectMapper mapper;

    public DashboardSerialiser() {
        super(DashboardDoc.class);
        mapper = getMapper(true);
    }

    @Override
    public DashboardDoc read(final Map<String, byte[]> data) throws IOException {
        final DashboardDoc document = super.read(data);

        // Deal with old XML format data.
        final String xml = EncodingUtil.asString(data.get(XML));
        if (xml != null) {
            document.setDashboardConfig(getDashboardConfigFromLegacyXML(xml));
        }

        final String json = EncodingUtil.asString(data.get(JSON));
        if (json != null) {
            try {
                final DashboardConfig dashboardConfig = mapper.readValue(new StringReader(json), DashboardConfig.class);
                document.setDashboardConfig(dashboardConfig);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return document;
    }

    @Override
    public Map<String, byte[]> write(final DashboardDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        DashboardConfig dashboardConfig = document.getDashboardConfig();
        if (dashboardConfig != null) {
            final StringWriter stringWriter = new StringWriter();
            mapper.writeValue(stringWriter, dashboardConfig);
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