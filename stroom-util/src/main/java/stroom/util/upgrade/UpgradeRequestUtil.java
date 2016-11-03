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

package stroom.util.upgrade;

import stroom.util.date.DateUtil;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;

public class UpgradeRequestUtil {
    public static final String UPGRADE_HASH = "upgradeHash";
    public static final String UPGRADE_ACTION = "upgradeAction";
    public static final String ACTION_CONTINUE = "Continue";
    public static final String ACTION_SKIP = "Skip";
    public static final String ACTION_ABORT = "Abort";

//    public static void service(final ServletRequest request, final ServletResponse response,
//            final UpgradeHandler upgradeHandler) throws ServletException, IOException {
//        final String upgradeHash = request.getParameter(UPGRADE_HASH);
//        if (upgradeHash != null) {
//            final UpgradeMessage confirmUpgradeMessage = upgradeHandler.getConfirmUpgradeMessage();
//
//            if (confirmUpgradeMessage != null && upgradeHash.equals(String.valueOf(confirmUpgradeMessage.hashCode()))) {
//                final String action = request.getParameter(UPGRADE_ACTION);
//
//                if (ACTION_CONTINUE.equals(action)) {
//                    upgradeHandler.doConfirmUpgradeAction(UpgradeAction.CONTINUE);
//                }
//                if (ACTION_SKIP.equals(action)) {
//                    upgradeHandler.doConfirmUpgradeAction(UpgradeAction.SKIP);
//                }
//                if (ACTION_ABORT.equals(action)) {
//                    upgradeHandler.doConfirmUpgradeAction(UpgradeAction.ABORT);
//                }
//            }
//
//        }
//
//        final List<UpgradeMessage> upgradeMessageList = upgradeHandler.getUpgradeMessageList();
//
//        response.setContentType("text/html");
//
//        final PrintWriter printWriter = response.getWriter();
//
//        writeHtmlHead(printWriter);
//
//        printWriter.println("<h1>" + upgradeHandler.getUpgradeMessage() + "</h1>");
//        printWriter.println("<table>");
//        for (final UpgradeMessage upgradeMessage : upgradeMessageList) {
//            printWriter.println("<tr>");
//            printWriter.println("<td class=\"datetime\">"
//                    + DateUtil.createNormalDateTimeString(upgradeMessage.getTimeMs()) + "</td>");
//            printWriter.println("<td class=\"messagePrefix\">" + upgradeMessage.getMessagePrefix() + "</td>");
//            printWriter.println("<td class=\"message\">" + upgradeMessage.getMessage() + "</td>");
//            if (upgradeMessage.getUpgradeAction() != null) {
//                if (upgradeMessage.getUpgradeAction() == UpgradeAction.CONTINUE) {
//                    printWriter.println("<td class=\"action\">Continue</td>");
//                }
//                if (upgradeMessage.getUpgradeAction() == UpgradeAction.SKIP) {
//                    printWriter.println("<td class=\"action\">Skip</td>");
//                }
//                if (upgradeMessage.getUpgradeAction() == UpgradeAction.ABORT) {
//                    printWriter.println("<td class=\"action\">Abort</td>");
//                }
//            }
//            printWriter.println("</tr>");
//        }
//        printWriter.println("</table>");
//
//        final UpgradeMessage confirmUpgradeMessage = upgradeHandler.getConfirmUpgradeMessage();
//        if (confirmUpgradeMessage != null) {
//            printWriter.println("<p>" + confirmUpgradeMessage.getMessage() + "</p>");
//            printWriter.println("<form>");
//            printWriter.println("<input type=\"hidden\" name=\"" + UPGRADE_HASH + "\" value=\""
//                    + confirmUpgradeMessage.hashCode() + "\"/>");
//
//            @SuppressWarnings("unchecked")
//            final Enumeration<String> existingValueNames = request.getParameterNames();
//            while (existingValueNames.hasMoreElements()) {
//                final String name = existingValueNames.nextElement();
//                if (!UPGRADE_HASH.equals(name) && !UPGRADE_ACTION.equals(name)) {
//                    final String[] values = request.getParameterValues(name);
//                    if (values != null) {
//                        for (final String value : values) {
//                            printWriter
//                                    .println("<input type=\"hidden\" name=\"" + name + "\" value=\"" + value + "\"/>");
//
//                        }
//                    }
//                }
//
//            }
//
//            printWriter.println(
//                    "<input type=\"submit\" name=\"" + UPGRADE_ACTION + "\" value=\"" + ACTION_CONTINUE + "\"/>");
//            printWriter
//                    .println("<input type=\"submit\" name=\"" + UPGRADE_ACTION + "\" value=\"" + ACTION_SKIP + "\"/>");
//            printWriter
//                    .println("<input type=\"submit\" name=\"" + UPGRADE_ACTION + "\" value=\"" + ACTION_ABORT + "\"/>");
//            printWriter.println("</form>");
//        }
//
//        writeHtmlFooter(printWriter);
//
//        printWriter.close();
//    }

    public static void writeHtmlFooter(final PrintWriter printWriter) {
        printWriter.println("</body>");
        printWriter.println("</html>");
    }

    public static void writeHtmlHead(final PrintWriter printWriter) {
        printWriter.println("<html>");
        printWriter.println("<head>");
        printWriter.println("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">");
        printWriter.println("<title>Stroom</title>");
        printWriter.println("<META HTTP-EQUIV=Refresh CONTENT=\"2\"/>");
        printWriter.println("<style>");
        printWriter.println("body { font-family:arial,tahoma,verdana;color:white;background-color:black;}");
        printWriter.println(".datetime { width:160px; font-size: 10pt;  }");
        printWriter.println(".messagePrefix { width:150px; font-size: 10pt; }");
        printWriter.println(".message {	width:500px; font-size: 10pt; }");
        printWriter.println(".action { width: 100px; font-size: 10pt;  }");
        printWriter.println("</style>");
        printWriter.println("<body>");
    }
}
