import * as React from "react";
import { pipe } from "ramda";

import StoryRouter from "storybook-react-router";
import ReactModal from "react-modal";

import { useTestServer } from "./PollyDecorator";
import testData from "../data";
import { withRouter, RouteComponentProps } from "react-router";
import { AuthenticationContext } from "startup/Authentication";

import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import { Routes } from "components/AppChrome";
import setupFontAwesome from "lib/setupFontAwesome";

import { ThemeContextProvider } from "lib/useTheme/useTheme";
import { CustomRouter } from "lib/useRouter";

import { createBrowserHistory as createHistory } from "history";
import ConfigProvider from "startup/config/ConfigProvider";

import "styles/main.scss";
import { AuthorisationContextProvider } from "startup/Authorisation";
import { DocumentTreeContextProvider } from "components/DocumentEditors/api/explorer";
import { ErrorReportingContextProvider } from "components/ErrorPage";

export const history = createHistory();

const DndRoutes = DragDropContext(HTML5Backend)(Routes);

setupFontAwesome();

const WithTestServer: React.FunctionComponent = ({ children }) => {
  useTestServer(testData);

  return <div>{children}</div>;
};

const RouteWrapper: React.StatelessComponent<RouteComponentProps> = ({
  children,
  history,
}) => {
  return (
    <CustomRouter history={history}>
      <WithTestServer>{children}</WithTestServer>
    </CustomRouter>
  );
};
const DragDropRouted = pipe(
  DragDropContext(HTML5Backend),
  withRouter,
)(RouteWrapper);

ReactModal.setAppElement("#root");

setupFontAwesome();

export default (storyFn: any) =>
  StoryRouter()(() => (
    <ErrorReportingContextProvider>
      <ConfigProvider>
        <AuthenticationContext.Provider
          value={{
            idToken: "PollyWannaCracker",
            setIdToken: () => {
              console.error(
                "Setting the idToken in storybook? This is most unexpected!",
              );
            },
          }}
        >
          <AuthorisationContextProvider>
            <ThemeContextProvider>
              <DragDropRouted>
                <DocumentTreeContextProvider>
                  {storyFn()}
                </DocumentTreeContextProvider>
              </DragDropRouted>
            </ThemeContextProvider>
          </AuthorisationContextProvider>
        </AuthenticationContext.Provider>
      </ConfigProvider>
    </ErrorReportingContextProvider>
  ));
