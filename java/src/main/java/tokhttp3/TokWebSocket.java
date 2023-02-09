package tokhttp3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.ByteString;

/**
 *
 * @author johan
 */
public class TokWebSocket implements WebSocket {
    private static final Logger LOG = Logger.getLogger(TokWebSocket.class.getName());

    private final WebSocketListener twsl;
    private java.net.http.WebSocket jws;
    
    public TokWebSocket(WebSocketListener twsl) {
        this.twsl = twsl;
    }
    
    void setJavaWebSocket(java.net.http.WebSocket jw) {
        this.jws = jw;
    }

    @Override
    public boolean close(int code, String reason) {
        LOG.severe("We are asked to close this websocket, reason = "+reason+" and code = "+code);
        return true;
    }

    @Override
    public boolean send(ByteString of) {
        LOG.info("Sending data to "+this.jws.hashCode() +" and is,os state = "+this.jws.isInputClosed()+", "+this.jws.isOutputClosed());
        if (this.jws.isOutputClosed()) {
            twsl.onClosed(this, 0, "Output closed without reason");
        }
        CompletableFuture<java.net.http.WebSocket> cf = this.jws.sendBinary(of.asByteBuffer(), true);
        try {
            java.net.http.WebSocket wwss = cf.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(TokWebSocket.class.getName()).log(Level.SEVERE, null, ex);
        }
        LOG.info("Data has been sent to "+this.jws.hashCode());
        return true;
    }

    @Override
    public boolean send(String text) {
        LOG.info("Sending text to "+this.jws.hashCode() +" and is/os state = "+this.jws.isInputClosed()+", "+this.jws.isOutputClosed());
        this.jws.sendText(text, false);
        return true;
    }

    @Override
    public long queueSize() {
        return 1;
    }
    
}
