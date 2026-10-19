package experiment;

import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Passive field/argument/stack inspection, Windows dt_shmem only. Never invokes a target method. */
public final class BoltLifecycleObserver {
    private static final String INPUT="forge.gamemodes.match.input.";
    private static final String MAIN="experiment.BoltLifecycleMain";
    private static final Set<String> CLASSES=Set.of(MAIN,INPUT+"InputQueue",INPUT+"InputProxy",INPUT+"InputBase",
            INPUT+"InputSyncronizedBase","forge.gamemodes.net.ProtocolGuiGame");
    private static final List<String> NORMAL=List.of("InputPassPriority","InputSelectTargets","InputPayManaOfCostPayment","InputPassPriority","InputPassPriority");
    private final Map<Long,Generation> generations=new LinkedHashMap<>();
    private final Set<String> oracleEvents=new HashSet<>();
    private ObjectReference root;
    private Generation payment;
    private long seq, normalCount, loopThread;
    private long spellId,spellViewId,manaWorker;
    private int authorizedCount;
    private boolean fixtureReady, complete;
    private final PrintWriter trace;

    private static final class Generation {
        final long number, objectId, publication, publisher;
        final String type;
        long proxyPublication, awaitEntry, awaitReturn, removed, consumed, displayCompletions;
        int promptCallbacks, displayDepth;
        ObjectReference latch;
        long waiter,latchId;
        Generation(long n,ObjectReference input,long publication,long publisher) {
            number=n; objectId=input.uniqueID(); type=input.referenceType().name(); this.publication=publication; this.publisher=publisher;
        }
    }
    private BoltLifecycleObserver(PrintWriter trace) { this.trace=trace; }
    private static void require(boolean ok,String text) { if(!ok) throw new IllegalStateException(text); }
    private static ObjectReference object(Value v) { return v instanceof ObjectReference r?r:null; }
    private static Value field(ObjectReference o,String name) {
        if(o==null) return null;
        Field f=o.referenceType().allFields().stream().filter(x->x.name().equals(name)).findFirst().orElse(null);
        return f==null?null:o.getValue(f);
    }
    private static long id(Value v) { ObjectReference r=object(v); return r==null?0:r.uniqueID(); }
    private static long number(Value v) { require(v instanceof PrimitiveValue,"Missing primitive lifecycle field"); return ((PrimitiveValue)v).longValue(); }
    private static long count(ObjectReference latch) {
        require(latch!=null,"Original latch identity unavailable");
        // JDK 17 CountDownLatch.Sync extends AQS; field reads only, no getCount() invocation.
        return number(field(object(field(latch,"sync")),"state"));
    }
    private ObjectReference currentInput() {
        ObjectReference c=object(field(root,"controller"));
        ObjectReference proxy=object(field(c,"inputProxy"));
        return object(field(object(field(proxy,"input")),"value"));
    }
    private boolean ourQueue(ObjectReference queue) {
        return root!=null && id(field(object(field(root,"controller")),"inputQueue"))==id(queue);
    }
    private Generation generation(ObjectReference input) { return input==null?null:generations.get(input.uniqueID()); }
    private static boolean inputObject(ObjectReference o) {
        if(o==null || !(o.referenceType() instanceof ClassType type)) return false;
        for(ClassType c=type;c!=null;c=c.superclass()) if(c.name().equals(INPUT+"InputBase") || c.name().equals(INPUT+"InputLockUI")) return true;
        return false;
    }
    private static ObjectReference origin(ThreadReference thread) throws IncompatibleThreadStateException {
        for(StackFrame frame:thread.frames()) if(inputObject(frame.thisObject())) return frame.thisObject();
        return null;
    }
    private Map<String,Object> row(String event,ThreadReference thread) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("seq",++seq); r.put("observerNanos",System.nanoTime()); r.put("event",event);
        r.put("threadJdiId",thread.uniqueID()); r.put("threadName",thread.name());
        r.put("jdiThreadStatus",thread.status()); r.put("eventThreadSuspended",thread.isSuspended());
        Value tid=field(thread,"tid"); r.put("javaThreadId",tid==null?null:number(tid)); return r;
    }
    private void emit(Map<String,Object> row) {
        trace.println(LifecycleJson.encode(row)); trace.flush();
        require(!trace.checkError(),"Cannot preserve witness trace");
    }
    private static void attach(Map<String,Object> row,Generation g) {
        if(g!=null) { row.put("generation",g.number); row.put("inputJdiId",g.objectId); row.put("inputType",g.type);
            row.put("publishedAt",g.publication); row.put("awaitEnteredAt",g.awaitEntry); row.put("consumedAt",g.consumed); }
    }
    private void install(VirtualMachine vm,ReferenceType type) throws AbsentInformationException {
        switch(type.name()) {
            case MAIN -> method(vm,type,"witnessPoint","driver-marker");
            case INPUT+"InputQueue" -> { line(vm,type,104,"setInput","queue-publication"); line(vm,type,65,"removeInput","queue-removal"); }
            case INPUT+"InputProxy" -> line(vm,type,73,"update","proxy-before-prompt-post");
            case INPUT+"InputSyncronizedBase" -> line(vm,type,55,"stop","stop-before-countdown");
            case INPUT+"InputBase" -> {
                method(vm,type,"showMessageInitial","display-entry");
                MethodExitRequest exit=vm.eventRequestManager().createMethodExitRequest();
                exit.addClassFilter(type); exit.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); exit.enable();
            }
            case "forge.gamemodes.net.ProtocolGuiGame" -> {
                method(vm,type,"updateButtons","callback-buttons"); method(vm,type,"showPromptMessage","callback-message");
            }
            default -> throw new IllegalStateException("Unexpected prepared class");
        }
    }
    private static void method(VirtualMachine vm,ReferenceType type,String name,String tag) {
        List<Method> methods=type.methodsByName(name);
        require(methods.size()==1,"Ambiguous/missing method: "+type.name()+"."+name);
        breakpoint(vm,methods.get(0).location(),tag);
    }
    private static void line(VirtualMachine vm,ReferenceType type,int line,String method,String tag) throws AbsentInformationException {
        List<Location> locations=type.locationsOfLine(line);
        require(locations.size()==1 && locations.get(0).method().name().equals(method),"Pinned source site unavailable: "+type.name()+":"+line);
        breakpoint(vm,locations.get(0),tag);
    }
    private static void breakpoint(VirtualMachine vm,Location location,String tag) {
        BreakpointRequest r=vm.eventRequestManager().createBreakpointRequest(location); r.putProperty("site",tag);
        r.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); r.enable();
    }
    private void observe(BreakpointEvent e) throws Exception {
        String site=(String)e.request().getProperty("site"); StackFrame frame=e.thread().frame(0);
        if(site.equals("driver-marker")) { marker(e,frame); return; }
        Map<String,Object> r=row(site,e.thread());
        if(site.equals("queue-publication")) {
            if(!ourQueue(frame.thisObject())) return;
            ObjectReference input=object(frame.getArgumentValues().get(0));
            require(input!=null,"No original published input argument");
            Generation g=generation(input); boolean fresh=g==null;
            if(fresh) {
                g=new Generation(generations.size()+1,input,seq,e.thread().uniqueID()); generations.put(input.uniqueID(),g);
                if(fixtureReady) {
                    require(normalCount<NORMAL.size() && g.type.equals(INPUT+NORMAL.get((int)normalCount)),"Unexpected new normal input generation");
                    require(g.publisher==loopThread,"Original Game-0 publishes normal input"); normalCount++;
                }
                if(payment!=null && g!=payment) {
                    r.put("priorPaymentLatchJdiId",id(payment.latch)); r.put("priorPaymentLatchCount",count(payment.latch));
                    require(count(payment.latch)==0 && payment.awaitReturn>0,"No new generation while original payment is unresolved");
                    require(e.thread().uniqueID()==payment.waiter,"Payment await owner advances to next generation");
                }
                if(g.type.equals(INPUT+"InputPayManaOfCostPayment")) { require(payment==null,"One original payment input"); payment=g; }
            }
            attach(r,g); r.put("freshGeneration",fresh); emit(r); return;
        }
        if(site.equals("queue-removal")) {
            if(!ourQueue(frame.thisObject())) return;
            Generation g=generation(object(frame.getArgumentValues().get(0))); require(g!=null,"Removing an unobserved input");
            g.removed=seq; attach(r,g); emit(r); return;
        }
        if(site.equals("proxy-before-prompt-post")) {
            ObjectReference c=object(field(frame.thisObject(),"controller"));
            if(root==null || id(c)!=id(field(root,"controller"))) return;
            ObjectReference input=object(field(object(field(frame.thisObject(),"input")),"value")); Generation g=generation(input);
            if(g!=null) { g.proxyPublication=seq; attach(r,g); }
            else require(input!=null && input.referenceType().name().equals(INPUT+"InputLockUI"),"Proxy references an unpublished input");
            r.put("proxyInputJdiId",id(input)); r.put("transitionOnly",g==null); emit(r); return;
        }
        if(site.equals("stop-before-countdown")) {
            Generation g=generation(frame.thisObject()); require(g!=null,"Stop for an unobserved original input");
            ObjectReference latch=object(field(frame.thisObject(),"cdlDone"));
            require(g.latch!=null && id(g.latch)==id(latch),"Exact original stop/await latch identity");
            if(g==payment) require(manaWorker!=0 && e.thread().uniqueID()==manaWorker,"Normal Mountain worker performs payment stop");
            attach(r,g); r.put("latchJdiId",id(latch)); r.put("latchCountBeforeCall",count(latch));
            r.put("notReleaseLinearizationOrWorkerExit",true); emit(r); return;
        }
        ObjectReference input=site.equals("display-entry")?frame.thisObject():origin(e.thread());
        Generation g=generation(input); attach(r,g);
        r.put("originInputJdiId",id(input)); r.put("currentProxyInputJdiId",id(currentInput()));
        if(g==null) {
            require(input==null || input.referenceType().name().equals(INPUT+"InputLockUI"),"Callback originates in an unpublished synchronized input");
            ObjectReference current=currentInput();
            require(!site.equals("callback-buttons") || input!=null || current==null || current.referenceType().name().equals(INPUT+"InputLockUI"),"Unbound buttons for a live synchronized input");
            r.put("staleRelativeToProxy",input!=null && id(input)!=id(current));
            r.put("binding","unbound-or-lock-transition"); emit(r); return;
        }
        boolean current=id(currentInput())==g.objectId;
        boolean stale=!current || g.consumed>0 || g.removed>0 || g.awaitReturn>0;
        require(!stale || g.consumed>0 || g.removed>0 || g.awaitReturn>0,"Unexplained mismatched callback origin");
        r.put("binding",stale?"stale-or-consumed":g.awaitEntry==0?"published-pending-await":"current-awaited");
        if(site.equals("display-entry")) g.displayDepth++;
        else { r.put("repeatCallback",g.promptCallbacks++>0); r.put("displayDepth",g.displayDepth); }
        emit(r);
    }
    private void displayExit(MethodExitEvent e) throws Exception {
        if(!e.method().name().equals("showMessageInitial")) return;
        ObjectReference input=e.thread().frame(0).thisObject(); Generation g=generation(input);
        if(g==null) { require(input==null || input.referenceType().name().equals(INPUT+"InputLockUI"),"Unmapped display exit"); return; }
        Map<String,Object> r=row("display-return",e.thread());
        require(g.displayDepth>0,"Display return paired with actual original receiver entry");
        g.displayDepth--; g.displayCompletions++; attach(r,g); emit(r);
    }
    private void marker(BreakpointEvent e,StackFrame frame) {
        if(root==null) root=frame.thisObject(); else require(root.uniqueID()==frame.thisObject().uniqueID(),"One standalone driver");
        List<Value> args=frame.getArgumentValues(); String event=((StringReference)args.get(0)).value();
        ObjectReference input=object(args.get(1)), latch=object(args.get(2)); Generation g=generation(input);
        Map<String,Object> r=row(event,e.thread()); attach(r,g);
        r.put("targetSeq",number(args.get(3))); r.put("targetNanos",number(args.get(4)));
        if(latch!=null) { r.put("latchJdiId",id(latch)); r.put("latchCount",count(latch)); }
        if(event.equals("fixture-ready")) { require(!fixtureReady,"One prepared fixture"); fixtureReady=true; loopThread=e.thread().uniqueID(); }
        if(event.equals("await-enter")) {
            require(g!=null && g.proxyPublication>0 && g.awaitEntry==0 && latch!=null,"Original published/proxy input, exactly one await entry");
            require(g.publisher==e.thread().uniqueID(),"Publication and await use same original caller");
            g.awaitEntry=seq; g.waiter=e.thread().uniqueID(); g.latch=latch; g.latchId=id(latch); attach(r,g);
        }
        if(event.equals("await-return")) {
            require(g!=null && g.awaitEntry>0 && g.awaitReturn==0 && id(latch)==id(g.latch) && count(latch)==0,"Original await return observes released same latch");
            require(e.thread().uniqueID()==g.waiter,"Original await returns on same owner"); g.awaitReturn=seq;
        }
        if(event.equals("dispatch-consume")) {
            require(g!=null && g.consumed==0 && g.awaitEntry>0 && count(g.latch)==1 && id(currentInput())==g.objectId,"EDT dispatch uses live, unconsumed original input");
            require(g.displayCompletions>0 && g.displayDepth==0 && e.thread().name().startsWith("AWT-EventQueue"),"Dispatch follows original prompt runnable on EDT");
            g.consumed=seq; attach(r,g);
        }
        if(event.startsWith("authorized-question-")) {
            require(authorizedCount<3 && event.equals("authorized-question-"+authorizedCount),"Exact ordered C1 questions");
            ObjectReference a=object(field(object(field(root,"authorization")),"value")); require(a!=null,"Published C1 authorization");
            Map<String,Object> refs=new LinkedHashMap<>();
            for(String name:List.of("spell","spellView","mana","manaView")) refs.put(name,id(field(a,name)));
            if(authorizedCount==0) { spellId=id(field(a,"spell")); spellViewId=id(field(a,"spellView")); }
            require(spellId!=0 && spellViewId!=0 && spellId==id(field(a,"spell")) && spellViewId==id(field(a,"spellView")),"C1 spell reference continuity across questions");
            if(authorizedCount==2) {
                require(id(field(a,"mana"))!=0 && id(field(a,"manaView"))!=0 && payment!=null && id(latch)==payment.latchId && count(latch)==1,"Original Mountain offer during same unresolved payment");
                manaWorker=e.thread().uniqueID();
            }
            authorizedCount++;
            r.put("authorizedReferences",refs);
        }
        if(event.startsWith("original-")) oracleEvents.add(event);
        if(event.equals("failure")) {
            ObjectReference f=object(field(object(field(root,"failure")),"value"));
            r.put("atomicFailureJdiId",id(f)); r.put("failureClass",f==null?null:f.referenceType().name());
            Value message=field(f,"detailMessage"); r.put("failureMessage",message instanceof StringReference s?s.value():null);
            emit(r); throw new IllegalStateException("Owner received recorded target failure; see exact artifact in trace");
        }
        if(event.equals("scenario-complete")) {
            require(normalCount==5 && authorizedCount==3 && g!=null && g.type.equals(INPUT+"InputPassPriority") && g.awaitEntry>0 && g.consumed==0 && count(g.latch)==1
                    && g.displayCompletions>0 && g.displayDepth==0,"Five genuine normal generations; final displayed human priority unanswered");
            require(oracleEvents.containsAll(Set.of("original-cast-oracle","original-mountain-tapped","original-life-20-to-17","original-spell-resolved","original-bolt-graveyard")),"Original result oracle events received"); complete=true;
        }
        emit(r);
    }
    private List<Map<String,Object>> summaries() {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Generation g:generations.values()) { Map<String,Object> r=new LinkedHashMap<>(); attach(r,g);
            r.put("proxyPublishedAt",g.proxyPublication); r.put("awaitReturnedAt",g.awaitReturn); r.put("removedAt",g.removed);
            r.put("promptCallbacks",g.promptCallbacks); r.put("displayCompletions",g.displayCompletions);
            r.put("waiterJdiId",g.waiter); r.put("latchJdiId",g.latchId); rows.add(r); }
        return rows;
    }
    private static Thread copy(InputStream stream,Path file,java.util.concurrent.atomic.AtomicReference<IOException> failure) {
        Thread t=new Thread(()->{try(InputStream in=stream;OutputStream out=Files.newOutputStream(file)){in.transferTo(out);}catch(IOException e){failure.compareAndSet(null,e);}},"Lifecycle-output");
        t.setDaemon(true); t.start(); return t;
    }
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]); Files.createDirectories(output);
        Map<String,Object> result=new LinkedHashMap<>(); int status=1; Process child=null; long pid=0;
        Thread stdout=null,stderr=null; var copyFailure=new java.util.concurrent.atomic.AtomicReference<IOException>();
        long start=System.nanoTime(); boolean killed=false;
        try(PrintWriter trace=new PrintWriter(Files.newBufferedWriter(output.resolve("ordering.jsonl")))) {
            BoltLifecycleObserver observer=new BoltLifecycleObserver(trace);
            try {
                LaunchingConnector connector=Bootstrap.virtualMachineManager().launchingConnectors().stream()
                        .filter(c->c.name().equals("com.sun.jdi.CommandLineLaunch") && c.transport().name().equals("dt_shmem"))
                        .findFirst().orElseThrow(()->new IllegalStateException("No approved dt_shmem connector; no socket fallback"));
                Map<String,Connector.Argument> launch=connector.defaultArguments();
                launch.get("home").setValue(System.getProperty("java.home"));
                String opens=System.getProperty("lifecycle.opens"); require(opens!=null,"Original POM opens supplied by launcher");
                launch.get("options").setValue(opens+" -cp \""+System.getProperty("java.class.path")+"\"");
                launch.get("main").setValue(MAIN+" \""+output.resolve("driver-result.json")+"\""); launch.get("suspend").setValue("true");
                VirtualMachine vm=connector.launch(launch); child=vm.process(); pid=child.pid();
                result.put("targetPid",pid); result.put("transport",connector.transport().name()); result.put("networkListener",false);
                Files.writeString(output.resolve("target-process.json"),LifecycleJson.encode(Map.of("pid",pid,"parentPid",ProcessHandle.current().pid()))+"\n");
                stdout=copy(child.getInputStream(),output.resolve("target.stdout.log"),copyFailure);
                stderr=copy(child.getErrorStream(),output.resolve("target.stderr.log"),copyFailure);
                for(String name:CLASSES) { ClassPrepareRequest r=vm.eventRequestManager().createClassPrepareRequest(); r.addClassFilter(name);
                    r.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); r.enable(); }
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(190); boolean dead=false;
                vm.resume();
                while(!dead && System.nanoTime()<deadline) {
                    EventSet set=vm.eventQueue().remove(250); if(set==null) continue;
                    try {
                        for(Event event:set) {
                            if(event instanceof ClassPrepareEvent e) observer.install(vm,e.referenceType());
                            else if(event instanceof BreakpointEvent e) observer.observe(e);
                            else if(event instanceof MethodExitEvent e) observer.displayExit(e);
                            else if(event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) dead=true;
                        }
                    } catch(Throwable t) {
                        // Terminate only this owned target before any failing event-thread can resume.
                        if(child.isAlive()) { killed=true; child.destroyForcibly(); }
                        throw t;
                    } finally { if(!killed) try { set.resume(); } catch(VMDisconnectedException ended) { dead=true; } }
                }
                require(dead,"Observer deadline; stop without retry");
                require(child.waitFor(10,TimeUnit.SECONDS),"Owned target exit deadline");
                stdout.join(5000); stderr.join(5000); require(!stdout.isAlive() && !stderr.isAlive(),"Complete target logs required");
                require(copyFailure.get()==null,"Target output preservation failed");
                require(child.exitValue()==0 && observer.complete,"Original driver and ordering witness both completed");
                result.put("outcome","PASS"); status=0;
            } catch(Throwable t) { result.put("outcome","FAIL"); result.put("failure",t.toString()); t.printStackTrace(); }
            finally {
                try {
                    if(child!=null && child.isAlive()) { killed=true; child.destroyForcibly(); child.waitFor(10,TimeUnit.SECONDS); }
                    if(stdout!=null) stdout.join(5000); if(stderr!=null) stderr.join(5000);
                } catch(Throwable secondary) {
                    status=1; result.put("outcome","FAIL"); result.put("terminationObservationFailure",secondary.toString());
                }
                boolean logsComplete=stdout!=null && stderr!=null && !stdout.isAlive() && !stderr.isAlive() && copyFailure.get()==null;
                result.put("targetLogsComplete",logsComplete);
                if(!logsComplete) { status=1; result.put("outcome","FAIL"); result.put("outputFailure",String.valueOf(copyFailure.get())); }
                result.put("targetPid",pid); result.put("targetHasExited",child!=null && !child.isAlive());
                if(child!=null && !child.isAlive()) result.put("targetExit",child.exitValue());
                result.put("targetForceTerminated",killed); result.put("generations",observer.summaries());
                result.put("normalGenerations",observer.normalCount); result.put("oracleEvents",new TreeSet<>(observer.oracleEvents));
                result.put("authorizedQuestions",observer.authorizedCount); result.put("paymentWorkerJdiId",observer.manaWorker);
                result.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0); result.put("targetMethodInvocations",0);
                result.put("workerDeathClaim",false); result.put("graphProjection",false); result.put("awaitEntryHold",false);
                Files.writeString(output.resolve("ordering-result.json"),LifecycleJson.encode(result)+"\n");
            }
        }
        System.exit(status);
    }
}
