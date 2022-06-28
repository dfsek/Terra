package com.dfsek.terra.mod.mixin.fix;

import net.minecraft.structure.StructureGeneratorFactory.Context;
import net.minecraft.structure.StructurePiecesGenerator;
import net.minecraft.world.gen.feature.NetherFossilFeature;
import net.minecraft.world.gen.feature.RangeFeatureConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import com.dfsek.terra.mod.generation.MinecraftChunkGeneratorWrapper;


/**
 * Disable fossil generation in Terra worlds, as they are very expensive due to consistently triggering cache misses.
 *
 * Currently, on Fabric, Terra cannot be specified as a Nether generator. TODO: logic to turn fossils back on if chunk generator is in nether.
 */
@Mixin(NetherFossilFeature.class)
public class NetherFossilOptimization {
    @Inject(method = "addPieces", at = @At("HEAD"), cancellable = true)
    private static void injectFossilPositions(Context<RangeFeatureConfig> context,
                                              CallbackInfoReturnable<Optional<StructurePiecesGenerator<RangeFeatureConfig>>> cir) {
        if(context.chunkGenerator() instanceof MinecraftChunkGeneratorWrapper) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
