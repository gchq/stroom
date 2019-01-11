package stroom.dashboard.client.table;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableCell {
    @Test
    void testRender() {
        final String value = "Here we [Have](http//some-url/First){browser} several [Links](http//some-url/First){browser} for a [User](http//some-url/First) to click [On](http//some-url/First){browser} OK";
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        TableCell.append(value, builder);
        assertThat(builder.toSafeHtml().asString()).isEqualTo("Here we <u link=\"[Have](http//some-url/First){browser}\">Have</u> several <u link=\"[Links](http//some-url/First){browser}\">Links</u> for a <u link=\"[User](http//some-url/First)\">User</u> to click <u link=\"[On](http//some-url/First){browser}\">On</u> OK");
    }
}
