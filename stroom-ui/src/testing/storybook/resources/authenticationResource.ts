import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { PasswordValidationRequest } from "components/authentication/types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { authBaseServiceUrl }: Config,
) => {
  const authenticationServiceUrl = `${authBaseServiceUrl}/authentication/v1`;
  server
    .get(`${authenticationServiceUrl}/idToken`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const accessCode = req.params.accessCode;
      console.log("Trying access code", accessCode);

      res.sendStatus(200);
    });

  // Login
  server
    .post(`${authenticationServiceUrl}/authenticate`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { email, password, sessionId, requestingClientId } = JSON.parse(
        req.body,
      );

      console.log("Received ", {
        email,
        password,
        sessionId,
        requestingClientId,
      });
      res.json({ loginSuccessful: true, redirectUrl: "#" });
    });

  // Change Password
  server
    .post(`${authenticationServiceUrl}/changePassword/`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { newPassword, oldPassword } = JSON.parse(req.body);

      console.log("Changing Password", { oldPassword, newPassword });
      res.send(200);
    });

  // Reset Password
  server
    .post(`${authenticationServiceUrl}/resetPassword/`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { password } = JSON.parse(req.body);
      console.log("Resetting Password, given", { password });

      res.send(200);
    });

  // Submit Reset Request
  server
    .get(`${authenticationServiceUrl}/reset/:email`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { email } = req.params.email;
      console.log("Requesting Password Reset for ", { email });
      res.send(200);
    });

  // Is Password Valid
  server
    .post(`${authenticationServiceUrl}/isPasswordValid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const validationReq: PasswordValidationRequest = JSON.parse(req.body);
      console.log("Validation Request", validationReq);
      res.send(200);
    });
};

export default resourceBuilder;
