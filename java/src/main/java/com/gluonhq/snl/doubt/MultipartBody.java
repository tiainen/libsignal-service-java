package com.gluonhq.snl.doubt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import okio.Buffer;
import okio.BufferedSink;

/**
 *
 * see
 * https://github.com/yskszk63/jnhttp-multipartformdata-bodypublisher/blob/main/src/main/java/io/github/yskszk63/jnhttpmultipartformdatabodypublisher/MultipartFormDataBodyPublisher.java
 */
public class MultipartBody {

    public static final MediaType FORM = MediaType.parse("multipart/form-data");

    abstract static class Part {

        String name;

        String name() {
            return name;
        }

        Optional<String> filename() {
            return Optional.empty();
        }

        Optional<String> contentType() {
            return Optional.empty();
        }

        abstract ReadableByteChannel open() throws IOException;
    }

    static class DataPart extends Part {

        String val;
        final Charset charset;

        DataPart(String name, String val, Charset charset) {
            this.name = name;
            this.val = val;
            this.charset = charset;
        }

        @Override
        public ReadableByteChannel open() throws IOException {
            var input = new ByteArrayInputStream(this.val.getBytes(this.charset));
            return Channels.newChannel(input);
        }
    }

    static class FilePart extends Part {

        String fileName;
        RequestBody fileBody;

        FilePart(String name, String fileName, RequestBody fileBody) {
            this.name = name;
            this.fileName = fileName;
            this.fileBody = fileBody;
        }

        @Override
        public ReadableByteChannel open() throws IOException {
            return Files.newByteChannel(Paths.get(name));
        }
    }

    static class RequestBodyPart extends Part {

        String fileName;
        RequestBody fileBody;

        RequestBodyPart(String name, String fileName, RequestBody fileBody) {
            this.name = name;
            this.fileName = fileName;
            this.fileBody = fileBody;
        }

        @Override
        public ReadableByteChannel open() throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(fileBody.getRawBytes());
            return Channels.newChannel(bais);
        }
    }

    public static class Builder {

        public static final MediaType FORM = MediaType.parse("multipart/form-data");
        private MediaType mediaType;
        private final String boundary = UUID.randomUUID().toString();
        private final List<Part> parts = new ArrayList<>();

        public Builder() {
        }

        public Builder setType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public Builder addFormDataPart(String name, String val) {
            parts.add(new DataPart(name, val, Charset.defaultCharset()));
            return this;
        }

        public Builder addFormDataPart(String name, String filename, RequestBody body) {
            parts.add(new RequestBodyPart(name, filename, body));
            return this;
        }

        public MultiPartRequestBody build() {
            return new MultiPartRequestBody(parts, mediaType, boundary);
        }
    }

    public static class MultiPartRequestBody extends RequestBody {

        private List<Part> parts;
        String boundary;
        private MediaType contentType;

        public MultiPartRequestBody(List<Part> parts, MediaType mediaType, String boundary) {
            this.parts = parts;
            this.boundary = boundary;
            this.contentType = MediaType.parse(mediaType.getMediaType() + "; boundary=" + boundary);
        }

        @Override
        public long contentLength() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public MediaType contentType() {
            return this.contentType;
        }
//
//        @Override
//        public void writeTo(BufferedSink sink) throws IOException {
//            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//        }
//
        public MultipartBodyPublisher getBodyPublisher() {
            return new MultipartBodyPublisher(this.parts, boundary, Charset.defaultCharset());
        }

    }
}
