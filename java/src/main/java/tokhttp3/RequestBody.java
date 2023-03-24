package tokhttp3;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import okio.BufferedSink;

public abstract class RequestBody {

    public abstract long contentLength();

    public abstract MediaType contentType();

    public abstract void writeTo(okio.BufferedSink sink) throws IOException;

    public abstract BodyPublisher getBodyPublisher();

    public static RequestBody create(MediaType contentType, byte[] content) {
        return new MyRequestBody(contentType, content);
    }

    public static RequestBody create(MediaType contentType, String string) {
        return new MyRequestBody(contentType, string.getBytes(StandardCharsets.UTF_8));
    }

    static class MyRequestBody extends RequestBody {

        MediaType contentType;
        byte[] content;

        MyRequestBody(MediaType contentType, byte[] content) {
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public BodyPublisher getBodyPublisher() {
            return HttpRequest.BodyPublishers.ofByteArray(content);
        }

        @Override
        public long contentLength() {
            return this.content.length;
        }

        @Override
        public MediaType contentType() {
            return this.contentType;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(this.content);
        }
    }
}
