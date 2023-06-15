package com.gluonhq.snl;

import java.net.http.HttpResponse;
import java.util.List;

/**
 *
 * @author johan
 */
public class Response<T> {

  //  private HttpResponse<T> httpAnswer;
    private final int statusCode;
    T body;

    public Response(byte[] rawBytes, int statusCode) {
        this.body = (T)rawBytes;
        this.statusCode = statusCode;
    }
    public Response(HttpResponse<T> httpAnswer) {
     //   this.httpAnswer = httpAnswer;
        this.body = httpAnswer.body();
        this.statusCode = httpAnswer.statusCode();
        System.err.println("Got response "+httpAnswer.body());
    }

    public ResponseBody<T> body() {
        return new ResponseBody(body);
    }

    public List<String> headers(String setCookie) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String header(String key) {
                throw new UnsupportedOperationException("Not supported yet.");

    }
    public boolean isSuccessful() {
        return ((statusCode >= 200) && (statusCode < 300));
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String message() {
        return "RENAME ME TO GETMESSAGE!";
    }

}
