import * as React from "react";
import { storiesOf } from "@storybook/react";
import EditTokenForm from "./EditTokenForm";
import { action } from "@storybook/addon-actions";
import { Token } from "../api/types";
import moment from "moment";

const token: Token = {
  id: 1,
  version: 1,
  createTimeMs: moment().milliseconds(),
  updateTimeMs: moment().milliseconds(),
  createUser: "test user",
  updateUser: "test user",
  userId: "userId",
  tokenType: "api",
  data: "token string",
  expiresOnMs: moment().milliseconds(),
  comments: "Test",
  enabled: true,
};

storiesOf("Tokens", module).add("Edit", () => (
  <EditTokenForm
    onChangeState={action("onChangeState")}
    onBack={action("onBack")}
    token={token}
  />
));
