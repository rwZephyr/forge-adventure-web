package probe;

import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.game.CardTraitBase;
import forge.game.ability.AbilityKey;
import forge.game.keyword.*;
import forge.game.replacement.*;
import forge.game.trigger.*;
import forge.game.zone.ZoneType;

import java.util.*;

/** Only calls original factories. No game bootstrap, GUI, AI, or substitute products. */
public final class FactoryCases {
    private FactoryCases() { }

    public static final class Result {
        final Map<String, Object> fields = new LinkedHashMap<>();
        final Map<String, Object> observations = new LinkedHashMap<>();
        Result(String id, String expected) {
            fields.put("id", id);
            fields.put("expectedClass", expected);
            fields.put("observations", observations);
        }
        void product(Object value, Class<?> expected) {
            fields.put("actualClass", value == null ? null : value.getClass().getName());
            check(value != null && value.getClass() == expected, "exact concrete class");
        }
        void observe(String key, Object value) { observations.put(key, value); }
        void check(boolean condition, String label) {
            if (!condition) throw new IllegalStateException("Assertion failed: " + label);
        }
    }

    @FunctionalInterface private interface Action { void run(Result result); }

    private static void attempt(List<Map<String, Object>> rows, String id, String expected, Action action) {
        Result r = new Result(id, expected);
        try {
            action.run(r);
            r.fields.put("passed", true);
        } catch (Throwable failure) {
            r.fields.put("passed", false);
            r.fields.put("errorClass", failure.getClass().getName());
            r.fields.put("error", failure.getMessage());
            List<String> stack = new ArrayList<>();
            for (StackTraceElement frame : failure.getStackTrace()) stack.add(frame.toString());
            r.fields.put("stack", stack);
            if (failure.getCause() != null) r.fields.put("cause", failure.getCause().toString());
        }
        rows.add(r.fields);
    }

    private static Map<String, String> params(String... pairs) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> result = new ArrayList<>();
        for (Enum<?> value : values) result.add(value.name());
        return result;
    }

    private static void trait(Result r, CardTraitBase value, boolean intrinsic, String key, String expected) {
        r.check(value.getHostCard() == null, "null host stored");
        r.check(value.isIntrinsic() == intrinsic, "intrinsic propagated");
        r.check(expected.equals(value.getParam(key)), "parameter copied");
        r.check(expected.equals(value.getOriginalParam(key)), "original parameter copied");
    }

    private static void trigger(Result r, boolean lifeGained) {
        TriggerType type = lifeGained ? TriggerType.LifeGained : TriggerType.smartValueOf(" always ");
        Class<?> expectedClass = lifeGained ? TriggerLifeGained.class : TriggerAlways.class;
        Trigger previous = null;
        for (boolean intrinsic : new boolean[] {true, false}) {
            Map<String, String> input = lifeGained
                    ? params("Mode", "LifeGained", "FirstTime", "True")
                    : params("Mode", "Always", "TriggerZones", "Battlefield");
            Trigger value = type.createTrigger(input, null, intrinsic);
            r.product(value, expectedClass);
            r.check(value != previous, "independent trigger instances");
            trait(r, value, intrinsic, "Mode", type.name());
            r.check(value.getMode() == type, "trigger mode assigned");
            input.put("Mode", "ChangedAfterConstruction");
            trait(r, value, intrinsic, "Mode", type.name());
            if (lifeGained) {
                Map<AbilityKey, Object> event = new EnumMap<>(AbilityKey.class);
                event.put(AbilityKey.FirstTime, true);
                boolean first = ((TriggerLifeGained) value).performTest(event);
                event.put(AbilityKey.FirstTime, false);
                boolean later = ((TriggerLifeGained) value).performTest(event);
                r.observe("firstTimeTrue", first); r.observe("firstTimeFalse", later);
                r.check(first && !later, "original FirstTime predicate");
            } else {
                r.check(value.getActiveZone().equals(EnumSet.of(ZoneType.Battlefield)), "original zone parsing");
                boolean test = ((TriggerAlways) value).performTest(Collections.emptyMap());
                r.observe("activeZone", "Battlefield"); r.observe("performTest", test);
                r.check(test, "Always predicate");
            }
            previous = value;
        }
        r.observe("mode", type.name());
        r.observe("intrinsicBothValues", true); r.observe("inputMutationPreserved", true);
    }

    private static void replacement(Result r, boolean gainLife) {
        ReplacementType type = gainLife ? ReplacementType.GainLife : ReplacementType.smartValueOf(" draw ");
        Class<?> expectedClass = gainLife ? ReplaceGainLife.class : ReplaceDraw.class;
        ReplacementEffect previous = null;
        for (boolean intrinsic : new boolean[] {true, false}) {
            Map<String, String> input = params("Event", type.name());
            ReplacementEffect value = type.createReplacement(input, null, intrinsic);
            r.product(value, expectedClass);
            r.check(previous != value, "independent replacement instances");
            trait(r, value, intrinsic, "Event", type.name());
            r.check(value.getMode() == type, "replacement mode assigned");
            input.put("Event", "ChangedAfterConstruction");
            trait(r, value, intrinsic, "Event", type.name());
            if (gainLife) {
                List<Boolean> results = new ArrayList<>();
                for (int amount : new int[] {1, 0, -1}) {
                    Map<AbilityKey, Object> event = new EnumMap<>(AbilityKey.class);
                    event.put(AbilityKey.LifeGained, amount);
                    boolean actual = ((ReplaceGainLife) value).canReplace(event); results.add(actual);
                    r.check(actual == (amount > 0), "original life gain predicate: " + amount);
                }
                r.observe("lifeGained1_0_minus1", results);
            }
            previous = value;
        }
        r.observe("mode", type.name());
        r.observe("intrinsicBothValues", true); r.observe("inputMutationPreserved", true);
    }

    private static void keyword(Result r, String input, Keyword expected, Class<?> expectedClass) {
        KeywordInterface value = Keyword.getInstance(input);
        // Capture these before class assertion so a silent fallback is inspectable.
        r.observe("keyword", value.getKeyword().name());
        r.observe("original", value.getOriginal());
        r.observe("amount", value.getAmount());
        r.product(value, expectedClass);
        r.check(value.getKeyword() == expected, "keyword enum identity");
        r.check(input.equals(value.getOriginal()), "original keyword input");
        KeywordInterface second = Keyword.getInstance(input);
        r.check(second != value && second.getClass() == expectedClass, "independent keyword instance");
        if (expected == Keyword.BUSHIDO) {
            r.check(value.getAmount() == 2 && "2".equals(value.getAmountString()), "amount parsed");
            r.observe("amountString", value.getAmountString());
        } else if (expected == Keyword.ENCHANT) {
            KeywordWithType typed = (KeywordWithType) value;
            r.observe("validType", typed.getValidType()); r.observe("typeDescription", typed.getTypeDescription());
            r.check("Card.Creature".equals(typed.getValidType()) && "creature".equals(typed.getTypeDescription()), "type parsed");
        } else if (expected == Keyword.KICKER || expected == Keyword.MULTIKICKER) {
            KeywordWithCostInterface cost = (KeywordWithCostInterface) value;
            r.check("1 R".equals(cost.getCostString()), "original cost string");
            r.check(cost.getCost() != null && cost.getCost().getCostMana() != null, "real mana cost exists");
            ManaCost mana = cost.getCost().getCostMana().getMana();
            r.observe("costString", cost.getCostString()); r.observe("manaValue", mana.getCMC());
            r.observe("generic", mana.getGenericCost()); r.observe("redShards", mana.getShardCount(ManaCostShard.RED));
            r.check(mana.getCMC() == 2 && mana.getGenericCost() == 1 && mana.getShardCount(ManaCostShard.RED) == 1, "original mana parser");
        }
    }

    public static Map<String, Object> run() {
        List<Map<String, Object>> rows = new ArrayList<>();
        attempt(rows, "enum.trigger", null, r -> r.observe("names", names(TriggerType.values())));
        attempt(rows, "enum.replacement", null, r -> r.observe("names", names(ReplacementType.values())));
        attempt(rows, "enum.keyword", null, r -> r.observe("names", names(Keyword.values())));
        attempt(rows, "T1", TriggerAlways.class.getName(), r -> trigger(r, false));
        attempt(rows, "T2", TriggerLifeGained.class.getName(), r -> trigger(r, true));
        attempt(rows, "R1", ReplaceDraw.class.getName(), r -> replacement(r, false));
        attempt(rows, "R2", ReplaceGainLife.class.getName(), r -> replacement(r, true));
        attempt(rows, "K1", SimpleKeyword.class.getName(), r -> keyword(r, "Flying", Keyword.FLYING, SimpleKeyword.class));
        attempt(rows, "K2", KeywordWithAmount.class.getName(), r -> keyword(r, "Bushido:2", Keyword.BUSHIDO, KeywordWithAmount.class));
        attempt(rows, "K3", KeywordWithType.class.getName(), r -> keyword(r, "Enchant:Card.Creature:creature", Keyword.ENCHANT, KeywordWithType.class));
        attempt(rows, "K4", KeywordWithCost.class.getName(), r -> keyword(r, "Multikicker:1 R", Keyword.MULTIKICKER, KeywordWithCost.class));
        attempt(rows, "K5", Kicker.class.getName(), r -> keyword(r, "Kicker:1 R", Keyword.KICKER, Kicker.class));
        attempt(rows, "invalid.trigger", null, r -> {
            boolean thrown = false;
            try { TriggerType.smartValueOf("NotAForgeTrigger"); } catch (RuntimeException expected) {
                if (!"Element NotAForgeTrigger not found in TriggerType enum".equals(expected.getMessage())) throw expected;
                thrown = true;
            }
            r.check(thrown, "invalid trigger throws"); r.observe("threw", thrown);
        });
        attempt(rows, "invalid.replacement", null, r -> {
            boolean thrown = false;
            try { ReplacementType.smartValueOf("NotAForgeReplacement"); } catch (RuntimeException expected) {
                if (!"Element NotAForgeReplacement not found in ReplacementType enum".equals(expected.getMessage())) throw expected;
                thrown = true;
            }
            r.check(thrown, "invalid replacement throws"); r.observe("threw", thrown);
        });
        attempt(rows, "unknown.keyword", SimpleKeyword.class.getName(), r -> keyword(r, "NotAForgeKeyword", Keyword.UNDEFINED, SimpleKeyword.class));
        int passed = 0;
        for (Map<String, Object> row : rows) if (Boolean.TRUE.equals(row.get("passed"))) passed++;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("completed", true);
        result.put("passed", passed == rows.size()); result.put("passedCases", passed);
        result.put("totalCases", rows.size()); result.put("cases", rows);
        return result;
    }
}
