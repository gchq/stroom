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

import stroom.ui.config.shared.ThemeConfig;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class DynamicCSSServlet extends HttpServlet {
    private static final String THEME_BACKGROUND_ATTACHMENT = "@THEME_BACKGROUND_ATTACHMENT@";
    private static final String THEME_BACKGROUND_COLOR = "@THEME_BACKGROUND_COLOR@";
    private static final String THEME_BACKGROUND_IMAGE = "@THEME_BACKGROUND_IMAGE@";
    private static final String THEME_BACKGROUND_POSITION = "@THEME_BACKGROUND_POSITION@";
    private static final String THEME_BACKGROUND_REPEAT = "@THEME_BACKGROUND_REPEAT@";
    private static final String THEME_BACKGROUND_OPACITY = "@THEME_BACKGROUND_OPACITY@";
    private static final String THEME_TUBE_VISIBLE = "@THEME_TUBE_VISIBLE@";
    private static final String THEME_TUBE_OPACITY = "@THEME_TUBE_OPACITY@";

    private final transient ThemeConfig themeConfig;
    private String cssTemplate;

    @Inject
    DynamicCSSServlet(final ThemeConfig themeConfig) {
        this.themeConfig = themeConfig;
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
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final PrintWriter pw = response.getWriter();
        response.setContentType("text/css");

        String css = getCssTemplate();

        css = replaceAll(css, THEME_BACKGROUND_ATTACHMENT, themeConfig.getBackgroundAttachment());

        css = replaceAll(css, THEME_BACKGROUND_COLOR, themeConfig.getBackgroundColor());

        css = replaceAll(css, THEME_BACKGROUND_IMAGE, themeConfig.getBackgroundImage());

        css = replaceAll(css, THEME_BACKGROUND_POSITION, themeConfig.getBackgroundPosition());

        css = replaceAll(css, THEME_BACKGROUND_REPEAT, themeConfig.getBackgroundRepeat());

        css = replaceAll(css, THEME_BACKGROUND_OPACITY, themeConfig.getBackgroundOpacity());

        css = replaceAll(css, THEME_TUBE_OPACITY, themeConfig.getTubeOpacity());

        css = replaceAll(css, THEME_TUBE_VISIBLE, themeConfig.getTubeVisible());

        pw.write(css);
        pw.close();

    }

    private String replaceAll(final String css, final String sourcePattern, final String value) {
        if (value != null) {
            return css.replace(sourcePattern, value);
        }
        return css;
    }
}
