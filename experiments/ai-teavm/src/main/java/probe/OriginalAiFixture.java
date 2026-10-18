package probe;

import forge.ai.*;
import forge.ai.ability.DamageDealAi;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.DamageDealEffect;
import forge.game.card.*;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.AITimeoutTracker;
import forge.util.Lang;
import forge.util.Localizer;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.util.MyRandom;
import java.util.*;

/** Controlled engine snapshot only. All choices, payment and resolution are original Forge. */
public final class OriginalAiFixture {
    public static volatile String stage = "not-started";
    public static volatile SpellAbility chosen;

    public static void bootstrap() {
        Localizer.getInstance().initialize("en-US", "build/fixture/languages/");
        Lang.createInstance("en-US");
        // Same original type-list parser/bootstrap calls as FModel; no GUI/model required.
        if (!CardType.Constant.LOADED.isSet()) {
            Map<String,List<String>> contents=FileSection.parseSections(FileUtil.readFile("build/fixture/TypeLists.txt"));
            for (Map.Entry<String,List<String>> entry:contents.entrySet()) CardType.Helper.parseTypes(entry.getKey(),entry.getValue());
            CardType.Constant.LOADED.set();
        }
        require(CardType.isABasicLandType("Mountain"), "original type data loaded");
    }
    public static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Assertion failed: " + message);
    }
    private static Card card(PaperCard paper, Player owner, Game game, ZoneType zone) {
        Card result = CardFactory.getCard(paper, owner, game);
        owner.getZone(zone).add(result);
        require(result.getOwner() == owner && result.getController() == owner, "card ownership/controller");
        require(result.getZone() == owner.getZone(zone), "original zone identity");
        return result;
    }
    public static Map<String,Object> run(boolean emptyHand) {
        long start = System.nanoTime();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("emptyHand", emptyHand);
        result.put("passed", false);
        try {
            stage = "bootstrap"; bootstrap();
            stage = "original-random-initialization";
            long randomStart=System.nanoTime();
            MyRandom.getRandom().nextInt(); // initialize the genuine SecureRandom before the AI's fixed deadline
            result.put("originalRandomInitializationMs",(System.nanoTime()-randomStart)/1_000_000.0);
            PaperCard bolt = new PaperCard(CardRules.fromScript(Arrays.asList(FixtureScripts.BOLT)), "", CardRarity.Common);
            PaperCard mountain = new PaperCard(CardRules.fromScript(Arrays.asList(FixtureScripts.MOUNTAIN)), "", CardRarity.Common);
            Deck deckAi = new Deck("Bounded Bolt AI");
            deckAi.getOrCreate(DeckSection.Main).add(mountain,12);
            if (!emptyHand) deckAi.getOrCreate(DeckSection.Main).add(bolt,2);
            Deck deckOpponent = new Deck("Bounded opponent");
            deckOpponent.getOrCreate(DeckSection.Main).add(mountain,10);
            List<RegisteredPlayer> registered = Arrays.asList(
                new RegisteredPlayer(deckAi).setPlayer(new LobbyPlayerAi("AI", Collections.emptySet())),
                new RegisteredPlayer(deckOpponent).setPlayer(new LobbyPlayerAi("Opponent", Collections.emptySet())));
            GameRules rules = new GameRules(GameType.Constructed);
            Game game = new Game(registered, rules, new Match(rules, registered, "Bounded Bolt"));
            Player ai = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
            require(ai.getController().getClass() == PlayerControllerAi.class && opponent.getController().getClass() == PlayerControllerAi.class, "genuine controllers");
            AiController brain = ((PlayerControllerAi) ai.getController()).getAi();
            brain.setUseSimulation(null); game.AI_TIMEOUT = 2;
            require(!brain.usesFullSimulation() && !brain.usesHybridSimulation(), "simulation disabled");
            stage = "cards-and-zones";
            Set<Integer> cardIds = new HashSet<>();
            for (Player player : Arrays.asList(ai,opponent)) for (int i=0;i<10;i++)
                require(cardIds.add(card(mountain,player,game,ZoneType.Library).getId()), "unique library card id");
            List<Card> lands = new ArrayList<>();
            for (int i=0;i<2;i++) {
                Card land=card(mountain,ai,game,ZoneType.Battlefield);
                require(cardIds.add(land.getId()) && !land.isTapped(), "unique untapped Mountain");
                lands.add(land);
                if (!emptyHand) {
                    Card spell=card(bolt,ai,game,ZoneType.Hand);
                    require(cardIds.add(spell.getId()), "unique Bolt");
                }
            }
            game.setAge(GameStage.Play);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
            game.getPhaseHandler().resetPriority();
            ai.setLife(20,null); opponent.setLife(3,null);
            game.copyLastState();
            result.put("manaAbilityCounts",Arrays.asList(lands.get(0).getManaAbilities().size(),lands.get(1).getManaAbilities().size()));
            result.put("priorityPlayerIsAi",game.getPhaseHandler().getPriorityPlayer()==ai);
            require(game.getStack().isEmpty(), "initial stack empty");
            require(ai.getManaPool().isEmpty(), "initial mana pool empty");
            require(ai.getCardsIn(ZoneType.Library).size()==10 && opponent.getCardsIn(ZoneType.Library).size()==10, "nonempty original libraries");
            List<SpellAbility> candidates=ComputerUtilAbility.getSpellAbilities(ComputerUtilAbility.getAvailableCards(game,ai),ai);
            List<SpellAbility> bolts = new ArrayList<>();
            for (SpellAbility ability:candidates) if (ability.isSpell() && !ability.isManaAbility() && ability.getHostCard().getName().equals("Lightning Bolt")) bolts.add(ability);
            require(emptyHand ? bolts.isEmpty() && candidates.stream().noneMatch(SpellAbility::isSpell) : !bolts.isEmpty(), "eligible original spell candidates");
            if (!emptyHand) {
                for (SpellAbility ability:bolts) {
                    ability.setActivatingPlayer(ai);
                    require(ability.canCastTiming(ai), "legal original cast timing");
                    require(ComputerUtilCost.canPayCost(ability,ai,false), "original affordability");
                }
                require(ApiType.DealDamage.getSpellEffect().getClass()==DamageDealEffect.class, "exact damage effect");
                require(SpellApiToAi.Converter.get(ApiType.DealDamage).getClass()==DamageDealAi.class, "exact damage AI");
            }
            result.put("gameId",game.getId()); result.put("aiPlayerId",ai.getId());
            result.put("registeredDeckCards",Arrays.asList(deckAi.getMain().countAll(),deckOpponent.getMain().countAll()));
            result.put("libraryCounts",Arrays.asList(ai.getCardsIn(ZoneType.Library).size(),opponent.getCardsIn(ZoneType.Library).size()));
            result.put("candidateCount",candidates.size()); result.put("boltCandidateCount",bolts.size());
            result.put("manaCandidateCount",candidates.stream().filter(SpellAbility::isManaAbility).count());
            stage = "original-ai-decision";
            long decisionStart=System.nanoTime();
            List<SpellAbility> decision=brain.chooseSpellAbilityToPlay();
            result.put("decisionMs",(System.nanoTime()-decisionStart)/1_000_000.0);
            require(!AITimeoutTracker.isActive(AITimeoutTracker.key(game.getId(),ai.getId())), "AI timeout session cleaned");
            if (emptyHand) {
                require(decision==null, "empty-hand null decision");
                require(ai.getLife()==20 && opponent.getLife()==3 && game.getStack().isEmpty(), "negative unchanged game state");
                require(lands.stream().noneMatch(Card::isTapped), "negative no mana payment");
                result.put("decision",null);
            } else {
                require(decision!=null && decision.size()==1, "one original spell choice");
                chosen=decision.get(0);
                require(chosen.isSpell() && chosen.getHostCard().getName().equals("Lightning Bolt") && chosen.getApi()==ApiType.DealDamage, "genuine Bolt decision");
                Iterator<Player> targets=chosen.getTargets().getTargetPlayers().iterator();
                require(targets.hasNext() && targets.next()==opponent && !targets.hasNext(), "AI chose opponent");
                result.put("chosenCardId",chosen.getHostCard().getId()); result.put("chosenClass",chosen.getClass().getName());
                result.put("targetPlayerId",opponent.getId()); result.put("effectClass",ApiType.DealDamage.getSpellEffect().getClass().getName());
                stage = "original-payment-and-cast";
                require(ComputerUtil.playStack(chosen,ai,game), "original paid cast succeeds");
                SpellAbility onStack=game.getStack().peekAbility();
                require(game.getStack().size()==1 && onStack.getHostCard().getName().equals("Lightning Bolt"), "Bolt on original stack");
                require(opponent.getLife()==3, "no damage before resolution");
                List<Mana> paid=onStack.getPayingMana();
                require(paid.size()==1 && paid.get(0).color()==MagicColor.RED, "one real R paid");
                require(paid.get(0).sourceCard().getName().equals("Mountain"), "original Mountain mana source");
                long tapped=lands.stream().filter(Card::isTapped).count();
                require(tapped==1, "exactly one Mountain tapped");
                result.put("paidMana",paid.get(0).toString()); result.put("paidSourceCardId",paid.get(0).sourceCard().getId());
                result.put("payingManaAbilityCount",onStack.getPayingManaAbilities().size()); result.put("tappedMountains",tapped);
                result.put("stackBeforeResolution",game.getStack().size());
                stage = "original-resolution";
                game.getStack().resolveStack(); game.getAction().checkStateEffects(true);
                require(opponent.getLife()==0 && ai.getLife()==20, "original three damage and life totals");
                require(game.getStack().isEmpty(), "resolved stack empty");
                require(ai.getCardsIn(ZoneType.Graveyard).stream().filter(c->c.getName().equals("Lightning Bolt")).count()==1, "cast Bolt in graveyard");
                require(game.isGameOver(), "original lethal game outcome");
                result.put("lifeAfter",Arrays.asList(ai.getLife(),opponent.getLife()));
                result.put("stackAfterResolution",game.getStack().size()); result.put("gameOver",game.isGameOver());
            }
            stage = "complete"; result.put("passed",true);
        } catch (Throwable error) {
            result.put("errorClass",error.getClass().getName()); result.put("error",String.valueOf(error.getMessage()));
            List<String> stack=new ArrayList<>(); for (StackTraceElement frame:error.getStackTrace()) stack.add(frame.toString());
            result.put("stack",stack);
        }
        result.put("stage",stage); result.put("elapsedMs",(System.nanoTime()-start)/1_000_000.0);
        return result;
    }
}
