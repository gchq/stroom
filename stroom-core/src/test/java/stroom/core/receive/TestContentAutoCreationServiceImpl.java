package stroom.core.receive;

import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestContentAutoCreationServiceImpl {

    @Mock
    private AutoContentCreationConfig mockAutoContentCreationConfig;

    @Mock
    private ContentTemplateStore mockContentTemplateStore;

    @Test
    void testFeedAlreadyExists() {

        new ContentAutoCreationServiceImpl()
        final ContentAutoCreationService kcontentTemplateService = new ContentTemplateServiceImpl(
                new MockSecurityContext(),
                mockContentTemplateStore,
                mockAutoContentCreationConfig);

        contentTemplateService.tr

    }
}
