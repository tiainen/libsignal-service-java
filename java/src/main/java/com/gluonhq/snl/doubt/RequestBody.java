package com.gluonhq.snl.doubt;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author johan
 */
public class RequestBody {

    MediaType mediaType;
    String jsonBody;
    byte[] raw;
    
    public RequestBody() {
    }
    
    RequestBody(MediaType mt, String jb) {
        this.mediaType = mt;
        this.jsonBody = jb;
    }

    RequestBody(MediaType mt, byte[] raw) {
        this.mediaType = mt;
        this.raw = raw;
    }

    public static RequestBody create(MediaType mt, String jsonBody) {
        return new RequestBody(mt, jsonBody);
    }

    public static RequestBody create(MediaType mt, byte[] raw) {
        return new RequestBody(mt, raw);
    }

    public long contentLength() {
        throw new UnsupportedOperationException("NYI");
    }

    public MediaType contentType() {
        return this.mediaType;
    }

    public byte[] getRawBytes() {
        if (raw != null) return raw;
        return jsonBody.getBytes();
    }

    public void writeTo(OutputStream sink) throws IOException {
        throw new UnsupportedOperationException("NYI");
    }
}
