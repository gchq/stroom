package stroom.security.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermission {

    @Test
    void test() {
        higher(DocumentPermission.OWNER, DocumentPermission.DELETE);
        higher(DocumentPermission.DELETE, DocumentPermission.EDIT);
        higher(DocumentPermission.EDIT, DocumentPermission.VIEW);
        higher(DocumentPermission.VIEW, DocumentPermission.USE);
    }

    private void higher(final DocumentPermission permission1, final DocumentPermission permission2) {
        assertThat(permission1.isEqualOrHigher(permission2)).isTrue();
    }
}
