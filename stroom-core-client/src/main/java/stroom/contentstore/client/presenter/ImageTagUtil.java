package stroom.contentstore.client.presenter;

import com.google.gwt.http.client.URL;

/**
 * Provides a place for HTML image tag utilities for the Content Store.
 */
public class ImageTagUtil {

    /**
     * Returns the image tag for an icon with the given ID.
     */
    public static String getImageTag(final int height,
                                     final int width,
                                     final String id) {
        final String encodedId = URL.encode(id);
        return "<img height=\"" + height + "\" width=\"" + width
               + "\" src=\"/iconPassThrough?" + encodedId + "\">";
    }

}
