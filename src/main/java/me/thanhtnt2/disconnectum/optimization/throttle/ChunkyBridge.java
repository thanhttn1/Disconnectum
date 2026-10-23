package me.thanhtnt2.disconnectum.optimization.throttle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Talks to Chunky through reflection only.
 *
 * <p>Why reflection: Chunky is GPLv3 and Disconnectum is MIT. Calling its public API at runtime without
 * linking against it keeps the two licences cleanly apart, and a Chunky update that changes names simply
 * disables this bridge instead of crashing the game.
 *
 * <p>Everything used here is Chunky's public API: {@code ChunkyProvider.get()}, {@code Chunky#getApi()},
 * {@code Chunky#getGenerationTasks()} and {@code ChunkyAPI#pauseTask/continueTask}. Pausing only flags the
 * task as stopped; Chunky finishes its in-flight chunks, saves progress and removes the task by itself.
 * Calls must come from the server thread, the same thread Chunky's own commands run on.
 */
final class ChunkyBridge {

    private static final String PROVIDER_CLASS = "org.popcraft.chunky.ChunkyProvider";
    private static final String API_CLASS = "org.popcraft.chunky.api.ChunkyAPI";

    private final Method getInstance;
    private final Method getApi;
    private final Method getGenerationTasks;
    private final Method pauseTask;
    private final Method continueTask;

    private ChunkyBridge(Method getInstance, Method getApi, Method getGenerationTasks,
                         Method pauseTask, Method continueTask) {
        this.getInstance = getInstance;
        this.getApi = getApi;
        this.getGenerationTasks = getGenerationTasks;
        this.pauseTask = pauseTask;
        this.continueTask = continueTask;
    }

    /** @throws ReflectiveOperationException when Chunky is missing or its API changed shape */
    static ChunkyBridge create(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> providerClass = Class.forName(PROVIDER_CLASS, false, loader);
        Class<?> apiClass = Class.forName(API_CLASS, false, loader);
        Method getInstance = providerClass.getMethod("get");
        Class<?> chunkyClass = getInstance.getReturnType();
        return new ChunkyBridge(
                getInstance,
                chunkyClass.getMethod("getApi"),
                chunkyClass.getMethod("getGenerationTasks"),
                apiClass.getMethod("pauseTask", String.class),
                apiClass.getMethod("continueTask", String.class));
    }

    /** Names of worlds that currently have a running Chunky task; empty while Chunky is not initialised. */
    List<String> runningWorlds() {
        Object chunky = invoke(getInstance, null);
        if (chunky == null) {
            return List.of();
        }
        Object tasks = invoke(getGenerationTasks, chunky);
        if (tasks instanceof Map<?, ?> map) {
            return map.keySet().stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    boolean pause(String world) {
        return callApi(pauseTask, world);
    }

    boolean resume(String world) {
        return callApi(continueTask, world);
    }

    private boolean callApi(Method method, String world) {
        Object chunky = invoke(getInstance, null);
        if (chunky == null) {
            return false;
        }
        Object api = invoke(getApi, chunky);
        return api != null && Boolean.TRUE.equals(invoke(method, api, world));
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Chunky call failed: " + method.getName(), exception);
        }
    }
}
