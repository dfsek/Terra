package com.dfsek.terra.mod;

import com.dfsek.tectonic.api.TypeRegistry;
import com.dfsek.tectonic.api.depth.DepthTracker;
import com.dfsek.tectonic.api.exception.LoadException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.world.biome.Biome.Precipitation;
import net.minecraft.world.biome.BiomeEffects.GrassColorModifier;

import java.util.List;
import java.util.Locale;

import com.dfsek.terra.AbstractPlatform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.world.biome.PlatformBiome;
import com.dfsek.terra.mod.config.ProtoPlatformBiome;


public abstract class ModPlatform extends AbstractPlatform {
    public abstract MinecraftServer getServer();
    
    
    @Override
    public void register(TypeRegistry registry) {
        super.register(registry);
        registry.registerLoader(PlatformBiome.class, (type, o, loader, depthTracker) -> parseBiome((String) o, depthTracker))
                .registerLoader(Identifier.class, (type, o, loader, depthTracker) -> {
                    Identifier identifier = Identifier.tryParse((String) o);
                    if(identifier == null)
                        throw new LoadException("Invalid identifier: " + o, depthTracker);
                    return identifier;
                })
                .registerLoader(Precipitation.class, (type, o, loader, depthTracker) -> Precipitation.valueOf(((String) o).toUpperCase(
                        Locale.ROOT)))
                .registerLoader(GrassColorModifier.class, (type, o, loader, depthTracker) -> GrassColorModifier.valueOf(((String) o).toUpperCase(
                        Locale.ROOT)));
    }
    
    private ProtoPlatformBiome parseBiome(String id, DepthTracker tracker) throws LoadException {
        Identifier identifier = Identifier.tryParse(id);
        if(BuiltinRegistries.BIOME.get(identifier) == null) throw new LoadException("Invalid Biome ID: " + identifier, tracker); // failure.
        return new ProtoPlatformBiome(identifier);
    }
    
    @Override
    protected Iterable<BaseAddon> platformAddon() {
        return List.of(new MinecraftAddon(this));
    }
}
