package forge.net.embedded;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.event.GameEvent;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbilityView;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.ProtocolGuiGame;
import forge.gamemodes.net.ProtocolMethod;
import forge.gamemodes.net.event.GuiGameEvent;
import forge.gamemodes.net.event.IdentifiableNetEvent;
import forge.gamemodes.net.event.NetEvent;
import forge.gui.FThreads;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.net.TestUtils;
import forge.player.GamePlayerUtil;
import forge.player.LobbyPlayerHuman;
import forge.trackable.TrackableCollection;
import forge.util.MyRandom;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * A stock {@code PlayerControllerHuman} plays against the AI through {@link ProtocolGuiGame}
 * and an in-process {@link IRemote}. Automated cases use no FServerManager, FGameClient, socket or Netty channel.
 * The separately approved, opt-in manual-browser method alone adds a disposable HTTP listener.
 */
public class ProtocolGuiGameInProcessTest {

    /** In-process endpoint: records every protocol event and answers blocking ones from a script. */
    static final class RecordingRemote implements IRemote {
        final BoltScript script;
        RecordingRemote() { this(null); }
        RecordingRemote(final BoltScript script) { this.script = script; }
        final List<GuiGameEvent> log = Collections.synchronizedList(new ArrayList<>());
        final BlockingQueue<GuiGameEvent> buttonPrompts = new LinkedBlockingQueue<>();
        final Map<ProtocolMethod, AtomicInteger> counts = new ConcurrentHashMap<>();
        final List<String> blockingCalls = Collections.synchronizedList(new ArrayList<>());
        final List<String> threadsSeen = Collections.synchronizedList(new ArrayList<>());
        volatile TrackableCollection<PlayerView> myPlayers;
        volatile int fullStates, deltasWithState, deltasWithEvents, rawEvents, newObjects, changedObjects;
        volatile int wrappedEvents;
        volatile String lastPrompt = "";

        @Override
        public void send(final NetEvent event) {
            final GuiGameEvent ev = (GuiGameEvent) event;
            record(ev);
            final Object[] args = ev.getObjects();
            switch (ev.getMethod()) {
                case openView -> myPlayers = (TrackableCollection<PlayerView>) args[0];
                case setGameView -> fullStates++;
                case applyDelta -> {
                    final DeltaPacket d = (DeltaPacket) args[0];
                    if (!d.getNewObjects().isEmpty() || !d.getObjectDeltas().isEmpty()) {
                        deltasWithState++;
                        newObjects += d.getNewObjects().size();
                        changedObjects += d.getObjectDeltas().size();
                    }
                    if (d.hasEvents()) {
                        deltasWithEvents++;
                        for (final Object o : d.getEvents()) {
                            if (o instanceof GameEvent) rawEvents++; else wrappedEvents++;
                        }
                    }
                }
                case showPromptMessage -> lastPrompt = String.valueOf(args[1]);
                case updateButtons -> buttonPrompts.offer(ev);
                default -> { }
            }
            if (script != null) script.sent(ev);
        }

        @Override
        public Object sendAndWait(final IdentifiableNetEvent event) {
            final GuiGameEvent ev = (GuiGameEvent) event;
            record(ev);
            if (script != null) return script.answer(ev);
            threadsSeen.add(Thread.currentThread().getName());
            final Object[] a = ev.getObjects();
            final Object answer = switch (ev.getMethod()) {
                case getAbilityToPlay -> {
                    // Prefer playing the land over cycling it
                    final List<SpellAbilityView> abilities = (List<SpellAbilityView>) a[1];
                    SpellAbilityView pick = abilities.get(0);
                    for (final SpellAbilityView sa : abilities) {
                        if (!sa.toString().toLowerCase().contains("cycling")) { pick = sa; break; }
                    }
                    yield pick;
                }
                case getChoices -> {
                    final List<?> choices = (List<?>) a[3];
                    final int min = (Integer) a[1];
                    final int max = (Integer) a[2];
                    yield new ArrayList<>(choices.subList(0, Math.min(choices.size(), Math.max(min, Math.min(1, max)))));
                }
                case confirm -> a[2];
                case showConfirmDialog -> a[4];
                case showOptionDialog -> a[4];
                case showInputDialog -> a[3];
                default -> null;
            };
            blockingCalls.add(ev.getMethod() + " -> " + answer);
            return answer;
        }

        private void record(final GuiGameEvent ev) {
            log.add(ev);
            counts.computeIfAbsent(ev.getMethod(), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static Deck deck(final String name, final String card) {
        final Deck d = new Deck(name);
        d.getMain().add(card, 40);
        return d;
    }

    private static final List<String> TRANSPORT_CLASSES = List.of(
            "io.netty.channel.Channel", "io.netty.channel.nio.NioEventLoopGroup", "io.netty.buffer.ByteBuf",
            "net.jpountz.lz4.LZ4BlockOutputStream",
            "forge.gamemodes.net.CompatibleObjectEncoder", "forge.gamemodes.net.CObjectOutputStream",
            "forge.gamemodes.net.TrackableSerializer", "forge.gamemodes.net.server.RemoteClient",
            "forge.gamemodes.net.server.RemoteClientGuiGame", "forge.gamemodes.net.client.FGameClient",
            "forge.gamemodes.net.server.FServerManager");

    private List<String> loadedTransportClasses() throws Exception {
        final java.lang.reflect.Method findLoaded = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
        findLoaded.setAccessible(true);
        final ClassLoader cl = getClass().getClassLoader();
        final List<String> loaded = new ArrayList<>();
        for (final String name : TRANSPORT_CLASSES) {
            if (findLoaded.invoke(cl, name) != null) loaded.add(name);
        }
        return loaded;
    }

    @Test(timeOut = 180_000)
    public void humanControllerRunsThroughInProcessRemote() throws Exception {
        TestUtils.ensureFModelInitialized();
        FModel.getPreferences().setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, false);
        // Recording a replay encodes with the netplay codec, which is exactly what this test shows the game does not need
        FModel.getPreferences().setPref(FPref.UI_RECORD_REPLAYS, false);
        MyRandom.setRandom(new Random(7));

        final List<String> loadedBefore = loadedTransportClasses();
        final RecordingRemote remote = new RecordingRemote();
        final ProtocolGuiGame gui = new ProtocolGuiGame(remote);

        final RegisteredPlayer human = new RegisteredPlayer(deck("Embedded", "Drifting Meadow"))
                .setPlayer(new LobbyPlayerHuman("Embedded"));
        final RegisteredPlayer ai = new RegisteredPlayer(deck("AI", "Plains"))
                .setPlayer(GamePlayerUtil.createAiPlayer("AI"));

        final HostedMatch match = new HostedMatch();
        match.startMatch(GameType.Constructed, null, List.of(human, ai), human, gui);

        // Client role: answer Input prompts through IGameController, on the EDT like GameProtocolHandler does
        boolean playedLand = false;
        int answered = 0;
        int deltasBeforeLand = -1;
        final long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            final GuiGameEvent ub = remote.buttonPrompts.poll(5, TimeUnit.SECONDS);
            if (ub == null) {
                continue;
            }
            final PlayerView owner = (PlayerView) ub.getObjects()[0];
            if (remote.myPlayers == null || owner == null || !remote.myPlayers.contains(owner)) {
                continue;
            }
            final IGameController controller = gui.getGameController(owner);
            final GameView gv = gui.getGameView();
            final boolean myMain = gv.getPlayerTurn() != null && gv.getPlayerTurn().equals(owner)
                    && gv.getPhase() == PhaseType.MAIN1;
            CardView land = null;
            if (myMain && !playedLand && owner.getHand() != null) {
                for (final CardView c : owner.getHand()) { land = c; break; }
            }
            answered++;
            System.out.printf("[in-process] prompt #%d: '%s' buttons=[%s|%s] turn=%d phase=%s%n", answered,
                    remote.lastPrompt.replace('\n', ' '), ub.getObjects()[1], ub.getObjects()[2], gv.getTurn(), gv.getPhase());
            if (land != null) {
                final CardView toPlay = land;
                deltasBeforeLand = remote.deltasWithState;
                playedLand = true;
                FThreads.invokeInEdtLater(() -> controller.selectCard(toPlay, null, null));
            } else if (playedLand && gv.getTurn() >= 3) {
                FThreads.invokeInEdtLater(controller::concede);
                break;
            } else {
                FThreads.invokeInEdtLater(controller::selectButtonOk);
            }
        }

        // Let the concede settle
        final long end = System.currentTimeMillis() + 30_000;
        while (match.getGameView() != null && !match.getGameView().isGameOver() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }

        System.out.println("[in-process] method counts: " + remote.counts);
        System.out.println("[in-process] blocking calls: " + remote.blockingCalls);
        System.out.println("[in-process] blocking threads: " + remote.threadsSeen);
        System.out.printf("[in-process] fullStates=%d deltasWithState=%d (new=%d changed=%d) deltasWithEvents=%d rawEvents=%d wrapped=%d%n",
                remote.fullStates, remote.deltasWithState, remote.newObjects, remote.changedObjects,
                remote.deltasWithEvents, remote.rawEvents, remote.wrappedEvents);

        final PlayerView me = remote.myPlayers.iterator().next();
        final int myLands = me.getBattlefield() == null ? 0 : me.getBattlefield().size();
        System.out.println("[in-process] my battlefield: " + myLands + " card(s)");

        // Which transport classes did this game load that were not loaded before it?
        final List<String> loaded = loadedTransportClasses();
        loaded.removeAll(loadedBefore);
        System.out.println("[in-process] transport classes loaded by the game: " + loaded + " (already loaded: " + loadedBefore + ")");

        assertNotNull(remote.myPlayers, "openView reached the remote");
        assertEquals(myLands, 1, "the land reached the battlefield");
        assertTrue(loaded.stream().noneMatch(n -> n.startsWith("io.netty") || n.startsWith("net.jpountz")
                || n.endsWith("ObjectEncoder") || n.endsWith("CObjectOutputStream") || n.endsWith("TrackableSerializer")
                || n.endsWith("RemoteClient") || n.endsWith("FGameClient") || n.endsWith("FServerManager")),
                "no network or serialization class loaded");
        assertTrue(remote.fullStates >= 1, "full state (setGameView) reached the remote");
        assertTrue(remote.deltasWithState >= 2, "state deltas reached the remote after init");
        assertTrue(remote.blockingCalls.stream().anyMatch(s -> s.startsWith("getAbilityToPlay")), "a synchronous decision reached sendAndWait");
        assertTrue(playedLand, "the human played a land");
        assertTrue(remote.deltasWithState > deltasBeforeLand, "state kept flowing after the answer");
        assertEquals(remote.wrappedEvents, 0, "no Java-serialized event wrappers on the embedded path");
        assertTrue(remote.rawEvents > 0, "raw GameEvents reach the remote");
        assertFalse(match.getGameView() != null && !match.getGameView().isGameOver(), "game ended after concede");
    }

    /** Immutable records belong only to the Java test oracle, never to a browser payload. */
    private enum BoltMode { HUMAN, AI, EMPTY, UNANSWERED, CAPTURE, DTO, DTO_UNANSWERED, MANUAL_BROWSER }

    private record Prompt(Object input, Object[] buttons) { }
    private record FailureArtifact(String action, String input, long threadId, String thread,
                                   String reason, Throwable error) { }

    /** Observation only: retains original awaitInput and graph traversal implementations. */
    private static final class ObservedGui extends ProtocolGuiGame {
        final BoltScript script;
        final java.util.concurrent.atomic.AtomicReference<Thread> walking = new java.util.concurrent.atomic.AtomicReference<>();
        volatile Thread waiter;
        volatile java.util.concurrent.CountDownLatch latch;
        ObservedGui(RecordingRemote remote, BoltScript script) { super(remote); this.script = script; }

        @Override public void awaitInput(java.util.concurrent.CountDownLatch done) throws InterruptedException {
            waiter = Thread.currentThread();
            latch = done;
            script.trace("await-enter", "count=" + done.getCount());
            try { super.awaitInput(done); }
            finally { script.trace("await-return", "count=" + done.getCount()); }
        }
        private boolean enterWalk(String operation) {
            Thread caller = Thread.currentThread();
            Thread owner = walking.get();
            if (owner == caller) return false; // Original same-thread flush recursion.
            if (!walking.compareAndSet(null, caller)) {
                throw script.fail(operation, "concurrent original graph operations; other=" + walking.get(), null);
            }
            script.trace(operation + "-enter", "waiter=" + waitState());
            return true;
        }
        String waitState() {
            Thread t = waiter;
            return t == null ? "none" : t.getId() + ":" + t.getName() + ":" + t.getState()
                    + ":latch=" + (latch == null ? -1 : latch.getCount());
        }
        private void leaveWalk(String operation, boolean outer) {
            if (outer) {
                script.trace(operation + "-return", "waiter=" + waitState());
                walking.set(null);
            }
        }
        @Override public void updateGameView() {
            boolean outer = enterWalk("updateGameView");
            try { super.updateGameView(); } finally { leaveWalk("updateGameView", outer); }
        }
        @Override public void handleGameEvents(List<GameEvent> events) {
            boolean outer = enterWalk("handleGameEvents");
            try { super.handleGameEvents(events); } finally { leaveWalk("handleGameEvents", outer); }
        }
    }

    /** Strict opt-in script; historical RecordingRemote callers retain their original answers. */
    private static final class BoltScript {
        final long started = System.nanoTime();
        final BoltMode mode;
        final DtoPort port;
        final List<forge.game.card.Card> mountains = new ArrayList<>();
        final java.util.Set<Integer> boltIds = new java.util.HashSet<>();
        volatile int chosenBoltId;
        volatile boolean naturalOutcome;
        volatile boolean failurePositionUntouched;
        volatile boolean receivedFailureByOwner;
        final BlockingQueue<Prompt> prompts = new LinkedBlockingQueue<>();
        // Test-policy arbitration only; never a lock claiming ownership of Forge's graph.
        private record CleanupState(boolean committed, Throwable cause, FailureArtifact artifact) { }
        final java.util.concurrent.atomic.AtomicReference<CleanupState> cleanupState =
                new java.util.concurrent.atomic.AtomicReference<>(new CleanupState(false, null, null));
        // The artifact and cleanup veto are published together, before any failure diagnostic.
        final java.util.function.Supplier<FailureArtifact> failure = () -> cleanupState.get().artifact();
        final java.util.concurrent.ConcurrentLinkedQueue<Throwable> secondaryFailures = new java.util.concurrent.ConcurrentLinkedQueue<>();
        volatile String lastObservedInput = "not-observed";
        final HostedMatch match = new HostedMatch();
        final RegisteredPlayer humanRegistration;
        final RegisteredPlayer aiRegistration;
        final RecordingRemote remote = new RecordingRemote(this);
        final ObservedGui gui = new ObservedGui(remote, this);
        volatile forge.game.player.Player human, ai;
        volatile forge.game.card.Card bolt, mountain;
        volatile boolean ready, castRequested, targetSelected, manualMana, stackSeen, resolved;
        volatile long loopThreadId;
        int abilityReplies;
        int manaReplies;
        Object paymentInput;

        BoltScript(BoltMode mode) {
            this.mode = mode;
            port = mode == BoltMode.DTO || mode == BoltMode.DTO_UNANSWERED || mode == BoltMode.MANUAL_BROWSER ? new DtoPort(this) : null;
            Deck humanDeck = new Deck("Human Bolt");
            boolean humanBolt = humanCase();
            humanDeck.getMain().add("Mountain", humanBolt ? 39 : 40);
            if (humanBolt) humanDeck.getMain().add("Lightning Bolt", 1);
            Deck aiDeck = new Deck("AI");
            aiDeck.getMain().add("Mountain", mode == BoltMode.AI ? 38 : 40);
            if (mode == BoltMode.AI) aiDeck.getMain().add("Lightning Bolt", 2);
            humanRegistration = new RegisteredPlayer(humanDeck).setPlayer(new LobbyPlayerHuman("Human Bolt"));
            aiRegistration = new RegisteredPlayer(aiDeck).setPlayer(GamePlayerUtil.createAiPlayer("AI"));
        }
        boolean humanCase() { return mode != BoltMode.AI && mode != BoltMode.EMPTY; }
        forge.player.PlayerControllerHuman controller() {
            forge.game.Game game = match.getGame();
            if (game == null) return null;
            for (forge.game.player.Player p : game.getPlayers()) {
                if (p.getRegisteredPlayer() == humanRegistration) return (forge.player.PlayerControllerHuman) p.getController();
            }
            return null;
        }
        Object input() { var c = controller(); return c == null ? null : c.getInputProxy().getInput(); }
        String inputId() { Object i = input(); return i == null ? "none" : i.getClass().getSimpleName() + "@" + System.identityHashCode(i); }
        void trace(String site, String detail) {
            Thread t = Thread.currentThread();
            String observedInput = cleanupState.get().cause() == null ? inputId() : lastObservedInput;
            lastObservedInput = observedInput;
            System.out.printf("[bolt] ns=%d site=%s tid=%d thread=%s edt=%b input=%s %s%n",
                    System.nanoTime() - started, mode + ":" + site, t.getId(), t.getName(), javax.swing.SwingUtilities.isEventDispatchThread(), observedInput, detail);
        }
        void abort(Throwable cause) {
            abort(cause, null);
        }
        void abort(Throwable cause, FailureArtifact artifact) {
            // Never wait for EDT, a graph walk, or a controller call to finish before owner delivery.
            while (true) {
                var previous = cleanupState.get();
                if (previous.cause() != null) {
                    if (previous.cause() != cause) secondaryFailures.add(cause);
                    return;
                }
                if (cleanupState.compareAndSet(previous, new CleanupState(previous.committed(), cause, artifact))) return;
            }
        }
        IllegalStateException fail(String action, String reason, Throwable cause) {
            var error = new IllegalStateException(action + ": " + reason, cause);
            var t = Thread.currentThread();
            // Publish before diagnostics. Failure reporting must not traverse an unsettled graph.
            var artifact = new FailureArtifact(action, lastObservedInput, t.getId(), t.getName(), reason, error);
            abort(error, artifact);
            try { System.out.println("[bolt] " + mode + (failure.get() == artifact ? ":ATOMIC-FAILURE" : ":SECONDARY-FAILURE") + " cachedInput=true " + artifact); }
            catch (Throwable diagnostic) { error.addSuppressed(diagnostic); }
            return error;
        }
        void require(boolean condition, String message) { if (!condition) throw fail("assertion", message, null); }
        void checkFailure() {
            var state = cleanupState.get();
            var f = state.artifact();
            if (f != null) throw new AssertionError("Owner received atomic failure: " + f, f.error());
            var cause = state.cause();
            if (cause != null) throw new AssertionError("Fixture aborted without an atomic assertion artifact", cause);
        }
        void sent(GuiGameEvent event) {
            require(!gui.isPaused(), "GUI never paused");
            if (event.getMethod() == ProtocolMethod.showPromptMessage || event.getMethod() == ProtocolMethod.updateButtons) {
                trace("prompt-" + event.getMethod(), "waiter=" + gui.waitState());
                Object current = input();
                if (current instanceof forge.gamemodes.match.input.InputSelectTargets
                        || current instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment
                        || current instanceof forge.gamemodes.match.input.InputPassPriority) {
                    require(javax.swing.SwingUtilities.isEventDispatchThread(), "relevant prompt display on EDT");
                }
                if (event.getMethod() == ProtocolMethod.updateButtons) prompts.offer(new Prompt(current, event.getObjects().clone()));
            }
        }
        Object answer(GuiGameEvent event) {
            checkFailure();
            long caller = Thread.currentThread().getId();
            if ((mode == BoltMode.UNANSWERED || mode == BoltMode.DTO_UNANSWERED) && event.getMethod() == ProtocolMethod.getAbilityToPlay) {
                failurePositionUntouched = match.getGame().getStack().isEmpty() && ai.getLife() == 20
                        && human.getLife() == 20 && !mountain.isTapped() && human.getManaPool().isEmpty();
                require(failurePositionUntouched, "unanswered control before cast/payment/damage");
                throw fail("required-question", "deliberately unanswered getAbilityToPlay", null);
            }
            if (port != null) return port.answer(event);
            trace("reply-enter-" + event.getMethod(), "waiter=" + gui.waitState());
            if (event.getMethod() != ProtocolMethod.getAbilityToPlay) throw fail("required-question", "unsupported " + event.getMethod(), null);
            Object[] a = event.getObjects();
            require(a[0] instanceof CardView, "offered host is a CardView");
            int hostId = ((CardView) a[0]).getId();
            List<SpellAbilityView> choices = (List<SpellAbilityView>) a[1];
            require(choices.size() == 1, "exactly one offered fixture ability");
            SpellAbilityView choice = choices.get(0);
            require(choice.getHostCard().getId() == hostId, "exact offered ability host");
            if (hostId == bolt.getId()) {
                require(choice.isSpell(), "original Bolt spell");
                abilityReplies++;
            } else if (hostId == mountain.getId() && input() instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment) {
                require(!choice.isSpell() && mountain.getManaAbilities().stream().anyMatch(sa -> sa.getView() == choice), "original offered Mountain mana ability");
                manaReplies++;
            } else throw fail("required-question", "unsupported ability host=" + hostId, null);
            trace("reply-return-" + event.getMethod(), "ability=" + choice.getId() + " answer=" + choice);
            require(caller == Thread.currentThread().getId(), "immediate reply stays on caller thread");
            return choice;
        }
        void setup() {
            try {
                require(!javax.swing.SwingUtilities.isEventDispatchThread(), "fixture hook is original game thread");
                loopThreadId = Thread.currentThread().getId();
                forge.game.Game game = match.getGame();
                for (forge.game.player.Player p : game.getPlayers()) {
                    if (p.getRegisteredPlayer() == humanRegistration) human = p;
                    if (p.getRegisteredPlayer() == aiRegistration) ai = p;
                    for (forge.game.card.Card c : new ArrayList<>(p.getAllCards())) game.getAction().moveToLibrary(c, null);
                }
                require(human != null && ai != null, "registered identities found");
                human.setLife(humanCase() ? 20 : 3, null); ai.setLife(20, null);
                var caster = humanCase() ? human : ai;
                for (forge.game.card.Card c : new ArrayList<>(caster.getCardsIn(forge.game.zone.ZoneType.Library))) {
                    if (c.getName().equals("Lightning Bolt")) {
                        var card = game.getAction().moveToHand(c, null);
                        boltIds.add(card.getId());
                        if (bolt == null) bolt = card;
                    } else if (c.getName().equals("Mountain") && mountains.size() < (humanCase() ? 1 : 2)) {
                        mountains.add(game.getAction().moveToPlay(c, null, forge.game.ability.AbilityKey.newMap()));
                    }
                }
                mountain = mountains.get(0);
                var nonCaster = humanCase() ? ai : human;
                game.getAction().moveToHand(nonCaster.getCardsIn(forge.game.zone.ZoneType.Library).get(0), null);
                require(boltIds.size() == (mode == BoltMode.EMPTY ? 0 : humanCase() ? 1 : 2), "original deck Bolt count");
                require(mountains.size() == (humanCase() ? 1 : 2) && mountains.stream().noneMatch(forge.game.card.Card::isTapped), "untapped original sources");
                require(human.getManaPool().isEmpty() && ai.getManaPool().isEmpty() && game.getStack().isEmpty(), "no prepaid mana or synthetic stack");
                require(!human.getCardsIn(forge.game.zone.ZoneType.Library).isEmpty() && !ai.getCardsIn(forge.game.zone.ZoneType.Library).isEmpty(), "both libraries nonempty");
                var brain = ((forge.ai.PlayerControllerAi) ai.getController()).getAi();
                require(!brain.usesFullSimulation() && !brain.usesHybridSimulation(), "original AI is non-simulation");
                require(game.getAITimeout() == FModel.getPreferences().getPrefInt(FPref.MATCH_AI_TIMEOUT), "original HostedMatch AI deadline retained");
                game.getPhaseHandler().devModeSet(PhaseType.MAIN1, caster, false, 1);
                game.copyLastState();
                trace("fixture", "human=" + human.getId() + " ai=" + ai.getId() + " boltIds=" + boltIds
                        + " mountain=" + mountain.getId() + " timeout=" + game.getAITimeout());
                ready = true;
            } catch (Throwable t) { throw fail("fixture", t.toString(), t); }
        }
        void parked() {
            require(gui.waiter != null && gui.waiter.getId() == loopThreadId, "same original loop owns input wait");
            require(gui.latch != null && gui.latch.getCount() == 1 && gui.waiter.getState() == Thread.State.WAITING,
                    "checkpoint requires original game latch parked: " + gui.waitState());
        }
        /** Bounded fixture checkpoint, not a general graph lock. Driver is the only action producer. */
        void captureCheckpoint(Object expected) throws Exception {
            require(javax.swing.SwingUtilities.isEventDispatchThread() && ready, "capture on displayed fixture input");
            parked();
            require(input() == expected && controller().getInputQueue().getInput() == expected, "same original queued input");
            require(java.util.Arrays.stream(gui.waiter.getStackTrace()).anyMatch(f -> f.getClassName().equals("java.util.concurrent.CountDownLatch") && f.getMethodName().equals("await")), "original latch stack");
            var field = forge.util.ThreadUtil.class.getDeclaredField("gameThreadPool");
            field.setAccessible(true);
            var pool = (java.util.concurrent.ThreadPoolExecutor) field.get(null);
            long submitted = pool.getTaskCount(), completed = pool.getCompletedTaskCount();
            trace("capture-workers", "submitted=" + submitted + " completed=" + completed + " active=" + pool.getActiveCount() + " queued=" + pool.getQueue().size());
            require(pool.getActiveCount() == 1 && pool.getQueue().isEmpty(), "only parked loop active; no queued worker");
            for (var entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                require(!thread.isAlive() || !thread.getName().equals("Game AI Eval"), "no AI evaluation during capture");
                if (thread != gui.waiter && thread.getName().startsWith("Game-") && thread.isAlive()) {
                    require(thread.getState() == Thread.State.TIMED_WAITING
                            && java.util.Arrays.stream(entry.getValue()).anyMatch(f -> f.getClassName().equals("java.util.concurrent.ThreadPoolExecutor") && f.getMethodName().equals("getTask")), "other game worker must be idle in original executor");
                }
            }
            boolean outer = gui.enterWalk("capture");
            require(outer, "capture outside original graph callbacks");
            try {
                var game = match.getGame();
                require(!game.getTracker().isFrozen(), "tracker fully published");
                String first = captureFingerprint();
                require(first.equals(captureFingerprint()), "repeat engine/view graph read coherent");
                require(input() == expected && gui.latch.getCount() == 1 && gui.waiter.getState() == Thread.State.WAITING,
                        "same parked input after read");
                require(pool.getTaskCount() == submitted && pool.getCompletedTaskCount() == completed && pool.getActiveCount() == 1,
                        "no worker submission/completion during capture");
                trace("capture-coherent", "submitted=" + submitted + " completed=" + completed + " active=1 fingerprint=" + first);
            } finally { gui.leaveWalk("capture", outer); }
        }
        String captureFingerprint() {
            var game = match.getGame();
            require(game.getPhaseHandler().getPhase() == game.getView().getPhase(), "engine/view phase matches");
            StringBuilder out = new StringBuilder(game.getView().getTurn() + ":" + game.getView().getPhase());
            for (var p : List.of(human, ai)) {
                require(p.getLife() == p.getView().getLife(), "engine/view life matches");
                out.append('|').append(p.getId()).append(':').append(p.getLife());
                out.append("/pool:").append(p.getManaPool().totalMana());
                for (var z : List.of(forge.game.zone.ZoneType.Hand, forge.game.zone.ZoneType.Battlefield, forge.game.zone.ZoneType.Graveyard)) {
                    for (var card : p.getCardsIn(z)) {
                        require(card.getView().getZone() == z && card.getView().isTapped() == card.isTapped(), "engine/view card zone/tap matches");
                        out.append('/').append(z).append(':').append(card.getId()).append(':').append(card.isTapped());
                    }
                }
            }
            require(game.getStack().size() == game.getView().getStack().size(), "engine/view stack count matches");
            for (var item : game.getStack()) {
                var sa = item.getSpellAbility();
                out.append("/stack:").append(sa.getId()).append(':').append(com.google.common.collect.ImmutableList.copyOf(sa.getTargets().getTargetPlayers()).stream().map(p -> p.getId()).toList())
                        .append(':').append(sa.getPayingMana().stream().map(m -> m.color() + ":" + m.sourceCard().getId()).toList());
            }
            var paidFor = human.getPaidForSA();
            if (paidFor != null) out.append("/pay:").append(paidFor.getId()).append(':').append(paidFor.getManaCostBeingPaid())
                    .append(':').append(com.google.common.collect.ImmutableList.copyOf(paidFor.getTargets().getTargetPlayers()).stream().map(p -> p.getId()).toList());
            return out.toString();
        }
        void visibility(String stage) {
            parked();
            var viewer = human.getView();
            require(remote.myPlayers != null && remote.myPlayers.size() == 1 && remote.myPlayers.contains(viewer), "nonempty actual human viewers");
            java.util.Set<Integer> allowed = new java.util.HashSet<>();
            int hidden = 0;
            for (var p : List.of(human, ai)) {
                for (var z : List.of(forge.game.zone.ZoneType.Hand, forge.game.zone.ZoneType.Library,
                        forge.game.zone.ZoneType.Battlefield, forge.game.zone.ZoneType.Graveyard)) {
                    for (var card : p.getCardsIn(z)) {
                        CardView view = card.getView();
                        boolean expected = z != forge.game.zone.ZoneType.Library && (z != forge.game.zone.ZoneType.Hand || p == human);
                        boolean actual = view.canBeShownTo(viewer) && view.canFaceDownBeShownTo(viewer);
                        require(actual == expected, "fixture visibility for card=" + card.getId() + " zone=" + z);
                        if (actual) allowed.add(card.getId()); else hidden++;
                    }
                }
            }
            if (!match.getGame().getStack().isEmpty()) {
                var view = match.getGame().getStack().peekAbility().getHostCard().getView();
                require(view.canBeShownTo(viewer) && view.canFaceDownBeShownTo(viewer), "stack Bolt face is public");
                allowed.add(view.getId());
            }
            // Raw GameView intentionally exposes private Java object identities/names.
            var rawAi = gui.getGameView().getPlayers().stream().filter(p -> p.getId() == ai.getId()).findFirst().orElseThrow();
            var hiddenAi = mode == BoltMode.EMPTY ? rawAi.getLibrary().iterator().next() : rawAi.getHand().iterator().next();
            require(hiddenAi.getName().equals(mode == BoltMode.AI ? "Lightning Bolt" : "Mountain") && !hiddenAi.canBeShownTo(viewer), "raw full graph contains named hidden AI card");
            boolean rawFullReceived;
            boolean hiddenDeltaReceived;
            synchronized (remote.log) {
                rawFullReceived = remote.log.stream().anyMatch(e -> e.getMethod() == ProtocolMethod.setGameView
                        && e.getObjects()[0] == gui.getGameView());
                int key = DeltaPacket.makeDeltaKey(hiddenAi);
                hiddenDeltaReceived = remote.log.stream().filter(e -> e.getMethod() == ProtocolMethod.applyDelta)
                        .map(e -> (DeltaPacket) e.getObjects()[0]).anyMatch(d -> d.getNewObjects().containsKey(key)
                                || d.getObjectDeltas().containsKey(key));
            }
            require(rawFullReceived && hiddenDeltaReceived, "raw full state and deltas disclose known private Java card identity");
            var immutableAllowed = java.util.Set.copyOf(allowed);
            boolean rejected = false;
            try { validateFaceDisclosure(immutableAllowed, hiddenAi.getId()); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "disclosure validator explicitly rejects submitted hidden face ID");
            if (humanCase()) require(allowed.contains(bolt.getId()), "visible human Bolt face ID accepted");
            if (chosenBoltId != 0) require(allowed.contains(chosenBoltId), "cast Bolt face ID accepted");
            if (humanCase()) validateFaceDisclosure(immutableAllowed, bolt.getId());
            if (chosenBoltId != 0) validateFaceDisclosure(immutableAllowed, chosenBoltId);
            trace("visibility-" + stage, "allowedFaceCount=" + allowed.size() + " hiddenFaceCount=" + hidden + " rawGraphPrivate=true");
        }
        static void validateFaceDisclosure(java.util.Set<Integer> allowed, int submittedFaceId) {
            if (!allowed.contains(submittedFaceId)) throw new IllegalArgumentException("Hidden face identity must not be disclosed");
        }
        void observePriority() {
            parked();
            var game = match.getGame();
            if (!game.getStack().isEmpty()) {
                var sa = game.getStack().peekAbility();
                require(game.getStack().size() == 1 && sa.getHostCard().getId() == bolt.getId(), "one original Bolt on stack");
                require(ai.getLife() == 20 && com.google.common.collect.Iterables.size(sa.getTargets().getTargetPlayers()) == 1
                        && sa.getTargets().getTargetPlayers().iterator().next() == ai, "opponent player targeted before damage");
                var paid = sa.getPayingMana();
                require(paid.size() == 1 && paid.get(0).color() == forge.card.MagicColor.RED
                        && paid.get(0).sourceCard().getId() == mountain.getId() && mountain.isTapped(), "one real R from clicked Mountain");
                require(targetSelected && manualMana, "original human target and manual payment inputs used");
                stackSeen = true;
                trace("stack-before-resolution", "bolt=" + bolt.getId() + " target=" + ai.getId() + " life=20 paid=R source=" + mountain.getId());
                visibility("stack");
            } else if (stackSeen && ai.getLife() == 17) {
                require(human.getLife() == 20 && human.getCardsIn(forge.game.zone.ZoneType.Graveyard).stream().anyMatch(c -> c.getId() == bolt.getId()), "loop-driven resolution and original graveyard");
                visibility("resolved");
                resolved = true;
                trace("human-success", "aiLife=20->17 stack=0 graveyardBolt=" + bolt.getId());
            } else if (!castRequested) visibility("initial");
        }
        void observeAiPriority() {
            parked();
            var game = match.getGame();
            if (mode == BoltMode.EMPTY) {
                require(game.getStack().isEmpty() && human.getLife() == 3 && ai.getLife() == 20, "empty-hand no spell/damage");
                require(ai.getCardsIn(forge.game.zone.ZoneType.Hand).isEmpty() && mountains.stream().noneMatch(forge.game.card.Card::isTapped)
                        && ai.getManaPool().isEmpty(), "empty-hand no cast/payment, sources untapped");
                visibility("empty-control");
                resolved = true;
                trace("empty-control-success", "first settled human priority after original AI loop");
                return;
            }
            require(game.getStack().size() == 1, "original AI selected and cast exactly one Bolt before human priority");
            var sa = game.getStack().peekAbility();
            chosenBoltId = sa.getHostCard().getId();
            require(boltIds.contains(chosenBoltId) && sa.getActivatingPlayer() == ai, "AI chose genuine fixture Bolt");
            require(human.getLife() == 3 && com.google.common.collect.Iterables.size(sa.getTargets().getTargetPlayers()) == 1
                    && sa.getTargets().getTargetPlayers().iterator().next() == human, "original AI opponent target before damage");
            var paid = sa.getPayingMana();
            require(paid.size() == 1 && paid.get(0).color() == forge.card.MagicColor.RED
                    && mountains.stream().anyMatch(c -> c.getId() == paid.get(0).sourceCard().getId())
                    && mountains.stream().filter(forge.game.card.Card::isTapped).count() == 1, "AI paid one real R from one of two Mountains");
            stackSeen = true;
            trace("ai-stack-before-resolution", "bolt=" + chosenBoltId + " ability=" + sa.getId() + " target=" + human.getId()
                    + " life=3 paid=R source=" + paid.get(0).sourceCard().getId());
            visibility("ai-stack");
        }
        void ended() {
            if (mode != BoltMode.AI || failure.get() != null) return;
            try {
                var game = match.getGame();
                require(stackSeen && human.getLife() == 0 && ai.getLife() == 20 && game.getStack().isEmpty(), "original loop resolved lethal AI Bolt");
                require(ai.getCardsIn(forge.game.zone.ZoneType.Graveyard).stream().anyMatch(c -> c.getId() == chosenBoltId), "AI Bolt in original graveyard");
                require(game.getOutcome() != null && game.getOutcome().isWinner(aiRegistration), "original GameOutcome names AI winner");
                naturalOutcome = true; resolved = true;
                trace("ai-success", "humanLife=3->0 winner=AI graveyardBolt=" + chosenBoltId);
            } catch (Throwable t) { fail("end-game", t.toString(), t); }
        }
        void act(Prompt prompt) throws Exception {
            checkFailure();
            Object current = input();
            if (current != prompt.input()) { trace("discard-obsolete-button-event", ""); return; }
            require(javax.swing.SwingUtilities.isEventDispatchThread(), "controller action on EDT");
            require(!gui.isPaused(), "GUI never paused");
            Object[] b = prompt.buttons();
            // An already-submitted payment refresh is not a decision checkpoint. Do not read its graph.
            if (ready && mode == BoltMode.CAPTURE && manualMana && current == paymentInput) {
                trace("discard-consumed-payment-before-capture", "worker may still be active; no graph read");
                return;
            }
            if (ready && mode == BoltMode.CAPTURE) captureCheckpoint(current);
            IGameController c = gui.getGameController((PlayerView) b[0]);
            trace("action", "buttons=" + b[1] + "/" + b[2] + " waiter=" + gui.waitState());
            if (!ready && current instanceof forge.gamemodes.match.input.InputConfirm
                    && "Play".equals(b[1]) && "Draw".equals(b[2]) && Boolean.TRUE.equals(b[3])) {
                c.selectButtonOk(); // Explicit offered play-first choice; not a generic fallback.
            } else if (!ready && current instanceof forge.gamemodes.match.input.InputConfirmMulligan
                    && "Keep".equals(b[1]) && "Mulligan".equals(b[2]) && Boolean.TRUE.equals(b[3])) {
                c.selectButtonOk();
            } else if (ready && current instanceof forge.gamemodes.match.input.InputPassPriority) {
                require(Thread.currentThread().getId() != loopThreadId, "EDT differs from waiting game loop");
                if (humanCase()) observePriority(); else observeAiPriority();
                if (resolved) return; // Only finishCleanly may concede, after owner-side success assertions.
                else if (humanCase() && !castRequested) { castRequested = true; c.selectCard(bolt.getView(), null, null); }
                else { require(Boolean.TRUE.equals(b[3]), "priority OK enabled"); c.selectButtonOk(); }
            } else if (ready && current instanceof forge.gamemodes.match.input.InputSelectTargets) {
                require(!targetSelected, "one target action"); targetSelected = true;
                c.selectPlayer(ai.getView(), null);
            } else if (ready && current instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment) {
                if (manualMana) {
                    require(current == paymentInput, "duplicate refresh belongs to same answered payment input");
                    trace("duplicate-payment-button-refresh", "no second click");
                    return;
                }
                paymentInput = current;
                manualMana = true;
                c.selectCard(mountain.getView(), null, null);
            } else throw fail("required-input", "unsupported input/buttons " + inputId() + " " + b[1] + "/" + b[2], null);
        }
        void run() throws Exception {
            try {
                match.setStartGameHook(this::setup);
                match.setEndGameHook(this::ended);
                match.startMatch(GameType.Constructed, null, List.of(humanRegistration, aiRegistration), humanRegistration, gui);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
                while (!resolved && System.nanoTime() < deadline) {
                    checkFailure();
                    Prompt prompt = prompts.poll(100, TimeUnit.MILLISECONDS);
                    if (prompt == null) continue;
                    if (port != null && ready) {
                        port.step(prompt);
                        continue;
                    }
                    var done = new java.util.concurrent.CountDownLatch(1);
                    FThreads.invokeInEdtLater(() -> {
                        try { act(prompt); } catch (Throwable t) { abort(t); }
                        finally { done.countDown(); }
                    });
                    while (!done.await(100, TimeUnit.MILLISECONDS)) { checkFailure(); require(System.nanoTime() < deadline, "action deadline"); }
                    checkFailure();
                }
                checkFailure();
                require(resolved, "scenario completed");
                if (port != null) port.completed();
                if (humanCase()) require(abilityReplies == 2 && manaReplies == 1, "human Bolt exact original ability replies");
                else require(abilityReplies == 0 && manaReplies == 0 && (mode == BoltMode.EMPTY || naturalOutcome), "AI scenario without scripted spell/payment decision");
                finishCleanly();
                checkFailure();
            } catch (Exception | Error primary) {
                // Keep the actual unwinding Throwable, even if another thread won the artifact CAS.
                // No graph reads, trace(), replies, input release, queued cleanup or game-over wait.
                abort(primary);
                Throwable recorded = cleanupState.get().cause();
                if (recorded != primary && recorded != primary.getCause()) primary.addSuppressed(recorded);
                for (Throwable secondary : secondaryFailures) {
                    if (secondary != primary && secondary != primary.getCause()
                            && secondary.getCause() != primary) primary.addSuppressed(secondary);
                }
                throw primary;
            }
        }
        void finishCleanly() throws Exception {
            checkFailure();
            var finish = new java.util.concurrent.FutureTask<Void>(() -> {
                try {
                    // Revoke a queued success action if failure was published before execution.
                    checkFailure();
                    require(resolved, "cleanup only after completed scenario and owner assertions");
                    if (!match.getGame().isGameOver()) {
                        Object expected = input();
                        require(expected instanceof forge.gamemodes.match.input.InputPassPriority, "clean terminal priority input");
                        captureCheckpoint(expected); // Original latch, idle workers and repeat graph checks; no weaker cleanup predicate.
                        checkFailure();
                        var terminalController = controller();
                        require(terminalController != null, "original terminal controller available");
                        trace("cleanup-concede", "success-only settled checkpoint");
                        var permit = cleanupState.get();
                        if (permit.cause() != null) { checkFailure(); }
                        require(!permit.committed(), "one clean termination attempt");
                        // Linearize admission against abort(), including failure after this action was queued.
                        if (!cleanupState.compareAndSet(permit, new CleanupState(true, null, null))) {
                            checkFailure();
                            throw fail("cleanup", "termination permit changed", null);
                        }
                        terminalController.concede();
                    }
                    return null;
                } catch (Exception | Error primary) { abort(primary); throw primary; }
            });
            FThreads.invokeInEdtLater(finish);
            long finishDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!finish.isDone()) {
                checkFailure();
                // Publish cancellation before throwing: no interruptible wait may start unwinding
                // while an unrevoked success-only action is still queued on EDT.
                if (Thread.currentThread().isInterrupted()) {
                    var interrupted = new InterruptedException("Interrupted awaiting clean termination");
                    abort(interrupted);
                    throw interrupted;
                }
                if (System.nanoTime() >= finishDeadline) {
                    var timeout = new java.util.concurrent.TimeoutException("Clean termination EDT deadline");
                    abort(timeout);
                    throw timeout;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            try { finish.get(); } // Already completed: no unbounded waiter here.
            catch (java.util.concurrent.ExecutionException wrapped) {
                Throwable cause = wrapped.getCause();
                if (cause instanceof Exception e) throw e;
                if (cause instanceof Error e) throw e;
                throw new AssertionError(cause);
            }
            long cleanup = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < cleanup) {
                checkFailure();
                if (match.getGame().isGameOver()) return;
                Thread.sleep(50);
            }
            checkFailure();
            require(false, "bounded game cleanup");
        }
    }

    // The peer sees only these immutable values. Everything below DtoPort stays private Java.
    record Face(String handle, String name, String zone, String controller, boolean tapped) { }
    record Seat(String handle, boolean self, int life) { }
    record StackFace(String source, List<String> targets) { StackFace { targets = List.copyOf(targets); } }
    record Choice(String id, String operation, String entity) { }
    record Decision(String id, String kind, String source, List<String> selected, int min, int max, List<Choice> choices) {
        Decision { selected = List.copyOf(selected); choices = List.copyOf(choices); }
    }
    record Payment(List<String> remaining, int redPool) { Payment { remaining = List.copyOf(remaining); } }
    record Frame(String match, String session, long revision, int turn, String phase, List<Seat> players,
                         List<Face> cards, List<StackFace> stack, Payment payment, Decision prompt) {
        Frame { players = List.copyOf(players); cards = List.copyOf(cards); stack = List.copyOf(stack); }
    }
    record Payload(String type, int version, String match, String session, long revision, String prompt, String choice) { }
    record Action(String id, Payload payload) { }
    record Receipt(String status, String code) { }

    /** No constructor, field, method argument or result carries a Forge object or server reference. */
    private static final class BrowserPeer {
        int actions;
        boolean paymentSeen, stackSeen, resolutionSeen;
        Action choose(Frame frame) {
            paymentSeen |= frame.payment() != null;
            stackSeen |= !frame.stack().isEmpty();
            if (frame.players().stream().anyMatch(p -> !p.self() && p.life() == 17)) { resolutionSeen = true; return null; }
            Choice choice = frame.prompt().choices().stream().filter(c -> switch (c.operation()) {
                case "target" -> frame.players().stream().anyMatch(p -> !p.self() && p.handle().equals(c.entity()));
                case "cast", "mana", "pass" -> true;
                default -> false;
            }).findFirst().orElseThrow();
            return new Action("client-" + ++actions, new Payload("action", 1, frame.match(), frame.session(), frame.revision(), frame.prompt().id(), choice.id()));
        }
    }

    private static final class DtoPort {
        private record Binding(Choice choice, Object entity, Object input) { }
        private record Authorization(forge.game.spellability.SpellAbility spell, SpellAbilityView spellView,
                                     forge.game.spellability.SpellAbility mana, SpellAbilityView manaView) { }
        private record Pending(Payload payload, java.util.concurrent.CompletableFuture<Receipt> result) { }
        final BoltScript s;
        final BrowserPeer peer = new BrowserPeer();
        final Object trusted = new Object();
        final String match = token(), session = token();
        final java.util.concurrent.atomic.AtomicReference<Frame> published = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Authorization> authorization = new java.util.concurrent.atomic.AtomicReference<>();
        final AtomicInteger question = new AtomicInteger();
        final Map<String, Object> entities = new java.util.HashMap<>(); // EDT-confined live handles.
        final Map<String, Binding> bindings = new java.util.HashMap<>();
        final Map<String, Pending> ledger = new java.util.LinkedHashMap<>(); // guarded by this.
        long revision;
        boolean inFlight, controls;
        int dispatches, rejected;
        DtoPort(BoltScript s) { this.s = s; }
        static String token() { return java.util.UUID.randomUUID().toString(); }
        String handle(Object entity) {
            for (var entry : entities.entrySet()) if (entry.getValue() == entity) return entry.getKey();
            String handle = token(); entities.put(handle, entity); return handle;
        }
        boolean visible(CardView card) {
            return card != null && card.getZone() != null && !card.isFaceDown()
                    && card.canBeShownTo(s.human.getView()) && card.canFaceDownBeShownTo(s.human.getView())
                    && (card.getZone() != forge.game.zone.ZoneType.Hand || card.getController() == s.human.getView())
                    && java.util.Set.of(forge.game.zone.ZoneType.Hand, forge.game.zone.ZoneType.Battlefield,
                    forge.game.zone.ZoneType.Stack, forge.game.zone.ZoneType.Graveyard).contains(card.getZone());
        }
        Frame prepare(Prompt prompt) throws Exception {
            s.checkFailure();
            if (s.input() != prompt.input() || s.manualMana && s.input() == s.paymentInput) return null;
            s.captureCheckpoint(prompt.input());
            if (s.input() instanceof forge.gamemodes.match.input.InputPassPriority) s.observePriority();
            boolean outer = s.gui.enterWalk("dto-project");
            try {
                entities.clear(); bindings.clear();
                var seats = new ArrayList<Seat>(); var faces = new ArrayList<Face>(); var stack = new ArrayList<StackFace>();
                for (var p : List.of(s.human, s.ai)) seats.add(new Seat(handle(p.getView()), p == s.human, p.getLife()));
                java.util.function.Consumer<forge.game.card.Card> addFace = card -> {
                    CardView view = card.getView(); s.require(visible(view), "project only permitted face");
                    String h = handle(view);
                    if (faces.stream().noneMatch(f -> f.handle().equals(h))) faces.add(new Face(h, card.getName(), view.getZone().name(), handle(view.getController()), card.isTapped()));
                };
                for (var p : List.of(s.human, s.ai)) for (var z : List.of(forge.game.zone.ZoneType.Battlefield, forge.game.zone.ZoneType.Graveyard))
                    for (var card : p.getCardsIn(z)) addFace.accept(card);
                for (var card : s.human.getCardsIn(forge.game.zone.ZoneType.Hand)) addFace.accept(card);
                for (var item : s.match.getGame().getStack()) {
                    var ability = item.getSpellAbility(); addFace.accept(ability.getHostCard());
                    var targets = new ArrayList<String>(); for (var p : ability.getTargets().getTargetPlayers()) targets.add(handle(p.getView()));
                    stack.add(new StackFace(handle(ability.getHostCard().getView()), targets));
                }
                Object input = s.input(); var choices = new ArrayList<Choice>(); String kind; Payment payment = null;
                String source = ""; var selected = new ArrayList<String>();
                if (input instanceof forge.gamemodes.match.input.InputSelectTargets || input instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment) {
                    var spell = authorization.get().spell(); addFace.accept(spell.getHostCard());
                    source = handle(spell.getHostCard().getView());
                    for (var target : spell.getTargets().getTargetPlayers()) selected.add(handle(target.getView()));
                }
                if (input instanceof forge.gamemodes.match.input.InputPassPriority) {
                    kind = "priority";
                    if (!s.castRequested) {
                        var abilities = s.bolt.getAllPossibleAbilities(s.human, true);
                        s.require(abilities.size() == 1 && abilities.get(0).isSpell(), "one original playable Bolt ability");
                        var spell = abilities.get(0);
                        authorization.set(new Authorization(spell, spell.getView(), null, null));
                        offer(choices, "cast", s.bolt.getView(), input);
                    }
                    s.require(Boolean.TRUE.equals(prompt.buttons()[3]), "original priority button enabled");
                    offer(choices, "pass", null, input);
                } else if (input instanceof forge.gamemodes.match.input.InputSelectTargets) {
                    kind = "target";
                    var selection = s.gui.getSelection();
                    s.require(selection.min() == 1 && selection.max() == 1, "original Bolt target cardinality");
                    for (Object target : selection.targets()) {
                        s.require(target instanceof PlayerView && entities.containsValue(target), "only visible offered player targets");
                        offer(choices, "target", target, input);
                    }
                } else if (input instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment) {
                    kind = "payment";
                    var auth = authorization.get(); var mana = s.mountain.getManaAbilities();
                    s.require(mana.size() == 1 && s.controller().getActivateDescription(s.mountain.getView()) != null, "original usable Mountain ability");
                    authorization.set(new Authorization(auth.spell(), auth.spellView(), mana.get(0), mana.get(0).getView()));
                    var cost = s.human.getPaidForSA().getManaCostBeingPaid();
                    payment = new Payment(cost.getUnpaidShards().stream().map(Enum::name).toList(), s.human.getManaPool().getAmountOfColor(forge.card.MagicColor.RED));
                    offer(choices, "mana", s.mountain.getView(), input); // No Auto/OK capability.
                } else throw s.fail("dto-input", "unsupported original decision", null);
                var frame = new Frame(match, session, ++revision, s.match.getGame().getView().getTurn(), s.match.getGame().getView().getPhase().name(), seats, faces, stack,
                        payment, new Decision(token(), kind, source, selected, 1, 1, choices));
                auditDto(frame);
                for (Object entity : entities.values()) if (entity instanceof CardView card) s.require(visible(card), "live registry excludes hidden cards");
                published.set(frame);
                System.out.println("[browser-frame] " + frame);
                return frame;
            } finally { s.gui.leaveWalk("dto-project", outer); }
        }
        void offer(List<Choice> choices, String operation, Object entity, Object input) {
            Choice choice = new Choice(token(), operation, entity == null ? "" : handle(entity));
            choices.add(choice); bindings.put(choice.id(), new Binding(choice, entity, input));
        }
        Object answer(GuiGameEvent event) {
            s.require(event.getMethod() == ProtocolMethod.getAbilityToPlay, "no unspecified synchronous question");
            var auth = authorization.get(); s.require(auth != null, "immutable authorization published before dispatch");
            var offered = (List<SpellAbilityView>) event.getObjects()[1];
            int step = question.get();
            s.require(offered.size() == 1 && step < 3, "exact authorized singleton sequence");
            var ability = step < 2 ? auth.spell() : auth.mana();
            var view = step < 2 ? auth.spellView() : auth.manaView();
            s.require(ability != null && offered.get(0) == view && ability.getView() == view, "C1 exact offered view/ability binding");
            try {
                var field = forge.player.PlayerControllerHuman.class.getDeclaredField("spellViewCache"); field.setAccessible(true);
                var cache = (Map<SpellAbilityView, forge.game.spellability.SpellAbility>) field.get(s.controller());
                s.require(cache.size() == 1 && cache.entrySet().stream().anyMatch(e -> e.getKey() == view && e.getValue() == ability), "C1 live cache reference pair");
            } catch (ReflectiveOperationException e) { throw s.fail("dto-authorization", "cache unavailable", e); }
            s.require(step == 0 ? javax.swing.SwingUtilities.isEventDispatchThread() : Thread.currentThread().getName().equals(step == 1 ? "Game-0" : "Game-1"), "original question caller");
            s.require(question.compareAndSet(step, step + 1), "single consumption of authorization");
            if (step < 2) s.abilityReplies++; else s.manaReplies++;
            s.trace("dto-authorized-reply", "step=" + step + " exactReferences=true");
            return view;
        }
        java.util.concurrent.CompletableFuture<Receipt> reject(String code) {
            rejected++; return java.util.concurrent.CompletableFuture.completedFuture(new Receipt("rejected", code));
        }
        synchronized java.util.concurrent.CompletableFuture<Receipt> submit(Object context, Action action, java.util.function.Consumer<Runnable> scheduler) {
            if (context != trusted) return reject("unauthorized");
            if (action == null || action.id() == null || action.id().isEmpty() || action.id().length() > 64) return reject("invalidAction");
            var previous = ledger.get(action.id());
            if (previous != null) return previous.payload().equals(action.payload()) ? previous.result() : reject("actionIdConflict");
            var p = action.payload(); var f = published.get();
            if (p == null || p.type() == null || p.match() == null || p.session() == null || p.prompt() == null || p.choice() == null
                    || java.util.stream.Stream.of(p.type(), p.match(), p.session(), p.prompt(), p.choice()).anyMatch(v -> v.length() > 64)) return reject("invalidAction");
            if (!p.type().equals("action") || p.version() != 1) return reject("invalidOperation");
            if (!p.match().equals(match) || !p.session().equals(session)) return reject("unauthorized");
            if (inFlight) return reject("busy");
            if (f == null || f.prompt() == null || p.revision() != f.revision() || !p.prompt().equals(f.prompt().id())) return reject("staleDecision");
            if (f.prompt().choices().stream().noneMatch(c -> c.id().equals(p.choice()))) return reject("unavailableChoice");
            if (ledger.size() >= 256 || action.id().isEmpty() || action.id().length() > 64) return reject("invalidAction");
            var result = new java.util.concurrent.CompletableFuture<Receipt>(); ledger.put(action.id(), new Pending(p, result)); inFlight = true;
            scheduler.accept(() -> dispatch(action, result)); return result;
        }
        void dispatch(Action action, java.util.concurrent.CompletableFuture<Receipt> result) {
            try {
                s.checkFailure();
                s.require(javax.swing.SwingUtilities.isEventDispatchThread(), "same-runnable EDT recheck and dispatch");
                var f = published.get(); var p = action.payload();
                if (f == null || f.prompt() == null || f.revision() != p.revision() || !f.prompt().id().equals(p.prompt())) { result.complete(new Receipt("rejected", "staleDecision")); return; }
                Binding b = bindings.get(p.choice());
                if (b == null || b.input() != s.input() || b.entity() != null && (entities.get(b.choice().entity()) != b.entity()
                        || b.entity() instanceof CardView card && !visible(card))) { result.complete(new Receipt("rejected", "unavailableChoice")); return; }
                s.captureCheckpoint(b.input());
                published.set(new Frame(match, session, f.revision(), f.turn(), f.phase(), f.players(), f.cards(), f.stack(), f.payment(), null));
                dispatches++;
                // No suspension, queued hop or cached off-thread entity between validation and original call.
                switch (b.choice().operation()) {
                    case "cast" -> { s.require(s.input() instanceof forge.gamemodes.match.input.InputPassPriority, "cast decision"); s.castRequested = true; s.require(s.controller().selectCard((CardView)b.entity(), null, null), "original cast action accepted"); }
                    case "target" -> { s.require(s.input() instanceof forge.gamemodes.match.input.InputSelectTargets, "target decision"); s.targetSelected = true; s.controller().selectPlayer((PlayerView)b.entity(), null); }
                    case "mana" -> { s.require(s.input() instanceof forge.gamemodes.match.input.InputPayManaOfCostPayment, "payment decision"); s.paymentInput = s.input(); s.manualMana = true; s.require(s.controller().selectCard((CardView)b.entity(), null, null), "original manual mana action accepted"); }
                    case "pass" -> { s.require(s.input() instanceof forge.gamemodes.match.input.InputPassPriority, "ordinary priority decision"); s.controller().selectButtonOk(); }
                    default -> throw s.fail("dto-operation", "unsupported operation", null);
                }
                result.complete(new Receipt("accepted", "controllerDispatched"));
            } catch (Throwable t) {
                s.abort(t); result.completeExceptionally(t);
            } finally { synchronized (this) { inFlight = false; } }
        }
        <T> T edt(java.util.concurrent.Callable<T> task) throws Exception {
            var future = new java.util.concurrent.FutureTask<>(task); FThreads.invokeInEdtLater(future);
            try { return future.get(15, TimeUnit.SECONDS); }
            catch (Exception e) { s.checkFailure(); throw e; }
        }
        static Action action(Frame f, String id, String choice) { return new Action(id, new Payload("action", 1, f.match(), f.session(), f.revision(), f.prompt().id(), choice)); }
        void rejected(Action a, Object context, String code, String before) throws Exception {
            Object expected = edt(s::input);
            int calls = dispatches;
            var receipt = submit(context, a, FThreads::invokeInEdtLater).get(15, TimeUnit.SECONDS);
            s.require(receipt.equals(new Receipt("rejected", code)) && calls == dispatches, "rejection receipt and zero controller calls: " + code);
            s.require(before.equals(edt(() -> { s.captureCheckpoint(expected); return s.captureFingerprint(); })), "rejection did not advance original game or release input");
        }
        Frame controls(Frame f, Prompt prompt) throws Exception {
            String before = edt(s::captureFingerprint); Choice first = f.prompt().choices().get(0);
            rejected(action(f,"wrong-context",first.id()),new Object(),"unauthorized",before);
            rejected(new Action("wrong-session",new Payload("action",1,match,"foreign",f.revision(),f.prompt().id(),first.id())),trusted,"unauthorized",before);
            rejected(new Action("bad-operation",new Payload("cheat",1,match,session,f.revision(),f.prompt().id(),first.id())),trusted,"invalidOperation",before);
            rejected(new Action("old-prompt",new Payload("action",1,match,session,f.revision(),"retired",first.id())),trusted,"staleDecision",before);
            rejected(new Action("old-revision",new Payload("action",1,match,session,f.revision()-1,f.prompt().id(),first.id())),trusted,"staleDecision",before);
            rejected(action(f,"unknown","unknown-handle"),trusted,"unavailableChoice",before);
            // Private malicious injector only: legitimate peer never receives this hidden engine identity.
            String hidden = edt(() -> String.valueOf(s.ai.getCardsIn(forge.game.zone.ZoneType.Hand).get(0).getId()));
            rejected(action(f,"hidden",hidden),trusted,"unavailableChoice",before);
            rejected(action(f,"entity-is-not-authority",first.entity()),trusted,"unavailableChoice",before);
            var held = new ArrayList<Runnable>(); var stale = submit(trusted,action(f,"revoked",first.id()),held::add);
            edt(() -> { entities.remove(first.entity()); return null; });
            FThreads.invokeInEdtLater(held.get(0)); s.require(stale.get(15,TimeUnit.SECONDS).code().equals("unavailableChoice"), "live revoked handle rejected on EDT");
            s.require(before.equals(edt(s::captureFingerprint)) && dispatches == 0, "revocation changed no game state");
            Frame next = edt(() -> prepare(prompt)); held.clear();
            var superseded = submit(trusted,action(next,"superseded",next.prompt().choices().get(0).id()),held::add);
            Frame latest = edt(() -> prepare(prompt)); FThreads.invokeInEdtLater(held.get(0));
            s.require(superseded.get(15,TimeUnit.SECONDS).code().equals("staleDecision") && dispatches == 0 && before.equals(edt(s::captureFingerprint)), "delayed superseded action cannot dispatch");
            s.require(resync(trusted) == latest && resync(new Object()) == null, "authorized read-only cached resync does not create prompt/revision");
            edt(() -> { s.captureCheckpoint(prompt.input()); return null; }); // Pending unanswered input stayed latched.
            controls = true; s.trace("dto-negative-controls", "unauthorized hidden stale revoked superseded invalidOperation all rejected without controller calls");
            return latest;
        }
        void step(Prompt prompt) throws Exception {
            Frame frame = edt(() -> prepare(prompt)); if (frame == null) return;
            if (s.resolved) { s.require(peer.choose(frame) == null, "peer received resolved DTO state"); return; }
            if (!controls) frame = controls(frame, prompt);
            String immutableBefore = frame.toString(); Action action = peer.choose(frame);
            s.require(action != null, "peer selected a DTO capability");
            var held = new ArrayList<Runnable>(); var pending = submit(trusted,action,held::add);
            s.require(submit(trusted,action,FThreads::invokeInEdtLater) == pending && held.size() == 1, "pending duplicate shares original receipt/ticket");
            var p = action.payload();
            var conflict = new Action(action.id(),new Payload("action",1,match,session,p.revision(),"changed",p.choice()));
            s.require(submit(trusted,conflict,FThreads::invokeInEdtLater).get().code().equals("actionIdConflict"), "distinct duplicate-ID conflict code");
            FThreads.invokeInEdtLater(held.get(0));
            try { s.require(pending.get(15,TimeUnit.SECONDS).status().equals("accepted"), "original controller dispatched"); }
            catch (Exception e) { s.checkFailure(); throw e; }
            s.require(submit(trusted,action,FThreads::invokeInEdtLater) == pending, "terminal duplicate returns same receipt before stale checks");
            s.require(frame.toString().equals(immutableBefore), "old frame remains immutable after engine mutation");
            s.trace("dto-action", "id=" + action.id() + " accepted oneDispatch=true duplicatePendingAndTerminal=true actionIdConflict=true");
        }
        Frame resync(Object context) { return context == trusted ? published.get() : null; }
        void completed() { s.require(controls && question.get() == 3 && dispatches == 4 && peer.paymentSeen && peer.stackSeen && peer.resolutionSeen, "complete DTO sequence and negative controls"); }
        static void auditDto(Object value) throws Exception {
            if (value == null || value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Boolean) return;
            if (value instanceof List<?> list) {
                for (Object item : list) auditDto(item);
                try { ((List<Object>)list).add(null); throw new IllegalStateException("mutable DTO list"); } catch (UnsupportedOperationException expected) { }
                return;
            }
            if (!java.util.Set.of(Frame.class, Seat.class, Face.class, StackFace.class, Payment.class, Decision.class, Choice.class).contains(value.getClass())) throw new IllegalStateException("non-DTO object crossed peer boundary");
            for (var field : value.getClass().getRecordComponents()) auditDto(field.getAccessor().invoke(value));
        }
    }

    @Test(timeOut = 180_000)
    public void humanBoltTargetsOpponentWithManualMountainPayment() throws Exception {
        runBoltCase(BoltMode.HUMAN);
    }

    @Test(timeOut = 180_000)
    public void humanBoltCaptureCheckpoint() throws Exception { runBoltCase(BoltMode.CAPTURE); }

    @Test(timeOut = 180_000)
    public void humanBoltThroughBrowserDtos() throws Exception { runBoltCase(BoltMode.DTO); }

    @Test(timeOut = 180_000)
    public void browserDtoUnansweredPromptIsReported() throws Exception { runBoltCase(BoltMode.DTO_UNANSWERED); }

    /** Explicitly selected only; no listener or FModel initialization on ordinary test discovery. */
    @Test(timeOut = 2_100_000)
    public void manualBrowserBoltSession() throws Exception {
        if (!Boolean.getBoolean("forge.browser.listenerApproved")) throw new org.testng.SkipException("Manual listener requires separate owner approval");
        runBoltCase(BoltMode.MANUAL_BROWSER);
    }

    private void runManualBrowser(BoltScript s) throws Exception {
        var published = new java.util.concurrent.atomic.AtomicReference<Frame>();
        var boundHost = new java.util.concurrent.atomic.AtomicReference<ManualBrowserBoltHost>();
        ManualBrowserBoltHost.Session session = new ManualBrowserBoltHost.Session() {
            public Frame frame() { return published.get(); }
            public java.util.concurrent.CompletableFuture<Receipt> submit(Action action) {
                return s.port.submit(s.port.trusted, action, work -> FThreads.invokeInEdtLater(() -> {
                    if (boundHost.get().abortRequested() || !boundHost.get().currentOutcome().equals("ACTIVE")) s.abort(new IllegalStateException("Manual session no longer admits actions"));
                    work.run(); // Original dispatch begins with checkFailure before graph access.
                }));
            }
        };
        var host = ManualBrowserBoltHost.forApprovedRun(session);
        boundHost.set(host);
        Throwable primary = null;
        try {
            host.start();
            s.match.setStartGameHook(s::setup); s.match.setEndGameHook(s::ended);
            s.match.startMatch(GameType.Constructed, null, List.of(s.humanRegistration, s.aiRegistration), s.humanRegistration, s.gui);
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(25);
            final Object[] publishedInput = {null};
            while (!s.resolved) {
                s.checkFailure();
                if (host.abortRequested()) { host.outcome(host.transportFailed() ? "FAILED" : "ABORTED"); throw new IllegalStateException("Manual browser session aborted or transport failed"); }
                if (System.nanoTime() >= deadline) { host.outcome("EXPIRED"); throw new java.util.concurrent.TimeoutException("Manual interaction expired"); }
                Prompt prompt = s.prompts.poll(100, TimeUnit.MILLISECONDS);
                if (prompt == null) continue;
                if (!s.ready) {
                    manualEdt(s, () -> { s.act(prompt); return null; }); // Original explicit Play/Keep bootstrap only.
                    continue;
                }
                manualEdt(s, () -> {
                    // Refreshes never renew a published capability; cached HTTP reads never enter here.
                    if (s.input() == publishedInput[0]) return null;
                    Frame frame = s.port.prepare(prompt); // Original consumed-payment filter and full checkpoint unchanged.
                    if (frame != null) {
                        publishedInput[0] = prompt.input();
                        if (s.resolved) {
                            frame = new Frame(frame.match(),frame.session(),frame.revision(),frame.turn(),frame.phase(),frame.players(),frame.cards(),frame.stack(),frame.payment(),null);
                            s.port.published.set(frame); // Revoke terminal choices before publishing to the browser.
                        }
                        published.set(frame);
                    }
                    return null;
                });
            }
            s.require(s.stackSeen && s.targetSelected && s.manualMana && s.port.question.get() == 3
                    && s.port.dispatches == 4 && s.abilityReplies == 2 && s.manaReplies == 1,
                    "manual browser sequence confirmed by original Forge oracle and C1 replies");
            host.outcome("TERMINAL");
            long inspection = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            while (!host.finishRequested()) {
                s.checkFailure();
                if (host.abortRequested()) { host.outcome(host.transportFailed() ? "FAILED" : "ABORTED"); throw new IllegalStateException("Terminal inspection aborted or transport failed"); }
                if (System.nanoTime() >= inspection) { host.outcome("EXPIRED"); throw new java.util.concurrent.TimeoutException("Terminal inspection expired"); }
                Thread.sleep(100); // Owner thread only; no original input latch or EDT wait.
            }
            s.checkFailure();
            if (host.abortRequested()) { host.outcome(host.transportFailed() ? "FAILED" : "ABORTED"); throw new IllegalStateException("Abort vetoes terminal cleanup"); }
            if (System.nanoTime() >= inspection) { host.outcome("EXPIRED"); throw new java.util.concurrent.TimeoutException("Terminal inspection expired before cleanup"); }
            host.close(); // Transport failure vetoes success cleanup before any concession.
            s.checkFailure();
            if (host.abortRequested()) { host.outcome(host.transportFailed() ? "FAILED" : "ABORTED"); throw new IllegalStateException("Abort during shutdown vetoes cleanup"); }
            s.finishCleanly(); // Existing PR #23 success policy, explicitly harness cleanup, not browser gameplay.
            s.checkFailure();
            host.outcome("PASSED");
        } catch (Exception | Error error) {
            primary = error; s.abort(error); host.failUnlessEnded(); throw error;
        } finally {
            Throwable finalFailure = primary;
            for (java.util.concurrent.Callable<Void> finish : List.<java.util.concurrent.Callable<Void>>of(
                    () -> { host.close(); return null; }, () -> { host.writeOutcome(); return null; })) {
                try { finish.call(); } catch (Exception | Error secondary) {
                    s.abort(secondary);
                    if (finalFailure == null) finalFailure = secondary; else if (finalFailure != secondary) finalFailure.addSuppressed(secondary);
                }
            }
            if (primary == null) {
                if (finalFailure instanceof Exception e) throw e;
                if (finalFailure instanceof Error e) throw e;
            }
        }
    }

    private static <T> T manualEdt(BoltScript s, java.util.concurrent.Callable<T> work) throws Exception {
        var future = new java.util.concurrent.FutureTask<T>(() -> { s.checkFailure(); return work.call(); });
        FThreads.invokeInEdtLater(future);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            s.checkFailure();
            try { return future.get(100, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException waiting) {
                if (System.nanoTime() >= deadline) { s.abort(waiting); throw waiting; }
            } catch (java.util.concurrent.ExecutionException wrapped) {
                if (wrapped.getCause() instanceof Exception e) throw e;
                if (wrapped.getCause() instanceof Error e) throw e;
                throw new AssertionError(wrapped.getCause());
            }
        }
    }

    @Test(timeOut = 180_000)
    public void aiBoltRunsThroughOriginalPhaseLoop() throws Exception { runBoltCase(BoltMode.AI); }

    @Test(timeOut = 180_000)
    public void aiEmptyHandDoesNotCastOrPay() throws Exception { runBoltCase(BoltMode.EMPTY); }

    @Test(timeOut = 180_000)
    public void unansweredRequiredPromptIsReportedToTestOwner() throws Exception { runBoltCase(BoltMode.UNANSWERED); }

    private void runBoltCase(BoltMode mode) throws Exception {
        long bootstrapStart = System.nanoTime();
        TestUtils.ensureFModelInitialized();
        System.out.printf("[bolt] bootstrapMs=%.3f mode=%s%n", (System.nanoTime() - bootstrapStart) / 1_000_000.0, mode);
        var prefs = FModel.getPreferences();
        String highlights = prefs.getPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS);
        String replays = prefs.getPref(FPref.UI_RECORD_REPLAYS);
        String playerName = prefs.getPref(FPref.PLAYER_NAME);
        Random previousRandom = MyRandom.getRandom();
        Throwable primary = null;
        try {
            prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, false);
            prefs.setPref(FPref.UI_RECORD_REPLAYS, false);
            prefs.setPref(FPref.PLAYER_NAME, "Bolt Fixture");
            MyRandom.setRandom(new Random(7));
            var script = new BoltScript(mode);
            if (mode == BoltMode.UNANSWERED || mode == BoltMode.DTO_UNANSWERED) {
                AssertionError received = null;
                try { script.run(); } catch (AssertionError expected) { received = expected; }
                var artifact = script.failure.get();
                assertNotNull(received, "owning TestNG invocation must receive failure");
                assertNotNull(artifact, "required prompt must record an atomic artifact before throwing");
                assertEquals(artifact.action(), "required-question");
                assertEquals(artifact.reason(), "deliberately unanswered getAbilityToPlay");
                assertTrue(received.getCause() == artifact.error(), "owner received the recorded artifact's exact failure");
                assertTrue(artifact.threadId() != Thread.currentThread().getId(), "artifact originated on dispatch thread");
                assertTrue(script.failurePositionUntouched && !script.stackSeen && !script.manualMana && !script.targetSelected,
                        "no payment/target/cast/damage credited");
                script.receivedFailureByOwner = true;
                script.trace("negative-owner-received", "artifactThread=" + artifact.threadId() + " sameAtomicArtifact=true noCastOrPayment=true");
            } else {
                if (mode == BoltMode.MANUAL_BROWSER) runManualBrowser(script); else script.run();
                assertTrue(script.failure.get() == null && !script.gui.isPaused(), "zero unanswered failures or paused GUI");
            }
            assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && t.getName().equals("Game AI Eval")), "no evaluator survives scenario cleanup");
        } catch (Exception | Error original) {
            primary = original;
            throw original;
        } finally {
            // Local configuration restoration only; attempt every item, never replace an unwinding failure.
            Throwable restoration = primary;
            for (Runnable restore : List.<Runnable>of(
                    () -> prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, highlights),
                    () -> prefs.setPref(FPref.UI_RECORD_REPLAYS, replays),
                    () -> prefs.setPref(FPref.PLAYER_NAME, playerName),
                    () -> MyRandom.setRandom(previousRandom))) {
                try { restore.run(); }
                catch (RuntimeException | Error secondary) {
                    if (restoration == null) restoration = secondary;
                    else if (restoration != secondary) restoration.addSuppressed(secondary);
                }
            }
            if (primary == null) {
                if (restoration instanceof RuntimeException e) throw e;
                if (restoration instanceof Error e) throw e;
            }
        }
    }

}
