package com.gtmvisualrecipeeditor;

import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GTMVisualRecipeEditor.MOD_ID)
public class GTMVisualRecipeEditor {

    public static final String MOD_ID = "gtmvisualrecipeeditor";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> RECIPE_EDITOR = ITEMS.register("recipe_editor", () -> {
        ComponentItem item = ComponentItem.create(new Item.Properties().stacksTo(1).fireResistant());
        item.attachComponents(RecipeEditor.INSTANCE);
        return item;
    });

    public static ResourceLocation id(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    public GTMVisualRecipeEditor() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == GTCreativeModeTabs.ITEM.getKey()) {
            event.accept(RECIPE_EDITOR);
        }
    }
}
