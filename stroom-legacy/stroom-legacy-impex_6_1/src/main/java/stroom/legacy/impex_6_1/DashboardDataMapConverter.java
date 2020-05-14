package stroom.legacy.impex_6_1;

import stroom.dashboard.impl.DashboardSerialiser;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Dashboard;
import stroom.legacy.model_6_1.DashboardMarshaller;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

class DashboardDataMapConverter implements DataMapConverter {
//    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardDataMapConverter.class);

    private final DashboardSerialiser serialiser;

    @Inject
    DashboardDataMapConverter(final DashboardSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            try {
                Dashboard oldDashboard = new Dashboard();
                LegacyXmlSerialiser.performImport(oldDashboard, dataMap);
                final DashboardMarshaller marshaller = new DashboardMarshaller();
                oldDashboard = marshaller.unmarshal(oldDashboard);

                final DashboardDoc document = new DashboardDoc();
                document.setType(docRef.getType());
                document.setUuid(docRef.getUuid());
                document.setName(docRef.getName());
                document.setVersion(UUID.randomUUID().toString());
                document.setCreateTimeMs(oldDashboard.getCreateTime());
                document.setUpdateTimeMs(oldDashboard.getUpdateTime());
                document.setCreateUser(oldDashboard.getCreateUser());
                document.setUpdateUser(oldDashboard.getUpdateUser());

                document.setDashboardConfig(MappingUtil.map(oldDashboard.getDashboardData()));

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

//    public stroom.legacy.model_6_1.DashboardConfig getDashboardConfigFromLegacyXML(final String xml) {
//        if (xml != null) {
//            try {
//                final JAXBContext jaxbContext = JAXBContext.newInstance(stroom.legacy.model_6_1.DashboardConfig.class);
//                return XMLMarshallerUtil.unmarshal(jaxbContext, stroom.legacy.model_6_1.DashboardConfig.class, xml);
//            } catch (final JAXBException | RuntimeException e) {
//                LOGGER.error("Unable to unmarshal dashboard config", e);
//            }
//        }
//
//        return null;
//    }
}
