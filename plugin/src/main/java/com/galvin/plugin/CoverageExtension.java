package com.galvin.plugin;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class CoverageExtension {

    final Property<String> revision;
    final Property<String> baseline;

    public Property<String> getRevision() {
        return revision;
    }

    public Property<String> getBaseline() {
        return baseline;
    }

    public CoverageExtension(ObjectFactory objects) {
        revision = objects.property(String.class);
        baseline = objects.property(String.class);
    }

}
