package probe.compiler;

import org.teavm.extension.ExtensionEnvironment;
import org.teavm.extension.introspect.*;
import org.teavm.extension.spi.reflection.ReflectionPolicy;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Compiler-side metadata only. Never loads/initializes Forge enum classes. */
public final class ConstructorPolicy implements ReflectionPolicy {
    private final Map<String, String> signatures = new TreeMap<>();

    @Override public void initialize(ExtensionEnvironment environment) {
        String variant = System.getProperty("probe.variant", "default");
        if (variant.equals("default")) return;
        try {
            for (String line : Files.readAllLines(Path.of(System.getProperty("probe.manifest")))) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 2) throw new IllegalStateException("Invalid manifest line: " + line);
                if (variant.equals("missing-amount") && fields[0].equals("forge.game.keyword.KeywordWithAmount")) continue;
                signatures.put(fields[0], fields[1]);
            }
            System.out.println("CONSTRUCTOR_POLICY variant=" + variant + " requested=" + signatures.size());
        } catch (Exception failure) { throw new IllegalStateException("Cannot load constructor manifest", failure); }
    }

    @Override public Collection<IntrospectMember> classAccessibleMembers(IntrospectClass<?> cls) {
        String signature = signatures.get(cls.name());
        if (signature == null) return Collections.emptyList();
        List<IntrospectMember> matches = new ArrayList<>();
        for (IntrospectMethod method : cls.declaredMethods()) {
            if (!method.isConstructor()) continue;
            String actual = method.parameters().stream().map(p -> p.type().name()).collect(Collectors.joining(","));
            if (actual.equals(signature)) matches.add(method);
        }
        if (matches.size() != 1) throw new IllegalStateException("Expected exactly one original constructor: " + cls.name() + "(" + signature + "), found " + matches.size());
        System.out.println("CONSTRUCTOR_ALLOWED " + cls.name() + "(" + signature + ")");
        return matches;
    }
}
