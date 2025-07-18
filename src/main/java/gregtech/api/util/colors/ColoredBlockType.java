package gregtech.api.util.colors;

import appeng.block.networking.BlockCableBus;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import java.util.Set;

public enum ColoredBlockType {
    VANILLA {
        private static final Set<Block> ALLOWED_VANILLA_BLOCKS = ImmutableSet.of(
            Blocks.glass,
            Blocks.glass_pane,
            Blocks.stained_glass,
            Blocks.stained_glass_pane,
            Blocks.carpet,
            Blocks.hardened_clay,
            Blocks.stained_hardened_clay);

        @Override
        boolean isValidBlock(final Block block, final TileEntity tileEntity) {
            return ALLOWED_VANILLA_BLOCKS.contains(block);
        }

        @Override
        boolean isValidBlock(final ItemStack itemStack) {
            final Item item = itemStack.getItem();
            return item instanceof final ItemBlock itemBlock && isValidBlock(itemBlock.field_150939_a, null);
        }
    },
    GREGTECH {
        @Override
        boolean isValidBlock(final Block block, final TileEntity tileEntity) {
            return false;
        }
    },
    AE2_CABLE {
        @Override
        boolean isValidBlock(final Block block, final TileEntity tileEntity) {
            return block instanceof BlockCableBus;
        }
    },
    AE2_BLOCK {
        @Override
        boolean isValidBlock(final Block block, final TileEntity tileEntity) {
            return false;
        }
    };

    boolean isValidBlock(final Block block, final TileEntity tileEntity) {
        return false;
    }

    boolean isValidBlock(final ItemStack itemStack) {
        return false;
    }
}
