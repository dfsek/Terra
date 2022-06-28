/*
 * This file is part of Terra.
 *
 * Terra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Terra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Terra.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.dfsek.terra.mod.generation;

import com.dfsek.terra.mod.BeardGenerator;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep.Carver;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.densityfunction.DensityFunction.UnblendedNoisePos;
import net.minecraft.world.gen.random.AtomicSimpleRandom;
import net.minecraft.world.gen.random.ChunkRandom;
import net.minecraft.world.gen.random.RandomSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.chunk.generation.ChunkGenerator;
import com.dfsek.terra.api.world.chunk.generation.ProtoChunk;
import com.dfsek.terra.api.world.chunk.generation.ProtoWorld;
import com.dfsek.terra.api.world.chunk.generation.stage.Chunkified;
import com.dfsek.terra.api.world.chunk.generation.util.GeneratorWrapper;
import com.dfsek.terra.api.world.info.WorldProperties;
import com.dfsek.terra.mod.config.PreLoadCompatibilityOptions;
import com.dfsek.terra.mod.data.Codecs;
import com.dfsek.terra.mod.mixin.access.StructureAccessorAccessor;
import com.dfsek.terra.mod.util.MinecraftAdapter;


public class MinecraftChunkGeneratorWrapper extends net.minecraft.world.gen.chunk.ChunkGenerator implements GeneratorWrapper {
    private static final Logger logger = LoggerFactory.getLogger(MinecraftChunkGeneratorWrapper.class);
    
    private final TerraBiomeSource biomeSource;
    private final Registry<StructureSet> noiseRegistry;
    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private ChunkGenerator delegate;
    private ConfigPack pack;
    
    private final long seed;
    
    public MinecraftChunkGeneratorWrapper(Registry<StructureSet> noiseRegistry, TerraBiomeSource biomeSource, ConfigPack configPack,
                                          RegistryEntry<ChunkGeneratorSettings> settingsSupplier, long seed) {
        super(noiseRegistry, Optional.empty(), biomeSource);
        this.noiseRegistry = noiseRegistry;
        this.pack = configPack;
        this.settings = settingsSupplier;
        
        this.delegate = pack.getGeneratorProvider().newInstance(pack);
        logger.info("Loading world with config pack {}", pack.getID());
        this.biomeSource = biomeSource;
        this.seed = seed;
    }
    
    public long getSeed() {
        return seed;
    }
    
    public Registry<StructureSet> getNoiseRegistry() {
        return noiseRegistry;
    }
    
    @Override
    protected Codec<? extends net.minecraft.world.gen.chunk.ChunkGenerator> getCodec() {
        return Codecs.MINECRAFT_CHUNK_GENERATOR_WRAPPER;
    }
    
    @Override
    public net.minecraft.world.gen.chunk.ChunkGenerator withSeed(long seed) {
        return new MinecraftChunkGeneratorWrapper(noiseRegistry, biomeSource, pack, settings, seed);
    }
    
    @Override
    public MultiNoiseSampler getMultiNoiseSampler() {
        return MultiNoiseUtil.method_40443();
    }
    
    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, Chunk chunk) {
        // no op
    }
    
    @Override
    public void populateEntities(ChunkRegion region) {
        ChunkPos chunkPos = region.getCenterPos();
        RegistryEntry<Biome> biome = region.getBiome(chunkPos.getStartPos().withY(region.getTopY() - 1));
        ChunkRandom chunkRandom = new ChunkRandom(new AtomicSimpleRandom(RandomSeed.getSeed()));
        chunkRandom.setPopulationSeed(region.getSeed(), chunkPos.getStartX(), chunkPos.getStartZ());
        SpawnHelper.populateEntities(region, biome, chunkPos, chunkRandom);
    }
    
    @Override
    public int getWorldHeight() {
        return settings.value().generationShapeConfig().height();
    }
    
    
    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender,
                                                  StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            ProtoWorld world = (ProtoWorld) ((StructureAccessorAccessor) structureAccessor).getWorld();
            BiomeProvider biomeProvider = pack.getBiomeProvider();
            delegate.generateChunkData((ProtoChunk) chunk, world, biomeProvider, chunk.getPos().x, chunk.getPos().z);
            
            PreLoadCompatibilityOptions compatibilityOptions = pack.getContext().get(PreLoadCompatibilityOptions.class);
            if(compatibilityOptions.isBeard()) {
                new BeardGenerator(structureAccessor, chunk, compatibilityOptions.getBeardThreshold()).generate(delegate, world, biomeProvider);
            }
            return chunk;
        }, Util.getMainWorkerExecutor());
    }
    
    
    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
        super.generateFeatures(world, chunk, structureAccessor);
        pack.getStages().forEach(populator -> {
            if(!(populator instanceof Chunkified)) {
                populator.populate((ProtoWorld) world);
            }
        });
    }
    
    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }
    
    @Override
    public int getMinimumY() {
        return settings.value().generationShapeConfig().minimumY();
    }
    
    
    @Override
    public int getHeight(int x, int z, Type heightmap, HeightLimitView height) {
        WorldProperties properties = MinecraftAdapter.adapt(height, seed);
        BiomeProvider biomeProvider = pack.getBiomeProvider();
        int min = height.getBottomY();
        for(int y = height.getTopY() - 1; y >= min; y--) {
            if(heightmap
                    .getBlockPredicate()
                    .test((BlockState) delegate.getBlock(properties, x, y, z, biomeProvider))) return y + 1;
        }
        return min;
    }
    
    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView height) {
        BlockState[] array = new BlockState[height.getHeight()];
        WorldProperties properties = MinecraftAdapter.adapt(height, seed);
        BiomeProvider biomeProvider = pack.getBiomeProvider();
        for(int y = height.getTopY() - 1; y >= height.getBottomY(); y--) {
            array[y - height.getBottomY()] = (BlockState) delegate.getBlock(properties, x, y, z, biomeProvider);
        }
        return new VerticalBlockSample(height.getBottomY(), array);
    }
    
    @Override
    public void getDebugHudText(List<String> text, BlockPos pos) {
    
    }
    
    public ConfigPack getPack() {
        return pack;
    }
    
    public void setPack(ConfigPack pack) {
        this.pack = pack;
        this.delegate = pack.getGeneratorProvider().newInstance(pack);
        biomeSource.setPack(pack);
        
        logger.debug("Loading world with config pack {}", pack.getID());
    }
    
    @Override
    public void carve(ChunkRegion chunkRegion, long seed, BiomeAccess world, StructureAccessor structureAccessor,
                      Chunk chunk, Carver carverStep) {
        // no op
    }
    
    @Override
    public ChunkGenerator getHandle() {
        return delegate;
    }
    
    public RegistryEntry<ChunkGeneratorSettings> getSettings() {
        return settings;
    }
    
    @Override
    public TerraBiomeSource getBiomeSource() {
        return biomeSource;
    }
}
