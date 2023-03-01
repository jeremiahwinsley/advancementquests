package net.permutated.advancementquests;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbquests.FTBQuests;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.latvian.mods.itemfilters.api.ItemFiltersItems;
import dev.latvian.mods.itemfilters.item.ANDFilterItem;
import dev.latvian.mods.itemfilters.item.InventoryFilterItem;
import dev.latvian.mods.itemfilters.item.ItemInventory;
import dev.latvian.mods.itemfilters.item.ORFilterItem;
import dev.latvian.mods.itemfilters.item.StringValueData;
import dev.latvian.mods.itemfilters.item.StringValueFilterItem;
import dev.latvian.mods.itemfilters.item.TagFilterItem;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ConversionTask implements Runnable {

    private static final Logger logger = LogUtils.getLogger();

    private final Collection<Advancement> advancements;

    public ConversionTask(Collection<Advancement> advancements) {
        this.advancements = List.copyOf(advancements);
    }

    @Override
    public void run() {
        logger.info("Starting quest generation");

        QuestFile questFile = FTBQuests.PROXY.getQuestFile(false);
        List<Chapter> chapters = questFile.defaultChapterGroup.chapters;

        Map<String, Chapter> generated = new HashMap<>();
        ItemStack fallback = new ItemStack(Items.BARRIER);

        Function<Component, Optional<String>> visitor = component -> component.visit(Optional::of);

        for (Advancement advancement : advancements) {
            if (advancement.getDisplay() == null) {
                continue;
            }

            String namespace = advancement.getId().getNamespace();
            Chapter chapter = generated.computeIfAbsent(namespace, filename -> {
                Chapter c = new Chapter(questFile, questFile.defaultChapterGroup);
                c.id = ServerQuestFile.INSTANCE.newID();
                c.filename = "aq_" + filename;
                c.title = filename;
                return c;
            });

            Quest quest = new Quest(chapter);
            quest.id = ServerQuestFile.INSTANCE.newID();

            Optional<DisplayInfo> display = Optional.ofNullable(advancement.getDisplay());
            quest.icon = display.map(DisplayInfo::getIcon).orElse(fallback);
            quest.title = display.map(DisplayInfo::getTitle).flatMap(visitor).orElse("MISSING TITLE");
            quest.description.add(display.map(DisplayInfo::getDescription).flatMap(visitor).orElse("MISSING DESCRIPTION"));

            for (Criterion c : advancement.getCriteria().values()) {
                if (c.getTrigger() instanceof InventoryChangeTrigger.TriggerInstance trigger) {
                    ItemTask task = new ItemTask(quest);
                    task.id = ServerQuestFile.INSTANCE.newID();

                    if (trigger.predicates.length > 1) {
                        ItemStack filter = new ItemStack(ItemFiltersItems.AND.get());
                        ItemInventory inventory = InventoryFilterItem.getInventory(filter);

                        int i = 0;
                        for (ItemPredicate p : trigger.predicates) {
                            inventory.setItem(i++, predicateToStack(p));
                        }

                        inventory.save();
                        task.item = filter;
                    } else if (trigger.predicates.length == 1) {
                        task.item = predicateToStack(trigger.predicates[0]);
                    }
                    quest.tasks.add(task);
                }
            }

            int i = chapter.quests.size();
            quest.x = ((i % 9) + 1) * 2d - 2;
            quest.y = Math.ceil((i + 1) / 9d) - 1;

            chapter.quests.add(quest);
        }

        logger.info("Saving generated quests");
        chapters.addAll(generated.values());
        questFile.save();
        logger.info("Quest conversion done");
    }

    private ItemStack predicateToStack(ItemPredicate predicate) {
        if (predicate.items != null) {
            if (predicate.items.size() > 1) {
                ItemStack filter = new ItemStack(ItemFiltersItems.OR.get());
                ItemInventory inventory = InventoryFilterItem.getInventory(filter);

                int i = 0;
                for (Item item : predicate.items) {
                    inventory.setItem(i++, new ItemStack(item));
                }

                inventory.save();
                return filter;
            } else {
                return new ItemStack(predicate.items.iterator().next());
            }
        } else if (predicate.tag != null) {
            ItemStack filter = new ItemStack(ItemFiltersItems.TAG.get());
            StringValueData<TagKey<Item>> data = ((StringValueFilterItem) filter.getItem()).getStringValueData(filter);
            data.setValue(predicate.tag);
            return filter;
        } else {
            return ItemStack.EMPTY;
        }
    }
}
