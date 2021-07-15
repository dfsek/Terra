package com.dfsek.terra.addons.flora;

import com.dfsek.terra.addons.flora.flora.FloraLayer;
import com.dfsek.terra.api.TerraPlugin;
import com.dfsek.terra.api.profiler.ProfileFrame;
import com.dfsek.terra.api.util.PopulationUtil;
import com.dfsek.terra.api.vector.Vector2;
import com.dfsek.terra.api.world.Chunk;
import com.dfsek.terra.api.world.TerraWorld;
import com.dfsek.terra.api.world.World;
import com.dfsek.terra.api.world.biome.TerraBiome;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.generator.TerraGenerationStage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Populates Flora
 */
public class FloraPopulator implements TerraGenerationStage {
    private final TerraPlugin main;
    private final FloraAddon floraAddon;

    public FloraPopulator(TerraPlugin main, FloraAddon floraAddon) {
        this.main = main;
        this.floraAddon = floraAddon;
    }

    @SuppressWarnings("try")
    @Override
    public void populate(@NotNull World world, @NotNull Chunk chunk) {
        TerraWorld tw = main.getWorld(world);
        try(ProfileFrame ignore = main.getProfiler().profile("flora")) {
            if(tw.getConfig().disableFlora()) return;

            BiomeProvider provider = tw.getBiomeProvider();
            Map<Vector2, List<FloraLayer>> layers = new HashMap<>();
            for(int x = 0; x < 16; x++) {
                for(int z = 0; z < 16; z++) {
                    Vector2 l = new Vector2(x, z);
                    layers.put(l, floraAddon.getFlora(provider.getBiome((chunk.getX() << 4) + x, (chunk.getZ() << 4) + z)));
                }
            }

            Random random = PopulationUtil.getRandom(chunk);

            int iter = 0;
            boolean finished = false;
            while(!finished) {
                finished = true;
                for(Map.Entry<Vector2, List<FloraLayer>> entry : layers.entrySet()) {
                    if(entry.getValue().size() <= iter) continue;
                    finished = false;
                    FloraLayer layer = entry.getValue().get(iter);
                    if(layer.getDensity() >= random.nextDouble() * 100D) layer.place(chunk, entry.getKey());
                }
                iter++;
            }
        }
    }
}