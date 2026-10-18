package probe;

import org.teavm.jso.JSBody;

public final class BrowserMain {
    public static void main(String[] args) {
        report(Json.encode(FactoryCases.run()));
    }

    @JSBody(params = "json", script = "window.__factoryProbe = JSON.parse(json); document.getElementById('results').textContent = json; document.getElementById('status').textContent = window.__factoryProbe.passed ? 'PASS' : 'FAIL'; console.log('factory-results', json);")
    private static native void report(String json);
}
