import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { DocumentPermissions } from "components/AuthorisationManager/api/docPermission";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/docPermissions/v1`;

  // Get Permissions for Doc Type
  server
    .get(`${resource}/forDocType/:docRefType`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefType } = req.params;

      res.json(testCache.data!.docPermissionByType[docRefType]);
    });

  // Get Permissions for Doc For User
  server
    .get(`${resource}/forDocForUser/:docRefUuid/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid, userUuid } = req.params;

      res.json(
        testCache
          .data!.userDocPermission.filter(
            udp => udp.docRefUuid === docRefUuid && udp.userUuid === userUuid,
          )
          .map(udp => udp.permissionName),
      );
    });

  // Add Permission for User to Doc Ref
  server
    .post(`${resource}/forDocForUser/:docRefUuid/:userUuid/:permissionName`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid, userUuid, permissionName } = req.params;

      testCache.data!.userDocPermission = testCache.data!.userDocPermission.concat(
        [
          {
            docRefUuid,
            userUuid,
            permissionName,
          },
        ],
      );

      res.json(testCache.data!.allAppPermissions);
    });

  // Add Permission for User to Doc Ref
  server
    .delete(`${resource}/forDocForUser/:docRefUuid/:userUuid/:permissionName`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid, userUuid, permissionName } = req.params;

      testCache.data!.userDocPermission = testCache.data!.userDocPermission.filter(
        udp =>
          !(
            udp.userUuid === userUuid &&
            udp.docRefUuid === docRefUuid &&
            udp.permissionName === permissionName
          ),
      );

      res.status(204).send(undefined);
    });

  // Get Permissions for Document
  server
    .get(`${resource}/forDoc/:docRefUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid } = req.params;
      let documentPermissions: DocumentPermissions = {
        docRefUuid,
        userPermissions: testCache
          .data!.userDocPermission.filter(d => d.docRefUuid === docRefUuid)
          .reduce(
            (acc, { userUuid, permissionName }) => ({
              ...acc,
              [userUuid]: [...(acc[userUuid] || [])].concat([permissionName]),
            }),
            {},
          ),
      };

      res.json(documentPermissions);
    });

  // Clear Permission for Document for user
  server
    .delete(`${resource}/forDocForUser/:docRefUuid/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid, userUuid } = req.params;

      testCache.data!.userDocPermission = testCache.data!.userDocPermission.filter(
        udp => !(udp.docRefUuid === docRefUuid && udp.userUuid === userUuid),
      );

      res.status(204).send(undefined);
    });

  // Clear Permissions for Document
  server
    .delete(`${resource}/forDoc/:docRefUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefUuid } = req.params;

      testCache.data!.userDocPermission = testCache.data!.userDocPermission.filter(
        udp => udp.docRefUuid !== docRefUuid,
      );

      res.status(204).send(undefined);
    });
};

export default resourceBuilder;
