package ai.timefold.solver.core.config.heuristic.selector.move.generic.list;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import ai.timefold.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import ai.timefold.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.timefold.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(propOrder = {
        "minimumSubListSize",
        "maximumSubListSize",
        "selectReversingMoveToo",
        "subListSelectorConfig",
        "destinationSelectorConfig"
})
public class SubListChangeMoveSelectorConfig extends MoveSelectorConfig<SubListChangeMoveSelectorConfig> {

    public static final String XML_ELEMENT_NAME = "subListChangeMoveSelector";

    /**
     * @deprecated The minimumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#getMinimumSubListSize()} instead.
     */
    @Deprecated(forRemoval = true)
    protected Integer minimumSubListSize = null;
    /**
     * @deprecated The maximumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#getMaximumSubListSize()} instead.
     */
    @Deprecated(forRemoval = true)
    protected Integer maximumSubListSize = null;
    private Boolean selectReversingMoveToo = null;
    @XmlElement(name = "subListSelector")
    private SubListSelectorConfig subListSelectorConfig = null;
    @XmlElement(name = "destinationSelector")
    private DestinationSelectorConfig destinationSelectorConfig = null;

    /**
     * @deprecated The minimumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#getMinimumSubListSize()} instead.
     */
    @Deprecated(forRemoval = true)
    public Integer getMinimumSubListSize() {
        return minimumSubListSize;
    }

    /**
     * @deprecated The minimumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#setMinimumSubListSize(Integer)} instead.
     */
    @Deprecated(forRemoval = true)
    public void setMinimumSubListSize(Integer minimumSubListSize) {
        this.minimumSubListSize = minimumSubListSize;
    }

    /**
     * @deprecated The maximumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#getMaximumSubListSize()} instead.
     */
    @Deprecated(forRemoval = true)
    public Integer getMaximumSubListSize() {
        return maximumSubListSize;
    }

    /**
     * @deprecated The maximumSubListSize on the SubListChangeMoveSelectorConfig is deprecated and will be removed in a future
     *             major version of Timefold. Use {@link SubListSelectorConfig#setMaximumSubListSize(Integer)} instead.
     */
    @Deprecated(forRemoval = true)
    public void setMaximumSubListSize(Integer maximumSubListSize) {
        this.maximumSubListSize = maximumSubListSize;
    }

    public @Nullable Boolean getSelectReversingMoveToo() {
        return selectReversingMoveToo;
    }

    public void setSelectReversingMoveToo(@Nullable Boolean selectReversingMoveToo) {
        this.selectReversingMoveToo = selectReversingMoveToo;
    }

    public @Nullable SubListSelectorConfig getSubListSelectorConfig() {
        return subListSelectorConfig;
    }

    public void setSubListSelectorConfig(@Nullable SubListSelectorConfig subListSelectorConfig) {
        this.subListSelectorConfig = subListSelectorConfig;
    }

    public @Nullable DestinationSelectorConfig getDestinationSelectorConfig() {
        return destinationSelectorConfig;
    }

    public void setDestinationSelectorConfig(@Nullable DestinationSelectorConfig destinationSelectorConfig) {
        this.destinationSelectorConfig = destinationSelectorConfig;
    }

    // ************************************************************************
    // With methods
    // ************************************************************************

    public @NonNull SubListChangeMoveSelectorConfig withSelectReversingMoveToo(@NonNull Boolean selectReversingMoveToo) {
        this.setSelectReversingMoveToo(selectReversingMoveToo);
        return this;
    }

    public @NonNull SubListChangeMoveSelectorConfig
            withSubListSelectorConfig(@NonNull SubListSelectorConfig subListSelectorConfig) {
        this.setSubListSelectorConfig(subListSelectorConfig);
        return this;
    }

    public @NonNull SubListChangeMoveSelectorConfig
            withDestinationSelectorConfig(@NonNull DestinationSelectorConfig destinationSelectorConfig) {
        this.setDestinationSelectorConfig(destinationSelectorConfig);
        return this;
    }

    // ************************************************************************
    // Builder methods
    // ************************************************************************

    @Override
    public @NonNull SubListChangeMoveSelectorConfig inherit(@NonNull SubListChangeMoveSelectorConfig inheritedConfig) {
        super.inherit(inheritedConfig);
        this.minimumSubListSize =
                ConfigUtils.inheritOverwritableProperty(minimumSubListSize, inheritedConfig.minimumSubListSize);
        this.maximumSubListSize =
                ConfigUtils.inheritOverwritableProperty(maximumSubListSize, inheritedConfig.maximumSubListSize);
        this.selectReversingMoveToo =
                ConfigUtils.inheritOverwritableProperty(selectReversingMoveToo, inheritedConfig.selectReversingMoveToo);
        this.subListSelectorConfig =
                ConfigUtils.inheritOverwritableProperty(subListSelectorConfig, inheritedConfig.subListSelectorConfig);
        this.destinationSelectorConfig =
                ConfigUtils.inheritOverwritableProperty(destinationSelectorConfig, inheritedConfig.destinationSelectorConfig);
        return this;
    }

    @Override
    public @NonNull SubListChangeMoveSelectorConfig copyConfig() {
        return new SubListChangeMoveSelectorConfig().inherit(this);
    }

    @Override
    public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
        visitCommonReferencedClasses(classVisitor);
        if (subListSelectorConfig != null) {
            subListSelectorConfig.visitReferencedClasses(classVisitor);
        }
        if (destinationSelectorConfig != null) {
            destinationSelectorConfig.visitReferencedClasses(classVisitor);
        }
    }

    @Override
    public boolean hasNearbySelectionConfig() {
        return (subListSelectorConfig != null && subListSelectorConfig.hasNearbySelectionConfig())
                || (destinationSelectorConfig != null && destinationSelectorConfig.hasNearbySelectionConfig());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + subListSelectorConfig + ", " + destinationSelectorConfig + ")";
    }
}
