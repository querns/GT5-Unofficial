package gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import gregtech.api.GregTech_API;
import gregtech.api.util.GT_Utility;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.ArrayUtils;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.GT_MetaGenerated_Tool;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.*;
import gregtech.api.util.GT_Recipe;
import gtPlusPlus.api.objects.Logger;
import gtPlusPlus.core.lib.LoadedMods;
import gtPlusPlus.core.util.PollutionUtils;
import gtPlusPlus.core.util.math.MathUtils;
import gtPlusPlus.core.util.reflect.ReflectionUtils;
import gtPlusPlus.xmod.gregtech.api.gui.CONTAINER_MultiMachine;
import gtPlusPlus.xmod.gregtech.api.gui.GUI_MultiMachine;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch_InputBattery;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch_OutputBattery;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import static gtPlusPlus.core.util.array.ArrayUtils.removeNulls;

public abstract class GregtechMeta_MultiBlockBase
extends
GT_MetaTileEntity_MultiBlockBase {

	public GT_Recipe mLastRecipe;

	public ArrayList<GT_MetaTileEntity_Hatch_InputBattery> mChargeHatches = new ArrayList<GT_MetaTileEntity_Hatch_InputBattery>();
	public ArrayList<GT_MetaTileEntity_Hatch_OutputBattery> mDischargeHatches = new ArrayList<GT_MetaTileEntity_Hatch_OutputBattery>();

	public GregtechMeta_MultiBlockBase(final int aID, final String aName,
			final String aNameRegional) {
		super(aID, aName, aNameRegional);
	}

	public GregtechMeta_MultiBlockBase(final String aName) {
		super(aName);
	}

	public static boolean isValidMetaTileEntity(
			final MetaTileEntity aMetaTileEntity) {
		return (aMetaTileEntity.getBaseMetaTileEntity() != null)
				&& (aMetaTileEntity.getBaseMetaTileEntity()
						.getMetaTileEntity() == aMetaTileEntity)
				&& !aMetaTileEntity.getBaseMetaTileEntity().isDead();
	}

	@Override
	public Object getServerGUI(final int aID,
			final InventoryPlayer aPlayerInventory,
			final IGregTechTileEntity aBaseMetaTileEntity) {
		return new CONTAINER_MultiMachine(aPlayerInventory,
				aBaseMetaTileEntity);
	}

	@Override
	public Object getClientGUI(final int aID,
			final InventoryPlayer aPlayerInventory,
			final IGregTechTileEntity aBaseMetaTileEntity) {
		return new GUI_MultiMachine(aPlayerInventory, aBaseMetaTileEntity,
				this.getLocalName(), "MultiblockDisplay.png");
	}

	@Override
	public String[] getInfoData() {
		return new String[]{"Progress:", (this.mProgresstime / 20) + "secs",
				(this.mMaxProgresstime / 20) + "secs", "Efficiency:",
				(this.mEfficiency / 100.0F) + "%", "Problems:",
				"" + (this.getIdealStatus() - this.getRepairStatus())};
	}

	@Override
	public boolean isGivingInformation() {
		return true;
	}

	public int getAmountOfOutputs() {
		return 1;
	}

	@Override
	public boolean isCorrectMachinePart(final ItemStack paramItemStack) {
		return true;
	}

	@Override
	public int getDamageToComponent(final ItemStack paramItemStack) {
		return 0;
	}

	@Override
	public void startSoundLoop(final byte aIndex, final double aX, final double aY, final double aZ) {
		super.startSoundLoop(aIndex, aX, aY, aZ);
		if (aIndex == 1) {
			GT_Utility.doSoundAtClient(getSound(), 10, 1.0F, aX, aY, aZ);
		}
	}

	public void startProcess() {
		if(GT_Utility.isStringValid(getSound())) this.sendLoopStart((byte) 1);
	}

	public String getSound() { return ""; }

	public boolean canBufferOutputs(final GT_Recipe aRecipe) {
		// Count slots available in hatches
		int tEmptySlots = 0;
		for (final GT_MetaTileEntity_Hatch_OutputBus tBus : this.mOutputBusses) {
			if (!isValidMetaTileEntity(tBus)) {
				continue;
			}
			final IInventory tBusInv = tBus.getBaseMetaTileEntity();
			for (int i = 0; i < tBusInv.getSizeInventory(); i++) {
				if (tBus.getStackInSlot(i) == null) {
					tEmptySlots++;
				}
			}
		}

		// TODO: Check if any of the output stacks can stack with the stacks in the hatches?
		// Enough open slots?
		if (tEmptySlots < aRecipe.mOutputs.length) return false;

		// For each output fluid, make sure an output hatch can accept it.
		for (FluidStack tRecipeFluid: aRecipe.mFluidOutputs) {
			boolean tCanBufferFluid = false;
			int tRecipeAmount = tRecipeFluid.amount;
			for (final GT_MetaTileEntity_Hatch_Output tHatch : this.mOutputHatches) {
				FluidStack tHatchFluid = tHatch.getFluid();
				if (tHatchFluid == null) {
					if(tHatch.getCapacity() > tRecipeAmount) {
						tCanBufferFluid = true;
						break;
					}
				}
				else if (tHatchFluid.isFluidEqual(tRecipeFluid) && tHatch.getCapacity() - tHatchFluid.amount > tRecipeAmount) {
					tCanBufferFluid = true;
					break;
				}
			}
			if (!tCanBufferFluid) return false;
		}
		return true;
	}

	public boolean checkRecipeGeneric() {
		return checkRecipeGeneric(1, 100, 0);
	}

	public boolean checkRecipeGeneric(int aMaxParallelRecipes, int aEUPercent, int aSpeedBonusPercent) {
		return checkRecipeGeneric(aMaxParallelRecipes, aEUPercent, aSpeedBonusPercent, 10000);
	}

	public boolean checkRecipeGeneric(int aMaxParallelRecipes, int aEUPercent, int aSpeedBonusPercent, int aOutputChanceRoll) {
		ArrayList<ItemStack> tItems = getStoredInputs();
		ArrayList<FluidStack> tFluids = getStoredFluids();
		ItemStack[] tItemInputs = tItems.toArray(new ItemStack[tItems.size()]);
		FluidStack[] tFluidInputs = tFluids.toArray(new FluidStack[tFluids.size()]);
		return checkRecipeGeneric(tItemInputs, tFluidInputs, aMaxParallelRecipes, aEUPercent, aSpeedBonusPercent, aOutputChanceRoll);
	}

	public boolean checkRecipeGeneric(
			ItemStack[] aItemInputs, FluidStack[] aFluidInputs,
			int aMaxParallelRecipes, int aEUPercent,
			int aSpeedBonusPercent, int aOutputChanceRoll) {


		// Based on the Processing Array. A bit overkill, but very flexible.
		long tVoltage = getMaxInputVoltage();
		byte tTier = (byte) Math.max(1, GT_Utility.getTier(tVoltage));

		int parallelRecipes = 0;

		GT_Recipe tRecipe = this.getRecipeMap().findRecipe(
				getBaseMetaTileEntity(), mLastRecipe, false,
				gregtech.api.enums.GT_Values.V[tTier], aFluidInputs, aItemInputs);

		// Remember last recipe - an optimization for findRecipe()
		this.mLastRecipe = tRecipe;

		if (tRecipe == null) {
			return false;
		}

		if (!this.canBufferOutputs(tRecipe)) {
			return false;
		}

		// Convert speed bonus to duration multiplier
		// e.g. 100% speed bonus = 200% speed = 100%/200% = 50% recipe duration.
		aSpeedBonusPercent = Math.max(-99, aSpeedBonusPercent);
		float tTimeFactor = 100.0f / (100.0f + aSpeedBonusPercent);
		this.mMaxProgresstime = (int)(tRecipe.mDuration * tTimeFactor);

		// EU discount
		float tRecipeEUt = (tRecipe.mEUt * aEUPercent) / 100.0f;
		float tTotalEUt = 0.0f;

		this.mEUt = 0;

		// Count recipes to do in parallel, consuming input items and fluids and considering input voltage limits
		for (; parallelRecipes < aMaxParallelRecipes && tTotalEUt < (tVoltage - tRecipeEUt); parallelRecipes++) {
			if (!tRecipe.isRecipeInputEqual(true, aFluidInputs, aItemInputs)) {
				break;
			}
			tTotalEUt += tRecipeEUt;
		}

		this.mEUt = (int)Math.ceil(tTotalEUt);

		if (parallelRecipes == 0) {
			return false;
		}

		this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
		this.mEfficiencyIncrease = 10000;

		// Overclock
		if (this.mEUt <= 16) {
			this.mEUt = (this.mEUt * (1 << tTier - 1) * (1 << tTier - 1));
			this.mMaxProgresstime = (this.mMaxProgresstime / (1 << tTier - 1));
		} else {
			while (this.mEUt <= gregtech.api.enums.GT_Values.V[(tTier - 1)]) {
				this.mEUt *= 4;
				this.mMaxProgresstime /= 2;
			}
		}

		if (this.mEUt > 0) {
			this.mEUt = (-this.mEUt);
		}

		this.mMaxProgresstime = Math.max(1, this.mMaxProgresstime);

		// Collect fluid outputs
		FluidStack[] tOutputFluids = new FluidStack[tRecipe.mFluidOutputs.length];
		for (int h = 0; h < tRecipe.mFluidOutputs.length; h++) {
			if (tRecipe.getFluidOutput(h) != null) {
				tOutputFluids[h] = tRecipe.getFluidOutput(h).copy();
				tOutputFluids[h].amount *= parallelRecipes;
			}
		}

		// Collect output item types
		ItemStack[] tOutputItems = new ItemStack[tRecipe.mOutputs.length];
		for (int h = 0; h < tRecipe.mOutputs.length; h++) {
			if (tRecipe.getOutput(h) != null) {
				tOutputItems[h] = tRecipe.getOutput(h).copy();
				tOutputItems[h].stackSize = 0;
			}
		}

		// Set output item stack sizes (taking output chance into account)
		for (int f = 0; f < tOutputItems.length; f++) {
			if (tRecipe.mOutputs[f] != null && tOutputItems[f] != null) {
				for (int g = 0; g < parallelRecipes; g++) {
					if (getBaseMetaTileEntity().getRandomNumber(aOutputChanceRoll) < tRecipe.getOutputChance(f))
						tOutputItems[f].stackSize += tRecipe.mOutputs[f].stackSize;
				}
			}
		}

		tOutputItems = removeNulls(tOutputItems);

		// Sanitize item stack size, splitting any stacks greater than max stack size
		List<ItemStack> splitStacks = new ArrayList<ItemStack>();
		for (ItemStack tItem : tOutputItems) {
			while (tItem.getMaxStackSize() < tItem.stackSize) {
				ItemStack tmp = tItem.copy();
				tmp.stackSize = tmp.getMaxStackSize();
				tItem.stackSize = tItem.stackSize - tItem.getMaxStackSize();
				splitStacks.add(tmp);
			}
		}

		if (splitStacks.size() > 0) {
			ItemStack[] tmp = new ItemStack[splitStacks.size()];
			tmp = splitStacks.toArray(tmp);
			tOutputItems = ArrayUtils.addAll(tOutputItems, tmp);
		}

		// Strip empty stacks
		List<ItemStack> tSList = new ArrayList<ItemStack>();
		for (ItemStack tS : tOutputItems) {
			if (tS.stackSize > 0) tSList.add(tS);
		}
		tOutputItems = tSList.toArray(new ItemStack[tSList.size()]);

		// Commit outputs
		this.mOutputItems = tOutputItems;
		this.mOutputFluids = tOutputFluids;
		updateSlots();

		// Play sounds (GT++ addition - GT multiblocks play no sounds)
		startProcess();

		return true;
	}

	public GT_Recipe reduceRecipeTimeByPercentage(final GT_Recipe tRecipe,
			final float percentage) {
		int cloneTime = 0;
		GT_Recipe baseRecipe;
		GT_Recipe cloneRecipe = null;

		baseRecipe = tRecipe.copy();
		if ((cloneRecipe != baseRecipe) || (cloneRecipe == null)) {
			cloneRecipe = baseRecipe.copy();
			Logger.WARNING("Setting Recipe");
		}
		if ((cloneTime != baseRecipe.mDuration) || (cloneTime == 0)) {
			cloneTime = baseRecipe.mDuration;
			Logger.WARNING("Setting Time");
		}

		if (cloneRecipe.mDuration > 0) {
			final int originalTime = cloneRecipe.mDuration;
			final int tempTime = MathUtils.findPercentageOfInt(cloneRecipe.mDuration,
					(100 - percentage));
			cloneRecipe.mDuration = tempTime;
			if (cloneRecipe.mDuration < originalTime) {
				Logger.MACHINE_INFO("Generated recipe with a smaller time. | "
						+ originalTime + " | " + cloneRecipe.mDuration + " |");
				return cloneRecipe;
			} else {
				Logger.MACHINE_INFO("Did not generate recipe with a smaller time. | "
						+ originalTime + " | " + cloneRecipe.mDuration + " |");
				return tRecipe;
			}
		}
		Logger.MACHINE_INFO("Error generating recipe, returning null.");
		return null;

	}

	@Override
	public void onPostTick(final IGregTechTileEntity aBaseMetaTileEntity,
			final long aTick) {
		super.onPostTick(aBaseMetaTileEntity, aTick);
		//this.mChargeHatches.clear();
		//this.mDischargeHatches.clear();
	}

	@Override
	public void explodeMultiblock() {
		MetaTileEntity tTileEntity;
		for (final Iterator<GT_MetaTileEntity_Hatch_InputBattery> localIterator = this.mChargeHatches
				.iterator(); localIterator.hasNext(); tTileEntity
				.getBaseMetaTileEntity()
				.doExplosion(gregtech.api.enums.GT_Values.V[8])) {
			tTileEntity = localIterator.next();
		}
		tTileEntity = null;
		for (final Iterator<GT_MetaTileEntity_Hatch_OutputBattery> localIterator = this.mDischargeHatches
				.iterator(); localIterator.hasNext(); tTileEntity
				.getBaseMetaTileEntity()
				.doExplosion(gregtech.api.enums.GT_Values.V[8])) {
			tTileEntity = localIterator.next();
		}
		super.explodeMultiblock();
	}

	@Override
	public void updateSlots() {
		for (final GT_MetaTileEntity_Hatch_InputBattery tHatch : this.mChargeHatches) {
			if (isValidMetaTileEntity(tHatch)) {
				tHatch.updateSlots();
			}
		}
		for (final GT_MetaTileEntity_Hatch_OutputBattery tHatch : this.mDischargeHatches) {
			if (isValidMetaTileEntity(tHatch)) {
				tHatch.updateSlots();
			}
		}
		super.updateSlots();
	}

	public boolean isToolCreative(ItemStack mStack){
		Materials t1 = GT_MetaGenerated_Tool.getPrimaryMaterial(mStack);
		Materials t2 = GT_MetaGenerated_Tool.getSecondaryMaterial(mStack);
		if (t1 == Materials._NULL && t2 == Materials._NULL){
			return true;
		}
		return false;
	}

	@Override
	public boolean addToMachineList(final IGregTechTileEntity aTileEntity,
			final int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}

		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_InputBattery) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mChargeHatches.add(
					(GT_MetaTileEntity_Hatch_InputBattery) aMetaTileEntity);
		}
		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_OutputBattery) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mDischargeHatches.add(
					(GT_MetaTileEntity_Hatch_OutputBattery) aMetaTileEntity);
		}
		if (LoadedMods.TecTech){
			if (isThisHatchMultiDynamo()) {
				updateTexture(aTileEntity, aBaseCasingIndex);
				return this.mMultiDynamoHatches.add(
						(GT_MetaTileEntity_Hatch) aMetaTileEntity);
			}

		}
		return super.addToMachineList(aTileEntity, aBaseCasingIndex);
	}

	public boolean addChargeableToMachineList(final IGregTechTileEntity aTileEntity,
			final int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}
		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_InputBattery) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mChargeHatches.add(
					(GT_MetaTileEntity_Hatch_InputBattery) aMetaTileEntity);
		}
		return false;
	}

	public boolean addDischargeableInputToMachineList(
			final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}
		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_OutputBattery) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mDischargeHatches.add(
					(GT_MetaTileEntity_Hatch_OutputBattery) aMetaTileEntity);
		}
		return false;
	}


	public boolean addFluidInputToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}
		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_Input) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			((GT_MetaTileEntity_Hatch_Input) aMetaTileEntity).mRecipeMap = this.getRecipeMap();
			return this.mInputHatches.add((GT_MetaTileEntity_Hatch_Input) aMetaTileEntity);
		}
		return false;
	}

	public boolean addFluidOutputToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}
		if (aMetaTileEntity instanceof GT_MetaTileEntity_Hatch_Output) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mOutputHatches.add((GT_MetaTileEntity_Hatch_Output) aMetaTileEntity);
		}
		return false;
	}

	/**
	 * Enable Texture Casing Support if found in GT 5.09
	 */

	public boolean updateTexture(final IGregTechTileEntity aTileEntity, int aCasingID){
		try {
			Method mProper = Class.forName("gregtech.api.metatileentity.implementations.GT_MetaTileEntity_Hatch").getDeclaredMethod("updateTexture", int.class);
			if (mProper != null){
				if (aTileEntity instanceof GT_MetaTileEntity_Hatch){				
					mProper.setAccessible(true);
					mProper.invoke(this, aCasingID);
					return true;
				}					
			}
			else {
				return false;
			}
		}
		catch (NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {}		
		return false;
	}









	/**
	 * TecTech Support
	 */


	/**
	 * This is the array Used to Store the Tectech Multi-Amp hatches.
	 */

	public ArrayList<GT_MetaTileEntity_Hatch> mMultiDynamoHatches = new ArrayList();	

	/**
	 * TecTech Multi-Amp Dynamo Support
	 * @param aTileEntity - The Dynamo Hatch
	 * @param aBaseCasingIndex - Casing Texture
	 * @return
	 */

	public boolean addMultiAmpDynamoToMachineList(final IGregTechTileEntity aTileEntity, final int aBaseCasingIndex){
		//GT_MetaTileEntity_Hatch_DynamoMulti
		if (aTileEntity == null) {
			return false;
		}
		final IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
		if (aMetaTileEntity == null) {
			return false;
		}
		if (isThisHatchMultiDynamo()) {
			updateTexture(aTileEntity, aBaseCasingIndex);
			return this.mMultiDynamoHatches.add((GT_MetaTileEntity_Hatch) aMetaTileEntity);
		}
		return false;
	}

	public boolean isThisHatchMultiDynamo(){
		Class mDynamoClass;
		try {
			mDynamoClass = Class.forName("com.github.technus.tectech.thing.metaTileEntity.hatch.GT_MetaTileEntity_Hatch_DynamoMulti");
			if (mDynamoClass != null){
				if (mDynamoClass.isInstance(this)){
					return true;
				}
			}
		}
		catch (ClassNotFoundException e) {}
		return false;
	}

	@Override
	public boolean addDynamoToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
		if (LoadedMods.TecTech){
			if (isThisHatchMultiDynamo()) {
				addMultiAmpDynamoToMachineList(aTileEntity, aBaseCasingIndex);
			}

		}
		return super.addDynamoToMachineList(aTileEntity, aBaseCasingIndex);
	}


	/**
	 * Pollution Management
	 */

	public int getPollutionPerTick(ItemStack arg0) {
		return 0;
	}

	public boolean polluteEnvironment(int aPollutionLevel) {
		if (CORE.MAIN_GREGTECH_5U_EXPERIMENTAL_FORK){
			try {
				Integer mPollution = 0;
				Field f = ReflectionUtils.getField(this.getClass(), "mPollution");
				if (f != null){
					try {
						mPollution = (Integer) f.get(this);
					}
					catch (IllegalArgumentException | IllegalAccessException e) {}
				}
				if (f != null){
					try {
						if (mPollution != null){
							//Reflectively set the pollution back to the TE
							int temp = (mPollution += aPollutionLevel);
							f.set(this, temp);
							Logger.REFLECTION("Set pollution to "+temp+", it was "+mPollution+" before.");

							//Iterate Mufflers
							for (final GT_MetaTileEntity_Hatch_Muffler tHatch : this.mMufflerHatches) {
								if (isValidMetaTileEntity(tHatch)) {
									if (mPollution < 10000) {
										break;
									}
									if (!polluteEnvironmentHatch(tHatch)) {
										continue;
									}
									mPollution -= 10000;
								}
							}
							return mPollution < 10000;

						}
					}
					catch (IllegalArgumentException | IllegalAccessException e) {}
				}
			}
			catch (Throwable t){}
		}
		return false;
	}

	public boolean polluteEnvironmentHatch(GT_MetaTileEntity_Hatch_Muffler tHatch) {
		if (tHatch.getBaseMetaTileEntity().getAirAtSide(tHatch.getBaseMetaTileEntity().getFrontFacing())) {
			Logger.REFLECTION("doing pollution");
			PollutionUtils.addPollution(tHatch.getBaseMetaTileEntity(), calculatePollutionReduction(tHatch, 10000));
			return true;
		} else {
			return false;
		}
	}

	public int calculatePollutionReduction(GT_MetaTileEntity_Hatch_Muffler tHatch, int aPollution) {
		return (int) ((double) aPollution * Math.pow(0.7D, (double) (tHatch.mTier - 1)));
	}

}
