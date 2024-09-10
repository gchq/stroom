package stroom.legacy.db.migration;

import stroom.legacy.impex_6_1.MappingUtil;
import stroom.processor.impl.db.QueryDataXMLSerialiser;
import stroom.util.xml.XMLMarshallerUtil;

import jakarta.xml.bind.JAXBContext;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

@Deprecated
public class V07_00_00_1906__processor_filter extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_1906__processor_filter.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final JAXBContext jaxbContext = JAXBContext.newInstance(stroom.legacy.model_6_1.QueryData.class);
        final QueryDataXMLSerialiser queryDataXMLSerialiser = new QueryDataXMLSerialiser();

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement(
                "SELECT " +
                        "  id, " +
                        "  data " +
                        "FROM processor_filter")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        final int id = resultSet.getInt(1);
                        final String data = resultSet.getString(2);

                        if (data != null) {
                            final stroom.legacy.model_6_1.QueryData queryData = XMLMarshallerUtil.unmarshal(jaxbContext,
                                    stroom.legacy.model_6_1.QueryData.class,
                                    data);
                            final stroom.processor.shared.QueryData mapped = MappingUtil.map(queryData);
                            final String xml = queryDataXMLSerialiser.serialise(mapped);

                            if (!Objects.equals(data, xml)) {
                                // Update the record.
                                try (final PreparedStatement ps = context.getConnection().prepareStatement(
                                        "UPDATE processor_filter SET " +
                                                "  data = ? " +
                                                "WHERE id = ?")) {
                                    ps.setString(1, xml);
                                    ps.setInt(2, id);
                                    ps.executeUpdate();
                                } catch (final SQLException e) {
                                    throw new RuntimeException(e.getMessage(), e);
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
