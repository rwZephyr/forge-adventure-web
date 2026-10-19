package experiment;

import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.AllowConcurrentEvents;
import forge.deck.Deck;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.event.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.match.input.*;
import forge.gamemodes.net.*;
import forge.gamemodes.net.event.*;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.net.TestUtils;
import forge.player.*;
import forge.util.MyRandom;

import javax.swing.SwingUtilities;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Original-game driver for a passive dt_shmem ordering witness. No projector or capture hold. */
public final class BoltLifecycleMain {
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicReference<Authorization> authorization = new AtomicReference<>();
    private final AtomicInteger questions = new AtomicInteger();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<Input, Wait> waits = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Input> consumed = Collections.newSetFromMap(new IdentityHashMap<>()); // EDT only.
    private final BlockingQueue<Prompt> prompts = new LinkedBlockingQueue<>();
    private final HostedMatch match = new HostedMatch();
    private final Gui gui = new Gui();
    private final RegisteredPlayer humanRegistration, aiRegistration;
    private volatile PlayerControllerHuman controller;
    private volatile Thread loopThread;
    private volatile Player human, ai;
    private volatile Card bolt, mountain;
    private volatile CardView boltView, mountainView;
    private volatile PlayerView aiView;
    private volatile boolean ready, castVerified, resolved, lifeVerified, graveyard, tapped;
    private volatile int stage;
    private int bootstrapReplies;
    private volatile Wait payment;

    private record Authorization(SpellAbility spell, SpellAbilityView spellView, SpellAbility mana, SpellAbilityView manaView) { }
    private record Prompt(Input input, String ok, String cancel, boolean enabled) { }
    private static final class Wait {
        final Input input; final CountDownLatch latch; final Thread owner;
        volatile boolean entered, returned;
        Wait(Input input, CountDownLatch latch) { this.input=input; this.latch=latch; owner=Thread.currentThread(); }
    }
    private static void expect(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    private void fail(Throwable t) {
        if (failure.compareAndSet(null,t)) note("failure",null,null);
    }
    private void check() { Throwable t=failure.get(); if(t!=null) throw new IllegalStateException("Owner received original diagnostic failure",t); }
    private void note(String event, Input input, CountDownLatch latch) {
        witnessPoint(event,input,latch,sequence.incrementAndGet(),System.nanoTime());
    }
    /** Passive debugger breakpoint: arguments are actual references. No engine read, sleep or latch operation. */
    private void witnessPoint(String event, Input input, CountDownLatch latch, long seq, long targetNanos) {
        System.out.println("[lifecycle] seq="+seq+" ns="+targetNanos+" event="+event+" thread="+Thread.currentThread().getName());
    }
    private Object failAndPark(Throwable t) {
        fail(t); // Record BEFORE blocking; no asynchronous diagnostic exception or invented reply.
        CountDownLatch unanswered = new CountDownLatch(1);
        for (;;) try { unanswered.await(); } catch (InterruptedException e) { fail(e); }
    }

    private final class Remote implements IRemote {
        @Override public void send(NetEvent event) {
            try {
                GuiGameEvent e=(GuiGameEvent)event;
                if (e.getMethod()!=ProtocolMethod.updateButtons && e.getMethod()!=ProtocolMethod.showPromptMessage) return;
                if (controller==null) return;
                Input sample=controller.getInputProxy().getInput();
                note("callback-proxy-sample-"+e.getMethod(),sample,null);
                // This sample is NOT asserted to be the originating Input. The debugger inspects the actual caller receiver.
                if (e.getMethod()==ProtocolMethod.updateButtons && sample instanceof InputSyncronizedBase) {
                    Object[] a=e.getObjects();
                    prompts.offer(new Prompt(sample,(String)a[1],(String)a[2],Boolean.TRUE.equals(a[3])));
                }
            } catch(Throwable t) { fail(t); }
        }
        @Override public Object sendAndWait(IdentifiableNetEvent event) {
            try {
                check();
                GuiGameEvent e=(GuiGameEvent)event;
                expect(e.getMethod()==ProtocolMethod.getAbilityToPlay,"Unexpected required synchronous question: "+e.getMethod());
                Authorization a=authorization.get();
                expect(a!=null,"Authorization published before original dispatch");
                int step=questions.get(); expect(step<3,"Exactly three C1 questions");
                @SuppressWarnings("unchecked") List<SpellAbilityView> offered=(List<SpellAbilityView>)e.getObjects()[1];
                SpellAbility ability=step<2?a.spell():a.mana();
                SpellAbilityView view=step<2?a.spellView():a.manaView();
                expect(offered.size()==1 && ability!=null && view!=null && offered.get(0)==view && ability.getView()==view,"Exact original singleton offered ability/view");
                expect(e.getObjects()[0]==(step<2?boltView:mountainView),"Exact original offered host view");
                var field=PlayerControllerHuman.class.getDeclaredField("spellViewCache"); field.setAccessible(true);
                @SuppressWarnings("unchecked") Map<SpellAbilityView,SpellAbility> cache=(Map<SpellAbilityView,SpellAbility>)field.get(controller);
                expect(cache.size()==1 && cache.entrySet().stream().anyMatch(p->p.getKey()==view && p.getValue()==ability),"C1 exact live cache key/value references");
                expect(step==0?SwingUtilities.isEventDispatchThread():step==1?Thread.currentThread()==loopThread:
                        Thread.currentThread()!=loopThread && Thread.currentThread().getName().equals("Game-1"),"Original C1 caller thread");
                if(step==2) expect(payment!=null && payment.latch.getCount()==1 && !payment.returned,"Game-0 original payment remains unresolved during Mountain offer");
                expect(questions.compareAndSet(step,step+1),"Single consumption of C1 authorization");
                note("authorized-question-"+step,controller.getInputProxy().getInput(),payment==null?null:payment.latch);
                return view; // The actual offered reference, never a default, fabricated ability or ID lookup.
            } catch(Throwable t) { return failAndPark(t); }
        }
    }
    private final class Gui extends ProtocolGuiGame {
        Gui() { super(new Remote()); }
        @Override public void setOriginalGameController(PlayerView player, IGameController original) {
            super.setOriginalGameController(player,original);
            try {
                expect(controller==null && original instanceof PlayerControllerHuman,"One original human controller");
                controller=(PlayerControllerHuman)original; note("controller-bound",null,null);
            } catch(Throwable t) { fail(t); }
        }
        @Override public void awaitInput(CountDownLatch done) throws InterruptedException {
            Wait w=null;
            try {
                Input input=controller.getInputProxy().getInput();
                expect(input==controller.getInputQueue().getInput(),"Same original queue/proxy at await entry");
                w=new Wait(input,done);
                expect(waits.put(input,w)==null,"One original await per Input instance in this fixture");
                note("await-enter",input,done);
                w.entered=true; // Driver readiness only; original worker immediately delegates, never waits for a capture/driver grant.
            } catch(Throwable t) { fail(t); }
            try { super.awaitInput(done); } // Original latch wait: no await-entry hold is introduced.
            catch(InterruptedException e) { fail(e); throw e; }
            if(w!=null) {
                w.returned=true;
                try { expect(done.getCount()==0,"Successful original latch await returned at zero"); note("await-return",w.input,done); }
                catch(Throwable t) { fail(t); }
            }
        }
    }
    /** Narrow original event oracle, not a graph snapshot or browser state. */
    public final class Oracle {
        @Subscribe @AllowConcurrentEvents public void receive(GameEvent event) {
            try {
                Authorization a=authorization.get();
                if(event instanceof GameEventCardTapped e && e.card()==mountainView && e.tapped()) { tapped=true; note("original-mountain-tapped",null,null); }
                if(a!=null && event instanceof GameEventSpellAbilityCast e && e.sa()==a.spellView()) {
                    // Existing Stage C oracle, on the original emitting thread; no view graph walk/fingerprint.
                    expect(Thread.currentThread()==loopThread,"Original loop emits Bolt cast");
                    expect(e.si()!=null,"Original stack instance accompanies Bolt cast event");
                    var paid=a.spell().getPayingMana(); var targets=a.spell().getTargets().getTargetPlayers();
                    // Mana stores an LKI Card snapshot, not the live Mountain reference (accepted C1 finding).
                    expect(paid.size()==1 && paid.get(0).color()==forge.card.MagicColor.RED
                            && paid.get(0).sourceCard().getId()==mountain.getId() && mountain.isTapped(),"Original RED/source-card-ID/tapped-Mountain provenance");
                    expect(com.google.common.collect.Iterables.size(targets)==1 && targets.iterator().next()==ai,"Actual original opposing-player target");
                    castVerified=true; note("original-cast-oracle",null,null);
                }
                if(event instanceof GameEventPlayerLivesChanged e && e.player()==aiView) {
                    expect(e.oldLives()==20 && e.newLives()==17,"Original emitted opponent life transition 20 to 17");
                    lifeVerified=true; note("original-life-20-to-17",null,null);
                }
                if(a!=null && event instanceof GameEventSpellResolved e && e.spell()==a.spellView()) {
                    expect(!e.hasFizzled(),"Original Bolt resolves without fizzle"); resolved=true; note("original-spell-resolved",null,null);
                }
                // Original zone moves copy Cards/views; match the original fixture card ID, not cross-zone reference identity.
                if(event instanceof GameEventCardChangeZone e && e.card().getId()==bolt.getId() && e.to()!=null && e.to().zoneType()==ZoneType.Graveyard) {
                    graveyard=true; note("original-bolt-graveyard",null,null);
                }
            } catch(Throwable t) { fail(t); } // Never inject diagnostic exceptions into original workers.
        }
    }

    private BoltLifecycleMain() {
        Deck hd=new Deck("Human Bolt"); hd.getMain().add("Mountain",39); hd.getMain().add("Lightning Bolt",1);
        Deck ad=new Deck("AI"); ad.getMain().add("Mountain",40);
        humanRegistration=new RegisteredPlayer(hd).setPlayer(new LobbyPlayerHuman("Human Bolt"));
        aiRegistration=new RegisteredPlayer(ad).setPlayer(GamePlayerUtil.createAiPlayer("AI"));
    }
    private void arrange() {
        try {
            expect(!SwingUtilities.isEventDispatchThread(),"Original fixture hook outside EDT"); loopThread=Thread.currentThread();
            var game=match.getGame();
            for(Player p:game.getPlayers()) {
                if(p.getRegisteredPlayer()==humanRegistration) human=p;
                if(p.getRegisteredPlayer()==aiRegistration) ai=p;
                for(Card c:new ArrayList<>(p.getAllCards())) game.getAction().moveToLibrary(c,null);
            }
            expect(human!=null && ai!=null && human.getController()==controller && ai.getController() instanceof forge.ai.PlayerControllerAi,"Original human/AI registrations");
            human.setLife(20,null); ai.setLife(20,null);
            for(Card c:new ArrayList<>(human.getCardsIn(ZoneType.Library))) {
                if(c.getName().equals("Lightning Bolt")) bolt=game.getAction().moveToHand(c,null);
                else if(c.getName().equals("Mountain") && mountain==null) mountain=game.getAction().moveToPlay(c,null,forge.game.ability.AbilityKey.newMap());
            }
            game.getAction().moveToHand(ai.getCardsIn(ZoneType.Library).get(0),null);
            expect(bolt!=null && mountain!=null && !mountain.isTapped() && human.getLife()==20 && ai.getLife()==20,"Original prepared fixture");
            expect(game.getStack().isEmpty() && human.getManaPool().isEmpty() && ai.getManaPool().isEmpty(),"No synthetic stack or prepaid mana");
            expect(!human.getCardsIn(ZoneType.Library).isEmpty() && !ai.getCardsIn(ZoneType.Library).isEmpty(),"Nonempty original libraries");
            boltView=bolt.getView(); mountainView=mountain.getView(); aiView=ai.getView();
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1,human,false,1); game.copyLastState();
            game.subscribeToEvents(new Oracle()); ready=true; note("fixture-ready",null,null);
        } catch(Throwable t) { fail(t); }
    }
    private void dispatch(Prompt p) {
        check(); expect(SwingUtilities.isEventDispatchThread(),"Live controller dispatch on EDT");
        Input input=controller.getInputProxy().getInput();
        if(input!=p.input() || consumed.contains(input)) { note("stale-driver-callback",p.input(),null); return; }
        Wait w=waits.get(input);
        expect(w!=null && w.entered && !w.returned && w.latch.getCount()==1 && controller.getInputQueue().getInput()==input,"Live original awaited input");
        if(!ready) {
            expect(p.enabled() && (input instanceof InputConfirm && p.ok().equals("Play") && p.cancel().equals("Draw")
                    || input instanceof InputConfirmMulligan && p.ok().equals("Keep") && p.cancel().equals("Mulligan")),"Only offered Play/Keep bootstrap answers");
            expect(++bootstrapReplies<=2,"Bounded bootstrap");
        } else if(input instanceof InputPassPriority && !p.ok().equals("OK") || input instanceof InputPayManaOfCostPayment && !p.ok().equals("Auto")) {
            note("callback-label-current-sample-mismatch",input,w.latch); return; // JDI identifies the actual origin; no guess drives an action.
        }
        consumed.add(input); note("dispatch-consume",input,w.latch); // Same EDT runnable as recheck and controller selection.
        if(!ready) controller.selectButtonOk();
        else if(stage==0 && input instanceof InputPassPriority) {
            var abilities=bolt.getAllPossibleAbilities(human,true);
            expect(abilities.size()==1 && abilities.get(0).isSpell(),"Exactly one valid original initial Bolt ability");
            var spell=abilities.get(0); authorization.set(new Authorization(spell,spell.getView(),null,null));
            stage=1; note("dispatch-cast",input,w.latch); expect(controller.selectCard(boltView,null,null),"Original cast selection accepted");
        } else if(stage==1 && input instanceof InputSelectTargets) {
            stage=2; note("dispatch-target",input,w.latch); controller.selectPlayer(aiView,null);
        } else if(stage==2 && input instanceof InputPayManaOfCostPayment) {
            payment=w; var a=authorization.get(); var mana=mountain.getManaAbilities();
            expect(mana.size()==1,"Original singleton Mountain mana ability");
            authorization.set(new Authorization(a.spell(),a.spellView(),mana.get(0),mana.get(0).getView()));
            stage=3; note("dispatch-manual-mountain",input,w.latch); expect(controller.selectCard(mountainView,null,null),"Original manual Mountain accepted; never Auto/OK");
        } else if(stage==3 && input instanceof InputPassPriority) {
            expect(payment!=null && payment.returned && payment.latch.getCount()==0 && castVerified && tapped,"Payment resolved before new stack priority");
            expect(p.enabled(),"Original ordinary priority pass enabled");
            stage=4; note("dispatch-pass",input,w.latch); controller.selectButtonOk();
        } else throw new IllegalStateException("Unexpected input/stage; no fallback decision");
        note("dispatch-return",input,w.latch);
    }
    private void run() throws Exception {
        expect(!forge.gamemodes.net.server.HostingServer.isHosting(),"No hosting server");
        match.setStartGameHook(this::arrange);
        match.startMatch(GameType.Constructed,null,List.of(humanRegistration,aiRegistration),humanRegistration,gui);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);
        while(System.nanoTime()<deadline) {
            check(); Prompt p=prompts.poll(100,TimeUnit.MILLISECONDS); if(p==null) continue;
            // Pending-await prompts are legitimate. Wait for actual await before driving; never call them a violation.
            while(controller.getInputProxy().getInput()==p.input() && (waits.get(p.input())==null || !waits.get(p.input()).entered)) {
                check(); expect(System.nanoTime()<deadline,"Pending await deadline"); Thread.sleep(10);
            }
            FutureTask<Void> task=new FutureTask<>(()->{
                try {
                    check();
                    if(stage==4 && p.ok().equals("OK") && p.input() instanceof InputPassPriority && controller.getInputProxy().getInput()==p.input() && !consumed.contains(p.input())) {
                        Wait w=waits.get(p.input());
                        expect(w!=null && w.entered && w.latch.getCount()==1 && !w.returned && controller.getInputQueue().getInput()==p.input(),"Next original human priority remains unanswered");
                        expect(castVerified && resolved && lifeVerified && graveyard && tapped && questions.get()==3 && bootstrapReplies==2,"Original Bolt/Mountain result and exact C1 sequence");
                        stage=5; note("scenario-complete",p.input(),w.latch); return null;
                    }
                    dispatch(p); return null;
                } catch(Throwable t) {
                    fail(t); if(t instanceof Exception ex) throw ex; if(t instanceof Error error) throw error;
                    throw new IllegalStateException(t);
                }
            });
            SwingUtilities.invokeLater(task);
            while(!task.isDone()) { check(); expect(System.nanoTime()<deadline,"EDT dispatch deadline"); Thread.sleep(10); }
            task.get(); check(); if(stage==5) return;
        }
        throw new TimeoutException("Bounded original Bolt sequence did not complete");
    }
    public static void main(String[] args) throws Exception {
        Thread watchdog=new Thread(()->{try { Thread.sleep(150_000); } catch(InterruptedException e) { return; } Runtime.getRuntime().halt(124);},"Lifecycle-deadline");
        watchdog.setDaemon(true); watchdog.start();
        Path output=Path.of(args[0]); int status=1; Map<String,Object> result=new LinkedHashMap<>();
        try {
            TestUtils.ensureFModelInitialized();
            FModel.getPreferences().setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS,false);
            FModel.getPreferences().setPref(FPref.UI_RECORD_REPLAYS,false);
            FModel.getPreferences().setPref(FPref.PLAYER_NAME,"Bolt Fixture"); MyRandom.setRandom(new Random(7));
            BoltLifecycleMain driver=new BoltLifecycleMain();
            Thread.setDefaultUncaughtExceptionHandler((t,e)->{driver.fail(e); e.printStackTrace();});
            driver.run(); driver.check(); result.put("outcome","PASS"); result.put("c1Questions",driver.questions.get());
            result.put("originalCastTargetAndRedPayment",driver.castVerified); result.put("originalOpponent20To17",driver.lifeVerified);
            result.put("originalResolution",driver.resolved); result.put("originalGraveyard",driver.graveyard);
            result.put("nextHumanPriorityUnanswered",driver.stage==5); status=0;
        } catch(Throwable t) { result.put("outcome","FAIL"); result.put("failure",t.toString()); t.printStackTrace(); }
        finally {
            result.put("graphProjection",false); result.put("gameplayCleanup",false); result.put("testngLifecycle",false);
            try { Files.writeString(output,LifecycleJson.encode(result)+"\n"); }
            catch(Throwable t) { status=1; t.printStackTrace(); }
            System.out.flush(); System.err.flush(); System.exit(status);
        }
    }
}
