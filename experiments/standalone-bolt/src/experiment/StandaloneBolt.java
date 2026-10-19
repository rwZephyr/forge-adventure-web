package experiment;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.match.input.*;
import forge.gamemodes.net.*;
import forge.gamemodes.net.event.*;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.net.TestUtils;
import forge.player.GamePlayerUtil;
import forge.player.LobbyPlayerHuman;
import forge.player.PlayerControllerHuman;
import forge.util.MyRandom;

import javax.swing.SwingUtilities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable, listener-free lifecycle probe. Not a service, projector or gameplay implementation.
 * Fixture arrangement follows ProtocolGuiGameInProcessTest.setup; original Forge owns play.
 */
public final class StandaloneBolt {
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final BlockingQueue<Prompt> prompts = new LinkedBlockingQueue<>();
    private final HostedMatch match = new HostedMatch();
    private final ProbeGui gui = new ProbeGui();
    private final RegisteredPlayer human;
    private final RegisteredPlayer ai;
    private volatile boolean fixtureReady;
    private volatile Thread fixtureThread;
    private int bootstrapReplies;

    private record Prompt(Input input, String ok, String cancel, boolean enabled, Thread caller) { }
    private record Wait(Input input, CountDownLatch latch, Thread owner) { }

    private static String ref(Object o) {
        return o == null ? "null" : o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }
    private static synchronized void log(String event, String detail) {
        Thread t = Thread.currentThread();
        System.out.println("[standalone] " + event + " thread=" + t.getName() + "#" + t.getId() + " " + detail);
    }
    private void require(boolean condition, String message) {
        if (!condition) throw abort(new IllegalStateException(message));
    }
    private RuntimeException abort(Throwable t) {
        failure.compareAndSet(null, t);
        return t instanceof RuntimeException r ? r : new IllegalStateException(t);
    }
    private void check() {
        Throwable t = failure.get();
        if (t != null) throw new IllegalStateException("Owner received asynchronous failure", t);
    }

    private final class Endpoint implements IRemote {
        @Override public void send(NetEvent event) {
            try {
                GuiGameEvent e = (GuiGameEvent) event;
                // Deliberately do not retain/traverse raw views, deltas, events or card identities.
                if (e.getMethod() != ProtocolMethod.updateButtons) return;
                PlayerControllerHuman c = gui.controller;
                if (c == null) return;
                Input input = c.getInputProxy().getInput(); // AtomicReference in original InputProxy.
                if (input == null || input instanceof InputLockUI) return;
                require(SwingUtilities.isEventDispatchThread(), "decision prompt must arrive on EDT");
                Object[] a = e.getObjects();
                prompts.add(new Prompt(input, (String) a[1], (String) a[2], Boolean.TRUE.equals(a[3]), Thread.currentThread()));
                log("prompt", "input=" + ref(input) + " buttons=" + a[1] + "/" + a[2]);
            } catch (Throwable t) { throw abort(t); }
        }
        @Override public Object sendAndWait(IdentifiableNetEvent event) {
            // No spell is cast in D3a. Any required synchronous question is unexpected.
            RuntimeException t = abort(new IllegalStateException("Unanswered required question: " + ((GuiGameEvent) event).getMethod()));
            log("required-question-failure", "recordedBeforeThrow=" + (failure.get() == t));
            throw t;
        }
    }

    private final class ProbeGui extends ProtocolGuiGame {
        private volatile PlayerControllerHuman controller;
        private volatile Wait waiting;
        ProbeGui() { super(new Endpoint()); }
        @Override public void setOriginalGameController(PlayerView player, IGameController original) {
            super.setOriginalGameController(player, original);
            require(controller == null && original instanceof PlayerControllerHuman, "one original human controller binding");
            controller = (PlayerControllerHuman) original;
            log("controller-bound", "controller=" + ref(original));
        }
        @Override public void awaitInput(CountDownLatch done) throws InterruptedException {
            try {
                PlayerControllerHuman c = controller;
                Input input = c.getInputProxy().getInput();
                require(!SwingUtilities.isEventDispatchThread(), "original waiter outside EDT");
                require(input == c.getInputQueue().getInput(), "proxy and original queue reference agree at await entry");
                Wait w = new Wait(input, done, Thread.currentThread());
                waiting = w; // Volatile publication of immutable lifecycle references only.
                log("await-enter", "input=" + ref(input) + " latch=" + ref(done) + " count=" + done.getCount());
                super.awaitInput(done); // Original IGuiGame CountDownLatch.await, never replaced.
                log("await-return", "input=" + ref(input) + " count=" + done.getCount());
                require(waiting == w, "single original input waiter");
                waiting = null;
            } catch (InterruptedException e) { abort(e); throw e; }
            catch (Throwable t) { throw abort(t); }
        }
    }

    private StandaloneBolt() {
        Deck hd = new Deck("Human Bolt"); hd.getMain().add("Mountain", 39); hd.getMain().add("Lightning Bolt", 1);
        Deck ad = new Deck("AI"); ad.getMain().add("Mountain", 40);
        human = new RegisteredPlayer(hd).setPlayer(new LobbyPlayerHuman("Human Bolt"));
        ai = new RegisteredPlayer(ad).setPlayer(GamePlayerUtil.createAiPlayer("AI"));
    }

    /** Only graph reads/writes added by this experiment: original start hook, before the phase loop. */
    private void arrange() {
        try {
            require(!SwingUtilities.isEventDispatchThread(), "fixture hook outside EDT");
            fixtureThread = Thread.currentThread();
            var game = match.getGame();
            Player hp = null, ap = null;
            for (Player p : game.getPlayers()) {
                if (p.getRegisteredPlayer() == human) hp = p;
                if (p.getRegisteredPlayer() == ai) ap = p;
                for (Card c : new ArrayList<>(p.getAllCards())) game.getAction().moveToLibrary(c, null);
            }
            require(hp != null && ap != null && hp.getController() == gui.controller, "original registered controller identity");
            require(ap.getController() instanceof forge.ai.PlayerControllerAi, "original AI controller");
            hp.setLife(20, null); ap.setLife(20, null);
            Card bolt = null, mountain = null;
            for (Card c : new ArrayList<>(hp.getCardsIn(ZoneType.Library))) {
                if (c.getName().equals("Lightning Bolt")) bolt = game.getAction().moveToHand(c, null);
                else if (c.getName().equals("Mountain") && mountain == null)
                    mountain = game.getAction().moveToPlay(c, null, forge.game.ability.AbilityKey.newMap());
            }
            game.getAction().moveToHand(ap.getCardsIn(ZoneType.Library).get(0), null);
            require(bolt != null && mountain != null && !mountain.isTapped(), "original Bolt/Mountain fixture");
            require(game.getStack().isEmpty() && hp.getManaPool().isEmpty() && ap.getManaPool().isEmpty(), "no synthetic stack or prepaid mana");
            require(!hp.getCardsIn(ZoneType.Library).isEmpty() && !ap.getCardsIn(ZoneType.Library).isEmpty(), "nonempty original libraries");
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, hp, false, 1);
            game.copyLastState();
            log("fixture-arranged", "humanController=" + ref(hp.getController()) + " aiController=" + ref(ap.getController())
                    + " humanLife=20 aiLife=20 humanHandBolt=1 untappedMountain=1 phase=MAIN1 turn=1 observationTime=setup-only");
            fixtureReady = true;
        } catch (Throwable t) { throw abort(t); }
    }

    private void onEdt(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        SwingUtilities.invokeLater(task);
        task.get(5, TimeUnit.SECONDS); // Does not cancel/release original input on failure.
    }
    private void run() throws Exception {
        require(!HostingServerIsActive(), "no hosting server");
        match.setStartGameHook(this::arrange);
        match.startMatch(GameType.Constructed, null, List.of(human, ai), human, gui);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            check();
            Prompt p = prompts.poll(100, TimeUnit.MILLISECONDS);
            if (p == null) continue;
            if (!fixtureReady) {
                onEdt(() -> {
                    check();
                    require(!fixtureReady && gui.controller.getInputProxy().getInput() == p.input(), "bootstrap decision still live");
                    boolean play = p.input() instanceof InputConfirm && p.ok().equals("Play") && p.cancel().equals("Draw");
                    boolean keep = p.input() instanceof InputConfirmMulligan && p.ok().equals("Keep") && p.cancel().equals("Mulligan");
                    require(p.enabled() && (play || keep), "only explicit original play-first/keep bootstrap choices");
                    require(++bootstrapReplies <= 2, "bounded bootstrap decisions");
                    log("bootstrap-reply", "input=" + ref(p.input()) + " choice=" + p.ok());
                    gui.controller.selectButtonOk();
                });
                continue;
            }
            require(p.input() instanceof InputPassPriority, "first fixture decision is original human priority");
            Wait w;
            do {
                check(); w = gui.waiting;
                if (w != null && w.input() == p.input() && w.owner().getState() == Thread.State.WAITING) break;
                require(System.nanoTime() < deadline, "park observation deadline");
                Thread.sleep(10);
            } while (true);
            final Wait parked = w;
            onEdt(() -> {
                check();
                require(Thread.currentThread() == p.caller(), "same real EDT displays and checks prompt");
                require(gui.controller.getInputProxy().getInput() == p.input() && gui.controller.getInputQueue().getInput() == p.input(), "same live input reference, no graph read");
                log("edt-lifecycle-check", "input=" + ref(p.input()) + " noActionDispatched=true");
            });
            require(gui.waiting == parked && parked.latch().getCount() == 1 && parked.owner() == fixtureThread
                    && parked.owner().getState() == Thread.State.WAITING, "same original game loop parked with latch closed");
            var frames = Arrays.asList(parked.owner().getStackTrace());
            require(frames.stream().anyMatch(f -> f.getClassName().equals("java.util.concurrent.CountDownLatch") && f.getMethodName().equals("await")), "original latch await stack");
            require(frames.stream().anyMatch(f -> f.getClassName().equals("forge.gamemodes.match.input.InputSyncronizedBase") && f.getMethodName().equals("awaitLatchRelease")), "original input lifecycle stack");
            log("parked-human-input", "controller=" + ref(gui.controller) + " input=" + ref(p.input()) + " latch=" + ref(parked.latch())
                    + " count=1 owner=" + parked.owner().getName() + "#" + parked.owner().getId() + " state=WAITING sameReferenceChecks=true graphCapture=false");
            for (var e : Thread.getAllStackTraces().entrySet()) {
                Thread t = e.getKey();
                if (!t.getName().startsWith("Game") && !t.getName().startsWith("AWT-EventQueue")) continue;
                log("worker", "name=" + t.getName() + " id=" + t.getId() + " daemon=" + t.isDaemon() + " state=" + t.getState());
                for (StackTraceElement f : e.getValue()) System.out.println("  at " + f);
            }
            check();
            return; // No concede, priority pass, latch release, match shutdown or graph cleanup.
        }
        throw abort(new TimeoutException("No original parked fixture input before deadline"));
    }
    private static boolean HostingServerIsActive() { return forge.gamemodes.net.server.HostingServer.isHosting(); }

    public static void main(String[] args) throws Exception {
        Path result = Path.of(args[0]);
        // This disposable process owns no save/campaign. Bound shutdown even if a JVM hook stalls.
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(150_000); } catch (InterruptedException e) { return; }
            System.err.println("[standalone] DEADLINE: disposing only this diagnostic JVM; no in-game cleanup");
            Runtime.getRuntime().halt(124);
        }, "Standalone-deadline");
        watchdog.setDaemon(true); watchdog.start();
        int status = 1;
        try {
            TestUtils.ensureFModelInitialized(); // Unchanged test-only bootstrap; no TestNG lifecycle.
            FModel.getPreferences().setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, false);
            FModel.getPreferences().setPref(FPref.UI_RECORD_REPLAYS, false);
            FModel.getPreferences().setPref(FPref.PLAYER_NAME, "Bolt Fixture");
            MyRandom.setRandom(new Random(7));
            StandaloneBolt probe = new StandaloneBolt();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> { probe.abort(e); e.printStackTrace(); });
            probe.run();
            probe.check();
            Files.writeString(result, "{\"outcome\":\"PASS\",\"parkedHumanInput\":true,\"graphCapture\":false,\"gameplayCleanup\":false,\"testngLifecycle\":false}\n");
            status = 0;
        } catch (Throwable t) {
            t.printStackTrace();
            Files.writeString(result, "{\"outcome\":\"FAIL\",\"graphCapture\":false,\"gameplayCleanup\":false}\n");
        } finally {
            log("process-exit", "status=" + status + " noGameMutationForCleanup=true");
            System.out.flush(); System.err.flush();
            System.exit(status); // Isolated diagnostic lifetime, NOT a production match lifecycle API.
        }
    }
}
