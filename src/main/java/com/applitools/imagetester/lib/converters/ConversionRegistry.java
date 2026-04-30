package com.applitools.imagetester.lib.converters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConversionRegistry {
    private final List<FormatConverter> converters = new ArrayList<>();

    public void register(FormatConverter converter) {
        converters.add(converter);
    }

    public Optional<FormatConverter> find(File file) {
        for (FormatConverter c : converters) {
            if (c.accepts(file)) return Optional.of(c);
        }
        return Optional.empty();
    }
}
