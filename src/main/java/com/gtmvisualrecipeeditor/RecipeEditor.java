package com.gtmvisualrecipeeditor;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.IEditableUI;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.api.gui.widget.PhantomSlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.common.collect.Tables;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import dev.latvian.mods.kubejs.util.ConsoleJS;

import java.util.*;

public class RecipeEditor implements IItemUIFactory, IFancyUIProvider {

    public static final RecipeEditor INSTANCE = new RecipeEditor();

    private static final Map<MetaMachine, DummyMachine> CACHE = new HashMap<>();
    private static final Map<BlockPos, DummyMachine> POS_CACHE = new HashMap<>();

    private DummyMachine machine;

    @Override
    public InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        MetaMachine metaMachine = MetaMachine.getMachine(context.getLevel(), context.getClickedPos());
        if (metaMachine instanceof IRecipeLogicMachine) {
            machine = CACHE.computeIfAbsent(metaMachine, DummyMachine::createDummyMachine);
            machine.isGT = true;
        } else {
            Block block = context.getLevel().getBlockState(context.getClickedPos()).getBlock();
            if (block instanceof CraftingTableBlock) {
                machine = POS_CACHE.computeIfAbsent(context.getClickedPos(), p -> DummyMachine.createDummyMachine(BlockEntityType.CHEST, p, GTMachines.ASSEMBLER[1].defaultBlockState(), GTRecipeTypes.ASSEMBLER_RECIPES));
            } else if (block instanceof FurnaceBlock) {
                machine = POS_CACHE.computeIfAbsent(context.getClickedPos(), p -> DummyMachine.createDummyMachine(BlockEntityType.CHEST, p, GTMachines.ELECTRIC_FURNACE[1].defaultBlockState(), GTRecipeTypes.FURNACE_RECIPES));
            } else {
                return InteractionResult.PASS;
            }
        }
        IItemUIFactory.super.use(context.getItemInHand().getItem(), context.getLevel(), context.getPlayer(), context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        ItemStack heldItem = player.getItemInHand(usedHand);
        return InteractionResultHolder.success(heldItem);
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        return new ModularUI(176, 166, holder, entityPlayer).widget(new FancyMachineUIWidget(this, 176, 166));
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        GTRecipeTypeUI recipeUI = new GTRecipeTypeUI(machine.recipeType) {

            @Override
            protected WidgetGroup addInventorySlotGroup(boolean isOutputs, boolean isSteam, boolean isHighPressure) {
                int maxCount = 0;
                int totalR = 0;
                TreeMap<RecipeCapability<?>, Integer> map = new TreeMap<>(RecipeCapability.COMPARATOR);
                if (isOutputs) {
                    for (var value : machine.recipeType.maxOutputs.entrySet()) {
                        if (value.getKey().doRenderSlot) {
                            int val = value.getValue();
                            if (val > maxCount) {
                                maxCount = Math.min(val, 3);
                            }
                            totalR += (val + 2) / 3;
                            map.put(value.getKey(), val);
                        }
                    }
                } else {
                    for (var value : machine.recipeType.maxInputs.entrySet()) {
                        if (value.getKey().doRenderSlot) {
                            int val = value.getValue();
                            if (val > maxCount) {
                                maxCount = Math.min(val, 3);
                            }
                            totalR += (val + 2) / 3;
                            map.put(value.getKey(), val);
                        }
                    }
                }
                WidgetGroup group = new WidgetGroup(0, 0, maxCount * 18 + 8, totalR * 18 + 8);
                int index = 0;
                for (var entry : map.entrySet()) {
                    RecipeCapability<?> cap = entry.getKey();
                    boolean i = cap instanceof ItemRecipeCapability;
                    if (i || machine.isGT) {
                        if (cap.getWidgetClass() == null) {
                            continue;
                        }
                        int capCount = entry.getValue();
                        for (int slotIndex = 0; slotIndex < capCount; slotIndex++) {
                            int finalSlotIndex = slotIndex;
                            var slot = i ? new MyPhantomSlotWidget() : new PhantomFluidWidget(null, 0, 0, 0, 18, 18, isOutputs ? () -> machine.exportFluids.getFluidInTank(finalSlotIndex) : () -> machine.importFluids.getFluidInTank(finalSlotIndex), isOutputs ? f -> machine.exportFluids.setFluidInTank(finalSlotIndex, f) : f -> machine.importFluids.setFluidInTank(finalSlotIndex, f));
                            slot.setSelfPosition(new Position((index % 3) * 18 + 4, (index / 3) * 18 + 4));
                            slot.setBackground(getOverlaysForSlot(isOutputs, cap, slotIndex == capCount - 1, isSteam, isHighPressure));
                            slot.setId(cap.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
                            group.addWidget(slot);
                            index++;
                        }
                        index += (3 - (index % 3)) % 3;
                    }
                }
                return group;
            }

            @Override
            public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(boolean isSteam, boolean isHighPressure) {
                return new IEditableUI.Normal<>(() -> {
                    var inputs = addInventorySlotGroup(false, isSteam, isHighPressure);
                    var outputs = addInventorySlotGroup(true, isSteam, isHighPressure);
                    var maxWidth = Math.max(inputs.getSize().width, outputs.getSize().width);
                    var group = new WidgetGroup(0, 0, 2 * maxWidth + 40, Math.max(inputs.getSize().height, outputs.getSize().height));
                    var size = group.getSize();
                    inputs.addSelfPosition((maxWidth - inputs.getSize().width) / 2, (size.height - inputs.getSize().height) / 2);
                    outputs.addSelfPosition(maxWidth + 40 + (maxWidth - outputs.getSize().width) / 2, (size.height - outputs.getSize().height) / 2);
                    group.addWidget(inputs);
                    group.addWidget(outputs);
                    var progressWidget = new ProgressWidget(ProgressWidget.JEIProgress, maxWidth + 10, size.height / 2 - 10, 20, 20, getProgressBarTexture());
                    progressWidget.setId("progress");
                    group.addWidget(progressWidget);
                    progressWidget.setProgressTexture(getProgressBarTexture());
                    return group;
                }, (template, recipeHolder) -> {
                    for (var capabilityEntry : recipeHolder.storages().rowMap().entrySet()) {
                        IO io = capabilityEntry.getKey();
                        for (var storagesEntry : capabilityEntry.getValue().entrySet()) {
                            RecipeCapability<?> cap = storagesEntry.getKey();
                            Object storage = storagesEntry.getValue();
                            Class<? extends Widget> widgetClass = cap.getWidgetClass();
                            if (widgetClass != null) {
                                WidgetUtils.widgetByIdForEach(template, "^%s_[0-9]+$".formatted(cap.slotName(io)), widgetClass, widget -> {
                                    var index = WidgetUtils.widgetIdIndex(widget);
                                    cap.applyWidgetInfo(widget, index, false, io, recipeHolder, machine.recipeType, null, null, storage, 0, 0);
                                    if (widget instanceof TankWidget tankWidget) {
                                        tankWidget.setAllowClickDrained(true).setAllowClickFilled(true);
                                    } else if (widget instanceof SlotWidget slotWidget) slotWidget.setCanTakeItems(true).setCanPutItems(true);
                                });
                            }
                        }
                    }
                });
            }
        };
        var editableUI = new EditableMachineUI("simple", GTMVisualRecipeEditor.id("re"), () -> {
            WidgetGroup template = recipeUI.createEditableUITemplate(false, false).createDefault();

            WidgetGroup group = new WidgetGroup(0, 0, template.getSize().width, Math.max(template.getSize().height, 78));
            template.setSelfPosition(new Position(0, (group.getSize().height - template.getSize().height) / 2));
            group.addWidget(template);
            return group;
        }, (template, m) -> {
            var storages = Tables.newCustomTable(new EnumMap<>(IO.class), LinkedHashMap<RecipeCapability<?>, Object>::new);
            storages.put(IO.IN, ItemRecipeCapability.CAP, machine.importItems);
            storages.put(IO.OUT, ItemRecipeCapability.CAP, machine.exportItems);
            if (machine.isGT) {
                storages.put(IO.IN, FluidRecipeCapability.CAP, machine.importFluids);
                storages.put(IO.OUT, FluidRecipeCapability.CAP, machine.exportFluids);
            }
            recipeUI.createEditableUITemplate(false, false).setupUI(template, new GTRecipeTypeUI.RecipeHolder(() -> 0, storages, new CompoundTag(), Collections.emptyList(), false, false));
        });
        var template = editableUI.createCustomUI();
        if (template == null) {
            template = editableUI.createDefault();
        }
        editableUI.setupUI(template, machine);
        int x = template.getSize().width - getXOffset(machine.recipeType) - 18;
        int y = template.getSize().height - 10;
        if (machine.isGT) {
            template.addWidget(new AETextInputButtonWidget(x - 48, y - 70, 76, 12)
                    .setText(String.valueOf(machine.id))
                    .setOnConfirm(machine::setId)
                    .setButtonTooltips(Component.literal("ID")));
            template.addWidget(new AETextInputButtonWidget(x - 36, y - 55, 64, 12)
                    .setText(String.valueOf(machine.circuit))
                    .setOnConfirm(machine::setCircuit)
                    .setButtonTooltips(Component.literal("Circuit")));
            template.addWidget(new AETextInputButtonWidget(x - 36, y - 40, 64, 12)
                    .setText(String.valueOf(machine.eut))
                    .setOnConfirm(machine::setEUt)
                    .setButtonTooltips(Component.literal("EUt")));
            template.addWidget(new AETextInputButtonWidget(x - 36, y - 25, 64, 12)
                    .setText(String.valueOf(machine.duration))
                    .setOnConfirm(machine::setDuration)
                    .setButtonTooltips(Component.literal("Duration")));
        }
        template.addWidget(new ButtonWidget(x, y, 16, 16, new GuiTextureGroup(GuiTextures.BUTTON, new TextTexture("X")), cd -> {
            if (cd.isRemote) return;
            StringBuilder stringBuilder = new StringBuilder();
            if (machine.isGT) {
                String id = machine.id;
                if (id.isEmpty()) {
                    for (int i = 0; i < machine.exportItems.getSlots(); i++) {
                        if (!id.isEmpty()) break;
                        ItemStack stack = machine.exportItems.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                    }
                    for (int i = 0; i < machine.exportFluids.getSize(); i++) {
                        if (!id.isEmpty()) break;
                        FluidStack stack = machine.exportFluids.getFluidInTank(i);
                        if (stack.isEmpty()) continue;
                        id = BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath();
                    }
                }
                stringBuilder.append("\nevent.recipes.gtceu.").append(machine.recipeType.registryName.getPath()).append("(\"").append(id).append("\")\n");
                for (int i = 0; i < machine.importItems.getSlots(); i++) {
                    ItemStack stack = machine.importItems.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    stringBuilder.append(".itemInputs(\"");
                    if (stack.getCount() > 1) {
                        stringBuilder.append(stack.getCount()).append("x ");
                    }
                    stringBuilder.append(getItemId(stack.getItem())).append("\")\n");
                }
                for (int i = 0; i < machine.exportItems.getSlots(); i++) {
                    ItemStack stack = machine.exportItems.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    stringBuilder.append(".itemOutputs(\"");
                    if (stack.getCount() > 1) {
                        stringBuilder.append(stack.getCount()).append("x ");
                    }
                    stringBuilder.append(getItemId(stack.getItem())).append("\")\n");
                }
                for (int i = 0; i < machine.importFluids.getSize(); i++) {
                    FluidStack stack = machine.importFluids.getFluidInTank(i);
                    if (stack.isEmpty()) continue;
                    stringBuilder.append(".inputFluids(\"").append(ForgeRegistries.FLUIDS.getKey(stack.getFluid())).append(" ").append(stack.getAmount()).append("\")\n");
                }
                for (int i = 0; i < machine.exportFluids.getSize(); i++) {
                    FluidStack stack = machine.exportFluids.getFluidInTank(i);
                    if (stack.isEmpty()) continue;
                    stringBuilder.append(".outputFluids(\"").append(ForgeRegistries.FLUIDS.getKey(stack.getFluid())).append(" ").append(stack.getAmount()).append("\")\n");
                }
                if (machine.circuit > 0) {
                    stringBuilder.append(".circuit(").append(machine.circuit).append(")\n");
                }
                if (machine.eut != 0) {
                    stringBuilder.append(".EUt(").append(machine.eut).append(")\n");
                }
                stringBuilder.append(".duration(").append(machine.duration).append(")\n");
            } else {
                if (machine.recipeType == GTRecipeTypes.ASSEMBLER_RECIPES) {
                    stringBuilder.append("\nevent.shaped(\"").append(getItemId(machine.exportItems.getStackInSlot(0).getItem())).append("\", [\n    \"");
                    char c = 'A';
                    Map<String, Character> map = new LinkedHashMap<>();
                    for (int i = 0, j = 0; i < machine.importItems.getSlots(); i++, j++) {
                        ItemStack stack = machine.importItems.getStackInSlot(i);
                        String id = getItemId(stack.getItem());
                        if (!stack.isEmpty() && !map.containsKey(id)) {
                            map.put(id, c);
                            c++;
                        }
                        char d = stack.isEmpty() ? ' ' : map.get(id);
                        if (j > 2) {
                            stringBuilder.append("\",\n    \"").append(d);
                            j = 0;
                        } else {
                            stringBuilder.append(d);
                        }
                    }
                    stringBuilder.append("\"\n], {");
                    map.forEach((k, v) -> stringBuilder.append("\n    ").append(v).append(":").append(" \"").append(k).append("\","));
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("\n})");
                } else if (machine.recipeType == GTRecipeTypes.FURNACE_RECIPES) {
                    stringBuilder.append("\nevent.smelting(\"").append(getItemId(machine.exportItems.getStackInSlot(0).getItem())).append("\", \"").append(getItemId(machine.importItems.getStackInSlot(0).getItem())).append("\")");
                }
            }
            ConsoleJS.SERVER.info(stringBuilder.toString());
        }).appendHoverTooltips("Export"));
        return template;
    }

    private static String getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static int getXOffset(GTRecipeType recipe) {
        if (recipe.getRecipeUI().getOriginalWidth() != recipe.getRecipeUI().getJEISize().width) {
            return (recipe.getRecipeUI().getJEISize().width - recipe.getRecipeUI().getOriginalWidth()) / 2;
        }
        return 0;
    }

    @Override
    public void attachSideTabs(TabsWidget sideTabs) {
        sideTabs.setMainTab(this);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return new ItemStackTexture(GTItems.ROBOT_ARM_LV.get());
    }

    @Override
    public Component getTitle() {
        return Component.translatable("Recipe Editor");
    }

    private static class MyPhantomSlotWidget extends PhantomSlotWidget {

        @Override
        public ItemStack slotClickPhantom(Slot slot, int mouseButton, ClickType clickTypeIn, ItemStack stackHeld) {
            if (isRemote()) return slot.getItem();
            return super.slotClickPhantom(slot, mouseButton, clickTypeIn, stackHeld);
        }
    }
}
