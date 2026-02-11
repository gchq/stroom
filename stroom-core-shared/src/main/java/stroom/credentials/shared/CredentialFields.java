package stroom.credentials.shared;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;

public interface CredentialFields {

    String CREDENTIAL_UUID = "Uuid";
    String CREDENTIAL_CREATED_ON = "CreatedOn";
    String CREDENTIAL_CREATED_BY = "CreatedBy";
    String CREDENTIAL_UPDATED_ON = "UpdatedOn";
    String CREDENTIAL_UPDATED_BY = "UpdatedBy";
    String CREDENTIAL_NAME = "Name";
    String CREDENTIAL_TYPE = "Type";

    QueryField CREDENTIAL_UUID_FIELD = QueryField.createText(CREDENTIAL_UUID);
    QueryField CREDENTIAL_CREATED_ON_FIELD = QueryField.createDate(CREDENTIAL_CREATED_ON);
    QueryField CREDENTIAL_CREATED_BY_FIELD = QueryField.createText(CREDENTIAL_CREATED_BY);
    QueryField CREDENTIAL_UPDATED_ON_FIELD = QueryField.createDate(CREDENTIAL_UPDATED_ON);
    QueryField CREDENTIAL_UPDATED_BY_FIELD = QueryField.createText(CREDENTIAL_UPDATED_BY);
    QueryField CREDENTIAL_NAME_FIELD = QueryField.createText(CREDENTIAL_NAME);
    QueryField CREDENTIAL_TYPE_FIELD = QueryField.createText(CREDENTIAL_TYPE);
    List<QueryField> FIELDS = Arrays.asList(
            CREDENTIAL_UUID_FIELD,
            CREDENTIAL_CREATED_ON_FIELD,
            CREDENTIAL_CREATED_BY_FIELD,
            CREDENTIAL_UPDATED_ON_FIELD,
            CREDENTIAL_UPDATED_BY_FIELD,
            CREDENTIAL_NAME_FIELD,
            CREDENTIAL_TYPE_FIELD);
}
