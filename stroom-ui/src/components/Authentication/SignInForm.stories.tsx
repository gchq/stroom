import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Page, Form } from "./SignInForm";
import { Formik } from "formik";
import * as Yup from "yup";
import { useAlert } from "components/AlertDialog/AlertDisplayBoundary";
import { AlertType } from "../AlertDialog/AlertDialog";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets }) => {
  // const [submitting, setSubmitting] = React.useState<boolean>(false);
  const { alert } = useAlert();

  return (
    <Page allowPasswordResets={allowPasswordResets}>
      <Formik
        initialValues={{ userId: "", password: "" }}
        validationSchema={Yup.object().shape({
          userId: Yup.string()
            .email("User name not valid")
            .required("User name is required"),
          password: Yup.string().required("Password is required"),
        })}
        onSubmit={(values, actions) => {
          setTimeout(() => {
            alert({
              type: AlertType.INFO,
              title: "Submitted",
              message: JSON.stringify(values, null, 2),
            });
            actions.setSubmitting(false);
          }, 1000);
        }}
      >
        {(props) => (
          <Form {...props} />
          // <form onSubmit={props.handleSubmit}>
          //   <input
          //     type="text"
          //     onChange={props.handleChange}
          //     onBlur={props.handleBlur}
          //     value={props.values.name}
          //     name="name"
          //   />
          //   {props.errors.name && <div id="feedback">{props.errors.name}</div>}
          //   <button type="submit">Submit</button>
          // </form>
        )}
      </Formik>
    </Page>
  );
};

const stories = storiesOf("Authentication", module);
stories.add("Sign In - allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
stories.add("Sign In - disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
