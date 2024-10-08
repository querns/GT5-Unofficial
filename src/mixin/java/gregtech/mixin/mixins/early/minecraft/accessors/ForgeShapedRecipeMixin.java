package gregtech.mixin.mixins.early.minecraft.accessors;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import gregtech.mixin.interfaces.accessors.IRecipeMutableAccess;
import gregtech.mixin.interfaces.accessors.ShapedOreRecipeAccessor;

@Mixin(value = ShapedOreRecipe.class, remap = false)
public class ForgeShapedRecipeMixin implements IRecipeMutableAccess, ShapedOreRecipeAccessor {

    @Shadow
    private ItemStack output;

    @Shadow
    private Object[] input;

    @Shadow
    private int width;

    @Shadow
    private int height;

    @Override
    public ItemStack gt5u$getRecipeOutputItem() {
        return this.output;
    }

    @Override
    public void gt5u$setRecipeOutputItem(ItemStack newItem) {
        this.output = newItem;
    }

    @Override
    public Object gt5u$getRecipeInputs() {
        return this.input;
    }

    @Override
    public int gt5u$getWidth() {
        return this.width;
    }

    @Override
    public int gt5u$getHeight() {
        return this.height;
    }

}
