import * as Yup from "yup";

const expiresOnMsSchema = Yup.string()
  .label("expiresOnMs")
  .required("Required");

const userIdSchema = Yup.string().label("userId").required("Required");

export const newTokenValidationSchema = Yup.object().shape({
  expiresOnMs: expiresOnMsSchema,
  userId: userIdSchema,
});
