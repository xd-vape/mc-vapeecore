package dev.vapee.core.lobby.experience.glide;

import static dev.vapee.core.quest.QuestCompletionFixture.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import java.util.*;

/** Item API boundary only; production creates/binds/validates its real PDC itself. */
final class GlideTestItem extends ItemStack {
    final Material material;
    final Map<NamespacedKey,Object> tags = new HashMap<>();
    boolean unbreakable, rejectMeta;
    int amount=1;
    final ItemMeta meta;
    GlideTestItem(Material material){
        super();this.material=material;
        var pdc=proxy(PersistentDataContainer.class,(m,a)->switch(m){
            case "get"->tags.get(a[0]);case "has"->tags.containsKey(a[0])&&(a.length==1||((org.bukkit.persistence.PersistentDataType<?,?>)a[1]).getPrimitiveType().isInstance(tags.get(a[0])));
            case "set"->{tags.put((NamespacedKey)a[0],a[2]);yield null;}
            case "remove"->{tags.remove(a[0]);yield null;}case "getKeys"->Set.copyOf(tags.keySet());default->DEFAULT;
        });
        meta=proxy(ItemMeta.class,(m,a)->switch(m){
            case "getPersistentDataContainer"->pdc;case "setUnbreakable"->{unbreakable=(boolean)a[0];yield null;}
            case "isUnbreakable"->unbreakable;default->DEFAULT;
        });
    }
    @Override public Material getType(){return material;}
    @Override public int getAmount(){return amount;}
    @Override public void setAmount(int amount){this.amount=amount;}
    @Override public boolean isEmpty(){return amount<=0||material==Material.AIR;}
    @Override public boolean hasItemMeta(){return !tags.isEmpty()||unbreakable;}
    @Override public ItemMeta getItemMeta(){return meta;}
    @Override public boolean setItemMeta(ItemMeta meta){return !rejectMeta;}
    @Override public GlideTestItem clone(){var copy=new GlideTestItem(material);copy.tags.putAll(tags);copy.unbreakable=unbreakable;copy.amount=amount;return copy;}
    String fingerprint(){var sorted=new TreeMap<String,Object>();tags.forEach((k,v)->sorted.put(k.toString(),v));return material+":"+amount+":"+unbreakable+":"+sorted;}
}
