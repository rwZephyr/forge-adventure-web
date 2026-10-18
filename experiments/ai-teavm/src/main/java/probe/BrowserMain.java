package probe;
import org.teavm.jso.JSBody;
public final class BrowserMain {
    public static void main(String[] args) { publish(Json.encode(OriginalAiFixture.run(false))); }
    @JSBody(params={"json"},script="globalThis.__originalAiProbe=JSON.parse(json); console.log(json);")
    private static native void publish(String json);
}
