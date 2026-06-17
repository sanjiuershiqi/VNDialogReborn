package top.yourzi.dialog.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import top.yourzi.dialog.Dialog;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Dialog.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        ExistingFileHelper helper = event.getExistingFileHelper();
        DataGenerator generator = event.getGenerator();
        CompletableFuture<HolderLookup.Provider> provider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new DialogGenerator(output));
    }
}
