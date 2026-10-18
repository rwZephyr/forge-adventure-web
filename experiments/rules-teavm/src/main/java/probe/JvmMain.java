package probe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JvmMain {
    public static void main(String[] args) throws Exception {
        Map<String, Object> result = FactoryCases.run();
        String json = Json.encode(result);
        Files.writeString(Path.of(args[0]), json + "\n", StandardCharsets.UTF_8);
        System.out.println(json);
        if (!Boolean.TRUE.equals(result.get("passed"))) throw new IllegalStateException("JVM control failed; see case JSON");
    }
}
