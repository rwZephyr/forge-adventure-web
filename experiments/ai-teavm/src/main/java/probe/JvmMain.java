package probe;
import java.nio.file.*;
import java.util.*;
public final class JvmMain {
    public static void main(String[] args) throws Exception {
        Map<String,Object> result=OriginalAiFixture.run(args[0].equals("negative"));
        long surviving=Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive() && t.getName().equals("Game AI Eval")).count();
        result.put("survivingEvalThreads",surviving);
        if (surviving!=0) result.put("passed",false);
        Files.writeString(Path.of(args[1]),Json.encode(result)+"\n");
        System.out.println(Json.encode(result));
        if (!Boolean.TRUE.equals(result.get("passed"))) System.exit(1);
    }
}
