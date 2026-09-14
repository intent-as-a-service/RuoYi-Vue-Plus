import dev.intent.sdk.executor.ExecutorProfile;
import dev.intent.sdk.executor.ExecutorProfileLoader;
import dev.intent.sdk.executor.ExecutorProfileValidator;

import java.nio.file.Path;
import java.util.List;

public class CheckExecutor {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        ExecutorProfile profile = ExecutorProfileLoader.load(path);
        List<String> errors = ExecutorProfileValidator.validate(profile);
        System.out.println("id=" + profile.id() + " type=" + profile.type()
                + " steps=" + profile.steps().size() + " outputKeys=" + profile.output().keySet());
        System.out.println("steps=" + profile.steps().stream()
                .map(step -> step.name() + "->" + step.tool()).toList());
        System.out.println("blocks 模板以 [ 开头 ? "
                + profile.output().getOrDefault("blocks", "").trim().startsWith("["));
        System.out.println("blocks 占位符自带引号 ? "
                + profile.output().getOrDefault("blocks", "").contains("\"${steps."));
        System.out.println("errors=" + errors);
    }
}
