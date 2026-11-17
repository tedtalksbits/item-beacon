// Dev-only (ci/mixin-audit/run.sh): never part of the mod.
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Dev-only: loads the mod's classes and their Minecraft and Fabric API parents (applying every mixin and interface
 * injection on the way, Fabric API's included; a mixin that can't apply fails here) before the game starts, then exits.
 * The mod has no mixins of its own yet; when it gets some, add their targets here.
 */
public class MixinAudit implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        int code = 0;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String[] targets = {
            "net.minecraft.client.renderer.block.dispatch.BlockStateModel",
            "net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel",
            "net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.GuiGraphicsExtractor",
            "dev.whizzle.itembeacon.client.ItemBeaconClient", "dev.whizzle.itembeacon.client.BeaconCache",
            "dev.whizzle.itembeacon.client.SummaryAssembler", "dev.whizzle.itembeacon.client.Waypoints",
            "dev.whizzle.itembeacon.client.Format", "dev.whizzle.itembeacon.client.CreativeTab",
            "dev.whizzle.itembeacon.client.render.BeaconModels", "dev.whizzle.itembeacon.client.render.BeaconBlockModel",
            "dev.whizzle.itembeacon.client.ui.Items", "dev.whizzle.itembeacon.client.ui.PickerScreen",
            "dev.whizzle.itembeacon.client.net.Net", "dev.whizzle.itembeacon.client.net.RawPayload"};
        for (String t : targets) {
            try { Class.forName(t, false, cl); System.out.println("AUDIT loaded " + t); }   // loading applies the mixins; initialising would need the game's registries
            catch (Throwable e) { code = 1; System.out.println("AUDIT FAILED " + t + ": " + e); e.printStackTrace(System.out); }
        }
        try {
            Class<?> model = Class.forName("net.minecraft.client.renderer.block.dispatch.BlockStateModel", false, cl);
            Class<?> fabric = Class.forName("net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel", false, cl);
            System.out.println("AUDIT BlockStateModel has Fabric's emitQuads: " + fabric.isAssignableFrom(model));
            if (!fabric.isAssignableFrom(model)) code = 1;
            Class<?> ours = Class.forName("dev.whizzle.itembeacon.client.render.BeaconBlockModel", false, cl);
            for (String m : new String[]{"emitQuads", "particleMaterial", "createGeometryKey", "materialFlags", "hasMaterialFlag"}) {
                boolean found = java.util.Arrays.stream(ours.getDeclaredMethods()).anyMatch(x -> x.getName().equals(m));
                System.out.println("AUDIT BeaconBlockModel overrides " + m + ": " + found);
                if (!found) code = 1;
            }
            // Methods the screen overrides must still be the game's (a rename would silently stop them being called).
            Class<?> screen = Class.forName("dev.whizzle.itembeacon.client.ui.PickerScreen", false, cl);
            Class<?> base = Class.forName("net.minecraft.client.gui.screens.Screen", false, cl);
            for (var m : screen.getDeclaredMethods()) {
                if (!java.util.Set.of("extractBackground", "extractRenderState", "mouseClicked", "mouseScrolled", "isPauseScreen", "init").contains(m.getName())) continue;
                boolean overrides = false;
                for (Class<?> c = base; c != null && !overrides; c = c.getSuperclass())
                    try { c.getDeclaredMethod(m.getName(), m.getParameterTypes()); overrides = true; } catch (NoSuchMethodException ignored) { }
                for (Class<?> i : allInterfaces(base)) if (!overrides)
                    try { i.getDeclaredMethod(m.getName(), m.getParameterTypes()); overrides = true; } catch (NoSuchMethodException ignored) { }
                System.out.println("AUDIT PickerScreen." + m.getName() + " overrides the game's: " + overrides);
                if (!overrides) code = 1;
            }
        } catch (Throwable e) { code = 1; e.printStackTrace(System.out); }
        try { MixinEnvironment.getCurrentEnvironment().audit(); System.out.println("AUDIT mixin audit done"); }
        catch (Throwable e) { code = 1; System.out.println("AUDIT mixin audit FAILED: " + e); e.printStackTrace(System.out); }
        System.out.println("AUDIT RESULT " + (code == 0 ? "OK" : "FAILED"));
        Runtime.getRuntime().halt(code);
    }

    static java.util.Set<Class<?>> allInterfaces(Class<?> c) {
        java.util.Set<Class<?>> out = new java.util.LinkedHashSet<>();
        java.util.Deque<Class<?>> todo = new java.util.ArrayDeque<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) todo.addAll(java.util.List.of(k.getInterfaces()));
        while (!todo.isEmpty()) { Class<?> i = todo.pop(); if (out.add(i)) todo.addAll(java.util.List.of(i.getInterfaces())); }
        return out;
    }
}
