package stroom.dashboard.client.table;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class TestTableCell {

    @Ignore // This will never work as it relies on gwt javascript that is not available in a unit test
    @Test
    public void testRender() {
        final String value = "Here we [Have](http//some-url/First){browser} several [Links](http//some-url/First){browser} for a [User](http//some-url/First) to click [On](http//some-url/First){browser} OK";
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        TableCell.append(value, builder);
        Assert.assertEquals("Here we <u link=\"[Have](http//some-url/First){browser}\">Have</u> several <u link=\"[Links](http//some-url/First){browser}\">Links</u> for a <u link=\"[User](http//some-url/First)\">User</u> to click <u link=\"[On](http//some-url/First){browser}\">On</u> OK", builder.toSafeHtml().asString());
    }
}
