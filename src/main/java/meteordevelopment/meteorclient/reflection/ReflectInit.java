/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.reflection;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.pathing.PathManagers;
import net.fabricmc.loader.api.FabricLoader;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ReflectInit {
    private static final List<Reflections> reflections = new ArrayList<>();

    public static void registerPackages() {
        add(MeteorClient.ADDON);

        for (MeteorAddon addon : AddonManager.ADDONS) {
            try {
                add(addon);
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        }
    }

    private static void add(MeteorAddon addon) {
        String pkg = addon.getPackage();
        if (pkg == null || pkg.isBlank()) return;
        reflections.add(new Reflections(pkg, Scanners.MethodsAnnotated, Scanners.SubTypes));
    }

    public static <T extends IRegisterable> void initRegisterable(Class<T> registerableClass, Consumer<T> registrationCallback) {
        for (Reflections reflection : reflections) {
            Set<Class<? extends T>> initTasks = reflection.getSubTypesOf(registerableClass);
            if (initTasks == null) return;
            Set<Class<? extends T>> allTasks = new ReferenceOpenHashSet<>(initTasks);

            for (Iterator<Class<? extends T>> it; (it = initTasks.iterator()).hasNext();) {
                Class<? extends T> clazz = it.next();
                if (clazz.isAnnotationPresent(PathingDependant.class) && !PathManagers.isPresent()) {
                    it.remove();
                    allTasks.remove(clazz);
                    continue;
                }
                ModDependant modDependant;
                if ((modDependant = clazz.getAnnotation(ModDependant.class)) != null) {
                    if (checkDependencies(modDependant)) {
                        it.remove();
                        allTasks.remove(clazz);
                        continue;
                    }
                }
                reflectInitRegisterable(clazz, initTasks, allTasks, registrationCallback);
            }
        }
    }

    private static <T extends IRegisterable> void reflectInitRegisterable(Class<? extends T> registerableClass, Set<Class<? extends T>> left, Set<Class<? extends T>> all, Consumer<T> registrationCallback) {
        left.remove(registerableClass);

        RegisterableDependant registerableDependant;
        if ((registerableDependant = registerableClass.getAnnotation(RegisterableDependant.class)) != null) {
            Class<? extends T>[] dependencies;
            try {
                dependencies = (Class<? extends T>[]) registerableDependant.dependencies();
            } catch (ClassCastException e) {
                MeteorClient.LOG.error("Registerable '{}' declares dependencies which are of a different subtype, or that are not registerable.", registerableClass.getName());
                return;
            }
            for (Class<? extends T> dependency : dependencies) {
                if (left.contains(dependency)) {
                    reflectInitRegisterable(dependency, left, all, registrationCallback);
                } else if (!all.contains(dependency)) {
                    MeteorClient.LOG.error("Registerable '{}' defines dependency '{}' which could not be registered.", registerableClass.getName(), dependency.getName());
                }
            }
        }

        try {
            T registerable = registerableClass.getDeclaredConstructor().newInstance();
            registrationCallback.accept(registerable);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            MeteorClient.LOG.error("Registerable '{}' does not have a no-arg constructor.", registerableClass.getName());
        } catch (NoClassDefFoundError e) {
            MeteorClient.LOG.error("Registerable '{}' has an unfulfilled dependency.", registerableClass.getName());
        }
    }

    private static boolean checkDependencies(ModDependant modDependant) {
        for (String modDependency : modDependant.dependencies()) {
            if (!FabricLoader.getInstance().isModLoaded(modDependency)) {
                return true;
            }
        }
        return false;
    }

    public static void init(Class<? extends Annotation> annotation) {
        for (Reflections reflection : reflections) {
            Set<Method> initTasks = reflection.getMethodsAnnotatedWith(annotation);
            if (initTasks == null) return;

            Map<Class<?>, List<Method>> byClass = initTasks.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));
            Set<Method> left = new HashSet<>(initTasks);

            for (Method m; (m = left.stream().findAny().orElse(null)) != null;) {
                reflectInit(m, annotation, left, byClass);
            }
        }
    }

    private static <T extends Annotation> void reflectInit(Method task, Class<T> annotation, Set<Method> left, Map<Class<?>, List<Method>> byClass) {
        left.remove(task);

        for (Class<?> clazz : getDependencies(task, annotation)) {
            for (Method m : byClass.getOrDefault(clazz, Collections.emptyList())) {
                if (left.contains(m)) {
                    reflectInit(m, annotation, left, byClass);
                }
            }
        }

        try {
            task.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            throw new RuntimeException("Method \"%s\" using Init annotations from non-static context".formatted(task.getName()), e);
        }
    }

    private static <T extends Annotation> Class<?>[] getDependencies(Method task, Class<T> annotation) {
        T init = task.getAnnotation(annotation);

        if (init instanceof PreInit pre) {
            return pre.dependencies();
        }
        else if (init instanceof PostInit post) {
            return post.dependencies();
        }

        return new Class<?>[]{};
    }
}
