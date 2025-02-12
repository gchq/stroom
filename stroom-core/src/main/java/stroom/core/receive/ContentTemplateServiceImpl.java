package stroom.core.receive;

import stroom.receive.content.ContentTemplates;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;

import jakarta.inject.Inject;

public class ContentTemplateServiceImpl implements ContentTemplateService {

    private final SecurityContext securityContext;
    private final ContentTemplateStore contentTemplateStore;

    @Inject
    public ContentTemplateServiceImpl(final SecurityContext securityContext,
                                      final ContentTemplateStore contentTemplateStore) {
        this.securityContext = securityContext;
        this.contentTemplateStore = contentTemplateStore;
    }

    @Override
    public ContentTemplates fetch() {
        return securityContext.secureResult(
                AppPermission.MANAGE_CONTENT_TEMPLATES_PERMISSION,
                contentTemplateStore::getOrCreate);
    }

    @Override
    public ContentTemplates update(final ContentTemplates contentTemplates) {
        return securityContext.secureResult(
                AppPermission.MANAGE_CONTENT_TEMPLATES_PERMISSION,
                () -> contentTemplateStore.writeDocument(contentTemplates));
    }
}
