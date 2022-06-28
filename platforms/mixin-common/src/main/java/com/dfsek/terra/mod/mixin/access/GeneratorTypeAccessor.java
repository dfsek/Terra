package com.dfsek.terra.mod.mixin.access;

import net.minecraft.client.world.GeneratorType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;


@Mixin(GeneratorType.class)
public interface GeneratorTypeAccessor {
    @Accessor("VALUES")
    static List<GeneratorType> getValues() {
        throw new UnsupportedOperationException();
    }
    
    @Mutable
    @Accessor("displayName")
    void setDisplayName(Text translationKey);
}
