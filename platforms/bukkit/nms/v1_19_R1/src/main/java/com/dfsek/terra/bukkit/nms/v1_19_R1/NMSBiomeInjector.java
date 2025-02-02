package com.dfsek.terra.bukkit.nms.v1_19_R1;

import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.bukkit.config.VanillaBiomeProperties;
import com.dfsek.terra.bukkit.world.BukkitPlatformBiome;
import com.dfsek.terra.registry.master.ConfigRegistry;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.bukkit.NamespacedKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class NMSBiomeInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(NMSBiomeInjector.class);
    private static final Map<ResourceLocation, List<ResourceLocation>> terraBiomeMap = new HashMap<>();
    
    
    public static void registerBiomes(ConfigRegistry configRegistry) {
        try {
            LOGGER.info("Hacking biome registry...");
            WritableRegistry<Biome> biomeRegistry = (WritableRegistry<Biome>) Registries.biomeRegistry();
            
            Reflection.MAPPED_REGISTRY.setFrozen((MappedRegistry<?>) biomeRegistry, false);
            
            configRegistry.forEach(pack -> pack.getRegistry(com.dfsek.terra.api.world.biome.Biome.class).forEach((key, biome) -> {
                try {
                    BukkitPlatformBiome platformBiome = (BukkitPlatformBiome) biome.getPlatformBiome();
                    NamespacedKey vanillaBukkitKey = platformBiome.getHandle().getKey();
                    ResourceLocation vanillaMinecraftKey = new ResourceLocation(vanillaBukkitKey.getNamespace(), vanillaBukkitKey.getKey());
                    Biome platform = createBiome(
                            biome,
                            Objects.requireNonNull(biomeRegistry.get(vanillaMinecraftKey)) // get
                                                );
                    
                    ResourceKey<Biome> delegateKey = ResourceKey.create(Registry.BIOME_REGISTRY,
                                                                        new ResourceLocation("terra", createBiomeID(pack, key)));
                    
                    BuiltinRegistries.register(BuiltinRegistries.BIOME, delegateKey, platform);
                    biomeRegistry.register(delegateKey, platform, Lifecycle.stable());
                    platformBiome.getContext().put(new NMSBiomeInfo(delegateKey));
                    
                    terraBiomeMap.computeIfAbsent(vanillaMinecraftKey, i -> new ArrayList<>()).add(delegateKey.location());
                    
                    LOGGER.debug("Registered biome: " + delegateKey);
                } catch(NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }));
            
            Reflection.MAPPED_REGISTRY.setFrozen((MappedRegistry<?>) biomeRegistry, true); // freeze registry again :)
            
            LOGGER.info("Doing tag garbage....");
            Map<TagKey<Biome>, List<Holder<Biome>>> collect = biomeRegistry
                    .getTags() // streamKeysAndEntries
                    .collect(HashMap::new,
                             (map, pair) ->
                                     map.put(pair.getFirst(), new ArrayList<>(pair.getSecond().stream().toList())),
                             HashMap::putAll);
            
            terraBiomeMap
                    .forEach((vb, terraBiomes) ->
                                     getEntry(biomeRegistry, vb)
                                             .ifPresentOrElse(
                                                     vanilla -> terraBiomes
                                                             .forEach(tb -> getEntry(biomeRegistry, tb)
                                                                     .ifPresentOrElse(
                                                                             terra -> {
                                                                                 LOGGER.debug(vanilla.unwrapKey().orElseThrow().location() +
                                                                                              " (vanilla for " +
                                                                                              terra.unwrapKey().orElseThrow().location() +
                                                                                              ": " +
                                                                                              vanilla.tags().toList());
                                                                        
                                                                                 vanilla.tags()
                                                                                        .forEach(
                                                                                                tag -> collect
                                                                                                        .computeIfAbsent(tag,
                                                                                                                         t -> new ArrayList<>())
                                                                                                        .add(terra));
                                                                             },
                                                                             () -> LOGGER.error(
                                                                                     "No such biome: {}",
                                                                                     tb))),
                                                     () -> LOGGER.error("No vanilla biome: {}", vb)));
            
            biomeRegistry.resetTags();
            biomeRegistry.bindTags(ImmutableMap.copyOf(collect));
            
        } catch(SecurityException | IllegalArgumentException exception) {
            throw new RuntimeException(exception);
        }
    }
    
    public static <T> Optional<Holder<T>> getEntry(Registry<T> registry, ResourceLocation identifier) {
        return registry.getOptional(identifier)
                       .flatMap(registry::getResourceKey)
                       .map(registry::getOrCreateHolderOrThrow);
    }
    
    private static Biome createBiome(com.dfsek.terra.api.world.biome.Biome biome, Biome vanilla)
    throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
        
        builder
                .precipitation(vanilla.getPrecipitation())
                .downfall(vanilla.getDownfall())
                .temperature(vanilla.getBaseTemperature())
                .mobSpawnSettings(vanilla.getMobSettings())
                .generationSettings(vanilla.getGenerationSettings());
        
        
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder();
        
        effects.grassColorModifier(vanilla.getSpecialEffects().getGrassColorModifier());
        
        VanillaBiomeProperties vanillaBiomeProperties = biome.getContext().get(VanillaBiomeProperties.class);
        
        effects.fogColor(Objects.requireNonNullElse(vanillaBiomeProperties.getFogColor(), vanilla.getFogColor()))
        
               .waterColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterColor(), vanilla.getWaterColor()))
        
               .waterFogColor(Objects.requireNonNullElse(vanillaBiomeProperties.getWaterFogColor(), vanilla.getWaterFogColor()))
        
               .skyColor(Objects.requireNonNullElse(vanillaBiomeProperties.getSkyColor(), vanilla.getSkyColor()));
        
        if(vanillaBiomeProperties.getFoliageColor() == null) {
            vanilla.getSpecialEffects().getFoliageColorOverride().ifPresent(effects::foliageColorOverride);
        } else {
            effects.foliageColorOverride(vanillaBiomeProperties.getFoliageColor());
        }
        
        if(vanillaBiomeProperties.getGrassColor() == null) {
            vanilla.getSpecialEffects().getGrassColorOverride().ifPresent(effects::grassColorOverride);
        } else {
            // grass
            effects.grassColorOverride(vanillaBiomeProperties.getGrassColor());
        }
        
        builder.specialEffects(effects.build());
        
        return builder.build();
    }
    
    public static String createBiomeID(ConfigPack pack, com.dfsek.terra.api.registry.key.RegistryKey biomeID) {
        return pack.getID()
                   .toLowerCase() + "/" + biomeID.getNamespace().toLowerCase(Locale.ROOT) + "/" + biomeID.getID().toLowerCase(Locale.ROOT);
    }
}
