package tokhttp3;

import java.io.IOException;

/**
 *
 * @author johan
 */
public interface Call {

    Response execute() throws IOException;

    public void cancel();

    public void enqueue(Callback callback);
}
