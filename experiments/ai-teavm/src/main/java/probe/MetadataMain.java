package probe;

import forge.ai.SpellApiToAi;
import forge.game.ability.ApiType;
import forge.game.trigger.TriggerType;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** JVM-only inventory/audit of the original enums; no replacement factory behavior. */
public final class MetadataMain {
    public static void main(String[] args) throws Exception {
        OriginalAiFixture.bootstrap();
        TreeMap<String,String> constructors=new TreeMap<>();
        for (String line:Files.readAllLines(Path.of("../rules-teavm/reflection-constructors.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields=line.split("\t",-1); constructors.put(fields[0],fields[1]);
        }
        int originalCount=constructors.size();
        List<Map<String,Object>> effects=new ArrayList<>(), ai=new ArrayList<>();
        Field effect=ApiType.class.getDeclaredField("clsEffect"); effect.setAccessible(true);
        for (ApiType api:ApiType.values()) {
            Class<?> cls=(Class<?>)effect.get(api);
            constructors.put(cls.getName(),"");
            effects.add(Map.of("api",api.name(),"class",cls.getName()));
        }
        Field mapping=SpellApiToAi.class.getDeclaredField("apiToClass"); mapping.setAccessible(true);
        Map<?,?> originalMapping=(Map<?,?>)mapping.get(SpellApiToAi.Converter);
        for (Map.Entry<?,?> entry:originalMapping.entrySet()) {
            Class<?> cls=(Class<?>)entry.getValue(); constructors.put(cls.getName(),"");
            ai.add(Map.of("api",((ApiType)entry.getKey()).name(),"class",cls.getName()));
        }
        List<String> manifest=new ArrayList<>();
        manifest.add("# Audited original JVM constructor signatures; compiler-only access policy.");
        for (Map.Entry<String,String> entry:constructors.entrySet()) {
            Class<?> cls=Class.forName(entry.getKey(),false,MetadataMain.class.getClassLoader());
            List<Class<?>> params=new ArrayList<>();
            if (!entry.getValue().isEmpty()) for (String param:entry.getValue().split(",")) params.add(param.equals("boolean")?boolean.class:Class.forName(param));
            Constructor<?> constructor=cls.getDeclaredConstructor(params.toArray(new Class<?>[0]));
            if (!Modifier.isPublic(constructor.getModifiers())) throw new IllegalStateException("Nonpublic original constructor: "+entry);
            manifest.add(entry.getKey()+"\t"+entry.getValue());
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("originalStageASignatures",originalCount); result.put("triggerEnumEntries",TriggerType.values().length);
        result.put("apiEnumEntries",ApiType.values().length); result.put("aiMappings",originalMapping.size());
        result.put("auditedConstructorSignatures",constructors.size()); result.put("effects",effects); result.put("ai",ai);
        Files.write(Path.of("build/audited-constructors.tsv"),manifest);
        List<String> policy=new ArrayList<>();
        for (String line:manifest) {
            String cls=line.split("\t",-1)[0];
            if (!cls.startsWith("forge.ai.") || cls.equals("forge.ai.ability.DamageDealAi") || cls.equals("forge.ai.ability.ManaAi")) policy.add(line);
        }
        result.put("policyConstructorSignatures",policy.size()-1);
        result.put("policyAiClasses",List.of("forge.ai.ability.DamageDealAi","forge.ai.ability.ManaAi"));
        result.put("policyRationale","All Stage A and original effect constructors; only AI products used by the genuine Bolt and Mountain fixtures. Original enums/maps/classes are not trimmed.");
        Files.write(Path.of(args[0]),policy); Files.writeString(Path.of(args[1]),Json.encode(result)+"\n");
        System.out.println("Original constructors audited="+constructors.size()+"; ApiType="+effects.size()+"; AI mappings="+ai.size());
        System.out.println("Fixture compiler policy constructor signatures="+(policy.size()-1));
    }
}
