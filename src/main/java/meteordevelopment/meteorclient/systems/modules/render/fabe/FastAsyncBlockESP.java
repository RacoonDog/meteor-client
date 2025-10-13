/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.fabe;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.renderer.MeshUniforms;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.render.blockesp.ESPBlockData;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FastAsyncBlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to search for.")
        .onChanged(blocks1 -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .build()
    );

    private final Setting<ESPBlockData> defaultBlockConfig = sgGeneral.add(new GenericSetting.Builder<ESPBlockData>()
        .name("default-block-config")
        .description("Default block config.")
        .defaultValue(
            new ESPBlockData(
                ShapeMode.Lines,
                new SettingColor(0, 255, 200),
                new SettingColor(0, 255, 200, 25),
                true,
                new SettingColor(0, 255, 200, 125)
            )
        )
        .build()
    );

    private final Setting<Map<Block, ESPBlockData>> blockConfigs = sgGeneral.add(new BlockDataSetting.Builder<ESPBlockData>()
        .name("block-configs")
        .description("Config for each block.")
        .defaultData(defaultBlockConfig)
        .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Render tracer lines.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> frustumCulling = sgGeneral.add(new BoolSetting.Builder()
        .name("frustum-culling")
        .description("Culling Frustumly")
        .defaultValue(true)
        .build()
    );

    private final ExecutorService workerThread = Executors.newFixedThreadPool(4, function -> {
       Thread t = new Thread(function);
       t.setDaemon(true);
       t.setName("FastAsyncBlockESP Worker");
       t.setUncaughtExceptionHandler((thread, throwable) -> MeteorClient.LOG.error("FABE worker uncaught exception", throwable));
       return t;
    });

    private final Queue<Pair<ChunkPos, List<FABEMeshData>>> queuedMeshes = new ConcurrentLinkedQueue<>();
    private final Long2ObjectMap<List<FABEGpuGroupMesh>> meshesByChunk = new Long2ObjectOpenHashMap<>();

    private Dimension lastDimension;

    public FastAsyncBlockESP() {
        super(Categories.Render, "fast-async-block-esp", "Renders specified blocks through walls, quickly.", "search", "block-esp");

        RainbowColors.register(this::onTickRainbow);
    }

    @Override
    public void onActivate() {
        clearChunks();

        int renderDistance = Utils.getRenderDistance() + 1;
        BlockPos here = new BlockPos(mc.player.getChunkPos().x, 0, mc.player.getChunkPos().z);
        for (BlockPos pos : BlockPos.iterateOutwards(here, renderDistance, 0, renderDistance)) {
            @Nullable Chunk chunk = mc.world.getChunk(pos.getX(), pos.getZ());
            if (chunk != null) {
                searchChunk(chunk);
            }
        }

        lastDimension = PlayerUtils.getDimension();
    }

    @Override
    public void onDeactivate() {
        clearChunks();
    }

    private void clearChunks() {
        queuedMeshes.clear();

        for (List<FABEGpuGroupMesh> meshes : meshesByChunk.values()) {
            for (FABEGpuGroupMesh mesh : meshes) {
                mesh.close();
            }
        }
        meshesByChunk.clear();
    }

    private void onTickRainbow() {
        if (!isActive()) return;

        defaultBlockConfig.get().tickRainbow();
        for (ESPBlockData blockData : blockConfigs.get().values()) blockData.tickRainbow();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        searchChunk(event.chunk());

        // man i wish there was a better way to do this
        maybeTrySearchChunk(event.chunk().getPos().x + 1, event.chunk().getPos().z);
        maybeTrySearchChunk(event.chunk().getPos().x, event.chunk().getPos().z + 1);
        maybeTrySearchChunk(event.chunk().getPos().x - 1, event.chunk().getPos().z);
        maybeTrySearchChunk(event.chunk().getPos().x, event.chunk().getPos().z - 1);
    }

    private void maybeTrySearchChunk(int cx, int cz) {
        @Nullable Chunk chunk = mc.world.getChunk(cx, cz);
        if (chunk != null) searchChunk(chunk);
    }

    private void searchChunk(Chunk chunk) {
        workerThread.submit(() -> {
            if (!isActive() || isOutOfRange(chunk.getPos().x, chunk.getPos().z)) return;
            FABEChunk schunk = new FABEChunk(chunk);
            StoragePointer pointer = new StoragePointer(chunk);
            List<Block> blocks = this.blocks.get();

            // aggregate blocks
            for (ChunkSection section : chunk.getSectionArray()) {
                PalettedContainer.Data<BlockState> data = section.getBlockStateContainer().data;

                if (!section.isEmpty()) {
                    data.storage().forEach(blockIndex -> {
                        BlockState state = data.palette().get(blockIndex);

                        if (blocks.contains(state.getBlock())) {
                            schunk.add(state, pointer.x(), pointer.y(), pointer.z());
                        }

                        pointer.increment();
                    });
                }

                pointer.nextSection();
            }

            if (schunk.isEmpty()) {
                return;
            }

            // mesh blocks
            try {
                queuedMeshes.add(new ObjectObjectImmutablePair<>(chunk.getPos(), schunk.mesh()));
            } catch (Throwable t) {
                MeteorClient.LOG.error("Uh oh!", t);
            }
        });
    }

    static boolean isOutOfRange(int cx, int cz) {
        int viewDist = Utils.getRenderDistance() + 1;
        int chunkX = ChunkSectionPos.getSectionCoord(MinecraftClient.getInstance().player.getBlockPos().getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(MinecraftClient.getInstance().player.getBlockPos().getZ());

        return cx > chunkX + viewDist || cx < chunkX - viewDist || cz > chunkZ + viewDist || cz < chunkZ - viewDist;
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!blocks.get().contains(event.newState.getBlock()) && !blocks.get().contains(event.oldState.getBlock())) {
            return;
        }

        Chunk chunk = mc.world.getChunk(event.pos);
        searchChunk(chunk);

        // man i wish there was a better way to do this
        int x = event.pos.getX() & 15;
        if (x == 0) {
            maybeTrySearchChunk(chunk.getPos().x - 1, chunk.getPos().z);
        } else if (x == 15) {
            maybeTrySearchChunk(chunk.getPos().x + 1, chunk.getPos().z);
        }

        int z = event.pos.getZ() & 15;
        if (z == 0) {
            maybeTrySearchChunk(chunk.getPos().x, chunk.getPos().z - 1);
        } else if (z == 15) {
            maybeTrySearchChunk(chunk.getPos().x, chunk.getPos().z + 1);
        }
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        Dimension dimension = PlayerUtils.getDimension();

        if (lastDimension != dimension) onActivate();
        else {
            for (var it = Long2ObjectMaps.fastIterator(meshesByChunk); it.hasNext();) {
                Long2ObjectMap.Entry<List<FABEGpuGroupMesh>> chunkMeshes = it.next();
                int chunkX = ChunkPos.getPackedX(chunkMeshes.getLongKey());
                int chunkZ = ChunkPos.getPackedZ(chunkMeshes.getLongKey());

                if (isOutOfRange(chunkX, chunkZ)) {
                    for (FABEGpuGroupMesh old : chunkMeshes.getValue()) {
                        old.close();
                    }
                    it.remove();
                }
            }
        }

        lastDimension = dimension;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // upload buffers
        while (queuedMeshes.peek() != null) {
            Pair<ChunkPos, List<FABEMeshData>> chunk = queuedMeshes.poll();

            List<FABEGpuGroupMesh> gpuMeshes = chunk.value().stream().map(FABEGpuGroupMesh::upload).toList();

            @Nullable List<FABEGpuGroupMesh> oldMeshes = meshesByChunk.put(chunk.key().toLong(), gpuMeshes);
            if (oldMeshes != null) {
                for (FABEGpuGroupMesh old : oldMeshes) {
                    old.close();
                }
            }
        }

        if (meshesByChunk.isEmpty()) {
            return;
        }

        // render lines & faces
        Frustum frustum = ((WorldRendererAccessor) mc.worldRenderer).meteor$getFrustum();
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().mul(event.matrices.peek().getPositionMatrix());

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        RenderSystem.getModelViewStack().translate(0, (float) -cameraPos.y, 0);

        List<RenderPass.RenderObject<GpuBufferSlice>> renderLines = new ObjectArrayList<>();
        int largestLineIndex = 0;
        List<RenderPass.RenderObject<GpuBufferSlice>> renderFaces = new ObjectArrayList<>();
        int largestFaceIndex = 0;

        for (Long2ObjectMap.Entry<List<FABEGpuGroupMesh>> chunkMeshes : Long2ObjectMaps.fastIterable(meshesByChunk)) {
            int chunkX = ChunkPos.getPackedX(chunkMeshes.getLongKey());
            int chunkZ = ChunkPos.getPackedZ(chunkMeshes.getLongKey());

            Vector3f chunkOffset = new Vector3f(
                (float) (ChunkSectionPos.getBlockCoord(chunkX) - cameraPos.x),
                0f,
                (float) (ChunkSectionPos.getBlockCoord(chunkZ) - cameraPos.z)
            );

            for (FABEGpuGroupMesh mesh : chunkMeshes.getValue()) {
                ESPBlockData data = blockConfigs.get().getOrDefault(mesh.block(), defaultBlockConfig.get());

                if (frustumCulling.get() && !frustum.isVisible(mesh.aabb())) {
                    continue;
                }

                if (data.shapeMode.lines()) {
                    int icount = mesh.lines().indexCount();
                    if (icount > largestLineIndex) {
                        largestLineIndex = icount;
                    }

                    Vector4f color = new Vector4f(data.lineColor.r / 255f, data.lineColor.g / 255f, data.lineColor.b / 255f, data.lineColor.a / 255f);
                    GpuBufferSlice fabeMeshData = FABEMeshUniforms.write(chunkOffset, color);

                    renderLines.add(new RenderPass.RenderObject<>(
                        0,
                        mesh.lines().vertices(),
                        mesh.lines().indices(),
                        VertexFormat.IndexType.INT,
                        0,
                        mesh.lines().indexCount(),
                        (nothing, uniformUploader) -> uniformUploader.upload("FABEData", fabeMeshData)
                    ));
                }

                if (data.shapeMode.sides()) {
                    int icount = mesh.faces().indexCount();
                    if (icount > largestFaceIndex) {
                        largestFaceIndex = icount;
                    }

                    Vector4f color = new Vector4f(data.sideColor.r / 255f, data.sideColor.g / 255f, data.sideColor.b / 255f, data.sideColor.a / 255f);
                    GpuBufferSlice fabeMeshData = FABEMeshUniforms.write(chunkOffset, color);

                    renderFaces.add(new RenderPass.RenderObject<>(
                        0,
                        mesh.faces().vertices(),
                        mesh.faces().indices(),
                        VertexFormat.IndexType.INT,
                        0,
                        mesh.faces().indexCount(),
                        (nothing, uniformUploader) -> uniformUploader.upload("FABEData", fabeMeshData)
                    ));
                }
            }
        }

        GpuBufferSlice meshData = MeshUniforms.write(RenderUtils.projection, RenderSystem.getModelViewStack());

        if (!renderLines.isEmpty()) {
            RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.DEBUG_LINES);
            GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(largestLineIndex);
            VertexFormat.IndexType indexType = shapeIndexBuffer.getIndexType();

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "FABE Lines",
                MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty()
            )) {
                pass.setPipeline(MeteorRenderPipelines.FABE_LINES);
                pass.setUniform("MeshData", meshData);
                pass.drawMultipleIndexed(renderLines, gpuBuffer, indexType, List.of("MeshData"), null);
            }
        }

        if (!renderFaces.isEmpty()) {
            RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.TRIANGLES);
            GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(largestFaceIndex);
            VertexFormat.IndexType indexType = shapeIndexBuffer.getIndexType();

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "FABE Faces",
                MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView(),
                OptionalInt.empty()
            )) {
                pass.setPipeline(MeteorRenderPipelines.FABE);
                pass.setUniform("MeshData", meshData);
                pass.drawMultipleIndexed(renderFaces, gpuBuffer, indexType, List.of("MeshData"), null);
            }
        }

        RenderSystem.getModelViewStack().popMatrix();

        // render tracers
        for (List<FABEGpuGroupMesh> chunkMeshes : meshesByChunk.values()) {
            for (FABEGpuGroupMesh mesh : chunkMeshes) {
                ESPBlockData data = blockConfigs.get().getOrDefault(mesh.block(), defaultBlockConfig.get());

                if (tracers.get() && data.tracer) {
                    for (TracerLine tracerLine : mesh.tracerLines()) {
                        event.renderer.line(
                            RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                            tracerLine.x(), tracerLine.y(), tracerLine.z(),
                            data.tracerColor
                        );
                    }
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        int meshes = 0;

        for (List<FABEGpuGroupMesh> chunkMeshes : meshesByChunk.values()) {
            meshes += chunkMeshes.size();
        }

        return Integer.toString(meshes);
    }
}
