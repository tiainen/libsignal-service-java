package com.gluonhq.snl;

/**
 *
 * @author johan
 */
public class ResponseBody<T> {

    T body;
    
    public ResponseBody(T t) {
        this.body = t;
    }

    public String string() {
        if (body instanceof String bs) return bs;
        if (body instanceof byte[] rb) return new String(rb);
        throw new IllegalArgumentException ("Can't convert "+body+" to string");
    }

    public byte[] bytes() {
        if (body instanceof String bodyString) return bodyString.getBytes();
        if (body == null) return new byte[0];
        return (byte[]) body;
    }

    public int contentLength() {
        if (body instanceof byte[] bb) return bb.length;
        return -1;
    }

    public void close(){
    }
}
