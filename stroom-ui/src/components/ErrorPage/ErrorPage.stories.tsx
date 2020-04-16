/*
 * Copyright 2018 Crown Copyright
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

import * as React from "react";
import { storiesOf } from "@storybook/react";

import ErrorPage from "./ErrorPage";
import { useErrorReporting } from ".";
import { SingleError } from "./types";

const testErrorMessage = "Everything is a disaster";
const testStackTrace = `Invariant Violation: Objects are not valid as a React child (found: object with keys {sdfs}). If you meant to render a collection of children, use an array instead.
in code (created by ErrorPage)
in p (created by ErrorPage)
in div (created by ErrorPage)
in div (created by ErrorPage)
in div (created by ErrorPage)
in ErrorPage (created by Connect(ErrorPage))
in Connect(ErrorPage)
in div
in TestInitialisationDecorator (created by Connect(TestInitialisationDecorator))
in Connect(TestInitialisationDecorator)
in Provider
at invariant (http://localhost:9001/static/preview.bundle.js:3417:15)
at throwOnInvalidObjectType (http://localhost:9001/static/preview.bundle.js:35515:5)
at reconcileChildFibers (http://localhost:9001/static/preview.bundle.js:36282:7)
at reconcileChildrenAtExpirationTime (http://localhost:9001/static/preview.bundle.js:36393:30)
at reconcileChildren (http://localhost:9001/static/preview.bundle.js:36384:5)
at updateHostComponent (http://localhost:9001/static/preview.bundle.js:36692:5)
at beginWork (http://localhost:9001/static/preview.bundle.js:37139:16)
at performUnitOfWork (http://localhost:9001/static/preview.bundle.js:39967:16)
at workLoop (http://localhost:9001/static/preview.bundle.js:39996:26)
at renderRoot (http://localhost:9001/static/preview.bundle.js:40027:9)`;
const testHttpErrorCode = 501;

const TestErrorPage = ({
  errorMessage,
  stackTrace,
  httpErrorCode,
}: SingleError) => {
  const { reportError } = useErrorReporting();
  React.useEffect(() => {
    reportError({
      errorMessage,
      stackTrace,
      httpErrorCode,
    });
  }, [reportError, errorMessage, stackTrace, httpErrorCode]);

  return <ErrorPage />;
};

storiesOf("Sections/ErrorPage", module)
  .add("No details", () => <TestErrorPage />)
  .add("Just error message", () => (
    <TestErrorPage errorMessage={testErrorMessage} />
  ))
  .add("Error message and stack trace", () => (
    <TestErrorPage
      errorMessage={testErrorMessage}
      stackTrace={testStackTrace}
    />
  ))
  .add("Everything", () => (
    <TestErrorPage
      errorMessage={testErrorMessage}
      stackTrace={testStackTrace}
      httpErrorCode={testHttpErrorCode}
    />
  ));
