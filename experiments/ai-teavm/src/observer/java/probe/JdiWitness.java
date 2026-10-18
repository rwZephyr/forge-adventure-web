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
        if (args.length == 3 && args[0].equals("--testng")) {
            observeTestNg(Path.of(args[1]), args[2]);
            return;
        }
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

    // Opt-in observation of the existing embedded TestNG cases. No game setup or decisions here.
    private static final Map<String,List<String>> TEST_METHODS = Map.of(
        "forge.player.PlayerControllerHuman", List.of("playChosenSpellAbility", "payManaCost", "selectCard", "selectPlayer", "selectButtonOk", "concede"),
        "forge.gamemodes.match.input.InputSyncronizedBase", List.of("awaitLatchRelease"),
        "forge.gamemodes.match.input.InputSelectTargets", List.of("onPlayerSelected"),
        "forge.gamemodes.match.input.InputPayMana", List.of("onCardSelected"),
        "forge.game.cost.CostPayment", List.of("payCost", "payComputerCosts"),
        "forge.ai.ComputerUtil", List.of("handlePlayingSpellAbility"),
        "forge.game.zone.MagicStack", List.of("add", "resolveStack"),
        "forge.gamemodes.net.server.DeltaSyncManager", List.of("collectDeltas")
    );

    private static void installTestSites(VirtualMachine vm, ReferenceType type) throws Exception {
        if (type.name().equals("forge.ai.AiController")) {
            for (int line : SITES.keySet()) {
                List<Location> locations = type.locationsOfLine(line);
                if (locations.isEmpty()) throw new IllegalStateException("Missing original AI source site: " + line);
                for (Location location : locations) testBreakpoint(vm, location, SITES.get(line));
            }
        } else {
            for (String method : TEST_METHODS.getOrDefault(type.name(), List.of())) {
                List<Method> matches = type.methodsByName(method);
                if (matches.isEmpty()) throw new IllegalStateException("Missing original method: " + type.name() + "." + method);
                for (Method match : matches) {
                    if (match.location() == null) throw new IllegalStateException("Missing debug entry: " + match);
                    testBreakpoint(vm, match.location(), match.declaringType().name() + "." + method);
                }
            }
            if (type.name().equals("forge.game.zone.MagicStack")) {
                List<Location> locations = type.locationsOfLine(600);
                if (locations.isEmpty()) throw new IllegalStateException("Missing original resolveStack ability site");
                for (Location location : locations) testBreakpoint(vm, location, "resolution-with-ability");
            }
        }
    }
    private static void testBreakpoint(VirtualMachine vm, Location location, String label) {
        BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
        request.putProperty("testSite", label);
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable();
    }
    private static void abilityRow(Map<String,Object> row, ObjectReference ability) {
        if (ability == null) return;
        row.put("abilityObjectId", ability.uniqueID());
        row.put("abilityClass", ability.referenceType().name());
        row.put("abilityId", primitive(field(ability,"id")));
        ObjectReference card = object(field(ability,"hostCard"));
        row.put("hostCardId", primitive(field(card,"id")));
        row.put("hostCardName", primitive(field(object(field(card,"currentState")),"name")));
    }
    private static Map<String,Object> testRow(BreakpointEvent event, long start) throws Exception {
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("site", event.request().getProperty("testSite"));
        row.put("class", event.location().declaringType().name());
        row.put("method", event.location().method().name());
        row.put("line", event.location().lineNumber());
        row.put("observerNs", System.nanoTime() - start);
        row.put("thread", event.thread().name());
        row.put("threadObjectId", event.thread().uniqueID());
        row.put("javaThreadId", primitive(field(event.thread(),"tid")));
        List<String> frames = new ArrayList<>();
        for (StackFrame f : event.thread().frames(0,Math.min(24,event.thread().frameCount()))) frames.add(f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber());
        row.put("frames", frames);
        StackFrame frame = event.thread().frame(0);
        ObjectReference receiver = frame.thisObject();
        if (receiver != null) { row.put("receiverObjectId",receiver.uniqueID()); row.put("receiverClass",receiver.referenceType().name()); }
        ObjectReference ability = null;
        for (String name : List.of("sa","chosenSa","sp")) {
            ObjectReference candidate = object(local(frame,name));
            if (candidate != null) { ability=candidate; break; }
        }
        if (ability == null) ability=object(field(receiver,"ability"));
        if (ability == null) ability=object(field(receiver,"sa"));
        if (ability == null) ability=object(field(receiver,"saPaidFor"));
        abilityRow(row,ability);
        for (String name : List.of("card","player","cardView","playerView")) {
            ObjectReference argument=object(local(frame,name));
            if (argument!=null) {
                row.put(name+"ObjectId",argument.uniqueID());
                row.put(name+"GameId",primitive(field(argument,"id")));
            }
        }
        ObjectReference controller=object(field(receiver,"controller"));
        if (controller==null && receiver!=null && receiver.referenceType().name().equals("forge.player.PlayerControllerHuman")) controller=receiver;
        ObjectReference currentInput=object(field(object(field(controller,"inputProxy")),"input"));
        currentInput=object(field(currentInput,"value"));
        if (currentInput!=null) {
            row.put("currentInputObjectId",currentInput.uniqueID());
            row.put("currentInputClass",currentInput.referenceType().name());
        }
        for (StackFrame f : event.thread().frames()) {
            ObjectReference gui=f.thisObject();
            if (gui!=null && gui.referenceType().name().endsWith("ProtocolGuiGameInProcessTest$ObservedGui")) {
                ObjectReference waiter=object(field(gui,"waiter")), done=object(field(gui,"latch"));
                row.put("waiterJavaThreadId",primitive(field(waiter,"tid")));
                row.put("waiterThreadStatusBits",primitive(field(waiter,"threadStatus")));
                row.put("waiterLatchCount",primitive(field(object(field(done,"sync")),"state")));
                break;
            }
        }
        ObjectReference all=object(local(frame,"all"));
        if (all!=null) row.put("candidateListSize",primitive(field(all,"size")));
        ObjectReference future=object(local(frame,"future"));
        if (future!=null) {
            row.put("futureClass",future.referenceType().name()); row.put("futureObjectId",future.uniqueID());
            row.put("futureState",primitive(field(future,"state")));
            ObjectReference outcome=object(field(future,"outcome"));
            row.put("outcomeNull",outcome==null);
            if (outcome!=null) { row.put("outcomeObjectId",outcome.uniqueID()); row.put("outcomeCardId",primitive(field(object(field(outcome,"hostCard")),"id"))); }
        }
        if ("Game AI Eval".equals(event.thread().name())) {
            ObjectReference target=object(field(event.thread(),"target"));
            if (target!=null) { row.put("workerTargetClass",target.referenceType().name()); row.put("workerFutureObjectId",target.uniqueID()); }
        }
        ObjectReference latch=object(field(receiver,"cdlDone"));
        if (latch!=null) {
            row.put("inputLatchObjectId",latch.uniqueID());
            row.put("inputLatchCount",primitive(field(object(field(latch,"sync")),"state")));
        }
        return row;
    }
    private static boolean ancestor(Map<String,Object> row, String method) {
        return ((List<?>)row.get("frames")).stream().anyMatch(f->String.valueOf(f).contains(method));
    }
    private static boolean site(List<Map<String,Object>> rows, String label, String host) {
        return rows.stream().anyMatch(r->label.equals(r.get("site")) && (host==null||host.equals(r.get("hostCardName"))));
    }
    private static boolean correlatedAi(List<Map<String,Object>> rows, boolean empty) {
        for (Map<String,Object> result : rows) {
            if (!"timed-get-finally".equals(result.get("site")) || !Integer.valueOf(2).equals(result.get("futureState"))
                    || !ancestor(result,"forge.game.phase.PhaseHandler.mainLoopStep") || !ancestor(result,"forge.ai.PlayerControllerAi")) continue;
            Object future = result.get("futureObjectId"), caller = result.get("javaThreadId");
            boolean worker = rows.stream().anyMatch(r->"evaluation-lambda".equals(r.get("site")) && future.equals(r.get("workerFutureObjectId"))
                    && !Objects.equals(caller,r.get("javaThreadId")) && "java.util.concurrent.FutureTask".equals(r.get("workerTargetClass")));
            boolean get = rows.stream().anyMatch(r->"timed-get".equals(r.get("site")) && future.equals(r.get("futureObjectId")));
            if (!worker || !get) continue;
            if (empty && Boolean.TRUE.equals(result.get("outcomeNull"))) return true;
            Object outcome=result.get("outcomeObjectId");
            boolean evaluated=rows.stream().anyMatch(r->"canPlayAndPayFor-entry".equals(r.get("site")) && "Lightning Bolt".equals(r.get("hostCardName"))
                    && Objects.equals(outcome,r.get("abilityObjectId")) && !Objects.equals(caller,r.get("javaThreadId")));
            boolean cast=rows.stream().anyMatch(r->"forge.ai.ComputerUtil.handlePlayingSpellAbility".equals(r.get("site"))
                    && Objects.equals(outcome,r.get("abilityObjectId")) && Objects.equals(caller,r.get("javaThreadId")));
            boolean onStack=rows.stream().anyMatch(r->"forge.game.zone.MagicStack.add".equals(r.get("site"))
                    && Objects.equals(outcome,r.get("abilityObjectId")));
            boolean resolved=rows.stream().anyMatch(r->"resolution-with-ability".equals(r.get("site"))
                    && Objects.equals(result.get("outcomeCardId"),r.get("hostCardId")));
            if (!empty && evaluated && cast && onStack && resolved) return true;
        }
        return false;
    }
    private static void observeTestNg(Path output, String method) throws Exception {
        String prefix="forge.net.embedded.ProtocolGuiGameInProcessTest.";
        Set<String> allowed=Set.of("humanBoltTargetsOpponentWithManualMountainPayment","aiBoltRunsThroughOriginalPhaseLoop","aiEmptyHandDoesNotCastOrPay");
        if (!method.startsWith(prefix) || !allowed.contains(method.substring(prefix.length()))) throw new IllegalArgumentException("Only approved existing TestNG methods may be observed");
        Files.createDirectories(output);
        LaunchingConnector connector=Bootstrap.virtualMachineManager().launchingConnectors().stream()
            .filter(c->c.name().equals("com.sun.jdi.CommandLineLaunch") && c.transport().name().equals("dt_shmem"))
            .findFirst().orElseThrow(()->new IllegalStateException("Shared-memory JDI unavailable; no socket fallback permitted"));
        Map<String,Connector.Argument> args=connector.defaultArguments();
        args.get("home").setValue(System.getProperty("java.home"));
        args.get("options").setValue("-Xmx1536m -Djava.awt.headless=true --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.regex=ALL-UNNAMED --add-opens java.base/java.util.stream=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.desktop/javax.imageio.spi=ALL-UNNAMED -cp \""+System.getProperty("java.class.path")+"\"");
        args.get("main").setValue("org.testng.TestNG -methods "+method+" -d \""+output.resolve("reports")+"\"");
        args.get("suspend").setValue("true");
        VirtualMachine vm=connector.launch(args); Process child=vm.process();
        Thread stdout=copy(child.getInputStream(),output.resolve("stdout.log"));
        Thread stderr=copy(child.getErrorStream(),output.resolve("stderr.log"));
        List<Map<String,Object>> rows=new ArrayList<>();
        Set<String> classes=new HashSet<>(TEST_METHODS.keySet()); classes.add("forge.ai.AiController");
        for (String name:classes) {
            ClassPrepareRequest request=vm.eventRequestManager().createClassPrepareRequest();
            request.addClassFilter(name); request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
        }
        long start=System.nanoTime(), deadline=start+TimeUnit.SECONDS.toNanos(210);
        boolean dead=false,watchdog=false; String observerError=null;
        try {
            vm.resume();
            while (!dead && System.nanoTime()<deadline) {
                EventSet set=vm.eventQueue().remove(250); if(set==null) continue;
                try {
                    for(Event event:set) {
                        if(event instanceof ClassPrepareEvent e) installTestSites(vm,e.referenceType());
                        else if(event instanceof BreakpointEvent e) rows.add(testRow(e,start));
                        else if(event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) dead=true;
                    }
                } finally { set.resume(); }
            }
            if(!dead) {watchdog=true;child.destroyForcibly();}
        } catch(VMDisconnectedException normal) {dead=true;}
        catch(Exception error) {observerError=error.toString();child.destroyForcibly();}
        finally {
            try {vm.dispose();} catch(VMDisconnectedException ignored) { }
            if(!child.waitFor(3,TimeUnit.SECONDS)) {watchdog=true;child.destroyForcibly();child.waitFor();}
            stdout.join(2000);stderr.join(2000);
        }
        boolean human=method.endsWith("humanBoltTargetsOpponentWithManualMountainPayment"), empty=method.endsWith("aiEmptyHandDoesNotCastOrPay");
        boolean path=human
            ? rows.stream().anyMatch(r->"forge.player.PlayerControllerHuman.playChosenSpellAbility".equals(r.get("site")) && "Lightning Bolt".equals(r.get("hostCardName")) && ancestor(r,"forge.game.phase.PhaseHandler.mainLoopStep"))
                && site(rows,"forge.gamemodes.match.input.InputSelectTargets.onPlayerSelected","Lightning Bolt")
                && site(rows,"forge.game.cost.CostPayment.payCost","Lightning Bolt")
                && site(rows,"forge.player.PlayerControllerHuman.payManaCost","Lightning Bolt")
                && site(rows,"forge.game.zone.MagicStack.add","Lightning Bolt")
                && site(rows,"resolution-with-ability","Lightning Bolt")
            : correlatedAi(rows,empty) && (empty
                ? rows.stream().noneMatch(r -> "Lightning Bolt".equals(r.get("hostCardName")))
                : site(rows,"forge.game.cost.CostPayment.payComputerCosts","Lightning Bolt")
                    && site(rows,"forge.game.zone.MagicStack.add","Lightning Bolt")
                    && site(rows,"resolution-with-ability","Lightning Bolt"));
        List<Map<String,Object>> actions=rows.stream().filter(r->r.get("class").equals("forge.player.PlayerControllerHuman")
                && Set.of("selectCard","selectPlayer","selectButtonOk","concede").contains(r.get("method"))).toList();
        boolean actionEdt=!actions.isEmpty() && actions.stream().allMatch(r->String.valueOf(r.get("thread")).startsWith("AWT-EventQueue"));
        actionEdt &= rows.stream().filter(r->"Game AI Eval".equals(r.get("thread")))
                .noneMatch(r->actions.stream().anyMatch(a->Objects.equals(a.get("javaThreadId"),r.get("javaThreadId"))));
        boolean wait=rows.stream().anyMatch(r->"forge.gamemodes.match.input.InputSyncronizedBase.awaitLatchRelease".equals(r.get("site"))
                && Integer.valueOf(1).equals(r.get("inputLatchCount")) && !String.valueOf(r.get("thread")).startsWith("AWT-EventQueue"));
        boolean allFuturesNormal=rows.stream().filter(r->"timed-get-finally".equals(r.get("site")))
                .allMatch(r->Integer.valueOf(2).equals(r.get("futureState")));
        Path report=output.resolve("reports/testng-results.xml");
        String reportText=Files.exists(report)?Files.readString(report):"";
        boolean testPassed=reportText.contains("total=\"1\"") && reportText.contains("passed=\"1\"") && reportText.contains("failed=\"0\"") && reportText.contains("skipped=\"0\"")
                && reportText.contains("name=\""+method.substring(prefix.length())+"\"");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("mode","testng");result.put("method",method);result.put("transport",connector.transport().name());
        result.put("networkListener",false);result.put("debuggerInvokedTargetMethods",false);result.put("watchdogSeconds",210);
        result.put("watchdogExpired",watchdog);result.put("observerError",observerError);result.put("childExit",child.exitValue());
        result.put("testPassed",testPassed);result.put("pathWitnessPassed",path);result.put("controllerActionsOnEdt",actionEdt);result.put("originalInputLatchObserved",wait);
        result.put("allObservedFuturesNormal",allFuturesNormal);
        result.put("elapsedSeconds",(System.nanoTime()-start)/1_000_000_000.0);result.put("events",rows);
        Files.writeString(output.resolve("witness.json"),Json.encode(result)+"\n");
        System.out.printf("TestNG JDI: path=%b test=%b actionsEdt=%b latch=%b exit=%d rows=%d%n",path,testPassed,actionEdt,wait,child.exitValue(),rows.size());
        if(watchdog || observerError!=null || child.exitValue()!=0 || !testPassed || !path || !actionEdt || !wait || !allFuturesNormal) throw new IllegalStateException("Embedded TestNG witness failed/inconclusive");
    }

}
