declare module "@pollyjs/adapter-fetch" {
  const FetchAdapter: any;
  export default FetchAdapter;

  export interface HttpRequest {
    body: any;
    query: any;
    params: any;
  }

  export interface HttpResponse {
    json: (body: any) => void;
    send: (body: any) => void;
    setHeader: (key: string, value: string) => void;
    sendStatus: (code: number) => void;
  }
}
