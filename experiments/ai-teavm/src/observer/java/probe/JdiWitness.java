package probe;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** JVM-only passive debugger. Windows shared memory only; never opens a socket. */
public final class JdiWitness {
    private static final Map<Integer,String> SITES=Map.of(
        1669,"candidate-guard",1685,"future-construction",1687,"evaluation-lambda",
        1754,"canPlayAndPayFor-call",831,"canPlayAndPayFor-entry",
        1777,"thread-construction",1782,"timed-get",1819,"timed-get-finally");

    private static ObjectReference object(Value value) { return value instanceof ObjectReference ref ? ref : null; }
    private static Value field(ObjectReference ref,String name) {
        if (ref==null) return null;
        Field field=ref.referenceType().fieldByName(name);
        return field==null?null:ref.getValue(field);
    }
    private static Object primitive(Value value) {
        if (value==null) return null;
        if (value instanceof IntegerValue v) return v.value();
        if (value instanceof LongValue v) return v.value();
        if (value instanceof BooleanValue v) return v.value();
        if (value instanceof StringReference v) return v.value();
        if (value instanceof ObjectReference v) return v.referenceType().name()+"@"+v.uniqueID();
        return value.toString();
    }
    private static Value local(StackFrame frame,String name) throws AbsentInformationException {
        LocalVariable variable=frame.visibleVariableByName(name);
        return variable==null?null:frame.getValue(variable);
    }
    private static Thread copy(InputStream input,Path output) {
        Thread reader=new Thread(()->{try (InputStream in=input; OutputStream out=Files.newOutputStream(output)) {in.transferTo(out);} catch(IOException e) {throw new UncheckedIOException(e);}},"witness-log-copy");
        reader.setDaemon(true); reader.start(); return reader;
    }
    private static boolean has(List<Map<String,Object>> rows,String site,String thread) {
        return rows.stream().anyMatch(r->site.equals(r.get("site")) && (thread==null||thread.equals(r.get("thread"))));
    }
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]); Files.createDirectories(output);
        for (String mode:List.of("positive","negative")) observe(mode,output);
    }
    private static void observe(String mode,Path output) throws Exception {
        LaunchingConnector connector=Bootstrap.virtualMachineManager().launchingConnectors().stream()
            .filter(c->c.name().equals("com.sun.jdi.CommandLineLaunch") && c.transport().name().equals("dt_shmem"))
            .findFirst().orElseThrow(()->new IllegalStateException("Shared-memory JDI unavailable; no socket fallback permitted"));
        Map<String,Connector.Argument> args=connector.defaultArguments();
        args.get("home").setValue(System.getProperty("java.home"));
        args.get("options").setValue("-Xmx1024m -Dfile.encoding=UTF-8 -Duser.home=\""+System.getProperty("user.home")+"\" -Djava.io.tmpdir=\""+System.getProperty("java.io.tmpdir")+"\" -cp \""+System.getProperty("java.class.path")+"\"");
        args.get("main").setValue("probe.JvmMain "+mode+" \""+output.resolve(mode+"-fixture.json")+"\"");
        args.get("suspend").setValue("true");
        VirtualMachine vm=connector.launch(args);
        Process child=vm.process();
        Thread stdout=copy(child.getInputStream(),output.resolve(mode+"-stdout.log"));
        Thread stderr=copy(child.getErrorStream(),output.resolve(mode+"-stderr.log"));
        List<Map<String,Object>> rows=new ArrayList<>();
        ClassPrepareRequest prepare=vm.eventRequestManager().createClassPrepareRequest();
        prepare.addClassFilter("forge.ai.AiController"); prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); prepare.enable();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        long observerStart=System.nanoTime();
        boolean dead=false,watchdog=false;
        try {
            vm.resume();
            while (!dead && System.nanoTime()<deadline) {
                EventSet set=vm.eventQueue().remove(250);
                if (set==null) continue;
                for (Event event:set) {
                    if (event instanceof ClassPrepareEvent e) {
                        for (int line:SITES.keySet()) {
                            List<Location> locations=e.referenceType().locationsOfLine(line);
                            if (locations.isEmpty()) throw new IllegalStateException("Original source line missing: "+line);
                            for (Location location:locations) {
                                BreakpointRequest request=vm.eventRequestManager().createBreakpointRequest(location);
                                request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
                            }
                        }
                    } else if (event instanceof BreakpointEvent e) {
                        Map<String,Object> row=new LinkedHashMap<>();
                        row.put("site",SITES.get(e.location().lineNumber())); row.put("line",e.location().lineNumber());
                        row.put("method",e.location().method().name()); row.put("thread",e.thread().name()); row.put("threadId",e.thread().uniqueID());
                        StackFrame frame=e.thread().frame(0);
                        ObjectReference all=object(local(frame,"all"));
                        if (all!=null) row.put("candidateListSize",primitive(field(all,"size")));
                        ObjectReference future=object(local(frame,"future"));
                        if (future!=null) {
                            row.put("futureClass",future.referenceType().name()); row.put("futureObjectId",future.uniqueID());
                            row.put("futureState",primitive(field(future,"state")));
                            ObjectReference outcome=object(field(future,"outcome"));
                            if (outcome!=null) {
                                row.put("outcomeClass",outcome.referenceType().name()); row.put("outcomeObjectId",outcome.uniqueID());
                                row.put("outcomeCardId",primitive(field(object(field(outcome,"hostCard")),"id")));
                            }
                        }
                        ObjectReference sa=object(local(frame,"sa"));
                        if (sa!=null) {
                            row.put("abilityClass",sa.referenceType().name()); row.put("abilityObjectId",sa.uniqueID());
                            ObjectReference host=object(field(sa,"hostCard"));
                            row.put("hostCardId",primitive(field(host,"id")));
                            row.put("hostCardName",primitive(field(object(field(host,"currentState")),"name")));
                        }
                        rows.add(row);
                    } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) dead=true;
                }
                set.resume();
            }
            if (!dead) { watchdog=true; child.destroyForcibly(); }
        } catch (VMDisconnectedException normalExit) { dead=true; }
        finally {
            try {vm.dispose();} catch(VMDisconnectedException ignored) { }
            if (!child.waitFor(2,TimeUnit.SECONDS)) { watchdog=true; child.destroyForcibly(); child.waitFor(); }
            stdout.join(1000); stderr.join(1000);
        }
        boolean positive=mode.equals("positive");
        boolean path=positive
            ? has(rows,"future-construction",null) && has(rows,"evaluation-lambda","Game AI Eval")
                && rows.stream().anyMatch(r->"canPlayAndPayFor-entry".equals(r.get("site")) && "Game AI Eval".equals(r.get("thread")) && "Lightning Bolt".equals(r.get("hostCardName")))
                && has(rows,"timed-get",null)
                && rows.stream().anyMatch(r->"timed-get-finally".equals(r.get("site")) && Integer.valueOf(2).equals(r.get("futureState")) && r.containsKey("outcomeCardId"))
            : has(rows,"candidate-guard",null)
                && rows.stream().noneMatch(r->"Lightning Bolt".equals(r.get("hostCardName")))
                && rows.stream().noneMatch(r->"timed-get-finally".equals(r.get("site")) && r.containsKey("outcomeCardId"));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("mode",mode);result.put("transport",connector.transport().name());result.put("networkListener",false);
        result.put("sourceUnchanged",true);result.put("debuggerInvokedTargetMethods",false);result.put("watchdogExpired",watchdog);
        result.put("childExit",child.exitValue());result.put("pathWitnessPassed",path);result.put("events",rows);
        result.put("vmDeathOrDisconnectObserved",dead);result.put("observerElapsedMs",(System.nanoTime()-observerStart)/1_000_000.0);
        result.put("emptyGuardObserved",rows.stream().anyMatch(r->Integer.valueOf(0).equals(r.get("candidateListSize"))));
        result.put("negativeExpectation", "Empty hand has no spell candidate; original mana candidates may still create an evaluation task.");
        Files.writeString(output.resolve(mode+"-witness.json"),Json.encode(result)+"\n");
        System.out.println("JDI "+mode+": path="+path+", childExit="+child.exitValue()+", events="+rows.size());
        if (watchdog || child.exitValue()!=0 || !path) throw new IllegalStateException("Unchanged path witness inconclusive/failed: "+mode);
    }
}
