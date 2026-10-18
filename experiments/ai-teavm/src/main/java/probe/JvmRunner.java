package probe;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
public final class JvmRunner {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]); Files.createDirectories(output);
        List<Map<String,Object>> runs=new ArrayList<>();
        for (String run:List.of("positive-1","positive-2","positive-3","negative-1")) {
            List<String> command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-Xmx1024m","-Dfile.encoding=UTF-8","-Duser.home="+System.getProperty("user.home"),"-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"),
                "-cp",System.getProperty("java.class.path"),"probe.JvmMain",run.startsWith("negative")?"negative":"positive",output.resolve(run+".json").toString()));
            long start=System.nanoTime();
            Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.resolve(run+".log").toFile()).start();
            boolean completed=process.waitFor(15,TimeUnit.SECONDS);
            if (!completed) { process.destroyForcibly(); process.waitFor(); }
            Map<String,Object> row=new LinkedHashMap<>(); row.put("run",run); row.put("pid",process.pid()); row.put("completed",completed);
            row.put("exitCode",process.exitValue()); row.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0); runs.add(row);
            Files.writeString(output.resolve("runs.json"),Json.encode(runs)+"\n");
            System.out.println(Json.encode(row));
            if (!completed || process.exitValue()!=0) throw new IllegalStateException("Original fixture failed: "+run+"; see "+output);
        }
    }
}
