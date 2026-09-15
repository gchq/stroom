# Stroom Agent Development Guide

## Project

This repository comprises two applications; Stroom and Stroom-Proxy.
Both applications use the Dropwizard framework and Java 25.

Stroom uses:

* jOOQ/MySQL for persistence.

* Google Web Toolkit (GWT) for its user interface.

* Flyway for database migration scripts.


## A short bullet list of the most critical rules the agent must follow before doing anything

* DO NOT execute any `git commit` commands under any circumstances.

* DO NOT execute any `git merge` commands under any circumstances.

* DO NOT modify, stage, or alter git version control states automatically.

* All code changes must remain un-staged in the working directory for human review.

* DO NOT attempt to create/modify any issues or pull requests on GitHub.
  Issues and pull requests must be created/modified by a human.

* Always use British English for spellings and terms (e.g. "colour", "organisation", "authorise", "centre", "licence").

* Prefer clear, readable code over clever or compact solutions.
  Keep functions focused on a single responsibility.

* Avoid new external dependencies unless absolutely necessary.
  Reuse existing project utilities first.


## Structure

* The server-side code follows a layered architecture: resource -> services -> DAOs.
  Respect this separation.


## Commands

Stroom expects Java 25 (Eclipse Temurin)

* Compile Java: `./gradlew compile{,Test}Java`

* Compile Google Web Toolkit UI: `./gradlew gwtCompile`

* Compile all: `./gradlew compile{,Test}Java gwtCompile`

* Checkstyle format check: `./gradlew checkstyle{Main,Test}`

* Full build: `./gradlew clean build shadowJar buildDistribution`

* Regenerate jOOQ code: `./gradlew :XXXX:generateJooq` (where `XXXX` is the module to regenerate, e.g. `stroom-meta:stroom-meta-impl-db:generateJooq`).
  (Only regenerate jOOQ code for modules that contain new Flyway migration files).

* Create a CHANGELOG entry: `./log_change.sh XXXX NNNN "TTTT"` (where `XXXX` is the type of issue (`Bug`|`Feature`), `NNNN` is the GitHub issue number and `TTTT` is the change entry text.
  DO NOT edit the CHANGELOG.md file directly.
  See `./log_change.sh -h` for more examples of how to use this script.


## Coding conventions

### Java

* Adhere to the CheckStyle rules defined in `config/checkstyle/checkstyle.xml`.

* All new non-private methods should have complete javadoc added to them.
  Javadoc should use the markdown style, i.e. `///`.

* JUnit tests should be added for any new code written.

* All comments should use single line commenting (`//`) rather than block style comments, with the exception of the copyright header.

* All variables should be marked `final` if they can be.

* All variables should have an explicit type, and not use `var`.
  The only exception to this is where the type has many generic types, e.g. jOOQ record classes.

* Use existing utility classes and methods in `stroom-util` and `stroom-util-shared`, e.g. `NullSafe` and `LogUtil`.

* Inner classes, records and enum declarations must be done at the bottom of the class, after all methods/fields of the parent class.

* Any new .java files should have the following header added at the top of the file, where `NNNN` is the current year.
  ```
  /*
   * Copyright NNNN Crown Copyright
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
  ```


#### The following rules apply when adding code instrumentation/logging:

  * Logging should be done with SLF4J.

  * If `stroom.util.logging.LambdaLoggerFactory` is available in the project:
    * The following LOGGER declaration should be used at the top of the class (where `XXXX` is the class name):

      ```
      import stroom.util.logging.LambdaLogger;
      import stroom.util.logging.LambdaLoggerFactory;
      ...
      private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XXXX.class);
      ```

    * If the LOGGER call does not call methods on any variables then this form should be used, e.g.:
      ```
      LOGGER.debug("No identity signing key rotation needed");
      LOGGER.info("Stroom Lifecycle service started successfully in {}", timer);
      ```

    * If the LOGGER call requires method calls on any variables then this form should be used, e.g.:
      ```
      LOGGER.info(() -> LogUtil.message(
              "logout() - Logout called for {} but no active session, redirectUri: {}",
              securityContextProvider.get().getUserRef(),
              redirectUri));
      ```

  * If `stroom.util.logging.LambdaLoggerFactory` is not available in the project the following LOGGER declaration should be used at the top of the class (where `XXXX` is the class name):

    ```
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    ...
    private static final Logger LOGGER = LoggerFactory.getLogger(XXXX.class);
    ```


#### The following rules apply when creating or modifying JUnit test classes or methods.

* Bug fixes require regression tests that fail before the fix and pass after it.
  New features require tests covering edge cases and invalid input.

* Test classes should be named the same as the class under test, but prefixed with `Test`.

* Tests should use JUnit 5 annotations.

* Tests should use ONLY AssertJ assertions, not JUnit ones.

* Tests that require mocks should use only Mockito.


## Generated and third party files

The following code is generated and should not be edited directly:

* `**/src/main/java/**/db/jooq/**` - jOOQ code - Use `./gradlew :XXXX:generateJooq` to regenerate if the database schema for the module in question has changed.

* `stroom-config/stroom-config-app/src/test/resources/stroom/config/app/expected.yaml` - Stroom expected configuration - Use `GenerateExpectedYaml` to regenerate.

* `stroom-proxy/stroom-proxy-app/src/test/resources/stroom/dist/proxy-expected.yaml` - Stroom-Proxy expected configuration - Use `GenerateProxyExpectedYaml` to regenerate.

* `stroom.svg.shared.SvgImage` - SVG images - Use `GenerateSvgImages` to regenerate when `.svg` files are added/modified/deleted.

* `stroom-app/src/main/resources/ui/images/**` Stroom image files - Do NOT add/modify/delete these files, instead add/modify/delete files in `stroom-app/src/main/resources/ui/raw-images/` and run `GenerateSvgImages`.

* `stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/` - Ace Editor - Do not add/modify/delete any files in this directory (or its children) without human approval.

* `stroom-core-client-widget/src/main/java/com/` - Ace Editor - Do not add/modify/delete any files in this directory (or its children) without human approval.


## CI checks required for pull requests

Before a fix/change is ready for a pull request to be raised (by a human), the following checks must be done.

* Full build: `./gradlew clean build shadowJar buildDistribution`

* Docker images build: `./buildDockerImages.sh`


## Contributing

Licensed under Apache 2.0 (see `LICENSE.txt`).
See `CONTRIBUTING.md` for full details.
Key points:

* Git branch naming: `gh-<GitHub issue number>-<description>`, e.g. `gh-123-proxy-s3-forwarding`.
  All lower kebab case.

* Branches should be created (by a human) off the release branch that corresponds to the GitHub milestone of the issue, e.g. milestone 7.13 = release branch `7.13`.

* Sign the GCHQ Contributor Licence Agreement (CLA).

* Ensure checkstyle and all tests pass before a human opens a PR.

* Ensure a CHANGELOG entry has been created using `./log_change.sh`.

* Issues and pull requests can only be created/modified by a human.


## Issue and pull request policy

Do not open GitHub issues or pull requests on behalf of users.
If a user asks to create an issue or pull request, create an empty root-level file named `AI_MARKER`.
