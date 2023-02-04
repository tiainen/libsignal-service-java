package tokhttp3;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import okio.BufferedSink;

public abstract class RequestBody {

    HttpRequest.BodyPublisher jRequestBody;
    MediaType contentType;

    public abstract long contentLength();

    public abstract MediaType contentType();

    public abstract void writeTo(okio.BufferedSink sink) throws IOException;

    public abstract BodyPublisher getBodyPublisher();

    public static RequestBody create(MediaType contentType, byte[] content) {
        RequestBody answer = new MyRequestBody(contentType, content);

        return answer;
    }

    public static RequestBody create(MediaType contentType, String string) {
        return new MyRequestBody(contentType, string);
    }

    static class MyRequestBody extends RequestBody {

        MyRequestBody(MediaType contentType, byte[] content) {
            this.contentType = contentType;
            jRequestBody = HttpRequest.BodyPublishers.ofByteArray(content);
        }

        MyRequestBody(MediaType contentType, String content) {
            this.contentType = contentType;
            jRequestBody = HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public BodyPublisher getBodyPublisher() {
            return jRequestBody;
        }

        @Override
        public long contentLength() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public MediaType contentType() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

    }
}
