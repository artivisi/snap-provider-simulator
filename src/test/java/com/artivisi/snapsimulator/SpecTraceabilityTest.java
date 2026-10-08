package com.artivisi.snapsimulator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spec ↔ code ↔ test: every @SpecRef resolves to docs/spec-index.json, and every
 * in-scope item is referenced from src/main and from src/test.
 */
class SpecTraceabilityTest {

    /**
     * In-scope items not implemented yet. Must be empty for a release; an entry
     * that is already traced in main and test fails the build so the list only shrinks.
     */
    private static final Set<String> PENDING = Set.of(
            "snap.headers.token", "snap.headers.service", "snap.external-id",
            "bri.va.number-layout", "bri.oauth.token-b2b", "aspi.oauth.token-b2b-outbound",
            "aspi.va.create-va", "aspi.va.update-va", "aspi.va.inquiry-va", "aspi.va.delete-va",
            "aspi.va.inquiry-status", "bri.briva-online.inquiry", "bri.briva-online.payment",
            "bri.channel-id", "bri.payment-flag-status", "aspi.va.trx-type", "sim.statement-csv",
            "sim.portal.signup", "sim.portal.credentials", "sim.portal.key-generate", "sim.portal.key-upload",
            "sim.portal.endpoint", "sim.portal.settings", "sim.portal.checklist", "sim.portal.reset",
            "sim.admin.partners", "sim.diagnostic-mode", "sim.biller-payment.trigger",
            "sim.biller-payment.resend", "sim.va.pay", "sim.error-injection", "sim.exchange-log",
            "sim.reconciliation-seeder");

    private static final Map<String, JsonNode> ITEMS = new HashMap<>();
    private static final List<Ref> MAIN = new ArrayList<>();
    private static final List<Ref> TEST = new ArrayList<>();

    record Ref(String value, String location) {
        String itemId() {
            int hash = value.indexOf('#');
            return hash < 0 ? value : value.substring(0, hash);
        }
    }

    @BeforeAll
    static void scan() throws IOException {
        JsonNode index = JsonMapper.builder().build().readTree(Path.of("docs/spec-index.json").toFile());
        index.get("items").forEach(item -> ITEMS.put(item.get("id").asString(), item));
        collect(Path.of("target/classes"), MAIN);
        collect(Path.of("target/test-classes"), TEST);
    }

    @Test
    @DisplayName("Every @SpecRef names an existing item, and field refs name a field of that item")
    void refsResolve() {
        List<String> unresolved = Stream.concat(MAIN.stream(), TEST.stream())
                .filter(ref -> !resolves(ref.value()))
                .map(ref -> ref.value() + " at " + ref.location())
                .toList();
        assertThat(unresolved).as("unresolved @SpecRef").isEmpty();
    }

    @Test
    @DisplayName("Every in-scope item is implemented in src/main and verified in src/test")
    void inScopeItemsTraced() {
        Set<String> inMain = itemIds(MAIN);
        Set<String> inTest = itemIds(TEST);
        Set<String> missing = new TreeSet<>();
        Set<String> stalePending = new TreeSet<>();
        ITEMS.forEach((id, item) -> {
            if (!"in".equals(item.get("scope").asString())) {
                return;
            }
            boolean traced = inMain.contains(id) && inTest.contains(id);
            if (PENDING.contains(id)) {
                if (traced) {
                    stalePending.add(id);
                }
            } else if (!traced) {
                missing.add(id + (inMain.contains(id) ? "" : " [no main ref]") + (inTest.contains(id) ? "" : " [no test ref]"));
            }
        });
        assertThat(missing).as("in-scope items without main and test refs").isEmpty();
        assertThat(stalePending).as("traced items still listed in PENDING").isEmpty();
        assertThat(PENDING).as("PENDING ids that are not in-scope index items")
                .allMatch(id -> ITEMS.containsKey(id) && "in".equals(ITEMS.get(id).get("scope").asString()));
    }

    @Test
    @DisplayName("Out-of-scope items are not referenced from src/main")
    void outOfScopeNotImplemented() {
        List<String> refs = MAIN.stream()
                .filter(ref -> ITEMS.containsKey(ref.itemId()) && !"in".equals(ITEMS.get(ref.itemId()).get("scope").asString()))
                .map(ref -> ref.value() + " at " + ref.location())
                .toList();
        assertThat(refs).isEmpty();
    }

    private static boolean resolves(String value) {
        int hash = value.indexOf('#');
        JsonNode item = ITEMS.get(hash < 0 ? value : value.substring(0, hash));
        if (item == null) {
            return false;
        }
        if (hash < 0) {
            return true;
        }
        String field = value.substring(hash + 1);
        int dot = field.indexOf('.');
        if (dot < 0) {
            return false;
        }
        String side = field.substring(0, dot);
        String name = field.substring(dot + 1);
        if (!side.equals("request") && !side.equals("response")) {
            return false;
        }
        JsonNode fields = item.get(side);
        if (fields == null || !fields.isArray()) {
            return false;
        }
        for (JsonNode f : fields) {
            if (name.equals(f.get("name").asString())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> itemIds(List<Ref> refs) {
        Set<String> ids = new TreeSet<>();
        refs.forEach(ref -> ids.add(ref.itemId()));
        return ids;
    }

    private static void collect(Path root, List<Ref> into) throws IOException {
        assertThat(root).as("compiled classes; run through Maven").isDirectory();
        List<String> names;
        try (Stream<Path> files = Files.walk(root)) {
            names = files.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> root.relativize(p).toString().replace('/', '.').replace('\\', '.'))
                    .map(n -> n.substring(0, n.length() - ".class".length()))
                    .filter(n -> !n.endsWith("module-info") && !n.endsWith("package-info"))
                    .toList();
        }
        ClassLoader loader = SpecTraceabilityTest.class.getClassLoader();
        for (String name : names) {
            Class<?> type;
            try {
                type = Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalStateException("cannot load " + name, e);
            }
            add(type, name, into);
            for (var m : type.getDeclaredMethods()) {
                add(m, name + "#" + m.getName(), into);
            }
            for (var f : type.getDeclaredFields()) {
                add(f, name + "." + f.getName(), into);
            }
            for (var c : type.getDeclaredConstructors()) {
                add(c, name + "#<init>", into);
            }
        }
    }

    private static void add(AnnotatedElement element, String location, List<Ref> into) {
        for (SpecRef ref : element.getDeclaredAnnotationsByType(SpecRef.class)) {
            into.add(new Ref(ref.value(), location));
        }
    }
}
