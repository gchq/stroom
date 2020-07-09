import * as Yup from "yup";

const dataSchema = Yup.string().label("Data").required("Required");

export const newTokenValidationSchema = Yup.object().shape({
  data: dataSchema,
});
