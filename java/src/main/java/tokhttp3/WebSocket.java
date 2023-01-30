package tokhttp3;

import okio.ByteString;

/**
 *
 * @author johan
 */
public interface WebSocket {

    public boolean close(int i, String ok);

    public boolean send(ByteString of);
    
    public boolean send(String text);

    public long queueSize();
    
}
