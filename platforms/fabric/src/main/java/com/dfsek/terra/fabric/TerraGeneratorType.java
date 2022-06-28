package com.dfsek.terra.fabric;

import com.dfsek.terra.mod.generation.MinecraftChunkGeneratorWrapper;
import com.dfsek.terra.mod.generation.TerraBiomeSource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;

import com.dfsek.terra.api.config.ConfigPack;


@Environment(EnvType.CLIENT)
public class TerraGeneratorType extends GeneratorType {
    private final ConfigPack pack;
    
    public TerraGeneratorType(ConfigPack pack) {
        super("terra." + pack.getID());
        this.pack = pack;
    }
    
    @Override
    protected ChunkGenerator getChunkGenerator(DynamicRegistryManager manager, long seed) {
        Registry<ChunkGeneratorSettings> chunkGeneratorSettingsRegistry = manager.get(Registry.CHUNK_GENERATOR_SETTINGS_KEY);
        RegistryEntry<ChunkGeneratorSettings>
                settingsSupplier = chunkGeneratorSettingsRegistry.getEntry(ChunkGeneratorSettings.OVERWORLD).orElseThrow();
        Registry<StructureSet> noiseRegistry = manager.get(Registry.STRUCTURE_SET_KEY);
        return new MinecraftChunkGeneratorWrapper(noiseRegistry, new TerraBiomeSource(manager.get(Registry.BIOME_KEY), pack, seed), pack,
                                                  settingsSupplier,
                                                  seed);
    }
}

