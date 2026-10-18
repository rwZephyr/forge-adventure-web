package probe.compiler;

import org.teavm.tooling.*;
import org.teavm.tooling.sources.DirectorySourceFileProvider;
import org.teavm.vm.TeaVMOptimizationLevel;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Uses the pinned TeaVM compiler with gdx-TeaVM extensions on its classpath. */
public final class CompilerMain {
    public static void main(String[] args) throws Exception {
        String variant = args[0];
        Path output = Path.of(args[1]); Files.createDirectories(output);
        System.setProperty("probe.variant", variant);
        System.setProperty("probe.manifest", args[3]);
        // Audit signatures against unchanged JVM bytecode without initializing Forge classes.
        int audited = 0;
        for (String line : Files.readAllLines(Path.of(args[3]))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            Class<?> cls = Class.forName(fields[0], false, CompilerMain.class.getClassLoader());
            List<Class<?>> parameters = new ArrayList<>();
            if (!fields[1].isEmpty()) for (String type : fields[1].split(","))
                parameters.add(type.equals("boolean") ? boolean.class : Class.forName(type, false, CompilerMain.class.getClassLoader()));
            var constructor = cls.getDeclaredConstructor(parameters.toArray(new Class<?>[0]));
            if (!Modifier.isPublic(constructor.getModifiers())) throw new IllegalStateException("Constructor not public: " + line);
            audited++;
        }
        System.out.println("ORIGINAL_CONSTRUCTORS_AUDITED=" + audited + " (no Forge class initialization)");
        TeaVMTool tool = new TeaVMTool();
        tool.setMainClass("probe.BrowserMain");
        tool.setTargetType(TeaVMTargetType.JAVASCRIPT);
        tool.setTargetDirectory(output.toFile()); tool.setTargetFileName("app.js");
        tool.setEntryPointName("main"); tool.setObfuscated(false);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        tool.setSourceMapsFileGenerated(true); tool.setSourceFilesCopied(true);
        tool.setIncremental(false); tool.setAssertionsRemoved(false);
        tool.setLog(new ConsoleTeaVMToolLog(false));
        // Resource/class lookup includes compiler-side SPI services and unchanged runtime inputs.
        List<File> inputs = new ArrayList<>();
        for (String path : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) inputs.add(new File(path));
        tool.setClassPath(inputs);
        tool.setClassLoader(CompilerMain.class.getClassLoader());
        tool.getProperties().setProperty("gdx.teavm.webapp.enabled", "false");
        if (!variant.equals("default")) {
            tool.getProperties().setProperty("gdx.teavm.reflection.enabled", "false");
            tool.getProperties().setProperty("gdx.teavm.reflection.defaults", "false");
            tool.getProperties().setProperty("gdx.teavm.reflection.scan", "false");
        }
        for (String source : args[4].split(Pattern.quote(File.pathSeparator))) tool.addSourceFileProvider(new DirectorySourceFileProvider(new File(source)));
        tool.addSourceFileProvider(new DirectorySourceFileProvider(new File("src/main/java")));
        System.out.println("FACTORY_COMPILER variant=" + variant + " runtimeClasspath=" + args[2]);
        tool.generate();
        if (tool.getProblemProvider() != null && tool.getDependencyInfo() != null)
            TeaVMProblemRenderer.describeProblems(tool.getDependencyInfo().getCallGraph(), tool.getProblemProvider(), tool.getLog());
        int errors = tool.getProblemProvider() == null ? 0 : tool.getProblemProvider().getSevereProblems().size();
        System.out.println("FACTORY_COMPILER severeProblems=" + errors);
        if (errors != 0 || !Files.isRegularFile(output.resolve("app.js"))) {
            Files.deleteIfExists(output.resolve("app.js"));
            throw new IllegalStateException("TeaVM failed; no runnable artifact for " + variant);
        }
        Files.copy(Path.of("src/main/web/index.html"), output.resolve("index.html"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("../../LICENSE"), output.resolve("FORGE-LICENSE.txt"), StandardCopyOption.REPLACE_EXISTING);
    }
}
