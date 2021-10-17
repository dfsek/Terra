package com.dfsek.terra.api.event.events.config.type;

import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.config.ConfigType;
import com.dfsek.terra.api.registry.CheckedRegistry;


public class ConfigTypePreLoadEvent extends ConfigTypeLoadEvent {
    public ConfigTypePreLoadEvent(ConfigType<?, ?> type, CheckedRegistry<?> registry, ConfigPack pack) {
        super(type, registry, pack);
    }
}