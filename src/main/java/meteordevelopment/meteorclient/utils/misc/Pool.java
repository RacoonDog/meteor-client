/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

public class Pool<T> {
    private final ArrayDeque<T> items = new ArrayDeque<>();
    private final Producer<T> producer;

    public Pool(Producer<T> producer) {
        this.producer = producer;
    }

    public synchronized T get() {
        if (items.size() > 0) return items.poll();
        return producer.create();
    }

    public synchronized void free(T obj) {
        items.offer(obj);
    }

    public synchronized void freeAll(Collection<T> objs) {
        items.addAll(objs);
        objs.clear();
    }

    public synchronized void freeIf(Iterable<T> objs, Predicate<T> predicate) {
        for (Iterator<T> it = objs.iterator(); it.hasNext();) {
            T next = it.next();
            if (predicate.test(next)) {
                free(next);
                it.remove();
            }
        }
    }
}
