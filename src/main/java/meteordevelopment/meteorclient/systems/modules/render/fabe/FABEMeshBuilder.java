/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import net.minecraft.block.Block;
import net.minecraft.util.math.Box;

import java.util.Iterator;
import java.util.List;

public class FABEMeshBuilder {
    private final Long2ObjectMap<List<Line>> xLines = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<Line>> yLines = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<Line>> zLines = new Long2ObjectOpenHashMap<>();
    private final ObjectArrayList<Face> faces = new ObjectArrayList<>();
    private final ObjectArrayList<TracerLine> tracerLines = new ObjectArrayList<>();
    private final Block block;
    private Box aabb;

    public FABEMeshBuilder(Block block) {
        this.block = block;
    }

    public void box(Box aabb) {
        this.aabb = aabb;
    }

    public void tracerLine(TracerLine tracerLine) {
        this.tracerLines.add(tracerLine);
    }

    public void xLine(double minX, double maxX, double y, double z) {
        combineLines(xLines, minX, maxX, y, z);
    }

    public void yLine(double minY, double maxY, double x, double z) {
        combineLines(yLines, minY, maxY, x, z);
    }

    public void zLine(double minZ, double maxZ, double x, double y) {
        combineLines(zLines, minZ, maxZ, x, y);
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

    public void quadVertical(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.quad(
            x1, y1, z1,
            x1, y2, z1,
            x2, y2, z2,
            x2, y1, z2
        );
    }

    public void quadHorizontal(double x1, double y, double z1, double x2, double z2) {
        this.quad(
            x1, y, z1,
            x1, y, z2,
            x2, y, z2,
            x2, y, z1
        );
    }

    private void quad(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        this.faces.add(new Face(
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

        for (List<Line> lines : xLines.values()) {
            lineBuilder.ensureCapacity(0, lines.size() * 2);

            for (Line line : lines) {
                int i1 = insertVertex(lineVertices, line.min(), line.c1(), line.c2());
                int i2 = insertVertex(lineVertices, line.max(), line.c1(), line.c2());

                lineBuilder.line(i1, i2);
            }
        }

        for (List<Line> lines : yLines.values()) {
            lineBuilder.ensureCapacity(0, lines.size() * 2);

            for (Line line : lines) {
                int i1 = insertVertex(lineVertices, line.c1(), line.min(), line.c2());
                int i2 = insertVertex(lineVertices, line.c1(), line.max(), line.c2());

                lineBuilder.line(i1, i2);
            }
        }

        for (List<Line> lines : zLines.values()) {
            lineBuilder.ensureCapacity(0, lines.size() * 2);

            for (Line line : lines) {
                int i1 = insertVertex(lineVertices, line.c1(), line.c2(), line.min());
                int i2 = insertVertex(lineVertices, line.c1(), line.c2(), line.max());

                lineBuilder.line(i1, i2);
            }
        }

        lineBuilder.ensureCapacity(lineVertices.size(), 0);
        for (Vertex vertex : lineVertices.keySet()) {
            lineBuilder.rawVec3(vertex.x(), vertex.y(), vertex.z()).next();
        }

        // deduplicate face vertices
        // todo greedy mesh
        Object2IntLinkedOpenHashMap<Vertex> faceVertices = new Object2IntLinkedOpenHashMap<>();
        faceVertices.defaultReturnValue(-1);
        MeshBuilder faceBuilder = new MeshBuilder(MeteorRenderPipelines.FABE);
        faceBuilder.ensureCapacity(0, faces.size() * 6);

        for (Face face : faces) {
            int i1 = insertVertex(faceVertices, face.x1(), face.y1(), face.z1());
            int i2 = insertVertex(faceVertices, face.x2(), face.y2(), face.z2());
            int i3 = insertVertex(faceVertices, face.x3(), face.y3(), face.z3());
            int i4 = insertVertex(faceVertices, face.x4(), face.y4(), face.z4());

            faceBuilder.quad(i1, i2, i3, i4);
        }

        faceBuilder.ensureCapacity(faceVertices.size(), 0);

        for (Vertex vertex : faceVertices.keySet()) {
            faceBuilder.rawVec3(vertex.x(), vertex.y(), vertex.z()).next();
        }

        return new FABEMeshData(
            block,
            aabb,
            tracerLines,
            lineBuilder,
            faceBuilder
        );
    }

    private int insertVertex(Object2IntLinkedOpenHashMap<Vertex> vertices, double x, double y, double z) {
        int size = vertices.size();
        int retVal = vertices.putIfAbsent(new Vertex(x, y, z), size);
        return retVal != -1 ? retVal : size;
    }

    public record Vertex(double x, double y, double z) {}
    public record Line(double min, double max, double c1, double c2) {}
    public record Face(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {}
}
