package dev.whizzle.itembeacon.client.render;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/**
 * Loads the beacon cube ({@code itembeacon:block/item_beacon}: the owner's side and top, a lodestone bottom) and wraps
 * the lodestone's block state model in a {@link BeaconBlockModel}, which draws the cube where the server marked a
 * beacon (Glowing Glass's model swap).
 */
public final class BeaconModels {
    private BeaconModels() {}

    public static final Identifier MODEL = Identifier.fromNamespaceAndPath("itembeacon", "block/item_beacon");
    static final ExtraModelKey<BlockStateModel> KEY = ExtraModelKey.create(() -> "itembeacon:item_beacon");

    /** The baked beacon cube (null before the first resource load). */
    static BlockStateModel model() { return Minecraft.getInstance().getModelManager().getModel(KEY); }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            context.addModel(KEY, SimpleUnbakedExtraModel.blockStateModel(MODEL));
            context.modifyBlockModelAfterBake().register((model, ctx) ->
                ctx.state().is(Blocks.LODESTONE) ? new BeaconBlockModel(model, BeaconModels::model) : model);
        });
    }
}
