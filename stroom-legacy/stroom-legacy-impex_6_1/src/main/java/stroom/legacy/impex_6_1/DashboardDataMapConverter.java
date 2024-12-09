package stroom.legacy.impex_6_1;

import stroom.dashboard.impl.DashboardSerialiser;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.legacy.model_6_1.Dashboard;
import stroom.legacy.model_6_1.DashboardMarshaller;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Singleton
@Deprecated
class DashboardDataMapConverter implements DataMapConverter {

    private final DashboardSerialiser serialiser;

    @Inject
    DashboardDataMapConverter(final DashboardSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportSettings importSettings,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            try {
                Dashboard oldDashboard = new Dashboard();
                LegacyXmlSerialiser.performImport(oldDashboard, dataMap);
                final DashboardMarshaller marshaller = new DashboardMarshaller();
                oldDashboard = marshaller.unmarshal(oldDashboard);

                final DashboardDoc document = new DashboardDoc();
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
}
