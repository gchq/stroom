import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { ResourceBuilder } from "./types";
import { PasswordValidationRequest } from "components/Authentication/api/types";

const resourceBuilder: ResourceBuilder = (server: any, apiUrl: any) => {
  const resource = apiUrl("/Oldauthentication/v1");
  server
    .get(`${resource}/idToken`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const accessCode = req.params.accessCode;
      console.log("Trying access code", accessCode);

      res.sendStatus(200);
    });

  // SignIn
  server
    .post(`${resource}/noauth/login`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { userId, password, sessionId, requestingClientId } = JSON.parse(
        req.body,
      );

      console.log("Received ", {
        userId,
        password,
        sessionId,
        requestingClientId,
      });
      res.json({ loginSuccessful: true, redirectUri: "#" });
    });

  // Change Password
  server
    .post(`${resource}/noauth/changePassword/`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { newPassword, oldPassword } = JSON.parse(req.body);

      console.log("Changing Password", { oldPassword, newPassword });
      res.send(200);
    });

  // Reset Password
  server
    .post(`${resource}/resetPassword/`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { password } = JSON.parse(req.body);
      console.log("Resetting Password, given", { password });

      res.send(200);
    });

  // Submit Reset Request
  server
    .get(`${resource}/reset/:email`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { email } = req.params.email;
      console.log("Requesting Password Reset for ", { email });
      res.send(200);
    });

  // Is Password Valid
  server
    .post(`${resource}/noauth/isPasswordValid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const validationReq: PasswordValidationRequest = JSON.parse(req.body);
      console.log("Validation Request", validationReq);
      res.send(200);
    });
};

export default resourceBuilder;
