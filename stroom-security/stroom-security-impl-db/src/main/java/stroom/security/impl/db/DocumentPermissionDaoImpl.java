package stroom.security.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;

import javax.inject.Inject;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final Table<Record> TABLE = table("doc_permission");
    private static final Field<ULong> FIELD_ID = field("id", ULong.class);
    private static final Field<ULong> FIELD_VERSION = field("version", ULong.class);
    private static final Field<String> FIELD_USER_UUID = field("user_uid", String.class);
    private static final Field<String> FIELD_DOC_TYPE = field("doc_type", String.class);
    private static final Field<String> FIELD_DOC_UUID = field("doc_uuid", String.class);
    private static final Field<String> FIELD_PERMISSION = field("permission", String.class);

    private String userUuid;
    private String docType;
    private String docUuid;
    private String permission;

    @Inject
    public DocumentPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public DocumentPermissionJooq getPermissionsForDocument(DocRef document) {
        return null;
    }

    @Override
    public void addPermission(String userUuid, DocRef document, String permission) {

    }

    @Override
    public void removePermission(String userUuid, DocRef document, String permission) {

    }

    @Override
    public void clearDocumentPermissions(DocRef document) {

    }

    @Override
    public void clearUserPermissions(String userUuid) {

    }
}
