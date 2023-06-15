package com.gluonhq.snl;

import java.util.Base64;

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
