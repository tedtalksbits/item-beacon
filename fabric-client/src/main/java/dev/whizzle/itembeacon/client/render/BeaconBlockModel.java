package dev.whizzle.itembeacon.client.render;

import dev.whizzle.itembeacon.client.ItemBeaconClient;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Wraps the lodestone's model: at a position the server marked as an Item Beacon it emits the beacon cube instead
 * (a swap, not an overlay); everywhere else the lodestone. Runs on chunk-meshing threads: reads only
 * {@link ItemBeaconClient#BEACONS}.
 */
public final class BeaconBlockModel extends WrapperBlockStateModel {
    private final Supplier<BlockStateModel> beacon;

    public BeaconBlockModel(BlockStateModel lodestone, Supplier<BlockStateModel> beacon) {
        super(lodestone);
        this.beacon = beacon;
    }

    private BlockStateModel at(BlockPos pos) {
        return ItemBeaconClient.BEACONS.at(pos.getX(), pos.getY(), pos.getZ()) ? beacon.get() : null;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
        BlockStateModel b = at(pos);
        if (b != null) b.emitQuads(emitter, level, pos, state, random, cullTest);
        else super.emitQuads(emitter, level, pos, state, random, cullTest);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return at(pos) != null ? null : super.createGeometryKey(level, pos, state, random);
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockStateModel b = at(pos);
        return b != null ? b.particleMaterial(level, pos, state) : super.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        BlockStateModel b = at(pos);
        return b != null ? b.materialFlags(level, pos, state, random) : super.materialFlags(level, pos, state, random);
    }

    @Override
    public boolean hasMaterialFlag(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, int flag) {
        BlockStateModel b = at(pos);
        return b != null ? b.hasMaterialFlag(level, pos, state, random, flag) : super.hasMaterialFlag(level, pos, state, random, flag);
    }
}
