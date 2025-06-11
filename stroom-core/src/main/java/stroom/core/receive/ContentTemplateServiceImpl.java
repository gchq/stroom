package stroom.core.receive;

import stroom.query.api.datasource.QueryField;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ContentTemplateServiceImpl implements ContentTemplateService {

    private final SecurityContext securityContext;
    private final ContentTemplateStore contentTemplateStore;
    private final AutoContentCreationConfig autoContentCreationConfig;

    @Inject
    public ContentTemplateServiceImpl(final SecurityContext securityContext,
                                      final ContentTemplateStore contentTemplateStore,
                                      final AutoContentCreationConfig autoContentCreationConfig) {
        this.securityContext = securityContext;
        this.contentTemplateStore = contentTemplateStore;
        this.autoContentCreationConfig = autoContentCreationConfig;
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
                () -> {
                    validateContentTemplates(contentTemplates);
                    return contentTemplateStore.writeDocument(contentTemplates);
                });
    }

    private void validateContentTemplates(final ContentTemplates contentTemplates) {
        Objects.requireNonNull(contentTemplates);
        final Set<String> names = new HashSet<>();
        for (final ContentTemplate contentTemplate : contentTemplates.getContentTemplates()) {
            if (NullSafe.isBlankString(contentTemplate.getName())) {
                throw new RuntimeException(LogUtil.message(
                        "Content template with number {} has a blank name.", contentTemplate.getTemplateNumber()));
            }
            if (!names.add(contentTemplate.getName())) {
                throw new RuntimeException(LogUtil.message(
                        "Content template with number {} has a duplicate name.", contentTemplate.getTemplateNumber()));
            }
        }
    }

    @Override
    public Set<QueryField> fetchFields() {
        return securityContext.secureResult(() ->
                NullSafe.stream(autoContentCreationConfig.getTemplateMatchFields())
                        .map(QueryField::createText)
                        .collect(Collectors.toSet()));
    }
}
