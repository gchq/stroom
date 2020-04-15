import * as React from "react";

import { storiesOf } from "@storybook/react";
import useAuthorisationContext from "./useAuthorisationContext";

import fullTestData from "testing/data";
import JsonDebug from "testing/JsonDebug";

interface Props {
  permissionName: string;
}

const TestHarness: React.FunctionComponent<Props> = ({ permissionName }) => {
  const permissions = useAuthorisationContext([permissionName]);

  return (
    <div>
      <h2>Permission Name {permissionName}</h2>
      <p>Does the user have this permission?</p>
      <p>{permissions[permissionName] ? "YES" : "NO"}</p>
      <JsonDebug value={{ permissionName, permissions }} />
    </div>
  );
};

storiesOf("startup/useAuthorisationContext", module)
  .add("Valid", () => (
    <TestHarness permissionName={fullTestData.allAppPermissions[0]} />
  ))
  .add("Invalid", () => <TestHarness permissionName="Mooostafa" />);
