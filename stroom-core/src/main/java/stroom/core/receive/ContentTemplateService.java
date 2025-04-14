package stroom.core.receive;

import stroom.datasource.api.v2.QueryField;
import stroom.receive.content.shared.ContentTemplates;

import java.util.Set;

public interface ContentTemplateService {

    ContentTemplates fetch();

    ContentTemplates update(final ContentTemplates contentTemplates);

    Set<QueryField> fetchFields();
}
