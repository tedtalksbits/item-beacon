package dev.whizzle.itembeacon.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel is (for the search bar, the sort buttons and dimming slots). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int itembeacon$left();
    @Accessor("topPos") int itembeacon$top();
    @Accessor("imageWidth") int itembeacon$width();
    @Accessor("imageHeight") int itembeacon$height();
}
