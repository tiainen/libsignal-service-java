package com.gluonhq.snl.doubt;

import com.gluonhq.snl.doubt.MultipartBody.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public class MultipartBodyPublisher implements BodyPublisher {

    private String boundary;
    private Charset charset;

    private final BodyPublisher delegate;

    private List<Part> parts;
    private static final Logger LOG = Logger.getLogger(MultipartBodyPublisher.class.getName());

    public MultipartBodyPublisher(List<Part> parts, String boundary, Charset charset) {
        this.parts = parts;
        this.boundary = boundary;
        this.charset = charset;
        MultipartFormDataChannel mpfd = new MultipartFormDataChannel(this.boundary, this.parts, this.charset);
        ByteBuffer bb = ByteBuffer.allocate(16384);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // we do all of this in the constructor, as we NEED to know the contentlength immediately.
        try {
            int read = mpfd.read(bb);
            while (read > -1) {
                baos.write(bb.array(), 0, read);
                bb = ByteBuffer.allocate(16384);
                read = mpfd.read(bb);
            }
        } catch (IOException ex) {
            Logger.getLogger(MultipartBodyPublisher.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.delegate = BodyPublishers.ofByteArray(baos.toByteArray());
    }

    @Override
    public long contentLength() {
        long answer = delegate.contentLength();
        LOG.info("Sending multipartbody content with length " + answer);
        return answer;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
    }

}

enum State {
    Boundary, Headers, Body, Done,
}

class MultipartFormDataChannel implements ReadableByteChannel {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private boolean closed = false;
    private State state = State.Boundary;
    private final String boundary;
    private final Iterator<Part> parts;
    private ByteBuffer buf = ByteBuffer.allocate(0);
    private Part current = null;
    private ReadableByteChannel channel = null;
    private final Charset charset;

    MultipartFormDataChannel(String boundary, Iterable<Part> parts, Charset charset) {
        this.boundary = boundary;
        this.parts = parts.iterator();
        this.charset = charset;
    }

    @Override
    public void close() throws IOException {
        if (this.channel != null) {
            this.channel.close();
            this.channel = null;
        }
        this.closed = true;
    }

    @Override
    public boolean isOpen() {
        return !this.closed;
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        while (true) {
            if (this.buf.hasRemaining()) {
                var n = Math.min(this.buf.remaining(), buf.remaining());
                var slice = this.buf.slice();
                slice.limit(n);
                buf.put(slice);
                this.buf.position(this.buf.position() + n);
                return n;
            }

            switch (this.state) {
                case Boundary:
                    if (this.parts.hasNext()) {
                        this.current = this.parts.next();
                        this.buf = ByteBuffer.wrap(("--" + this.boundary + "\r\n").getBytes(LATIN1));
                        this.state = State.Headers;
                    } else {
                        this.buf = ByteBuffer.wrap(("--" + this.boundary + "--\r\n").getBytes(LATIN1));
                        this.state = State.Done;
                    }
                    break;

                case Headers:
                    this.buf = ByteBuffer.wrap(this.currentHeaders().getBytes(this.charset));
                    this.state = State.Body;
                    break;

                case Body:
                    if (this.channel == null) {
                        this.channel = this.current.open();
                    }

                    var n = this.channel.read(buf);
                    if (n == -1) {
                        this.channel.close();
                        this.channel = null;
                        this.buf = ByteBuffer.wrap("\r\n".getBytes(LATIN1));
                        this.state = State.Boundary;
                    } else {
                        return n;
                    }
                    break;

                case Done:
                    return -1;
            }
        }
    }

    static String escape(String s) {
        return s.replaceAll("\"", "\\\"");
    }

    String currentHeaders() {
        var current = this.current;

        if (current == null) {
            throw new IllegalStateException();
        }

        var contentType = current.contentType();
        var filename = current.filename();
        if (contentType.isPresent() && filename.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n")
                    .add("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"").add("Content-Type: %s")
                    .toString();
            try ( var formatter = new Formatter()) {
                return formatter
                        .format(format, escape(current.name()), escape(filename.get()), escape(contentType.get()))
                        .toString() + "\r\n"; // FIXME
            }

        } else if (contentType.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n").add("Content-Disposition: form-data; name=\"%s\"")
                    .add("Content-Type: %s").toString();
            try ( var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name()), escape(contentType.get())).toString() + "\r\n"; // FIXME
                // escape
            }

        } else if (filename.isPresent()) {
            var format = new StringJoiner("\r\n", "", "\r\n")
                    .add("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"").toString();
            try ( var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name()), escape(filename.get())).toString() + "\r\n"; // FIXME
                // escape
            }

        } else {
            var format = new StringJoiner("\r\n", "", "\r\n").add("Content-Disposition: form-data; name=\"%s\"")
                    .toString();
            try ( var formatter = new Formatter()) {
                return formatter.format(format, escape(current.name())).toString() + "\r\n"; // FIXME escape
            }
        }
    }
}
