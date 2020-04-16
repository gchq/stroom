import { ResourceBuilder } from "./types";

import activityResource from "./activityResource";
import authenticationResource from "./authenticationResource";
import authorisationResource from "./authorisationResource";
import appPermissionResource from "./appPermissionResource";
import buildInfoResource from "./sessionInfoResource";
import documentPermissionResource from "./documentPermissionResource";
import elementsResource from "./elementsResource";
import explorerResource from "./explorerResource";
import indexVolumeGroupResource from "./indexVolumeGroupResource";
import indexVolumeResource from "./indexVolumeResource";
import pipelineResource from "./pipelineResource";
import streamAttributeMapResource from "./streamAttributeMapResource";
import streamTaskResource from "./streamTaskResource";
import stroomUserResource from "./userAndGroupsResource";
import documentResources from "./documentResources";
import welcomeResource from "./welcomeResource";

const resourceBuilders: ResourceBuilder[] = [
  activityResource,
  appPermissionResource,
  authenticationResource,
  authorisationResource,
  buildInfoResource,
  documentPermissionResource,
  documentResources,
  elementsResource,
  explorerResource,
  indexVolumeGroupResource,
  indexVolumeResource,
  pipelineResource,
  streamAttributeMapResource,
  streamTaskResource,
  stroomUserResource,
  welcomeResource,
];

export default resourceBuilders;
