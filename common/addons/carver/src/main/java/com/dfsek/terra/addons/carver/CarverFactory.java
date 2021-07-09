package com.dfsek.terra.addons.carver;

import com.dfsek.paralithic.eval.tokenizer.ParseException;
import com.dfsek.tectonic.exception.LoadException;
import com.dfsek.terra.api.TerraPlugin;
import com.dfsek.terra.api.config.ConfigFactory;
import com.dfsek.terra.api.config.ConfigPack;
import com.dfsek.terra.api.util.MathUtil;

import java.util.Arrays;
import java.util.List;

public class CarverFactory implements ConfigFactory<CarverTemplate, UserDefinedCarver> {
    private final ConfigPack pack;

    public CarverFactory(ConfigPack pack) {
        this.pack = pack;
    }

    @Override
    public UserDefinedCarver build(CarverTemplate config, TerraPlugin main) throws LoadException {
        double[] start = new double[] {config.getStartX(), config.getStartY(), config.getStartZ()};
        double[] mutate = new double[] {config.getMutateX(), config.getMutateY(), config.getMutateZ()};
        List<String> radius = Arrays.asList(config.getRadMX(), config.getRadMY(), config.getRadMZ());
        long hash = MathUtil.hashToLong(config.getID());
        UserDefinedCarver carver;
        try {
            carver = new UserDefinedCarver(config.getHeight(), config.getLength(), start, mutate, radius, pack.getVarScope(), hash, config.getCutTop(), config.getCutBottom(), config, main, pack.getTemplate().getNoiseBuilderMap(), pack.getTemplate().getFunctions());
        } catch(ParseException e) {
            throw new LoadException("Unable to parse radius equations", e);
        }
        carver.setRecalc(config.getRecalc());
        carver.setRecalcMagnitude(config.getRecaclulateMagnitude());
        return carver;
    }
}