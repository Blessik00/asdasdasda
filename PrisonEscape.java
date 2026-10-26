package pe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class PrisonEscape implements ModInitializer {
    static BlockPos center;
    static int startTick;
    static final int R = 20;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((d, r, e) -> d.register(
            CommandManager.literal("prison").executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                build(c.getSource().getWorld(), p);
                return 1;
            })));

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (center == null || s.getTicks() % 20 != 0) return;
            for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                if (!p.getCommandTags().contains("prisoner")) continue;
                if (Math.abs(p.getX() - center.getX()) > R + 2 || Math.abs(p.getZ() - center.getZ()) > R + 2) {
                    p.removeCommandTag("prisoner");
                    int sec = (s.getTicks() - startTick) / 20;
                    p.sendMessage(Text.literal("§a§lТЫ СБЕЖАЛ! Время побега: " + sec + " сек."), false);
                }
            }
        });
    }

    static void set(ServerWorld w, BlockPos o, int x, int y, int z, Block b) {
        w.setBlockState(o.add(x, y, z), b.getDefaultState());
    }

    static void cell(ServerWorld w, BlockPos o, int cx, int cz, int doorDir) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
            for (int y = 0; y <= 3; y++) set(w, o, cx + x, y, cz + z, edge ? Blocks.STONE_BRICKS : Blocks.AIR);
            set(w, o, cx + x, 4, cz + z, Blocks.STONE_BRICKS);
        }
        set(w, o, cx + 3 * doorDir, 0, cz, Blocks.IRON_BARS);
        set(w, o, cx + 3 * doorDir, 1, cz, Blocks.IRON_BARS);
        set(w, o, cx - 2 * doorDir, 0, cz - 2, Blocks.RED_BED);
        set(w, o, cx, 3, cz, Blocks.LANTERN);
    }

    static void guard(ServerWorld w, BlockPos o, int x, int z) {
        var m = EntityType.VINDICATOR.spawn(w, o.add(x, 0, z), SpawnReason.COMMAND);
        if (m instanceof MobEntity mob) {
            mob.setCustomName(Text.literal("§cОхранник"));
            mob.setPersistent();
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        }
    }

    static void build(ServerWorld w, ServerPlayerEntity p) {
        BlockPos o = p.getBlockPos();
        center = o;
        startTick = w.getServer().getTicks();

        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            boolean wall = Math.abs(x) == R || Math.abs(z) == R;
            set(w, o, x, -1, z, Blocks.STONE_BRICKS);
            for (int y = 0; y <= 9; y++) set(w, o, x, y, z, wall ? Blocks.STONE_BRICKS : Blocks.AIR);
            if (wall && (x + z) % 5 == 0) set(w, o, x, 9, z, Blocks.LANTERN);
        }
        // 4 камеры; игрок в первой
        cell(w, o, -9, -9, 1);
        cell(w, o, 9, -9, -1);
        cell(w, o, -9, 9, 1);
        cell(w, o, 9, 9, -1);

        // столовая-склад: сундук с инструментами
        set(w, o, 0, 0, 14, Blocks.CHEST);
        if (w.getBlockEntity(o.add(0, 0, 14)) instanceof ChestBlockEntity ch) {
            ItemStack key = new ItemStack(Items.IRON_PICKAXE);
            key.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Заточка"));
            ch.setStack(0, key);
            ch.setStack(1, new ItemStack(Items.LADDER, 16));
            ch.setStack(2, new ItemStack(Items.COBBLESTONE, 32));
        }
        set(w, o, -1, 0, 14, Blocks.CRAFTING_TABLE);
        set(w, o, 1, 0, 14, Blocks.FURNACE);

        int[][] g = {{0, 0}, {0, -12}, {12, 0}, {-12, 0}, {0, 12}, {14, 14}};
        for (int[] q : g) guard(w, o, q[0], q[1]);

        p.requestTeleport(o.getX() - 9 + 0.5, o.getY(), o.getZ() - 9 + 0.5);
        p.addCommandTag("prisoner");
        ItemStack spoon = new ItemStack(Items.WOODEN_PICKAXE);
        spoon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Ложка"));
        p.giveItemStack(spoon);
        p.sendMessage(Text.literal("§6Ты в камере. Прокопай стену ложкой, найди сундук с «Заточкой» во дворе и выберись за внешнюю стену. Охрана злая!"), false);
    }
}
