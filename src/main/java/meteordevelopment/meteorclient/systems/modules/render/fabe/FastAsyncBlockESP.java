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
import meteordevelopment.meteorclient.renderer.GpuMesh;
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
        .onChanged(v -> {
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
        .onChanged(v -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .build()
    );

    private final Setting<Map<Block, ESPBlockData>> blockConfigs = sgGeneral.add(new BlockDataSetting.Builder<ESPBlockData>()
        .name("block-configs")
        .description("Config for each block.")
        .defaultData(defaultBlockConfig)
        .onChanged(v -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
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

    private final Queue<Pair<ChunkPos, FABEMeshData>> queuedMeshes = new ConcurrentLinkedQueue<>();
    private final Long2ObjectMap<FABEGpuGroupMesh> meshesByChunk = new Long2ObjectOpenHashMap<>();

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

        for (FABEGpuGroupMesh mesh : meshesByChunk.values()) {
            mesh.close();
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
                FABEMeshData mesh = schunk.mesh(this);

                if (mesh.lineBuilder().getIndicesCount() == 0 && mesh.faceBuilder().getIndicesCount() == 0) {
                    return;
                }

                queuedMeshes.add(new ObjectObjectImmutablePair<>(chunk.getPos(), schunk.mesh(this)));
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
                Long2ObjectMap.Entry<FABEGpuGroupMesh> chunkMesh = it.next();
                int chunkX = ChunkPos.getPackedX(chunkMesh.getLongKey());
                int chunkZ = ChunkPos.getPackedZ(chunkMesh.getLongKey());

                if (isOutOfRange(chunkX, chunkZ)) {
                    chunkMesh.getValue().close();
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
            Pair<ChunkPos, FABEMeshData> chunk = queuedMeshes.poll();
            FABEMeshData mesh = chunk.right();

            @Nullable GpuMesh lineMesh = mesh.lineBuilder().getIndicesCount() != 0 ? GpuMesh.upload(mesh.lineBuilder()) : null;
            @Nullable GpuMesh faceMesh = mesh.faceBuilder().getIndicesCount() != 0 ? GpuMesh.upload(mesh.faceBuilder()) : null;

            FABEGpuGroupMesh gpuMesh = new FABEGpuGroupMesh(
                mesh.aabb(),
                mesh.tracerLines(),
                lineMesh,
                faceMesh
            );

            @Nullable FABEGpuGroupMesh oldMesh = meshesByChunk.put(chunk.key().toLong(), gpuMesh);
            if (oldMesh != null) {
                oldMesh.close();
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

        for (Long2ObjectMap.Entry<FABEGpuGroupMesh> chunkMesh : Long2ObjectMaps.fastIterable(meshesByChunk)) {
            int chunkX = ChunkPos.getPackedX(chunkMesh.getLongKey());
            int chunkZ = ChunkPos.getPackedZ(chunkMesh.getLongKey());

            Vector3f chunkOffset = new Vector3f(
                (float) (ChunkSectionPos.getBlockCoord(chunkX) - cameraPos.x),
                0f,
                (float) (ChunkSectionPos.getBlockCoord(chunkZ) - cameraPos.z)
            );

            FABEGpuGroupMesh mesh = chunkMesh.getValue();

            if (frustumCulling.get() && !frustum.isVisible(mesh.aabb())) {
                continue;
            }

            GpuBufferSlice fabeMeshData = FABEMeshUniforms.write(chunkOffset);

            if (mesh.lines() != null) {
                int icount = mesh.lines().indexCount();
                if (icount > largestLineIndex) {
                    largestLineIndex = icount;
                }

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

            if (mesh.faces() != null) {
                int icount = mesh.faces().indexCount();
                if (icount > largestFaceIndex) {
                    largestFaceIndex = icount;
                }

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
        for (FABEGpuGroupMesh mesh : meshesByChunk.values()) {

            if (tracers.get()) {
                for (TracerLine tracerLine : mesh.tracerLines()) {
                    if (tracerLine.blockData().tracer) {
                        event.renderer.line(
                            RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                            tracerLine.x(), tracerLine.y(), tracerLine.z(),
                            tracerLine.blockData().tracerColor
                        );
                    }
                }
            }
        }
    }

    public ESPBlockData getBlockData(Block block) {
        return blockConfigs.get().getOrDefault(block, defaultBlockConfig.get());
    }

    @Override
    public String getInfoString() {
        return Integer.toString(meshesByChunk.size());
    }
}
