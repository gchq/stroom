import { storiesOf } from "@storybook/react";
import * as React from "react";
import { EditTokenFormik } from "./EditToken";
import { Dialog } from "components/Dialog/Dialog";

storiesOf("Token", module).add("Edit Token", () => (
  <Dialog>
    <EditTokenFormik
      initialValues={{}}
      onSubmit={() => undefined}
      onClose={() => undefined}
    />
  </Dialog>
));
