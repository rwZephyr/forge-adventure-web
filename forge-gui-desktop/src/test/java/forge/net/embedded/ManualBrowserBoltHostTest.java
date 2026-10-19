package forge.net.embedded;

import org.testng.annotations.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static forge.net.embedded.ProtocolGuiGameInProcessTest.*;
import static org.testng.Assert.*;

/** No sockets, FModel, controllers or game simulation: direct codec/HTTP-policy calls only. */
public class ManualBrowserBoltHostTest {
    private static final String SECRET="A".repeat(43), ORIGIN="http://127.0.0.1:8765";
    private static final String ACTION="{\"id\":\"browser-1\",\"payload\":{\"type\":\"action\",\"version\":1,\"match\":\"match\",\"session\":\"session\",\"revision\":1,\"prompt\":\"prompt\",\"choice\":\"choice\"}}";
    private static final class Peer implements ManualBrowserBoltHost.Session {
        final AtomicInteger calls=new AtomicInteger();
        final CompletableFuture<Receipt> pending=new CompletableFuture<>();
        final Frame frame=new Frame("match","session",1,1,"MAIN1",List.of(new Seat("human",true,20),new Seat("ai",false,20)),List.of(),List.of(),null,new Decision("prompt","priority","",List.of(),1,1,List.of(new Choice("choice","cast","public"))));
        public Frame frame(){return frame;}
        public CompletableFuture<Receipt> submit(Action action){calls.incrementAndGet();return pending;}
    }
    private static ManualBrowserBoltHost host(Peer peer){return new ManualBrowserBoltHost(peer,"127.0.0.1",8765,SECRET,Map.of("/experiments/battlefield-static/live.html",new ManualBrowserBoltHost.Asset("text/html",new byte[0])),null);}
    private static Map<String,String> headers(){return new HashMap<>(Map.of("host","127.0.0.1:8765","origin",ORIGIN,"authorization","Bearer "+SECRET,"content-type","application/json"));}
    private static ManualBrowserBoltHost.Request request(String method,String path,String body,Map<String,String> headers){return new ManualBrowserBoltHost.Request(method,path,null,headers,body.getBytes(StandardCharsets.UTF_8));}
    private static ManualBrowserBoltHost.Response call(ManualBrowserBoltHost host,String method,String path,String body){return host.handle(request(method,path,body,headers()));}
    private static String text(ManualBrowserBoltHost.Response response){return new String(response.body(),StandardCharsets.UTF_8);}

    @Test public void constructorCannotListenAndManualTestIsOptIn() throws Exception {
        var peer=new Peer();var host=host(peer);
        assertEquals(host.shutdownState(),"NOT_STARTED");assertEquals(peer.calls.get(),0);
        assertThrows(IllegalStateException.class,host::start); // null output refuses even if an outside property were supplied.
        assertFalse(Boolean.getBoolean("forge.browser.listenerApproved"),"Listener-free verification must not supply approval");
        assertThrows(org.testng.SkipException.class,()->new ProtocolGuiGameInProcessTest().manualBrowserBoltSession());
        host.close();assertEquals(host.shutdownState(),"CLOSED");
    }
    @Test public void authorizationIsSeparateFromClientSessionAndNeverDisclosed(){
        var peer=new Peer();var host=host(peer);var missing=headers();missing.remove("authorization");
        var rejected=host.handle(request("GET","/api/state","",missing));assertEquals(rejected.status(),401);
        missing.put("authorization","Bearer session");assertEquals(host.handle(request("POST","/api/action",ACTION,missing)).status(),401);
        assertEquals(peer.calls.get(),0);assertFalse(text(rejected).contains(SECRET));
        assertFalse(text(call(host,"GET","/api/state","")).contains(SECRET));
    }
    @Test public void originHostQueryAndContentTypeAreRejected(){
        var peer=new Peer();var host=host(peer);
        for(String method:List.of("GET","POST")){var h=headers();h.put("origin","http://foreign.invalid");assertEquals(host.handle(request(method,"/api/state","",h)).status(),403);}
        var h=headers();h.remove("origin");assertEquals(host.handle(request("POST","/api/action",ACTION,h)).status(),403);
        h=headers();h.put("host","foreign.invalid");assertEquals(host.handle(request("GET","/api/state","",h)).status(),400);
        h=headers();h.put("sec-fetch-site","cross-site");assertEquals(host.handle(request("GET","/api/state","",h)).status(),403);
        h=headers();h.put("content-type","text/plain");assertEquals(host.handle(request("POST","/api/action",ACTION,h)).status(),415);
        assertEquals(host.handle(new ManualBrowserBoltHost.Request("GET","/api/state","token=not-accepted",headers(),new byte[0])).status(),400);
        assertEquals(peer.calls.get(),0);
    }
    @DataProvider public Object[][] malformed(){return new Object[][]{
        {"null"},{"[]"},{"{}"},{ACTION+" {}"},{ACTION.replace("\"id\":","\"extra\":1,\"id\":")},
        {ACTION.replace("\"id\":\"browser-1\"","\"id\":\"browser-1\",\"id\":\"duplicate\"")},
        {ACTION.replace("\"revision\":1","\"revision\":1.0")},{ACTION.replace("\"version\":1","\"version\":\"1\"")},
        {ACTION.replace("\"revision\":1","\"revision\":9223372036854775808")},
        {ACTION.replace("\"choice\":\"choice\"","\"choice\":null")}
    };}
    @Test(dataProvider="malformed") public void strictJsonDoesNotReachSubmission(String body){
        var peer=new Peer();assertEquals(call(host(peer),"POST","/api/action",body).status(),400);assertEquals(peer.calls.get(),0);
    }
    @Test public void bodyAndStaticRoutesAreBounded(){
        var peer=new Peer();var host=host(peer);
        assertEquals(call(host,"POST","/api/action","x".repeat(4097)).status(),413);
        for(String path:List.of("/.local-tools/capability.txt","/forge-gui-desktop/pom.xml","/../live.html","/api/unknown"))assertEquals(call(host,"GET",path,"").status(),404);
        assertEquals(call(host,"GET","/experiments/battlefield-static/live.html","").status(),200);assertEquals(peer.calls.get(),0);
    }
    @Test public void receiptPollingDoesNotDispatchOrInventAcceptance() throws Exception {
        var peer=new Peer();var host=host(peer);
        assertEquals(ManualBrowserBoltHost.decodeAction(ACTION.getBytes(StandardCharsets.UTF_8)).payload().revision(),1L);
        assertEquals(call(host,"POST","/api/action",ACTION).status(),202);
        assertEquals(call(host,"POST","/api/action",ACTION).status(),202);
        assertEquals(call(host,"POST","/api/action",ACTION.replace("\"choice\":\"choice\"","\"choice\":\"conflict\"")).status(),409);
        assertEquals(text(call(host,"POST","/api/receipt","{\"id\":\"browser-1\"}")),"{\"status\":\"pending\"}");
        peer.pending.complete(new Receipt("accepted","controllerDispatched"));
        assertTrue(text(call(host,"POST","/api/receipt","{\"id\":\"browser-1\"}")).contains("controllerDispatched"));
        assertEquals(peer.calls.get(),1);
        assertEquals(text(call(host,"GET","/api/state","")),text(call(host,"GET","/api/state","")));
    }
    @Test public void abortExpiryTerminalAckAndShutdownAreSeparate() throws Exception {
        var peer=new Peer();var host=host(peer);
        assertEquals(call(host,"POST","/api/finish","{}").status(),409);assertFalse(host.finishRequested());
        assertEquals(call(host,"POST","/api/abort","{}").status(),202);assertTrue(host.abortRequested());
        assertEquals(host.currentOutcome(),"ACTIVE"); // Only the owner can set the actual outcome.
        host.outcome("EXPIRED");host.failUnlessEnded();host.close();
        assertEquals(host.currentOutcome(),"EXPIRED");assertEquals(host.shutdownState(),"CLOSED");
        var terminal=host(peer);terminal.outcome("TERMINAL");assertEquals(call(terminal,"POST","/api/finish","{}").status(),202);
        assertTrue(terminal.finishRequested());assertEquals(call(terminal,"POST","/api/action",ACTION).status(),409);
        assertEquals(peer.calls.get(),0);terminal.close();
    }
}
