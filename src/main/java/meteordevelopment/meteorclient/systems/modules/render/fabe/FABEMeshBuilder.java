/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.Box;

import java.util.Iterator;
import java.util.List;

public class FABEMeshBuilder {
    private final Object2ObjectMap<SettingColor, Long2ObjectMap<List<Line>>> xLines = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<SettingColor, Long2ObjectMap<List<Line>>> yLines = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<SettingColor, Long2ObjectMap<List<Line>>> zLines = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<SettingColor, ObjectArrayList<Face>> faces = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<TracerLine> tracerLines = new ObjectArrayList<>();
    private Box aabb;

    public void box(Box aabb) {
        if (this.aabb == null) {
            this.aabb = aabb;
        } else {
            this.aabb = this.aabb.union(aabb);
        }
    }

    public void tracerLine(TracerLine tracerLine) {
        this.tracerLines.add(tracerLine);
    }

    public void xLine(SettingColor color, double minX, double maxX, double y, double z) {
        combineLines(xLines.computeIfAbsent(color, k -> new Long2ObjectOpenHashMap<>()), minX, maxX, y, z);
    }

    public void yLine(SettingColor color, double minY, double maxY, double x, double z) {
        combineLines(yLines.computeIfAbsent(color, k -> new Long2ObjectOpenHashMap<>()), minY, maxY, x, z);
    }

    public void zLine(SettingColor color, double minZ, double maxZ, double x, double y) {
        combineLines(zLines.computeIfAbsent(color, k -> new Long2ObjectOpenHashMap<>()), minZ, maxZ, x, y);
    }

    private void combineLines(Long2ObjectMap<List<Line>> axisLines, double min, double max, double c1, double c2) {
        List<Line> lines = axisLines.computeIfAbsent(key(c1, c2), k -> new ObjectArrayList<>());

        for (Iterator<Line> it = lines.iterator(); it.hasNext();) {
            Line other = it.next();

            if (min <= other.max() && other.min() <= max) {
                it.remove();
                min = Math.min(min, other.min());
                max = Math.max(max, other.max());
            }
        }

        lines.add(new Line(min, max, c1, c2));
    }

    public void quadVertical(SettingColor color, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.quad(
            color,
            x1, y1, z1,
            x1, y2, z1,
            x2, y2, z2,
            x2, y1, z2
        );
    }

    public void quadHorizontal(SettingColor color, double x1, double y, double z1, double x2, double z2) {
        this.quad(
            color,
            x1, y, z1,
            x1, y, z2,
            x2, y, z2,
            x2, y, z1
        );
    }

    private void quad(SettingColor color, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        this.faces.computeIfAbsent(color, k -> new ObjectArrayList<>()).add(new Face(
            x1, y1, z1,
            x2, y2, z2,
            x3, y3, z3,
            x4, y4, z4
        ));
    }

    private static long key(double k1, double k2) { // todo this probably fucks up near world border
        int ik1 = Float.floatToRawIntBits((float) k1);
        int ik2 = Float.floatToRawIntBits((float) k2);
        return (long) ik1 << 32 | ik2;
    }

    public FABEMeshData write() {
        tracerLines.trim();

        // deduplicate line vertices
        Object2IntLinkedOpenHashMap<Vertex> lineVertices = new Object2IntLinkedOpenHashMap<>();
        lineVertices.defaultReturnValue(-1);
        MeshBuilder lineBuilder = new MeshBuilder(MeteorRenderPipelines.FABE_LINES);

        for (var entry : Object2ObjectMaps.fastIterable(xLines)) {
            SettingColor color = entry.getKey();
            for (List<Line> lines : entry.getValue().values()) {
                lineBuilder.ensureCapacity(0, lines.size() * 2);

                for (Line line : lines) {
                    int i1 = insertVertex(lineVertices, color, line.min(), line.c1(), line.c2());
                    int i2 = insertVertex(lineVertices, color, line.max(), line.c1(), line.c2());

                    lineBuilder.line(i1, i2);
                }
            }
        }

        for (var entry : Object2ObjectMaps.fastIterable(yLines)) {
            SettingColor color = entry.getKey();
            for (List<Line> lines : entry.getValue().values()) {
                lineBuilder.ensureCapacity(0, lines.size() * 2);

                for (Line line : lines) {
                    int i1 = insertVertex(lineVertices, color, line.c1(), line.min(), line.c2());
                    int i2 = insertVertex(lineVertices, color, line.c1(), line.max(), line.c2());

                    lineBuilder.line(i1, i2);
                }
            }
        }

        for (var entry : Object2ObjectMaps.fastIterable(zLines)) {
            SettingColor color = entry.getKey();
            for (List<Line> lines : entry.getValue().values()) {
                lineBuilder.ensureCapacity(0, lines.size() * 2);

                for (Line line : lines) {
                    int i1 = insertVertex(lineVertices, color, line.c1(), line.c2(), line.min());
                    int i2 = insertVertex(lineVertices, color, line.c1(), line.c2(), line.max());

                    lineBuilder.line(i1, i2);
                }
            }
        }

        lineBuilder.ensureCapacity(lineVertices.size(), 0);
        for (Vertex vertex : lineVertices.keySet()) {
            lineBuilder.rawVec3(vertex.x(), vertex.y(), vertex.z()).color(vertex.color()).next();
        }

        // deduplicate face vertices
        // todo greedy mesh
        Object2IntLinkedOpenHashMap<Vertex> faceVertices = new Object2IntLinkedOpenHashMap<>();
        faceVertices.defaultReturnValue(-1);
        MeshBuilder faceBuilder = new MeshBuilder(MeteorRenderPipelines.FABE);

        for (var entry : Object2ObjectMaps.fastIterable(faces)) {
            faceBuilder.ensureCapacity(0, entry.getValue().size() * 6);

            SettingColor color = entry.getKey();
            for (Face face : entry.getValue()) {
                int i1 = insertVertex(faceVertices, color, face.x1(), face.y1(), face.z1());
                int i2 = insertVertex(faceVertices, color, face.x2(), face.y2(), face.z2());
                int i3 = insertVertex(faceVertices, color, face.x3(), face.y3(), face.z3());
                int i4 = insertVertex(faceVertices, color, face.x4(), face.y4(), face.z4());

                faceBuilder.quad(i1, i2, i3, i4);
            }
        }

        faceBuilder.ensureCapacity(faceVertices.size(), 0);

        for (Vertex vertex : faceVertices.keySet()) {
            faceBuilder.rawVec3(vertex.x(), vertex.y(), vertex.z()).color(vertex.color()).next();
        }

        return new FABEMeshData(
            aabb,
            tracerLines,
            lineBuilder,
            faceBuilder
        );
    }

    private int insertVertex(Object2IntLinkedOpenHashMap<Vertex> vertices, SettingColor color, double x, double y, double z) {
        int size = vertices.size();
        int retVal = vertices.putIfAbsent(new Vertex(color, x, y, z), size);
        return retVal != -1 ? retVal : size;
    }

    public record Vertex(SettingColor color, double x, double y, double z) {}
    public record Line(double min, double max, double c1, double c2) {}
    public record Face(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {}
}
