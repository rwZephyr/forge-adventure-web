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
        if (args.length == 2 && args[0].equals("--c1")) {
            observeTestNg(Path.of(args[1]), "forge.net.embedded.ProtocolGuiGameInProcessTest.humanBoltTargetsOpponentWithManualMountainPayment", true);
            return;
        }
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
        observeTestNg(output, method, false);
    }
    private static void observeTestNg(Path output, String method, boolean c1Mode) throws Exception {
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
        C1 c1 = c1Mode ? new C1() : null;
        Set<String> classes=new HashSet<>(TEST_METHODS.keySet()); classes.add("forge.ai.AiController");
        if (c1 != null) classes.addAll(C1.LINES.keySet());
        for (String name:classes) {
            ClassPrepareRequest request=vm.eventRequestManager().createClassPrepareRequest();
            request.addClassFilter(name); request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
        }
        long start=System.nanoTime(), deadline=start+TimeUnit.SECONDS.toNanos(210);
        boolean dead=false,watchdog=false; String observerError=null;
        try {
            if (c1 != null && !vm.canGetMethodReturnValues()) throw new IllegalStateException("C1 requires actual JDI return values");
            vm.resume();
            while (!dead && System.nanoTime()<deadline) {
                EventSet set=vm.eventQueue().remove(250); if(set==null) continue;
                try {
                    for(Event event:set) {
                        if(event instanceof ClassPrepareEvent e) {
                            installTestSites(vm,e.referenceType());
                            if (c1 != null) c1.install(vm,e.referenceType());
                        }
                        else if(event instanceof BreakpointEvent e) {
                            if (e.request().getProperty("c1Site") != null) c1.breakpoint(e,start);
                            else {
                                Map<String,Object> observed=testRow(e,start);
                                if (c1 != null) c1.payment(e,observed);
                                rows.add(observed);
                            }
                        }
                        else if(event instanceof MethodExitEvent e && c1 != null) c1.exit(e,start);
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
        if (c1 != null) {
            result.put("mode","c1");
            String failure = observerError;
            if (failure == null) try { c1.validate(rows); } catch (Exception e) { failure=e.toString(); }
            result.put("identityPassed",failure==null); result.put("identityFailure",failure);
            result.put("identityEvents",c1.rows);
            if (failure != null) observerError=failure;
        }
        Files.writeString(output.resolve("witness.json"),Json.encode(result)+"\n");
        System.out.printf("TestNG JDI: path=%b test=%b actionsEdt=%b latch=%b exit=%d rows=%d%n",path,testPassed,actionEdt,wait,child.exitValue(),rows.size());
        if(watchdog || observerError!=null || child.exitValue()!=0 || !testPassed || !path || !actionEdt || !wait || !allFuturesNormal) throw new IllegalStateException("Embedded TestNG witness failed/inconclusive");
    }

    /** Opt-in field/local-only C1 audit. No invocation, value writes or target collection pinning. */
    private static final class C1 {
        static final Map<String,Map<Integer,String>> LINES = Map.of(
            "forge.player.PlayerControllerHuman", Map.of(229,"offer",231,"mapped"),
            "forge.gamemodes.net.ProtocolGuiGame", Map.of(384,"gui-offer"),
            "forge.net.embedded.ProtocolGuiGameInProcessTest$BoltScript", Map.of(411,"peer-reply"),
            "forge.game.player.PlaySpellAbility", Map.of(146,"additional",149,"optional"),
            "forge.gamemodes.match.input.InputPassPriority", Map.of(367,"selected"),
            "forge.gamemodes.match.input.InputPayMana", Map.of(332,"mana-dispatch",333,"mana-execute",345,"mana-pay"),
            "forge.game.spellability.SpellAbility", Map.of(1246,"copy")
        );
        final List<Map<String,Object>> rows = new ArrayList<>();
        final Map<Long,Long> calls = new HashMap<>();
        final Map<Long,ObjectReference> controllers = new HashMap<>();
        long count;
        static void need(boolean ok,String reason) { if (!ok) throw new IllegalStateException("C1: "+reason); }
        static ObjectReference required(Value value,String name) {
            ObjectReference r=object(value); need(r!=null,"missing reference "+name); return r;
        }
        static long id(Value value) { return required(value,"identity").uniqueID(); }
        static List<ObjectReference> list(ObjectReference r) {
            need(r!=null,"missing list");
            if (r.referenceType().name().equals("forge.util.collect.FCollection")) return list(required(field(r,"list"),"backing list"));
            need(r.referenceType().name().equals("java.util.ArrayList"),"unsupported list layout "+r.referenceType().name());
            int n=((IntegerValue)field(r,"size")).value();
            ArrayReference a=(ArrayReference)field(r,"elementData");
            need(n>=0 && n<=256 && a!=null && n<=a.length(),"inconsistent list bounds");
            List<ObjectReference> out=new ArrayList<>();
            for(Value v:a.getValues(0,n)) out.add(required(v,"list element"));
            return out;
        }
        static List<Long> ids(List<ObjectReference> objects) { return objects.stream().map(ObjectReference::uniqueID).toList(); }
        static List<Map<String,Object>> cache(ObjectReference r) {
            need(r!=null && r.referenceType().name().equals("java.util.LinkedHashMap"),"unsupported controller cache");
            List<Map<String,Object>> out=new ArrayList<>(); Set<Long> seen=new HashSet<>();
            for(ObjectReference e=object(field(r,"head"));e!=null;e=object(field(e,"after"))) {
                need(seen.add(e.uniqueID()) && seen.size()<=16,"cache cycle/oversize");
                ObjectReference key=required(field(e,"key"),"cache key"), val=required(field(e,"value"),"cache value");
                Map<String,Object> pair=new LinkedHashMap<>();
                pair.put("viewObjectId",key.uniqueID());pair.put("abilityObjectId",val.uniqueID());
                pair.put("storedViewObjectId",id(field(val,"view")));
                pair.put("abilityId",primitive(field(val,"id")));pair.put("viewId",primitive(field(key,"id")));
                pair.put("hostObjectId",id(field(val,"hostCard")));out.add(pair);
            }
            need(out.size()==((IntegerValue)field(r,"size")).value(),"cache size changed"); return out;
        }
        void install(VirtualMachine vm,ReferenceType type) throws Exception {
            for(var site:LINES.getOrDefault(type.name(),Map.of()).entrySet()) {
                List<Location> locations=type.locationsOfLine(site.getKey());
                need(!locations.isEmpty(),"missing debug line "+type.name()+":"+site.getKey());
                // A source statement may have several bytecode locations; take its first execution point only.
                BreakpointRequest r=vm.eventRequestManager().createBreakpointRequest(locations.get(0));
                r.putProperty("c1Site",site.getValue());r.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);r.enable();
            }
        }
        Map<String,Object> row(LocatableEvent e,String site,long start) throws Exception {
            Map<String,Object> r=new LinkedHashMap<>(); rows.add(r);
            r.put("site",site);r.put("observerNs",System.nanoTime()-start);
            r.put("threadObjectId",e.thread().uniqueID());r.put("javaThreadId",primitive(field(e.thread(),"tid")));r.put("thread",e.thread().name());
            r.put("line",e.location().lineNumber());
            r.put("frames",e.thread().frames().stream().map(f->f.location().declaringType().name()+"."+f.location().method().name()+":"+f.location().lineNumber()).toList());
            Long call=calls.get(e.thread().uniqueID()); if(call!=null) r.put("call",call);
            ObjectReference c=controllers.get(e.thread().uniqueID());
            if(c!=null) {
                r.put("controllerObjectId",c.uniqueID());
                ObjectReference input=object(field(object(field(c,"inputProxy")),"input")); input=object(field(input,"value"));
                if(input!=null) { r.put("inputObjectId",input.uniqueID());r.put("inputClass",input.referenceType().name()); }
                ObjectReference gui=object(field(c,"gui")), waiter=object(field(gui,"waiter")), latch=object(field(gui,"latch"));
                r.put("waiterJavaThreadId",primitive(field(waiter,"tid")));r.put("waiterStatus",primitive(field(waiter,"threadStatus")));
                r.put("latchObjectId",latch==null?null:latch.uniqueID());r.put("latchCount",primitive(field(object(field(latch,"sync")),"state")));
            }
            return r;
        }
        void arm(LocatableEvent e,String site,Map<String,Object> entry) {
            MethodExitRequest request=e.virtualMachine().eventRequestManager().createMethodExitRequest();
            request.addClassFilter(e.location().declaringType().name());request.addThreadFilter(e.thread());
            request.putProperty("c1Exit",site);request.putProperty("c1Method",e.location().method());request.putProperty("c1Entry",entry);
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);request.enable();
        }
        void breakpoint(BreakpointEvent e,long start) throws Exception {
            String site=(String)e.request().getProperty("c1Site");StackFrame f=e.thread().frame(0);long thread=e.thread().uniqueID();
            if(site.equals("copy")) {
                boolean relevant=e.thread().frames().stream().anyMatch(x->x.location().method().name().equals("chooseOptionalAdditionalCosts")
                    || x.location().declaringType().name().endsWith("InputPassPriority") && x.location().method().name().equals("onCardSelected"));
                if(!relevant) return;
            }
            if(site.equals("offer")) {
                need(!calls.containsKey(thread) && ++count<=3,"unexpected/reentrant ability question");
                calls.put(thread,count);controllers.put(thread,f.thisObject());
            }
            Map<String,Object> r=row(e,site,start);
            switch(site) {
                case "offer", "mapped" -> {
                    ObjectReference c=f.thisObject(), map=required(field(c,"spellViewCache"),"spellViewCache");
                    List<ObjectReference> abilities=list(required(local(f,"abilities"),"abilities"));
                    r.put("candidateObjects",ids(abilities));r.put("candidateCount",abilities.size());r.put("cacheObjectId",map.uniqueID());r.put("pairs",cache(map));
                    need(abilities.size()==1 && cache(map).size()==1,"non-singleton offer/cache");
                    if(site.equals("offer")) arm(e,"ability-return",r);
                    else r.put("returnedViewObjectId",id(local(f,"resultView")));
                }
                case "gui-offer", "peer-reply" -> {
                    need(calls.containsKey(thread),"question without controller call");
                    List<ObjectReference> views=list(required(local(f,site.equals("gui-offer")?"abilities":"choices"),"offered views"));
                    r.put("viewObjects",ids(views));r.put("choiceCount",views.size());need(views.size()==1,"ambiguous GUI offer");
                    if(site.equals("peer-reply")) r.put("returnedViewObjectId",id(local(f,"choice")));
                }
                case "additional", "optional" -> {
                    r.put("originalObjectId",id(local(f,"original")));r.put("abilityObjects",ids(list(required(local(f,"abilities"),"additional abilities"))));
                    if(site.equals("additional")) arm(e,"additional-return",r);
                    else {r.put("chosenObjectId",id(local(f,"choosen")));r.put("optionalCount",list(required(local(f,"list"),"optional costs")).size());need(Integer.valueOf(0).equals(r.get("optionalCount")),"unexpected optional cost");}
                }
                case "selected" -> r.put("abilityObjects",ids(list(required(field(f.thisObject(),"chosenSa"),"priority chosen"))));
                case "mana-dispatch", "mana-execute", "mana-pay" -> {
                    r.put("chosenObjectId",id(local(f,"chosen")));r.put("paidForObjectId",id(field(f.thisObject(),"saPaidFor")));
                    if(site.equals("mana-dispatch")) r.put("sourceObjectId",id(local(f,"card")));
                }
                case "copy" -> {r.put("originalObjectId",f.thisObject().uniqueID());r.put("originalViewObjectId",id(field(f.thisObject(),"view")));r.put("lki",primitive(local(f,"lki")));arm(e,"copy-return",r);}
                default -> throw new IllegalStateException("Unknown C1 site");
            }
        }
        void exit(MethodExitEvent e,long start) throws Exception {
            String site=(String)e.request().getProperty("c1Exit");
            if(site==null || !e.method().equals(e.request().getProperty("c1Method"))) return;
            Map<String,Object> r=row(e,site,start);
            @SuppressWarnings("unchecked") Map<String,Object> entry=(Map<String,Object>)e.request().getProperty("c1Entry");
            ObjectReference returned=required(e.returnValue(),"method return");r.put("returnedObjectId",returned.uniqueID());
            r.put("entryNs",entry.get("observerNs"));r.put("storedViewObjectId",id(field(returned,"view")));
            if(site.equals("copy-return")) {r.put("originalObjectId",entry.get("originalObjectId"));r.put("lki",entry.get("lki"));}
            e.virtualMachine().eventRequestManager().deleteEventRequest(e.request());
            if(site.equals("ability-return")) {calls.remove(e.thread().uniqueID());controllers.remove(e.thread().uniqueID());}
        }
        List<Map<String,Object>> at(String site) {return rows.stream().filter(r->site.equals(r.get("site"))).toList();}
        void payment(BreakpointEvent e,Map<String,Object> row) throws Exception {
            if (!Set.of("forge.game.zone.MagicStack.add","resolution-with-ability").contains(row.get("site"))) return;
            StackFrame f=e.thread().frame(0);ObjectReference sa=object(local(f,"sp"));
            if(sa==null) sa=object(local(f,"sa"));
            if(sa==null) return;
            List<Map<String,Object>> paid=new ArrayList<>();
            for(ObjectReference mana:list(required(field(sa,"payingMana"),"payingMana"))) {
                ObjectReference card=required(field(mana,"sourceCard"),"mana source");
                Map<String,Object> p=new LinkedHashMap<>();p.put("manaObjectId",mana.uniqueID());
                p.put("color",((ByteValue)field(mana,"color")).value());p.put("sourceObjectId",card.uniqueID());p.put("sourceGameId",primitive(field(card,"id")));
                paid.add(p);
            }
            row.put("payingMana",paid);
        }
        @SuppressWarnings("unchecked") void validate(List<Map<String,Object>> original) {
            need(count==3 && calls.isEmpty(),"incomplete question sequence");
            List<Map<String,Object>> offers=at("offer"); List<Long> values=new ArrayList<>(),views=new ArrayList<>(),maps=new ArrayList<>();
            for(int i=0;i<3;i++) {
                Map<String,Object> a=offers.get(i);Object call=a.get("call");
                Map<String,Object> pair=((List<Map<String,Object>>)a.get("pairs")).get(0);
                Long value=(Long)pair.get("abilityObjectId"),view=(Long)pair.get("viewObjectId");values.add(value);views.add(view);maps.add((Long)a.get("cacheObjectId"));
                need(List.of(value).equals(a.get("candidateObjects")) && view.equals(pair.get("storedViewObjectId")),"candidate/cache/stored-view mismatch");
                for(String site:List.of("gui-offer","peer-reply","mapped","ability-return")) {
                    List<Map<String,Object>> match=at(site).stream().filter(r->call.equals(r.get("call"))).toList();need(match.size()==1,"missing/duplicate "+site);
                    Map<String,Object> b=match.get(0);need(a.get("javaThreadId").equals(b.get("javaThreadId")),"question caller changed");
                    if(b.containsKey("viewObjects")) need(List.of(view).equals(b.get("viewObjects")),"offered reference mismatch");
                    if(b.containsKey("returnedViewObjectId")) need(view.equals(b.get("returnedViewObjectId")),"reply reference mismatch");
                    if(site.equals("mapped")) need(a.get("cacheObjectId").equals(b.get("cacheObjectId")) && a.get("pairs").equals(b.get("pairs")),"cache changed during question");
                    if(site.equals("ability-return")) need(value.equals(b.get("returnedObjectId")) && view.equals(b.get("storedViewObjectId")),"mapped return mismatch");
                }
            }
            need(values.get(0).equals(values.get(1)) && views.get(0).equals(views.get(1)) && !values.get(0).equals(values.get(2)),"unexpected cross-call relation");
            need(new HashSet<>(maps).size()==3,"cache not rebuilt per call");
            need(offers.stream().map(r->r.get("javaThreadId")).distinct().count()==3
                && offers.stream().map(r->r.get("controllerObjectId")).distinct().count()==1,"caller/controller identity relation");
            need(String.valueOf(offers.get(0).get("thread")).startsWith("AWT-EventQueue") && "Game-0".equals(offers.get(1).get("thread")) && "Game-1".equals(offers.get(2).get("thread")),"question thread sequence");
            need(at("selected").size()==1 && List.of(values.get(0)).equals(at("selected").get(0).get("abilityObjects")),"priority selection identity");
            need(at("additional").size()==2 && at("optional").size()==2 && at("additional-return").size()==2,"additional-cost sequence");
            for(int i=0;i<2;i++) {
                Long expected=values.get(i==0?1:2);
                need(expected.equals(at("additional").get(i).get("originalObjectId")) && List.of(expected).equals(at("additional").get(i).get("abilityObjects"))
                    && expected.equals(at("optional").get(i).get("chosenObjectId")) && expected.equals(at("additional-return").get(i).get("returnedObjectId")),"optional-cost remap/copy");
            }
            for(String site:List.of("mana-dispatch","mana-execute","mana-pay")) {
                need(at(site).size()==1 && values.get(2).equals(at(site).get(0).get("chosenObjectId")) && values.get(0).equals(at(site).get(0).get("paidForObjectId")),"mana execution binding");
            }
            need(at("copy").isEmpty(),"copy on authorization chain requires review");
            for(String site:List.of("forge.player.PlayerControllerHuman.playChosenSpellAbility","forge.player.PlayerControllerHuman.payManaCost","resolution-with-ability"))
                need(original.stream().anyMatch(r->site.equals(r.get("site")) && values.get(0).equals(r.get("abilityObjectId"))),"missing original Bolt anchor "+site);
            need(original.stream().anyMatch(r->"forge.player.PlayerControllerHuman.payManaCost".equals(r.get("site")) && values.get(2).equals(r.get("abilityObjectId"))),"missing original mana anchor");
        }
    }

}
