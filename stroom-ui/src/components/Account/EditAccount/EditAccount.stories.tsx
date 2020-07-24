import { storiesOf } from "@storybook/react";
import * as React from "react";
import { EditAccountFormik } from "./EditAccount";
import { Dialog } from "components/Dialog/Dialog";

storiesOf("Account", module).add("Edit Account", () => (
  <Dialog>
    <EditAccountFormik
      initialValues={{}}
      onSubmit={() => undefined}
      onClose={() => undefined}
      onPasswordChange={() => undefined}
    />
  </Dialog>
));
