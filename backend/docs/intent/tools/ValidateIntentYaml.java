import dev.intent.protocol.IntentSpec;
import dev.intent.sdk.host.rule.IntentRuleLoader;
import dev.intent.sdk.host.rule.IntentSuggestionRule;
import dev.intent.sdk.spec.IntentSpecLoader;
import dev.intent.sdk.spec.IntentSpecValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 意图规范与事实规则的离线校验器（不依赖 Spring / DB）。
 *
 * 用真实的 SDK 加载器逐份解析 ruoyi-intent 模块里的 YAML，
 * 把"启动时才会暴露的契约错误"提前到交付前。
 */
public class ValidateIntentYaml {

    public static void main(String[] args) throws Exception {
        Path module = Path.of(args[0]);
        Path intentDir = module.resolve("src/main/resources/intent");
        Path ruleDir = module.resolve("src/main/resources/intent-rules");

        List<String> problems = new ArrayList<>();
        Map<String, IntentSpec> specs = new LinkedHashMap<>();

        System.out.println("=== 意图规范 (" + intentDir + ") ===");
        try (Stream<Path> files = Files.list(intentDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                String name = file.getFileName().toString();
                try {
                    IntentSpec spec = IntentSpecLoader.load(file);
                    List<String> errors = IntentSpecValidator.validate(spec);
                    if (errors.isEmpty()) {
                        specs.put(spec.getId(), spec);
                        System.out.printf("  OK   %-32s tools=%s pages=%s roles=%s%n",
                                spec.getId(), spec.getTools(), spec.getPages(),
                                spec.getPolicy() == null ? "-" : spec.getPolicy().getRoles());
                    } else {
                        problems.add(name + " -> " + errors);
                        System.out.println("  FAIL " + name + " -> " + errors);
                    }
                } catch (Exception e) {
                    problems.add(name + " -> " + e.getMessage());
                    System.out.println("  FAIL " + name + " -> " + e.getMessage());
                }
            }
        }
        // 编号唯一性
        long yamlCount;
        try (Stream<Path> files = Files.list(intentDir)) {
            yamlCount = files.filter(p -> p.toString().endsWith(".yaml")).count();
        }
        if (specs.size() != yamlCount) {
            problems.add("意图编号存在重复：文件 " + yamlCount + " 个，唯一编号 " + specs.size() + " 个");
        }

        System.out.println();
        System.out.println("=== 事实规则 (" + ruleDir + ") ===");
        List<IntentSuggestionRule> rules = new ArrayList<>();
        try (Stream<Path> files = Files.list(ruleDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList()) {
                try {
                    List<IntentSuggestionRule> loaded =
                            IntentRuleLoader.parse(Files.readString(file), file.getFileName().toString());
                    rules.addAll(loaded);
                    for (IntentSuggestionRule rule : loaded) {
                        System.out.printf("  OK   %-28s intent=%-28s dataset=%-24s badge=%s items=%d%n",
                                rule.id(), rule.intentId(), rule.dataset(),
                                rule.hasBadge() ? "Y" : "N",
                                rule.hasItems() ? rule.items().effectiveLimit() : 0);
                    }
                } catch (Exception e) {
                    problems.add(file.getFileName() + " -> " + e.getMessage());
                    System.out.println("  FAIL " + file.getFileName() + " -> " + e.getMessage());
                }
            }
        }

        // 规则对目录的契约校验（意图存在 + 参数在 Schema 内 + 必填可映射）
        System.out.println();
        System.out.println("=== 规则 × 目录契约校验 ===");
        try {
            IntentRuleLoader.validate(rules, id -> {
                IntentSpec spec = specs.get(id);
                return spec == null ? null : dev.intent.sdk.host.IntentCatalogEntries.of(spec);
            });
            System.out.println("  OK   全部 " + rules.size() + " 条规则通过");
        } catch (Exception e) {
            problems.add("规则契约校验 -> " + e.getMessage());
            System.out.println("  FAIL " + e.getMessage());
        }

        System.out.println();
        if (problems.isEmpty()) {
            System.out.println("结果：全部通过（意图 " + specs.size() + " 个，规则 " + rules.size() + " 条）");
        } else {
            System.out.println("结果：发现 " + problems.size() + " 个问题");
            problems.forEach(p -> System.out.println("  - " + p));
            System.exit(1);
        }
    }
}
