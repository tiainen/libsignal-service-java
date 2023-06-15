package com.gluonhq.snl.doubt;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
// https://android.googlesource.com/platform/external/okhttp/+/master/okhttp/src/main/java/com/squareup/okhttp/MediaType.java

public final class MediaType {

    private static final String TOKEN = "([a-zA-Z0-9-!#$%&'*+.^_`{|}~]+)";
    private static final String QUOTED = "\"([^\"]*)\"";
    private static final Pattern TYPE_SUBTYPE = Pattern.compile(TOKEN + "/" + TOKEN);
    private static final Pattern PARAMETER = Pattern.compile(
            ";\\s*(?:" + TOKEN + "=(?:" + TOKEN + "|" + QUOTED + "))?");

    private final String mediaType;
    private final String type;
    private final String subtype;
    private final String charset;

    private MediaType(String mediaType, String type, String subtype, String charset) {
        this.mediaType = mediaType;
        this.type = type;
        this.subtype = subtype;
        this.charset = charset;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getCharset() {
        return charset;
    }

    public static MediaType get(String string) {
        return parse(string);
    }

    public static MediaType parse(String string) {
        Matcher typeSubtype = TYPE_SUBTYPE.matcher(string);
        if (!typeSubtype.lookingAt()) {
            return null;
        }
        String type = typeSubtype.group(1).toLowerCase(Locale.US);
        String subtype = typeSubtype.group(2).toLowerCase(Locale.US);
        String charset = null;
        Matcher parameter = PARAMETER.matcher(string);
        for (int s = typeSubtype.end(); s < string.length(); s = parameter.end()) {
            parameter.region(s, string.length());
            if (!parameter.lookingAt()) {
                return null; // This is not a well-formed media type.
            }
            String name = parameter.group(1);
            if (name == null || !name.equalsIgnoreCase("charset")) {
                continue;
            }
            String charsetParameter = parameter.group(2) != null
                    ? parameter.group(2) // Value is a token.
                    : parameter.group(3); // Value is a quoted string.
            if (charset != null && !charsetParameter.equalsIgnoreCase(charset)) {
                throw new IllegalArgumentException("Multiple different charsets: " + string);
            }
            charset = charsetParameter;
        }
        return new MediaType(string, type, subtype, charset);
    }

}
