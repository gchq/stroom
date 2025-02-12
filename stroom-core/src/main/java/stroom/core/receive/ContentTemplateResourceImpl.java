package stroom.core.receive;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.receive.content.ContentTemplates;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
public class ContentTemplateResourceImpl implements ContentTemplateResource {

    private final Provider<ContentTemplateService> contentTemplateServiceProvider;

    @Inject
    public ContentTemplateResourceImpl(final Provider<ContentTemplateService> contentTemplateServiceProvider) {
        this.contentTemplateServiceProvider = contentTemplateServiceProvider;
    }

    @Override
    public ContentTemplates fetch() {
        return contentTemplateServiceProvider.get().fetch();
    }

    @Override
    public ContentTemplates update(final ContentTemplates contentTemplates) {
        return contentTemplateServiceProvider.get().update(contentTemplates);
    }
}
