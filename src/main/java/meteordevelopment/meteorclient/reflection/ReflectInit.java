/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.reflection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.pathing.PathManagers;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.Scanners;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

public class ReflectInit {
    private static final File CACHE = new File(MeteorClient.FOLDER, "reflections-cache");
    private static Reflections reflections;

    /* Create Reflections */

    public static void registerPackages() {
        if (!CACHE.exists()) CACHE.mkdir();

        Gson gson = new GsonBuilder().create();
        Map<ModContainer, String> checksumCache = new Reference2ObjectOpenHashMap<>();
        Reflections meteorReflections = getOrCreateReflections(MeteorClient.ADDON, gson, checksumCache);

        for (MeteorAddon addon : AddonManager.ADDONS) {
            try {
                Reflections addonReflections = getReflections(addon, gson, checksumCache);
                if (addonReflections != null) meteorReflections.merge(addonReflections);
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        }

        reflections = meteorReflections;
    }

    private static Reflections getOrCreateReflections(MeteorAddon addon, Gson gson, Map<ModContainer, String> checksumCache) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() && addon.getModContainer().getOrigin().getKind() != ModOrigin.Kind.UNKNOWN) {
            try {
                File cacheFile = new File(CACHE, addon.id + "-reflections.json");
                File checksumFile = new File(CACHE, addon.id + ".checksum");
                String checksum = checksum(addon, checksumCache);

                if (cacheFile.exists() && checksumFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(checksumFile))) {
                        if (checksum.equals(reader.readLine())) {
                            try (FileInputStream inputStream = new FileInputStream(cacheFile)) {
                                return gson.fromJson(new InputStreamReader(inputStream), Reflections.class);
                            }
                        }
                    }
                }

                Reflections addonReflections = createReflections(addon.getPackage());

                try (FileWriter checksumWriter = new FileWriter(checksumFile);
                     FileWriter cacheWriter = new FileWriter(cacheFile)) {
                    cacheWriter.write(gson.toJson(addonReflections));
                    checksumWriter.write(checksum);
                }

                return addonReflections;
            } catch (IOException | ReflectionsException e) {
                MeteorClient.LOG.error("Error loading reflections from addon '{}'", addon.name);
                e.printStackTrace();
            }
        }

        return createReflections(addon.getPackage());
    }

    private static Reflections createReflections(String pkg) {
        return new Reflections(pkg, Scanners.MethodsAnnotated, Scanners.SubTypes, Scanners.FieldsAnnotated);
    }

    @Nullable
    private static Reflections getReflections(MeteorAddon addon, Gson gson, Map<ModContainer, String> checksumCache) {
        String pkg = addon.getPackage();
        if (pkg == null || pkg.isBlank()) return null;
        return getOrCreateReflections(addon, gson, checksumCache);
    }

    private static String checksum(MeteorAddon addon, Map<ModContainer, String> checksumCache) {
        ModContainer mod = addon.getModContainer();
        if (mod.getOrigin().getKind() == ModOrigin.Kind.NESTED) mod = getParent(mod);

        return checksumCache.computeIfAbsent(mod, container -> {
            Adler32 hash = new Adler32();
            byte[] buffer = new byte[8192];

            for (Path path : container.getOrigin().getPaths()) {
                try (InputStream is = Files.newInputStream(path)) {
                    int read;
                    while ((read = is.read(buffer)) != -1) hash.update(buffer, 0, read);
                } catch (IOException ignored) {}
            }

            return Long.toHexString(hash.getValue());
        });
    }

    private static ModContainer getParent(ModContainer container) {
        while (container.getOrigin().getKind() == ModOrigin.Kind.NESTED) {
            container = FabricLoader.getInstance().getModContainer(container.getOrigin().getParentModId()).orElseThrow();
        }
        return container;
    }

    public static <T extends IRegisterable> void initRegisterable(Class<T> registerableClass, Consumer<T> registrationCallback) {
        Set<Class<? extends T>> initTasks = reflections.getSubTypesOf(registerableClass);
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

    private static <T extends IRegisterable> void reflectInitRegisterable(Class<? extends T> registerableClass, Set<Class<? extends T>> left, Set<Class<? extends T>> all, Consumer<T> registrationCallback) {
        left.remove(registerableClass);

        RegisterableDependant registerableDependant;
        if ((registerableDependant = registerableClass.getAnnotation(RegisterableDependant.class)) != null) {
            Class<? extends T>[] dependencies;
            try {
                //noinspection unchecked
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
                    return;
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
        Set<Method> initTasks = reflections.getMethodsAnnotatedWith(annotation);
        if (initTasks == null) return;

        Map<Class<?>, List<Method>> byClass = initTasks.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));
        Set<Method> left = new HashSet<>(initTasks);

        for (Method m; (m = left.stream().findAny().orElse(null)) != null;) {
            reflectInit(m, annotation, left, byClass);
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
