import * as React from "react";
import { storiesOf } from "@storybook/react";
import { Switch, Route, RouteComponentProps } from "react-router";

import useAppNavigation from "./useAppNavigation";

interface Props {
  routing: RouteComponentProps<any>;
}

const TEST_NAV_STRINGS = ["one", "two", "three"];

const TestNavigated: React.FunctionComponent<Props> = ({ routing }) => {
  const { nav: appNav } = useAppNavigation();

  return (
    <div>
      <h1>App Navigation Demo</h1>
      <p>Location: {routing.location.pathname}</p>
      {Object.entries(appNav)
        .map((k) => ({
          name: k[0],
          navigationFn: k[1],
        }))
        .map(({ name, navigationFn }) => (
          <div key={name}>
            <button onClick={() => navigationFn(...TEST_NAV_STRINGS)}>
              {name}
            </button>
          </div>
        ))}
    </div>
  );
};

const TestHarness: React.FunctionComponent = () => (
  <Switch>
    <Route
      render={(props: RouteComponentProps<any>) => (
        <TestNavigated routing={props} />
      )}
    />
  </Switch>
);

storiesOf("lib/useAppNavigation", module).add("test", () => <TestHarness />);
