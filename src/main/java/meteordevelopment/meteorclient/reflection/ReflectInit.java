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
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.reflections.ReflectionsException;
import org.reflections.scanners.Scanners;
import org.reflections.serializers.JsonSerializer;
import org.reflections.serializers.Serializer;

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
    private static Reflections reflections;
    private static final File CACHE = new File(MeteorClient.FOLDER, "reflections-cache");

    public static void registerPackages() {
        if (!CACHE.exists()) CACHE.mkdir();

        long timestamp = System.nanoTime();

        Serializer serializer = new JsonSerializer();

        Reflections meteorReflections = scanOrCreateReflections(MeteorClient.ADDON, serializer);

        for (MeteorAddon addon : AddonManager.ADDONS) {
            try {
                Reflections addonReflections = scanReflections(addon, serializer);
                if (addonReflections != null) meteorReflections.merge(addonReflections);
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        }

        reflections = meteorReflections;

        timestamp = System.nanoTime() - timestamp;

        MeteorClient.LOG.info("ReflectInit scan took %.3f ms".formatted(timestamp / 1000000d));
    }

    private static Reflections scanOrCreateReflections(MeteorAddon addon, Serializer serializer) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            try {
                File cacheFile = new File(CACHE, addon.id + "-reflections.json");
                File checksumFile = new File(CACHE, addon.id + ".checksum");
                String checksum = checksum(addon);

                if (cacheFile.exists() && checksumFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(checksumFile))) {
                        if (checksum.equals(reader.readLine())) {
                            try (FileInputStream inputStream = new FileInputStream(cacheFile)) {
                                return serializer.read(inputStream);
                            }
                        }
                    }
                }

                try (FileWriter checksumWriter = new FileWriter(checksumFile)) {
                    checksumWriter.write(checksum);
                }

                Reflections addonReflections = new Reflections(addon.getPackage(), Scanners.MethodsAnnotated, Scanners.SubTypes, Scanners.FieldsAnnotated);
                addonReflections.save(cacheFile.getAbsolutePath(), serializer);
                return addonReflections;
            } catch (IOException | ReflectionsException e) {
                MeteorClient.LOG.error("Error loading reflections from addon '{}'", addon.name);
                e.printStackTrace();
            }
        }

        return new Reflections(addon.getPackage(), Scanners.MethodsAnnotated, Scanners.SubTypes, Scanners.FieldsAnnotated);
    }

    @Nullable
    private static Reflections scanReflections(MeteorAddon addon, Serializer serializer) {
        String pkg = addon.getPackage();
        if (pkg == null || pkg.isBlank()) return null;
        return scanOrCreateReflections(addon, serializer);
    }

    private static String checksum(MeteorAddon addon) throws IOException {
        Adler32 hash = new Adler32();
        byte[] buffer = new byte[8192];
        int read;
        for (Path rootPath : addon.getModContainer().getRootPaths()) {
            try (InputStream is = Files.newInputStream(rootPath)) {
                while ((read = is.read(buffer)) != -1) hash.update(buffer, 0, read);
            }
        }
        return Long.toHexString(hash.getValue());
    }

    public static <T extends IRegisterable> void initRegisterable(Class<T> registerableClass, Consumer<T> registrationCallback) {
        long timestamp = System.nanoTime();

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

        timestamp = System.nanoTime() - timestamp;

        MeteorClient.LOG.info("Registerable '%s' initialization took %.3f ms".formatted(registerableClass.getSimpleName(), timestamp / 1000000d));
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
