import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Dialog } from "components/Dialog/Dialog";
import { CreateTokenFormik } from "./CreateToken";

storiesOf("Token", module).add("Create Token", () => (
  <Dialog>
    <CreateTokenFormik
      initialValues={{}}
      onSubmit={() => undefined}
      onClose={() => undefined}
    />
  </Dialog>
));
