package com.dfsek.terra.fabric;

import com.dfsek.tectonic.loading.TypeRegistry;
import com.dfsek.terra.api.TerraPlugin;
import com.dfsek.terra.api.addons.TerraAddon;
import com.dfsek.terra.api.addons.annotations.Addon;
import com.dfsek.terra.api.addons.annotations.Author;
import com.dfsek.terra.api.addons.annotations.Version;
import com.dfsek.terra.api.event.EventListener;
import com.dfsek.terra.api.event.EventManager;
import com.dfsek.terra.api.event.TerraEventManager;
import com.dfsek.terra.api.event.annotations.Global;
import com.dfsek.terra.api.event.annotations.Priority;
import com.dfsek.terra.api.event.events.config.ConfigPackPreLoadEvent;
import com.dfsek.terra.api.platform.block.BlockData;
import com.dfsek.terra.api.platform.handle.ItemHandle;
import com.dfsek.terra.api.platform.handle.WorldHandle;
import com.dfsek.terra.api.platform.world.World;
import com.dfsek.terra.api.registry.CheckedRegistry;
import com.dfsek.terra.api.registry.LockedRegistry;
import com.dfsek.terra.api.transform.NotNullValidator;
import com.dfsek.terra.api.transform.Transformer;
import com.dfsek.terra.api.util.logging.DebugLogger;
import com.dfsek.terra.api.util.logging.Logger;
import com.dfsek.terra.api.world.tree.Tree;
import com.dfsek.terra.config.GenericLoaders;
import com.dfsek.terra.config.PluginConfig;
import com.dfsek.terra.config.builder.BiomeBuilder;
import com.dfsek.terra.config.lang.LangUtil;
import com.dfsek.terra.config.lang.Language;
import com.dfsek.terra.config.pack.ConfigPack;
import com.dfsek.terra.fabric.inventory.FabricItemHandle;
import com.dfsek.terra.fabric.mixin.BiomeEffectsAccessor;
import com.dfsek.terra.fabric.mixin.GeneratorTypeAccessor;
import com.dfsek.terra.fabric.world.FabricBiome;
import com.dfsek.terra.fabric.world.FabricTree;
import com.dfsek.terra.fabric.world.FabricWorldHandle;
import com.dfsek.terra.fabric.world.TerraBiomeSource;
import com.dfsek.terra.fabric.world.features.PopulatorFeature;
import com.dfsek.terra.fabric.world.generator.FabricChunkGenerator;
import com.dfsek.terra.fabric.world.generator.FabricChunkGeneratorWrapper;
import com.dfsek.terra.registry.exception.DuplicateEntryException;
import com.dfsek.terra.registry.master.AddonRegistry;
import com.dfsek.terra.registry.master.ConfigRegistry;
import com.dfsek.terra.world.TerraWorld;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEffects;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.decorator.Decorator;
import net.minecraft.world.gen.decorator.NopeDecoratorConfig;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.ConfiguredFeatures;
import net.minecraft.world.gen.feature.DefaultBiomeFeatures;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import net.minecraft.world.gen.surfacebuilder.TernarySurfaceConfig;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class TerraFabricPlugin implements TerraPlugin, ModInitializer {

    private final Map<Long, TerraWorld> worldMap = new HashMap<>();
    private static TerraFabricPlugin instance;

    private final EventManager eventManager = new TerraEventManager(this);


    public static TerraFabricPlugin getInstance() {
        return instance;
    }

    public static final PopulatorFeature POPULATOR_FEATURE = new PopulatorFeature(DefaultFeatureConfig.CODEC);
    public static final ConfiguredFeature<?, ?> POPULATOR_CONFIGURED_FEATURE = POPULATOR_FEATURE.configure(FeatureConfig.DEFAULT).decorate(Decorator.NOPE.configure(NopeDecoratorConfig.INSTANCE));

    private final GenericLoaders genericLoaders = new GenericLoaders(this);
    private final Logger logger = new Logger() {
        private final org.apache.logging.log4j.Logger logger = LogManager.getLogger();
        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warning(String message) {
            logger.warn(message);
        }

        @Override
        public void severe(String message) {
            logger.error(message);
        }
    };
    private final DebugLogger debugLogger = new DebugLogger(logger);


    private final ItemHandle itemHandle = new FabricItemHandle();
    private final WorldHandle worldHandle = new FabricWorldHandle();
    private final ConfigRegistry registry = new ConfigRegistry();
    private final CheckedRegistry<ConfigPack> checkedRegistry = new CheckedRegistry<>(registry);

    private final AddonRegistry addonRegistry = new AddonRegistry(new FabricAddon(this), this);
    private final LockedRegistry<TerraAddon> addonLockedRegistry = new LockedRegistry<>(addonRegistry);


    private File config;

    private final PluginConfig plugin = new PluginConfig();

    @Override
    public WorldHandle getWorldHandle() {
        return worldHandle;
    }

    @Override
    public TerraWorld getWorld(World world) {
        if(worldMap.size() > 1) System.out.println(worldMap.size());
        return worldMap.computeIfAbsent(world.getSeed(), w -> {
            logger.info("Loading world " + w);
            return new TerraWorld(world, ((FabricChunkGeneratorWrapper) ((FabricChunkGenerator) world.getGenerator()).getHandle()).getPack(), this);
        });
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public PluginConfig getTerraConfig() {
        return plugin;
    }

    @Override
    public File getDataFolder() {
        return config;
    }

    @Override
    public boolean isDebug() {
        return true;
    }

    @Override
    public Language getLanguage() {
        return LangUtil.getLanguage();
    }

    @Override
    public CheckedRegistry<ConfigPack> getConfigRegistry() {
        return checkedRegistry;
    }

    @Override
    public LockedRegistry<TerraAddon> getAddons() {
        return addonLockedRegistry;
    }

    @Override
    public boolean reload() {
        return true;
    }

    @Override
    public ItemHandle getItemHandle() {
        return itemHandle;
    }

    @Override
    public void saveDefaultConfig() {
        try(InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            File configFile = new File(getDataFolder(), "config.yml");
            if(!configFile.exists()) FileUtils.copyInputStreamToFile(stream, configFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String platformName() {
        return "Fabric";
    }

    @Override
    public DebugLogger getDebugLogger() {
        return debugLogger;
    }

    private final Transformer<String, Biome> biomeFixer = new Transformer.Builder<String, Biome>()
            .addTransform(id -> BuiltinRegistries.BIOME.get(Identifier.tryParse(id)), new NotNullValidator<>())
            .addTransform(id -> BuiltinRegistries.BIOME.get(Identifier.tryParse("minecraft:" + id.toLowerCase())), new NotNullValidator<>()).build();

    @Override
    public void register(TypeRegistry registry) {
        genericLoaders.register(registry);
        registry
                .registerLoader(BlockData.class, (t, o, l) -> worldHandle.createBlockData((String) o))
                .registerLoader(com.dfsek.terra.api.platform.world.Biome.class, (t, o, l) -> new FabricBiome(biomeFixer.translate((String) o)));
    }

    public static String createBiomeID(ConfigPack pack, String biomeID) {
        return pack.getTemplate().getID().toLowerCase() + "/" + biomeID.toLowerCase(Locale.ROOT);
    }

    private Biome createBiome(BiomeBuilder biome) {
        SpawnSettings.Builder spawnSettings = new SpawnSettings.Builder();
        DefaultBiomeFeatures.addFarmAnimals(spawnSettings);
        DefaultBiomeFeatures.addMonsters(spawnSettings, 95, 5, 100);

        Biome vanilla = ((FabricBiome) new ArrayList<>(biome.getVanillaBiomes().getContents()).get(0)).getHandle();

        GenerationSettings.Builder generationSettings = new GenerationSettings.Builder();
        generationSettings.surfaceBuilder(SurfaceBuilder.DEFAULT.withConfig(new TernarySurfaceConfig(Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.GRAVEL.getDefaultState()))); // It needs a surfacebuilder, even though we dont use it.
        generationSettings.feature(GenerationStep.Feature.VEGETAL_DECORATION, POPULATOR_CONFIGURED_FEATURE);

        BiomeEffectsAccessor biomeEffectsAccessor = (BiomeEffectsAccessor) vanilla.getEffects();

        BiomeEffects.Builder effects = new BiomeEffects.Builder()
                .waterColor(biomeEffectsAccessor.getWaterColor())
                .waterFogColor(biomeEffectsAccessor.getWaterFogColor())
                .fogColor(biomeEffectsAccessor.getFogColor())
                .skyColor(biomeEffectsAccessor.getSkyColor())
                .grassColorModifier(biomeEffectsAccessor.getGrassColorModifier());
        if(biomeEffectsAccessor.getGrassColor().isPresent()) {
            effects.grassColor(biomeEffectsAccessor.getGrassColor().get());
        }
        if(biomeEffectsAccessor.getFoliageColor().isPresent()) {
            effects.foliageColor(biomeEffectsAccessor.getFoliageColor().get());
        }

        return (new Biome.Builder())
                .precipitation(vanilla.getPrecipitation())
                .category(vanilla.getCategory())
                .depth(vanilla.getDepth())
                .scale(vanilla.getScale())
                .temperature(vanilla.getTemperature())
                .downfall(vanilla.getDownfall())
                .effects(vanilla.getEffects()) // TODO: configurable
                .spawnSettings(spawnSettings.build())
                .generationSettings(generationSettings.build())
                .build();
    }

    @Override
    public void onInitialize() {
        instance = this;

        this.config = new File(FabricLoader.getInstance().getConfigDir().toFile(), "Terra");
        saveDefaultConfig();
        plugin.load(this);
        LangUtil.load(plugin.getLanguage(), this);
        logger.info("Initializing Terra...");

        if(!addonRegistry.loadAll()) {
            throw new IllegalStateException("Failed to load addons. Please correct addon installations to continue.");
        }
        logger.info("Loaded addons.");

        registry.loadAll(this);

        logger.info("Loaded packs.");

        Registry.register(Registry.FEATURE, new Identifier("terra", "flora_populator"), POPULATOR_FEATURE);
        RegistryKey<ConfiguredFeature<?, ?>> floraKey = RegistryKey.of(Registry.CONFIGURED_FEATURE_WORLDGEN, new Identifier("terra", "flora_populator"));
        Registry.register(BuiltinRegistries.CONFIGURED_FEATURE, floraKey.getValue(), POPULATOR_CONFIGURED_FEATURE);

        registry.forEach(pack -> pack.getBiomeRegistry().forEach((id, biome) -> Registry.register(BuiltinRegistries.BIOME, new Identifier("terra", createBiomeID(pack, id)), createBiome(biome)))); // Register all Terra biomes.
        Registry.register(Registry.CHUNK_GENERATOR, new Identifier("terra:terra"), FabricChunkGeneratorWrapper.CODEC);
        Registry.register(Registry.BIOME_SOURCE, new Identifier("terra:terra"), TerraBiomeSource.CODEC);

        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            registry.forEach(pack -> {
                final GeneratorType generatorType = new GeneratorType("terra." + pack.getTemplate().getID()) {
                    @Override
                    protected ChunkGenerator getChunkGenerator(Registry<Biome> biomeRegistry, Registry<ChunkGeneratorSettings> chunkGeneratorSettingsRegistry, long seed) {
                        return new FabricChunkGeneratorWrapper(new TerraBiomeSource(biomeRegistry, seed, pack), seed, pack);
                    }
                };
                //noinspection ConstantConditions
                ((GeneratorTypeAccessor) generatorType).setTranslationKey(new LiteralText("Terra:" + pack.getTemplate().getID()));
                GeneratorTypeAccessor.getVALUES().add(generatorType);
            });
        }

    }


    @Override
    public EventManager getEventManager() {
        return eventManager;
    }

    @Addon("Terra-Fabric")
    @Author("Terra")
    @Version("1.0.0")
    private static final class FabricAddon extends TerraAddon implements EventListener {

        private final TerraPlugin main;

        private FabricAddon(TerraPlugin main) {
            this.main = main;
        }

        @Override
        public void initialize() {
            main.getEventManager().registerListener(this, this);
        }

        @Priority(Priority.LOWEST)
        @Global
        public void injectTrees(ConfigPackPreLoadEvent event) {
            CheckedRegistry<Tree> treeRegistry = event.getPack().getTreeRegistry();
            injectTree(treeRegistry, "BROWN_MUSHROOM", ConfiguredFeatures.BROWN_MUSHROOM_GIANT);
            injectTree(treeRegistry, "RED_MUSHROOM", ConfiguredFeatures.RED_MUSHROOM_GIANT);
            injectTree(treeRegistry, "JUNGLE", ConfiguredFeatures.MEGA_JUNGLE_TREE);
            injectTree(treeRegistry, "JUNGLE_COCOA", ConfiguredFeatures.JUNGLE_TREE);
            injectTree(treeRegistry, "LARGE_OAK", ConfiguredFeatures.FANCY_OAK);
            injectTree(treeRegistry, "LARGE_SPRUCE", ConfiguredFeatures.PINE);
            injectTree(treeRegistry, "SMALL_JUNGLE", ConfiguredFeatures.JUNGLE_TREE);
            injectTree(treeRegistry, "SWAMP_OAK", ConfiguredFeatures.SWAMP_TREE);
            injectTree(treeRegistry, "TALL_BIRCH", ConfiguredFeatures.BIRCH_TALL);
            injectTree(treeRegistry, "ACACIA", ConfiguredFeatures.ACACIA);
            injectTree(treeRegistry, "BIRCH", ConfiguredFeatures.BIRCH);
            injectTree(treeRegistry, "DARK_OAK", ConfiguredFeatures.DARK_OAK);
            injectTree(treeRegistry, "OAK", ConfiguredFeatures.OAK);
            injectTree(treeRegistry, "CHORUS_PLANT", ConfiguredFeatures.CHORUS_PLANT);
            injectTree(treeRegistry, "SPRUCE", ConfiguredFeatures.SPRUCE);
            injectTree(treeRegistry, "JUNGLE_BUSH", ConfiguredFeatures.JUNGLE_BUSH);
            injectTree(treeRegistry, "MEGA_SPRUCE", ConfiguredFeatures.MEGA_SPRUCE);
            injectTree(treeRegistry, "CRIMSON_FUNGUS", ConfiguredFeatures.CRIMSON_FUNGI);
            injectTree(treeRegistry, "WARPED_FUNGUS", ConfiguredFeatures.WARPED_FUNGI);
        }


        private void injectTree(CheckedRegistry<Tree> registry, String id, ConfiguredFeature<?, ?> tree) {
            try {
                registry.add(id, new FabricTree(tree));
            } catch(DuplicateEntryException ignore) {
            }
        }
    }
}
