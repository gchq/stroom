package stroom.core.receive;

import stroom.query.api.datasource.QueryField;
import stroom.receive.content.shared.ContentTemplates;

import java.util.Set;

public interface ContentTemplateService {

    ContentTemplates fetch();

    ContentTemplates update(final ContentTemplates contentTemplates);

    Set<QueryField> fetchFields();
}
