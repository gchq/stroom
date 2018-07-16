/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.servlet;

import stroom.properties.api.PropertyService;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class DynamicCSSServlet extends HttpServlet {
    public static final String THEME_BACKGROUND_ATTACHMENT = "@THEME_BACKGROUND_ATTACHMENT@";
    public static final String THEME_BACKGROUND_COLOR = "@THEME_BACKGROUND_COLOR@";
    public static final String THEME_BACKGROUND_IMAGE = "@THEME_BACKGROUND_IMAGE@";
    public static final String THEME_BACKGROUND_POSITION = "@THEME_BACKGROUND_POSITION@";
    public static final String THEME_BACKGROUND_REPEAT = "@THEME_BACKGROUND_REPEAT@";
    public static final String THEME_BACKGROUND_OPACITY = "@THEME_BACKGROUND_OPACITY@";
    public static final String THEME_TUBE_VISIBLE = "@THEME_TUBE_VISIBLE@";
    public static final String THEME_TUBE_OPACITY = "@THEME_TUBE_OPACITY@";
    private static final long serialVersionUID = 1L;
    private static final String STROOM_THEME_BACKGROUND_ATTACHMENT = "stroom.theme.background-attachment";
    private static final String STROOM_THEME_BACKGROUND_COLOR = "stroom.theme.background-color";
    private static final String STROOM_THEME_BACKGROUND_IMAGE = "stroom.theme.background-image";
    private static final String STROOM_THEME_BACKGROUND_POSITION = "stroom.theme.background-position";
    private static final String STROOM_THEME_BACKGROUND_REPEAT = "stroom.theme.background-repeat";
    private static final String STROOM_THEME_BACKGROUND_OPACITY = "stroom.theme.background-opacity";
    private static final String STROOM_THEME_TUBE_VISIBLE = "stroom.theme.tube.visible";
    private static final String STROOM_THEME_TUBE_OPACITY = "stroom.theme.tube.opacity";

    private final transient PropertyService stroomPropertyService;
    private String cssTemplate;

    @Inject
    DynamicCSSServlet(final PropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    public String getCssTemplate() {
        if (cssTemplate == null) {
            final InputStream is = getClass().getResourceAsStream("dynamic.css");
            cssTemplate = StreamUtil.streamToString(is);
            CloseableUtil.closeLogAndIgnoreException(is);
        }
        return cssTemplate;

    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final PrintWriter pw = response.getWriter();
        response.setContentType("text/css");

        String css = getCssTemplate();

        css = replaceAll(css, THEME_BACKGROUND_ATTACHMENT, STROOM_THEME_BACKGROUND_ATTACHMENT);

        css = replaceAll(css, THEME_BACKGROUND_COLOR, STROOM_THEME_BACKGROUND_COLOR);

        css = replaceAll(css, THEME_BACKGROUND_IMAGE, STROOM_THEME_BACKGROUND_IMAGE);

        css = replaceAll(css, THEME_BACKGROUND_POSITION, STROOM_THEME_BACKGROUND_POSITION);

        css = replaceAll(css, THEME_BACKGROUND_REPEAT, STROOM_THEME_BACKGROUND_REPEAT);

        css = replaceAll(css, THEME_BACKGROUND_OPACITY, STROOM_THEME_BACKGROUND_OPACITY);

        css = replaceAll(css, THEME_TUBE_OPACITY, STROOM_THEME_TUBE_OPACITY);

        css = replaceAll(css, THEME_TUBE_VISIBLE, STROOM_THEME_TUBE_VISIBLE);

        pw.write(css);
        pw.close();

    }

    private String replaceAll(final String css, final String sourcePattern, final String key) {
        final String value = stroomPropertyService.getProperty(key);
        if (value != null) {
            return css.replace(sourcePattern, value);
        }
        return css;
    }
}
