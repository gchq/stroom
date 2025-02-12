package stroom.core.receive;

import stroom.receive.content.ContentTemplates;

public interface ContentTemplateService {

    ContentTemplates fetch();

    ContentTemplates update(final ContentTemplates contentTemplates);

}
