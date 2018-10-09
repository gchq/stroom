import { HttpError } from "./ErrorTypes";

export interface Response {
  status: number;
  statusText: string;
}

export default (response: Response) => {
  if (response.status === 200) {
    return Promise.resolve(response);
  }
  return Promise.reject(new HttpError(response.status, response.statusText));
};
