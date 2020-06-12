import * as React from "react";
import * as ReactDOM from "react-dom";

import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import { Routes } from "components/AppChrome";
import setupFontAwesome from "./lib/setupFontAwesome";

import { ThemeContextProvider } from "./lib/useTheme/useTheme";
import { CustomRouter } from "./lib/useRouter";

import { createBrowserHistory as createHistory } from "history";
import ConfigProvider from "startup/config/ConfigProvider";

import "./styles/main.scss";
import { AuthorisationContextProvider } from "startup/Authorisation";
import { AuthenticationContextProvider } from "startup/Authentication";
import { DocumentTreeContextProvider } from "components/DocumentEditors/api/explorer";
import { ErrorReportingContextProvider } from "components/ErrorPage";

export const history = createHistory();

const DndRoutes = DragDropContext(HTML5Backend)(Routes);

setupFontAwesome();

const App: React.FunctionComponent = () => (
  <ErrorReportingContextProvider>
    <ConfigProvider>
      <AuthenticationContextProvider>
        <AuthorisationContextProvider>
          <ThemeContextProvider>
            <CustomRouter history={history}>
              <DocumentTreeContextProvider>
                <DndRoutes />
              </DocumentTreeContextProvider>
            </CustomRouter>
          </ThemeContextProvider>
        </AuthorisationContextProvider>
      </AuthenticationContextProvider>
    </ConfigProvider>
  </ErrorReportingContextProvider>
);

ReactDOM.render(<App />, document.getElementById("root") as HTMLElement);
