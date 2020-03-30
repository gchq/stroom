import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { WelcomeData } from "components/Welcome/api/types";

const ABOUT_HTML: WelcomeData = {
  html: `<h1>About Stroom</h1>
<p>Stroom is designed to receive data from multiple systems.</p>`,
};

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
) => {
  const resource = `${stroomBaseServiceUrl}/welcome/v1/`;

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(ABOUT_HTML);
  });
};

export default resourceBuilder;
