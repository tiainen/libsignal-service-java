package tokhttp3;

import java.io.IOException;

/**
 *
 * @author johan
 */
public interface Callback {

    void onFailure(Call call, IOException e);

    void onResponse(Call call,
            Response response)
            throws IOException;
}
