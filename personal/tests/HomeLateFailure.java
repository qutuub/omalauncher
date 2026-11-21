import dev.omalauncher.widgets.lang.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class HomeLateFailure {
    private static final Value NULL = Null.INSTANCE;
    private static final Json JSON = Json.INSTANCE;

    // Run from the repo root with:
    // java -cp owl/build/cli/owl.jar personal/tests/HomeLateFailure.java
    public static void main(String[] args) throws Exception {
        var face = Path.of(args.length == 0 ? "personal/faces/home.owl" : args[0]);
        var program = dev.omalauncher.widgets.lang.Compiler.Companion.compile(Files.readString(face), "en");
        var host = new FaceHost(program);
        var env = Env.Companion.root(host);
        host.set("now", JSON.parse("1795132800"));
        host.set("todo", JSON.parse("\"- [ ] first\\n- [ ] second\""));

        var deadline = trigger(program, TriggerKind.AT);
        var saved = trigger(program, TriggerKind.ON);
        var taps = new ArrayList<Runnable>();
        collectTaps(program.getRoot(), env, taps);
        check(taps.size() == 2, "expected two task rows");

        taps.get(0).run();
        var oldCommand = host.get("edit");
        check(deadline.getExpr().invoke(env) != NULL, "first tap did not set a deadline");

        // Advance past the deadline, start a new tap, then deliver the old command's result.
        host.set("now", JSON.parse("1795132920"));
        deadline.getAction().getRun().invoke(env, List.of());
        taps.get(1).run();
        var newCommand = host.get("edit");
        var newDeadline = host.get("editDeadline");
        var failedBeforeResult = host.get("editFailed");
        check(host.get("ticket").equals(JSON.parse("2")), "second tap did not advance the ticket");
        check(newCommand != NULL && !newCommand.equals(oldCommand), "second tap did not start");
        check(newDeadline != NULL, "second tap did not set a deadline");

        var oldFailure = fail(oldCommand);
        saved.getAction().getRun().invoke(env, List.of(oldFailure, NULL));
        check(newCommand.equals(host.get("edit")), "late failure cleared the newer edit");
        check(newDeadline.equals(host.get("editDeadline")), "late failure changed the newer deadline");
        check(failedBeforeResult.equals(host.get("editFailed")), "late failure changed the error state");
        check(oldFailure.getFields().get("out").toString().equals("1"), "failure lost its ticket");

        var newFailure = fail(newCommand);
        saved.getAction().getRun().invoke(env, List.of(newFailure, oldFailure));
        check(host.get("edit") == NULL, "current failure did not clear the edit");
        check(host.get("editDeadline") == NULL, "current failure did not clear the deadline");
        check(host.get("editFailed").getTruthy(), "current failure did not set the error state");
        System.out.println("PASS: late failure leaves the newer tap pending");
    }

    private static Trigger trigger(Program program, TriggerKind kind) {
        return program.getTriggers().stream().filter(t -> t.getKind() == kind).findFirst().orElseThrow();
    }

    private static Obj fail(Value command) throws Exception {
        var builder = new ProcessBuilder("bash", "-c", command.toString());
        builder.environment().put("HOME", "/dev/null");
        var process = builder.start();
        var out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        var err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        int code = process.waitFor();
        check(code != 0 && err.contains("failed"), "expected the edit command to fail");
        return new Obj(Map.of("out", JSON.parseOrText("\"" + out + "\""),
                "err", JSON.parseOrText(err), "code", JSON.parse(Integer.toString(code)), "ok", JSON.parse("false")));
    }

    private static void collectTaps(List<WNode> nodes, Env env, List<Runnable> taps) {
        for (var node : nodes) {
            if (node instanceof WNode.Element element) {
                var tap = element.getHandlers().get("onTap");
                if (tap != null) taps.add(() -> tap.getRun().invoke(env, List.of()));
                collectTaps(element.getChildren(), env, taps);
            } else if (node instanceof WNode.If branch) {
                var children = branch.getCond().invoke(env).getTruthy() ? branch.getThen() : branch.getOrElse();
                collectTaps(children, env, taps);
            } else if (node instanceof WNode.For loop) {
                var items = ((Lst) loop.getList().invoke(env)).getItems();
                for (int i = 0; i < items.size(); i++) {
                    collectTaps(loop.getBody(), env.child(items.get(i), JSON.parse(Integer.toString(i))), taps);
                }
            } else if (node instanceof WNode.Lets lets) {
                collectTaps(lets.getChildren(), env.lets(lets.getValues()), taps);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static class FaceHost implements Host {
        private final List<Global> globals;
        private final Value[] slots;
        private final Budget budget = new Budget();

        FaceHost(Program program) {
            globals = program.getGlobals();
            slots = new Value[globals.size()];
            Arrays.fill(slots, NULL);
            var env = Env.Companion.root(this);
            for (int i = 0; i < globals.size(); i++) {
                if (globals.get(i) instanceof Global.State state) slots[i] = state.getInit().invoke(env);
            }
        }

        private int slot(String name) {
            for (int i = 0; i < globals.size(); i++) {
                if (globals.get(i).getName().equals(name)) return i;
            }
            throw new AssertionError("missing global " + name);
        }

        Value get(String name) { return read(slot(name)); }
        void set(String name, Value value) { write(slot(name), value); }
        public Value read(int slot) {
            if (globals.get(slot) instanceof Global.Let let) {
                return let.getValue().invoke(Env.Companion.root(this));
            }
            return slots[slot];
        }
        public void write(int slot, Value value) { slots[slot] = value; }
        public Value tile(String name) { return name.equals("linux") ? JSON.parse("true") : NULL; }
        public Integer theme(String name) { return null; }
        public double time() { return 0; }
        public Budget getBudget() { return budget; }
        public void act(String name, List<? extends Value> args) { throw new AssertionError(name); }
        public void report(String message) { throw new AssertionError(message); }
    }
}
