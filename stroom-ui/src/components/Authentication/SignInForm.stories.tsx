import { storiesOf } from "@storybook/react";
import * as React from "react";
import SignInForm from "./SignInForm";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";
import { Formik } from "formik";
import * as Yup from "yup";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets }) => {
  // const [submitting, setSubmitting] = React.useState<boolean>(false);

  return (
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
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {props => (
        <SignInForm allowPasswordResets={allowPasswordResets} {...props} />
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
  );
};

const stories = storiesOf("Authentication", module);
addThemedStories(stories, "Sign In - allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
addThemedStories(stories, "Sign In - disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
