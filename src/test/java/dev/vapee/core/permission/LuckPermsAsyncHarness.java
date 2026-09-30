package dev.vapee.core.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class LuckPermsAsyncHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        UUID id = new UUID(0, 1);
        Boundary boundary = new Boundary();
        LuckPermsService service = new LuckPermsService(boundary.api);
        boundary.loaded = user("admin");
        var cached = service.loadPrimaryGroup(id);
        check(cached.isDone() && value(cached).equals(Optional.of("admin")), "loaded primary group immediate");
        check(boundary.loads == 0 && boundary.lastId.equals(id), "loaded path no storage load");
        boundary.loaded = user("owner");
        check(value(service.loadPrimaryGroup(id)).equals(Optional.of("owner")), "fresh cache read not memoized");
        boundary.loaded = null;
        boundary.future = new CompletableFuture<>();
        var pending = service.loadPrimaryGroup(id);
        check(!pending.isDone() && boundary.loads == 1 && boundary.lastId.equals(id), "unknown user starts correct async load");
        Thread worker = new Thread(() -> boundary.future.complete(user("moderator")), "lp-test-worker");
        worker.start(); worker.join();
        check(value(pending).equals(Optional.of("moderator")), "async user primary group extraction");
        for (String group : new String[]{null, "", "   "}) {
            boundary.loaded = user(group);
            check(value(service.loadPrimaryGroup(id)).isEmpty(), "invalid cached primary group empty");
            boundary.loaded = null;
            boundary.future = CompletableFuture.completedFuture(user(group));
            check(value(service.loadPrimaryGroup(id)).isEmpty(), "invalid loaded primary group empty");
        }
        boundary.future = CompletableFuture.completedFuture(null);
        check(value(service.loadPrimaryGroup(id)).isEmpty(), "null resolved user empty");
        boundary.future = new CompletableFuture<>();
        var failed = service.loadPrimaryGroup(id);
        boundary.future.completeExceptionally(new IllegalStateException("storage failure"));
        check(failed.isCompletedExceptionally(), "async failure preserved");
        boundary.throwLoad = true;
        check(service.loadPrimaryGroup(id).isCompletedExceptionally(), "synchronous API failure becomes failed future");
        try { service.loadPrimaryGroup(null); throw new AssertionError("null UUID"); }
        catch (NullPointerException expected) { checks++; }
        check(boundary.otherCalls == 0, "no user save, mutation, metadata, inheritance or group API");
        System.out.println("LuckPermsAsyncHarness passed " + checks + " checks.");
    }
    private static Optional<String> value(CompletableFuture<Optional<String>> future) {
        AtomicReference<Optional<String>> result = new AtomicReference<>();
        future.thenAccept(result::set);
        if (result.get() == null) throw new AssertionError("future not completed");
        return result.get();
    }
    private static User user(String group) {
        return (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class[]{User.class}, (proxy, method, args) -> {
            if (method.getName().equals("getPrimaryGroup")) return group;
            throw new AssertionError("unexpected user API: " + method.getName());
        });
    }
    private static final class Boundary {
        User loaded;
        CompletableFuture<User> future;
        UUID lastId;
        int loads, otherCalls;
        boolean throwLoad;
        UserManager manager = (UserManager) Proxy.newProxyInstance(UserManager.class.getClassLoader(),
                new Class[]{UserManager.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUser": lastId = (UUID) args[0]; return loaded;
                        case "loadUser": loads++; lastId = (UUID) args[0];
                            if (throwLoad) throw new IllegalStateException("load failed");
                            return future;
                        default: otherCalls++; throw new AssertionError("unexpected manager API: " + method.getName());
                    }
                });
        LuckPerms api = (LuckPerms) Proxy.newProxyInstance(LuckPerms.class.getClassLoader(),
                new Class[]{LuckPerms.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUserManager")) return manager;
                    otherCalls++; throw new AssertionError("unexpected LP API: " + method.getName());
                });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
