package keystrokesmod.script.model;

import java.util.Collections;
import java.util.List;

public class Response {
    private final int responseCode;
    private final String contents;
    private final List<String[]> headers;

    public Response(int responseCode, String contents, List<String[]> headers) {
        this.responseCode = responseCode;
        this.contents = contents;
        this.headers = (headers == null) ? Collections.emptyList() : headers;
    }

    public Response(int responseCode, String contents) {
        this(responseCode, contents, null);
    }

    public int code() {
        return responseCode;
    }

    public String string() {
        return contents;
    }

    public Json json() {
        return contents == null ? null : Json.parse(contents);
    }

    public List<String[]> headers() {
        return headers;
    }

    @Override
    public String toString() {
        return "Response(" + responseCode + ")";
    }
}