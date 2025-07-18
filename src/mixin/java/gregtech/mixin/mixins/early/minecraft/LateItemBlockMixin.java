package gregtech.mixin.mixins.early.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import gregtech.api.items.MetaBaseItem;
import gregtech.crossmod.backhand.Backhand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(ItemBlock.class)
abstract public class LateItemBlockMixin {
    @WrapMethod(method = "placeBlockAt", remap = false)
    private boolean gt5$blockPlaceOffhandInterrupt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata, Operation<Boolean> original) {
        if (player != null) {
            final ItemStack offhand = Backhand.getOffhandItem(player);

            if (offhand != null && offhand.getItem() instanceof final MetaBaseItem mbItem) {
                final ItemStack itemStackToMutate = stack.copy();
                if (mbItem.forEachBehavior(offhand, behavior -> behavior.onBlockPlacedWhileToolInOffhand(mbItem, offhand, itemStackToMutate, player))) {
                    return original.call(itemStackToMutate, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
                }
            }
        }
        return original.call(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
    }
}
