package tokhttp3.rust;

import tokhttp3.WebSocket;

public class RustResponse {

    private int statusCode;
    private byte[] body;
    private int version;

    public RustResponse(int statusCode, byte[] body, int version) {
        this.statusCode = statusCode;
        this.body = body;
        this.version = version;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isHttp1_1() {
        return version == 1;
    }

    public boolean isHttp2() {
        return version == 2;
    }
}
