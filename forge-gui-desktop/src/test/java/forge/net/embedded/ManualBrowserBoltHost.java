package forge.net.embedded;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static forge.net.embedded.ProtocolGuiGameInProcessTest.*;

/** Disposable test host. Constructor/policy methods never open a socket or touch Forge. */
final class ManualBrowserBoltHost implements AutoCloseable {
    interface Session {
        Frame frame();
        CompletableFuture<Receipt> submit(Action action);
    }
    record Asset(String type, byte[] bytes) { }
    record Request(String method, String path, String query, Map<String,String> headers, byte[] body) { }
    record Response(int status, String type, byte[] body) { }
    private record Ticket(Payload payload, CompletableFuture<Receipt> result) { }
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    static final int MAX_BODY = 4096;
    private final Session session;
    private final String address, authority, origin, capability;
    private final int port;
    private final Map<String,Asset> assets;
    private final Path output;
    private final ConcurrentMap<String,Ticket> receipts = new ConcurrentHashMap<>();
    private final AtomicReference<String> outcome = new AtomicReference<>("ACTIVE");
    private final AtomicReference<String> shutdown = new AtomicReference<>("NOT_STARTED");
    private final AtomicBoolean finish = new AtomicBoolean(), abort = new AtomicBoolean();
    private final AtomicInteger rejected = new AtomicInteger();
    private final AtomicReference<String> transportFailure = new AtomicReference<>("");
    private HttpServer server;
    private ExecutorService executor;

    ManualBrowserBoltHost(Session session, String address, int port, String capability, Map<String,Asset> assets, Path output) {
        if (!Set.of("127.0.0.1", "100.122.229.121").contains(address) || port != 8765) throw new IllegalArgumentException("Unapproved bind configuration");
        if (capability == null || !capability.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid private capability");
        this.session=session; this.address=address; this.port=port; this.capability=capability;
        authority=address+":"+port; origin="http://"+authority; this.assets=Map.copyOf(assets); this.output=output;
    }
    static ManualBrowserBoltHost forApprovedRun(Session session) throws IOException {
        if (!Boolean.getBoolean("forge.browser.listenerApproved")) throw new IllegalStateException("Separate listener approval required");
        Path repo=Path.of(System.getProperty("forge.browser.repo")).toAbsolutePath().normalize();
        Path output=Path.of(System.getProperty("forge.browser.output")).toAbsolutePath().normalize();
        if (!output.startsWith(repo.resolve(".local-tools/browser-bolt"))) throw new IllegalArgumentException("Output must be isolated");
        Map<String,Asset> assets=new HashMap<>();
        for (String name : List.of("live.html","live-client.js","battlefield.js","battlefield.css"))
            assets.put("/experiments/battlefield-static/"+name, new Asset(name.endsWith("html")?"text/html; charset=utf-8":name.endsWith("js")?"text/javascript; charset=utf-8":"text/css; charset=utf-8",
                    Files.readAllBytes(repo.resolve("experiments/battlefield-static/"+name))));
        for (String name : List.of("adv_bg_match.jpg","IMG_CARDBG_L.png","IMG_CARDBG_R.png","sprite_manaicons.png"))
            assets.put("/forge-gui/res/skins/default/"+name, new Asset(name.endsWith("jpg")?"image/jpeg":"image/png",Files.readAllBytes(repo.resolve("forge-gui/res/skins/default/"+name))));
        byte[] random=new byte[32]; new SecureRandom().nextBytes(random);
        String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new ManualBrowserBoltHost(session,System.getProperty("forge.browser.bind","127.0.0.1"),8765,secret,assets,output);
    }
    void start() throws IOException {
        if (!Boolean.getBoolean("forge.browser.listenerApproved") || output == null) throw new IllegalStateException("Separate listener approval required");
        // CREATE_NEW prevents accidental reuse/overwrite. Never print the capability or put it in a URL.
        Files.writeString(output.resolve("capability.txt"),capability,StandardOpenOption.CREATE_NEW);
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByName(address),port),0);
        executor=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"ManualBrowserHttp");t.setDaemon(true);return t;});
        server.setExecutor(executor);
        server.createContext("/",exchange->{
            try {
                Map<String,String> headers=new HashMap<>();
                exchange.getRequestHeaders().forEach((k,v)->headers.put(k.toLowerCase(Locale.ROOT),v.size()==1?v.get(0):"<multiple>"));
                Request metadata=new Request(exchange.getRequestMethod(),exchange.getRequestURI().getRawPath(),exchange.getRequestURI().getRawQuery(),headers,new byte[0]);
                Response denied=preflight(metadata);
                Response response=denied!=null?denied:handle(new Request(metadata.method(),metadata.path(),metadata.query(),headers,exchange.getRequestBody().readNBytes(MAX_BODY+1)));
                var out=exchange.getResponseHeaders(); out.set("Content-Type",response.type());out.set("Cache-Control","no-store");
                out.set("X-Content-Type-Options","nosniff");out.set("X-Frame-Options","DENY");out.set("Referrer-Policy","no-referrer");
                out.set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
                exchange.sendResponseHeaders(response.status(),response.body().length);
                exchange.getResponseBody().write(response.body());
            } catch (Exception failure) {
                // Never log request metadata, exception messages, headers or bodies.
                transportFailed(failure);
            } finally { exchange.close(); }
        });
        server.start(); shutdown.set("LISTENING");
        System.out.println("[manual-browser] listener=started privateCapability=separate-file");
    }
    private Response preflight(Request r) {
        if (!authority.equals(r.headers().get("host")) || r.query()!=null) return error(400,"invalidRequest");
        String suppliedOrigin=r.headers().get("origin");
        if (suppliedOrigin!=null && !origin.equals(suppliedOrigin) || "cross-site".equals(r.headers().get("sec-fetch-site"))) return error(403,"originRejected");
        if (r.path().startsWith("/api/")) {
            String supplied=r.headers().getOrDefault("authorization","");
            if (!MessageDigest.isEqual(("Bearer "+capability).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) return error(401,"unauthorized");
            if (r.method().equals("POST") && !origin.equals(suppliedOrigin)) return error(403,"originRejected");
            if (r.method().equals("POST") && !"application/json".equals(r.headers().get("content-type"))) return error(415,"jsonRequired");
        }
        return null;
    }
    Response handle(Request r) {
        Response denied=preflight(r); if(denied!=null)return denied;
        if(r.body().length>MAX_BODY)return error(413,"bodyTooLarge");
        try {
            if (r.method().equals("GET") && r.path().equals("/api/state")) {
                Map<String,Object> state=new LinkedHashMap<>();state.put("frame",session.frame());state.put("outcome",outcome.get());state.put("shutdown",shutdown.get());
                return json(200,state);
            }
            if (r.method().equals("POST")) {
                switch(r.path()) {
                    case "/api/action": {
                        Action action=decodeAction(r.body());
                        if(!outcome.get().equals("ACTIVE") || abort.get())return error(409,"sessionNotActive");
                        synchronized(receipts){
                            Ticket previous=receipts.get(action.id());
                            if(previous!=null)return previous.payload().equals(action.payload())?receipt(previous.result()):json(409,new Receipt("rejected","actionIdConflict"));
                            if(receipts.size()>=256)return error(429,"actionLimit");
                            CompletableFuture<Receipt> result;
                            try{result=session.submit(action);}catch(RuntimeException failure){transportFailed(failure);return error(500,"ownerFailure");}
                            receipts.put(action.id(),new Ticket(action.payload(),result));return receipt(result);
                        }
                    }
                    case "/api/receipt": {
                        String id=decodeId(r.body());var result=receipts.get(id);
                        return result==null?error(404,"unknownAction"):receipt(result.result());
                    }
                    case "/api/finish":
                        requireEmpty(r.body());if(!outcome.get().equals("TERMINAL"))return error(409,"notTerminal");
                        finish.set(true);return json(202,Map.of("status","harnessFinishRequested"));
                    case "/api/abort":
                        requireEmpty(r.body());abort.set(true);return json(202,Map.of("status","harnessAbortRequested"));
                    default: return error(404,"notFound");
                }
            }
            if(r.method().equals("GET") && assets.containsKey(r.path())){Asset asset=assets.get(r.path());return new Response(200,asset.type(),asset.bytes());}
            return error(404,"notFound");
        }catch(IllegalArgumentException | IllegalStateException | IOException malformed){return error(400,"invalidJson");}
    }
    private Response receipt(CompletableFuture<Receipt> future) {
        if(!future.isDone())return json(202,Map.of("status","pending"));
        if(future.isCompletedExceptionally() || future.isCancelled())return error(500,"ownerFailure");
        Receipt value=future.getNow(null);return json(value.code().equals("actionIdConflict")?409:200,value);
    }
    private Response error(int status,String code){rejected.incrementAndGet();return json(status,Map.of("status","rejected","code",code));}
    private static Response json(int status,Object value){return new Response(status,"application/json; charset=utf-8",JSON.toJson(value).getBytes(StandardCharsets.UTF_8));}
    private static JsonReader reader(byte[] bytes) {
        JsonReader reader=new JsonReader(new StringReader(new String(bytes,StandardCharsets.UTF_8)));reader.setStrictness(Strictness.STRICT);return reader;
    }
    private static String string(JsonReader reader) throws IOException {
        if(reader.peek()!=JsonToken.STRING)throw new IllegalArgumentException();String value=reader.nextString();
        if(!value.matches("[A-Za-z0-9._:-]{1,64}"))throw new IllegalArgumentException();return value;
    }
    private static long number(JsonReader reader) throws IOException {
        if(reader.peek()!=JsonToken.NUMBER)throw new IllegalArgumentException();String value=reader.nextString();
        if(!value.matches("[0-9]{1,19}"))throw new IllegalArgumentException();return Long.parseLong(value);
    }
    private static Payload payload(JsonReader reader) throws IOException {
        reader.beginObject();Set<String> keys=new HashSet<>();Map<String,String> text=new HashMap<>();long revision=-1,version=-1;
        while(reader.hasNext()){
            String key=reader.nextName();if(!keys.add(key))throw new IllegalArgumentException();
            switch(key){case "type","match","session","prompt","choice"->text.put(key,string(reader));case "revision"->revision=number(reader);case "version"->version=number(reader);default->throw new IllegalArgumentException();}
        }
        reader.endObject();if(!keys.equals(Set.of("type","version","match","session","revision","prompt","choice")) || version>Integer.MAX_VALUE)throw new IllegalArgumentException();
        return new Payload(text.get("type"),(int)version,text.get("match"),text.get("session"),revision,text.get("prompt"),text.get("choice"));
    }
    static Action decodeAction(byte[] bytes) throws IOException {
        if(bytes.length>MAX_BODY)throw new IllegalArgumentException();try(JsonReader reader=reader(bytes)){
            reader.beginObject();Set<String> keys=new HashSet<>();String id=null;Payload payload=null;
            while(reader.hasNext()){String key=reader.nextName();if(!keys.add(key))throw new IllegalArgumentException();switch(key){case "id"->id=string(reader);case "payload"->payload=payload(reader);default->throw new IllegalArgumentException();}}
            reader.endObject();if(!keys.equals(Set.of("id","payload")) || reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();return new Action(id,payload);
        }
    }
    private static String decodeId(byte[] bytes) throws IOException {
        try(JsonReader reader=reader(bytes)){reader.beginObject();if(!reader.nextName().equals("id"))throw new IllegalArgumentException();String id=string(reader);if(reader.hasNext())throw new IllegalArgumentException();reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();return id;}
    }
    private static void requireEmpty(byte[] bytes) throws IOException {
        try(JsonReader reader=reader(bytes)){reader.beginObject();if(reader.hasNext())throw new IllegalArgumentException();reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();}
    }
    boolean abortRequested(){return abort.get();}
    boolean transportFailed(){return !transportFailure.get().isEmpty();}
    private void transportFailed(Exception failure){String type=failure.getClass().getName();transportFailure.compareAndSet("",type.substring(0,Math.min(type.length(),120)));abort.set(true);}
    boolean finishRequested(){return finish.get();}
    void outcome(String value){if(!Set.of("ACTIVE","TERMINAL","PASSED","ABORTED","EXPIRED","FAILED").contains(value))throw new IllegalArgumentException();outcome.set(value);}
    void failUnlessEnded(){outcome.updateAndGet(value->Set.of("ABORTED","EXPIRED").contains(value)?value:"FAILED");}
    String currentOutcome(){return outcome.get();}
    String shutdownState(){return shutdown.get();}
    @Override public synchronized void close() throws Exception {
        if(shutdown.get().equals("CLOSED"))return;
        if(shutdown.get().equals("FAILED"))throw new IllegalStateException("Prior HTTP shutdown failed; no retry");
        Throwable primary=null;
        try{if(server!=null)server.stop(1);}catch(Throwable t){primary=t;}
        try{if(executor!=null){executor.shutdownNow();if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("HTTP executor did not exit");}}
        catch(Throwable t){if(primary==null)primary=t;else primary.addSuppressed(t);}
        shutdown.set(primary==null?"CLOSED":"FAILED");
        if(primary!=null){if(primary instanceof Exception e)throw e;throw new AssertionError(primary);}
    }
    void writeOutcome() throws IOException {
        Map<String,Object> result=Map.of("outcome",outcome.get(),"shutdown",shutdown.get(),"harnessCleanupIsGameplay",false,"rejectedRequests",rejected.get(),"transportFailureType",transportFailure.get());
        if(output!=null)Files.writeString(output.resolve("manual-host-result.json"),JSON.toJson(result)+"\n");
        System.out.println("[manual-browser] outcome="+outcome.get()+" shutdown="+shutdown.get()+" harnessCleanupIsGameplay=false");
    }
}
