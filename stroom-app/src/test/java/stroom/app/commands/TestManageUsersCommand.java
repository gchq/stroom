/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.app.commands;


import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestManageUsersCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestManageUsersCommand.class);

    private static final String CREATE_USER_ARG_NAME = "createUser";
    private static final String CREATE_GROUP_ARG_NAME = "createGroup";
    private static final String ADD_TO_GROUP_ARG_NAME = "addToGroup";
    private static final String REMOVE_FROM_GROUP_ARG_NAME = "removeFromGroup";
    private static final String GRANT_PERMISSION = "grantPermission";
    private static final String REVOKE_PERMISSION = "revokePermission";
    private static final String USER_META_VAR = "USER_ID";
    private static final String GROUP_META_VAR = "GROUP_ID";
    private static final String USER_OR_GROUP_META_VAR = "USER_OR_GROUP_ID";
    private static final String TARGET_GROUP_META_VAR = "TARGET_GROUP_ID";
    private static final String PERMISSION_NAME_META_VAR = "PERMISSION_NAME";

    @Test
    void test() throws ArgumentParserException {
        final ArgumentParser parser = ArgumentParsers.newFor("test").build()
                .description("My test");

        final Subparsers subparsers = parser.addSubparsers();

        final Subparser subparser = subparsers.addParser("manageUsers");

        subparser.addArgument(asArg(CREATE_USER_ARG_NAME))
                .dest(CREATE_USER_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(1)
                .metavar(USER_META_VAR)
                .help("The id of the user to create");

        subparser.addArgument(asArg(CREATE_GROUP_ARG_NAME))
                .dest(CREATE_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(1)
                .metavar(GROUP_META_VAR)
                .help("The id of the group to create");

        subparser.addArgument(asArg(ADD_TO_GROUP_ARG_NAME))
                .dest(ADD_TO_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being added and the group to add it to");

        subparser.addArgument(asArg(REMOVE_FROM_GROUP_ARG_NAME))
                .dest(REMOVE_FROM_GROUP_ARG_NAME)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .required(false)
                .metavar(USER_OR_GROUP_META_VAR, TARGET_GROUP_META_VAR)
                .help("The name of the user/group being removed and the group it is being removed from");

        subparser.addArgument(asArg(GRANT_PERMISSION))
                .dest(GRANT_PERMISSION)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to grant to it");

        subparser.addArgument(asArg(REVOKE_PERMISSION))
                .dest(REVOKE_PERMISSION)
                .action(Arguments.append())
                .type(String.class)
                .nargs(2)
                .metavar(USER_OR_GROUP_META_VAR, PERMISSION_NAME_META_VAR)
                .help("The name of the user/group and the name of the application permission to revoke from it");

        LOGGER.info("help\n{}", subparser.formatHelp());

        final String[] args = new String[]{
                "manageUsers",
                asArg(CREATE_USER_ARG_NAME),
                "jbloggs",
                asArg(ADD_TO_GROUP_ARG_NAME),
                "jbloggs",
                "Administrators"
//                asArg(ADD_TO_GROUP_ARG_NAME),
//                "jdoe",
//                "DeadPeople"
        };
        LOGGER.info("Command: {}", String.join(" ", args));

        final Namespace namespace = parser.parseArgs(args);

        LOGGER.info("Namespace: {}", namespace);
    }

    private String asArg(final String name) {
        return "--" + name;
    }
}
