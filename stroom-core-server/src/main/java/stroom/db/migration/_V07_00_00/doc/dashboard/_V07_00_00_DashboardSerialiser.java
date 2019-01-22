package stroom.db.migration._V07_00_00.doc.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.migration._V07_00_00.doc._V07_00_00_EncodingUtil;
import stroom.db.migration._V07_00_00.doc._V07_00_00_JsonSerialiser2;
import stroom.entity.util.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public class _V07_00_00_DashboardSerialiser extends _V07_00_00_JsonSerialiser2<_V07_00_00_DashboardDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(_V07_00_00_DashboardSerialiser.class);

    private static final String XML = "xml";
    private static final String JSON = "json";

    private final ObjectMapper mapper;

    public _V07_00_00_DashboardSerialiser() {
        super(_V07_00_00_DashboardDoc.class);
        mapper = getMapper(true);
    }

    @Override
    public _V07_00_00_DashboardDoc read(final Map<String, byte[]> data) throws IOException {
        final _V07_00_00_DashboardDoc document = super.read(data);

        // Deal with old XML format data.
        final String xml = _V07_00_00_EncodingUtil.asString(data.get(XML));
        if (xml != null) {
            document.setDashboardConfig(getDashboardConfigFromLegacyXML(xml));
        }

        final String json = _V07_00_00_EncodingUtil.asString(data.get(JSON));
        if (json != null) {
            try {
                final _V07_00_00_DashboardConfig dashboardConfig = mapper.readValue(new StringReader(json), _V07_00_00_DashboardConfig.class);
                document.setDashboardConfig(dashboardConfig);
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return document;
    }

    @Override
    public Map<String, byte[]> write(final _V07_00_00_DashboardDoc document) throws IOException {
        final Map<String, byte[]> data = super.write(document);

        _V07_00_00_DashboardConfig dashboardConfig = document.getDashboardConfig();
        if (dashboardConfig != null) {
            final StringWriter stringWriter = new StringWriter();
            mapper.writeValue(stringWriter, dashboardConfig);
            data.put(JSON, _V07_00_00_EncodingUtil.asBytes(stringWriter.toString()));
        }
        return data;
    }

    public _V07_00_00_DashboardConfig getDashboardConfigFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_DashboardConfig.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_DashboardConfig.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal dashboard config", e);
            }
        }

        return null;
    }
}