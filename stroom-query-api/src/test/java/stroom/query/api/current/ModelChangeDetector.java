package stroom.query.api.current;

import org.junit.Test;
import stroom.query.api.SearchRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ModelChangeDetector {

    private static final String SEARCH_REQUEST_PORTRAIT_FILE = "src/test/resources/searchRequestPortrait.txt";

    @Test
    public void detect_changes_in_SearchRequest() throws IOException {
        String newPortrait = ClassPhotographer.takePortraitOf(SearchRequest.class, "stroom.query.api");
        // Uncomment this line to update the model portrait
//        Files.write(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE), newPortrait.getBytes());

        String existingPortrait = new String(Files.readAllBytes(Paths.get(SEARCH_REQUEST_PORTRAIT_FILE)));
        assertThat(existingPortrait, equalTo(newPortrait));
        System.out.println(newPortrait);
    }


}
