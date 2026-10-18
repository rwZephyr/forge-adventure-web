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
 * and an in-process {@link IRemote}. No FServerManager, FGameClient, socket or Netty channel.
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
    private enum BoltMode { HUMAN, AI, EMPTY, UNANSWERED }

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
        final List<forge.game.card.Card> mountains = new ArrayList<>();
        final java.util.Set<Integer> boltIds = new java.util.HashSet<>();
        volatile int chosenBoltId;
        volatile boolean naturalOutcome;
        volatile boolean failurePositionUntouched;
        volatile boolean receivedFailureByOwner;
        final BlockingQueue<Prompt> prompts = new LinkedBlockingQueue<>();
        final java.util.concurrent.atomic.AtomicReference<FailureArtifact> failure = new java.util.concurrent.atomic.AtomicReference<>();
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
            Deck humanDeck = new Deck("Human Bolt");
            boolean humanBolt = mode == BoltMode.HUMAN || mode == BoltMode.UNANSWERED;
            humanDeck.getMain().add("Mountain", humanBolt ? 39 : 40);
            if (humanBolt) humanDeck.getMain().add("Lightning Bolt", 1);
            Deck aiDeck = new Deck("AI");
            aiDeck.getMain().add("Mountain", mode == BoltMode.AI ? 38 : 40);
            if (mode == BoltMode.AI) aiDeck.getMain().add("Lightning Bolt", 2);
            humanRegistration = new RegisteredPlayer(humanDeck).setPlayer(new LobbyPlayerHuman("Human Bolt"));
            aiRegistration = new RegisteredPlayer(aiDeck).setPlayer(GamePlayerUtil.createAiPlayer("AI"));
        }
        boolean humanCase() { return mode == BoltMode.HUMAN || mode == BoltMode.UNANSWERED; }
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
            System.out.printf("[bolt] ns=%d site=%s tid=%d thread=%s edt=%b input=%s %s%n",
                    System.nanoTime() - started, mode + ":" + site, t.getId(), t.getName(), javax.swing.SwingUtilities.isEventDispatchThread(), inputId(), detail);
        }
        IllegalStateException fail(String action, String reason, Throwable cause) {
            var error = new IllegalStateException(action + ": " + reason, cause);
            var t = Thread.currentThread();
            var artifact = new FailureArtifact(action, inputId(), t.getId(), t.getName(), reason, error);
            if (failure.compareAndSet(null, artifact)) trace("ATOMIC-FAILURE", artifact.toString());
            return error;
        }
        void require(boolean condition, String message) { if (!condition) throw fail("assertion", message, null); }
        void checkFailure() { var f = failure.get(); if (f != null) throw new AssertionError("Owner received atomic failure: " + f, f.error()); }
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
            long caller = Thread.currentThread().getId();
            if (mode == BoltMode.UNANSWERED && event.getMethod() == ProtocolMethod.getAbilityToPlay) {
                failurePositionUntouched = match.getGame().getStack().isEmpty() && ai.getLife() == 20
                        && human.getLife() == 20 && !mountain.isTapped() && human.getManaPool().isEmpty();
                require(failurePositionUntouched, "unanswered control before cast/payment/damage");
                throw fail("required-question", "deliberately unanswered getAbilityToPlay", null);
            }
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
        void act(Prompt prompt) {
            Object current = input();
            if (current != prompt.input()) { trace("discard-obsolete-button-event", ""); return; }
            require(javax.swing.SwingUtilities.isEventDispatchThread(), "controller action on EDT");
            require(!gui.isPaused(), "GUI never paused");
            Object[] b = prompt.buttons();
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
                if (resolved) c.concede();
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
            match.setStartGameHook(this::setup);
            match.setEndGameHook(this::ended);
            match.startMatch(GameType.Constructed, null, List.of(humanRegistration, aiRegistration), humanRegistration, gui);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            try {
                while (!resolved && System.nanoTime() < deadline) {
                    checkFailure();
                    Prompt prompt = prompts.poll(100, TimeUnit.MILLISECONDS);
                    if (prompt == null) continue;
                    var done = new java.util.concurrent.CountDownLatch(1);
                    FThreads.invokeInEdtLater(() -> {
                        try { act(prompt); } catch (Throwable t) { fail("EDT-action", t.toString(), t); }
                        finally { done.countDown(); }
                    });
                    while (!done.await(100, TimeUnit.MILLISECONDS)) { checkFailure(); require(System.nanoTime() < deadline, "action deadline"); }
                    checkFailure();
                }
                checkFailure();
                require(resolved, "scenario completed");
                if (humanCase()) require(abilityReplies == 2 && manaReplies == 1, "human Bolt exact original ability replies");
                else require(abilityReplies == 0 && manaReplies == 0 && (mode == BoltMode.EMPTY || naturalOutcome), "AI scenario without scripted spell/payment decision");
            } finally {
                if (match.getGame() != null && !match.getGame().isGameOver()) {
                    FThreads.invokeInEdtLater(() -> { trace("cleanup-concede", "failurePreserved=" + (failure.get() != null)); controller().concede(); });
                }
                long cleanup = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (match.getGame() != null && !match.getGame().isGameOver() && System.nanoTime() < cleanup) Thread.sleep(50);
                require(match.getGame() == null || match.getGame().isGameOver(), "bounded game cleanup");
            }
        }
    }

    @Test(timeOut = 180_000)
    public void humanBoltTargetsOpponentWithManualMountainPayment() throws Exception {
        runBoltCase(BoltMode.HUMAN);
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
        try {
            prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, false);
            prefs.setPref(FPref.UI_RECORD_REPLAYS, false);
            prefs.setPref(FPref.PLAYER_NAME, "Bolt Fixture");
            MyRandom.setRandom(new Random(7));
            var script = new BoltScript(mode);
            if (mode == BoltMode.UNANSWERED) {
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
                script.run();
                assertTrue(script.failure.get() == null && !script.gui.isPaused(), "zero unanswered failures or paused GUI");
            }
            assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive() && t.getName().equals("Game AI Eval")), "no evaluator survives scenario cleanup");
        } finally {
            prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, highlights);
            prefs.setPref(FPref.UI_RECORD_REPLAYS, replays);
            prefs.setPref(FPref.PLAYER_NAME, playerName);
            MyRandom.setRandom(previousRandom);
        }
    }

}
