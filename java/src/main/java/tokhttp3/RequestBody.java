package tokhttp3;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import okio.Buffer;
import okio.BufferedSink;

public abstract class RequestBody {

    HttpRequest.BodyPublisher jRequestBody;
    MediaType contentType;

    public abstract long contentLength();

    public abstract MediaType contentType();

    public abstract void writeTo(okio.BufferedSink sink) throws IOException;

    public abstract BodyPublisher getBodyPublisher();

    public byte[] getRawBytes() throws IOException {
         throw new UnsupportedOperationException("Not supported yet.");
    }

    public static RequestBody create(MediaType contentType, byte[] content) {
        RequestBody answer = new MyRequestBody(contentType, content);

        return answer;
    }

    public static RequestBody create(MediaType contentType, String string) {
        return new MyRequestBody(contentType, string);
    }

    static class MyRequestBody extends RequestBody {
        byte[] cnt;

        MyRequestBody(MediaType contentType, byte[] content) {
            this.contentType = contentType;
            this.cnt = content;
            jRequestBody = HttpRequest.BodyPublishers.ofByteArray(content);
        }

        MyRequestBody(MediaType contentType, String content) {
            this.contentType = contentType;
            this.cnt = content.getBytes();
            jRequestBody = HttpRequest.BodyPublishers.ofString(content);
        }

        @Override
        public BodyPublisher getBodyPublisher() {
            return jRequestBody;
        }

        @Override
        public long contentLength() {
            return (cnt == null ? -1 : cnt.length);
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(cnt);
            sink.flush();
        }

        @Override
        public byte[] getRawBytes() throws IOException {
            int len = (int) contentLength();
            if (len < 1) return new byte[0];
            byte[] raw = new byte[len];
            Buffer sink = new Buffer();
            writeTo(sink);
            sink.read(raw);
            return raw;
        }

    }
}
