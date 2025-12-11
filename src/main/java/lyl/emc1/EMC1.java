package lyl.emc1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;

@Mod(modid = "emc1", name = "EMC1", version = "1.7.10")
public class EMC1 {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent ev) {
        System.out.println("[EMC1] preInit - 注册事件");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void init(FMLInitializationEvent ev) {
        System.out.println("[EMC1] init");
    }

    @SubscribeEvent
    public void onEMCRemap(EMCRemapEvent event) {
        System.out.println("[EMC1] 收到 EMCRemapEvent，开始覆盖与补缺失（使用 getSubItems）");

        // 覆盖已有条目为 1
        int changed = 0;
        for (Map.Entry<SimpleStack, Integer> e : EMCMapper.emc.entrySet()) {
            Integer old = e.getValue();
            if (old == null || old != 1) {
                e.setValue(1);
                changed++;
            }
        }
        System.out.println("[EMC1] 已覆盖已有条目: " + changed);

        // 补缺失（使用 item.getSubItems）
        int added = 0;
        try {
            // 以 itemRegistry 可迭代为例；在不同环境可以用 GameData.getItemRegistry().getKeys() 之类替代
            for (Object regObj : Item.itemRegistry) {
                if (regObj == null)
                    continue;
                Item item = (Item) regObj;
                List<ItemStack> subItems = new ArrayList<>();

                // 如果该 Item 有子类型，枚举子项；否则只用默认 stack
                if (item.getHasSubtypes()) { // 在 MCP 中常为 getHasSubtypes()
                    try {
                        // getSubItems(item, creativeTab, list) —— 传入 null 与 ManualPageHandler 的做法一致
                        item.getSubItems(item, null, subItems);
                    } catch (Throwable t) {
                        // 某些 Item 的实现可能抛异常，回退到只添加 damage=0
                        subItems.clear();
                    }
                }

                if (subItems.isEmpty()) {
                    subItems.add(new ItemStack(item, 1, 0));
                }

                for (ItemStack s : subItems) {
                    if (s == null || s.getItem() == null)
                        continue;
                    // 使用可读的 getter 名
                    int id = GameData.getItemRegistry()
                            .getIDForObject(s.getItem());
                    int dmg = s.getItemDamage();
                    SimpleStack key = new SimpleStack(id, 1, dmg);
                    if (!EMCMapper.mapContains(key)) {
                        EMCMapper.emc.put(key, 1);
                        added++;
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            // 回退策略（例如按 id 范围遍历）可以放在这里
        }

        System.out.println("[EMC1] 新加入缺失条目: " + added);
        System.out.println("[EMC1] 完成，最终 emc map size = " + EMCMapper.emc.size());
    }
}