# A short bullet list of the most critical rules the agent must follow before doing anything

* DO NOT execute any `git commit` commands under any circumstances.

* DO NOT execute any `git merge` commands under any circumstances.

* DO NOT modify, stage, or alter git version control states automatically.

* All code changes must remain un-staged in the working directory for human review.

* DO NOT attempt to create/modify any issues or pull requests on GitHub.


# The following rules apply to Java code

* All new non-private methods should have complete markdown style javadoc added to them.

* Junit tests should be added for any new code written unless it is UI layer code.

* All comments should use single line commenting (`//`) rather than block style comments.

* ALL variables should be marked `final` if they can be.

* All variables should have an explicit type, and not use `var`.

* Method chaining should be done on one line per method, even if they will fit on one line, e.g.
  ```
  final Set<Object> columnNames = DSL.using(txnConfig)
          .fetch(listColsSql, BUILD_VERSION_TABLE_NAME)
          .stream()
          .map(record -> record.get(0))
          .collect(Collectors.toSet());
  ```

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

## The following rules apply when adding code logging:

  * Logging should be done with SLF4J.

  * If `stroom.util.logging.LambdaLoggerFactory` is available in the project:
    * The following LOGGER declaration should be used at the top of the class:

      ```
      import stroom.util.logging.LambdaLogger;
      import stroom.util.logging.LambdaLoggerFactory;
      ...
      private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticAsyncSearchTaskHandler.class);
      ```
    * If the LOGGER call does not call methods on any variables then this form should be used:
      ```
      LOGGER.debug("No identity signing key rotation needed");
      LOGGER.info("Stroom Lifecycle service started successfully in {}", timer);
      ```
    * If the LOGGER call requires method calls on any variables then this form should be used:
      ```
      LOGGER.info(() -> LogUtil.message(
              "logout() - Logout called for {} but no active session, redirectUri: {}",
              securityContextProvider.get().getUserRef(),
              redirectUri));
      ```

  * If `stroom.util.logging.LambdaLoggerFactory` is not available in the project the following LOGGER declaration should be used at the top of the class:

    ```
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    ...
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticAsyncSearchTaskHandler.class);
    ```

## The following rules apply when creating or modifying Junit test classes or methods.

  * Test classes should be name the same as the class under test, but prefixed with `Test`.

  * Tests should use Junit 5 annotations.

  * Tests should use only AssertJ assertions, not Junit ones.

  * Tests that require mocks should use only Mockito.


