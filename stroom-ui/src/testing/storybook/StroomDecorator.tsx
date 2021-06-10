import * as React from "react";
import { pipe } from "ramda";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";
import StoryRouter from "storybook-react-router";
import ReactModal from "react-modal";

import { useTestServer } from "./PollyDecorator";

import setupFontAwesome from "lib/setupFontAwesome";
import testData from "../data";
import { ThemeContextProvider } from "lib/useTheme/useTheme";
import { withRouter, RouteComponentProps } from "react-router";
import { CustomRouter } from "lib/useRouter";
import { DocumentTreeContextProvider } from "components/DocumentEditors/api/explorer";
import { ErrorReportingContextProvider } from "components/ErrorPage";

import "styles/main.scss";
import { FunctionComponent, ReactElement } from "react";
import { PromptDisplayBoundary } from "../../components/Prompt/PromptDisplayBoundary";
setupFontAwesome();

const WithTestServer: FunctionComponent = ({ children }) => {
  useTestServer(testData);
  return <React.Fragment>{children}</React.Fragment>;
};

const RouteWrapper: FunctionComponent<RouteComponentProps> = ({
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

export default (storyFn: () => ReactElement): ReactElement =>
  StoryRouter()(() => (
    <ErrorReportingContextProvider>
      <PromptDisplayBoundary>
        <ThemeContextProvider>
          <DragDropRouted>
            <DocumentTreeContextProvider>
              {storyFn()}
            </DocumentTreeContextProvider>
          </DragDropRouted>
        </ThemeContextProvider>
      </PromptDisplayBoundary>
    </ErrorReportingContextProvider>
  ));
