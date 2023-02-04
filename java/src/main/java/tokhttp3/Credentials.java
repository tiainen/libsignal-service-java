package tokhttp3;

import java.util.Base64;
import java.util.Base64.Encoder;

/**
 *
 * @author johan
 */
public class Credentials {

    
    public static String basic(String username, String password) {
        String uap = username+":"+password;
        String enc = Base64.getEncoder().encodeToString(uap.getBytes());
        return "Basic "+enc;
    }
    
}