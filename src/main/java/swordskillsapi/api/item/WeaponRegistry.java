package swordskillsapi.api.item;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import swordskillsapi.SwordSkillsApi;

/**
 * 
 * Allows items from other mods to be registered as generic weapons or as swords.
 * Items that rely on NBT or damage value to determine their weapon status should
 * NOT be registered in this way - use {@link IWeapon} instead.
 * 
 * Note also that some skills require blocking to activate, so any item which cannot
 * be used to block will not be able to trigger those skills even if registered.
 *
 */
public class WeaponRegistry
{
	/** Use {@link IMC_ALLOW_SWORD} instead */
	@Deprecated
	public static final String IMC_SWORD_KEY = "ZssRegisterSword";

	/** Use {@link IMC_ALLOW_WEAPON} instead */
	@Deprecated
	public static final String IMC_WEAPON_KEY = "ZssRegisterWeapon";

	/** FML Inter-Mod Communication key for adding an item to the list of allowed swords */
	public static final String IMC_ALLOW_SWORD = "allow_sword";

	/** FML Inter-Mod Communication key for adding an item to the list of allowed non-sword melee weapons */
	public static final String IMC_ALLOW_WEAPON = "allow_weapon";

	/** FML Inter-Mod Communication key for adding an item to the list of items disallowed as swords */
	public static final String IMC_FORBID_SWORD = "forbid_sword";

	/** FML Inter-Mod Communication key for adding an item to the list of items disallowed as non-sword melee weapons */
	public static final String IMC_FORBID_WEAPON = "forbid_weapon";

	private final Set<Item> allowed_swords = new HashSet<Item>();

	private final Set<Item> allowed_weapons = new HashSet<Item>();

	private final Set<Item> forbidden_swords = new HashSet<Item>();

	private final Set<Item> forbidden_weapons = new HashSet<Item>();

	public static final WeaponRegistry INSTANCE = new WeaponRegistry();

	private WeaponRegistry() {}

	/**
	 * Returns true if the item is considered a sword and has not been forbidden as such
	 */
	public boolean isSword(Item item) {
		return !isSwordForbidden(item) && (item instanceof ItemSword || allowed_swords.contains(item));
	}

	/**
	 * Returns true if the item is forbidden either as a sword or a weapon (if it's not a weapon, it's not a sword).
	 * Recommended to use this method instead of {@link #isSword} when implementing {@link IWeapon#isSword}.
	 */
	public boolean isSwordForbidden(Item item) {
		return forbidden_swords.contains(item) || isWeaponForbidden(item);
	}

	/**
	 * Returns true if the item is considered a melee weapon of any kind and has not been forbidden as such
	 */
	public boolean isWeapon(Item item) {
		return !isWeaponForbidden(item) && (item instanceof ItemSword || item instanceof ItemAxe || allowed_weapons.contains(item));
	}

	/**
	 * Returns true if the item is forbidden as a weapon.
	 * Recommended to use this method instead of {@link #isWeapon} when implementing {@link IWeapon#isWeapon}.
	 */
	public boolean isWeaponForbidden(Item item) {
		return forbidden_weapons.contains(item);
	}

	/**
	 * If the message key is either {@link #IMC_SWORD_KEY} or {@link #IMC_WEAPON_KEY}
	 * and the message contains an ItemStack, the stack will be registered appropriately.
	 */
	public void processMessage(FMLInterModComms.IMCMessage msg) {
		if (!msg.isItemStackMessage()) {
			return;
		} else if (msg.key.equalsIgnoreCase(IMC_ALLOW_SWORD) || msg.key.equalsIgnoreCase(IMC_SWORD_KEY)) {
			registerSword("IMC", msg.getSender(), msg.getItemStackValue().getItem());
		} else if (msg.key.equalsIgnoreCase(IMC_ALLOW_WEAPON) || msg.key.equalsIgnoreCase(IMC_WEAPON_KEY)) {
			registerWeapon("IMC", msg.getSender(), msg.getItemStackValue().getItem());
		} else if (msg.key.equalsIgnoreCase(IMC_FORBID_SWORD)) {
			removeSword("IMC", msg.getSender(), msg.getItemStackValue().getItem());
		} else if (msg.key.equalsIgnoreCase(IMC_FORBID_WEAPON)) {
			removeWeapon("IMC", msg.getSender(), msg.getItemStackValue().getItem());
		} else {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] Invalid IMC message method name %s from mod %s", msg.key, msg.getSender()));
		}
	}

	/**
	 * Registers an array of named items either as swords or generic weapons
	 * @param names Must be in the format 'modid:registered_item_name'
	 * @param origin Information about the origin of the names array, e.g. "Config" or "Command"
	 * @param isSword True to register the items as swords, or false for generic melee weapons
	 */
	public void registerItems(String[] names, String origin, boolean isSword) {
		processArray(names, origin, isSword, true);
	}

	/**
	 * Forbids an array of named items either from the swords or generic weapons registry
	 * @param names Must be in the format 'modid:registered_item_name'
	 * @param origin Information about the origin of the names array, e.g. "Config" or "Command"
	 * @param isSword True to forbid the items as swords, or false for generic melee weapons
	 */
	public void forbidItems(String[] names, String origin, boolean isSword) {
		processArray(names, origin, isSword, false);
	}

	private void processArray(String[] names, String origin, boolean isSword, boolean register) {
		for (String s : names) {
			String[] parts = parseString(s);
			if (parts != null) {
				Item item = Item.REGISTRY.getObject(new ResourceLocation(parts[0], parts[1]));
				if (item == null) {
					SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] %s could not be found - the mod may not be installed or it may have been typed incorrectly", origin, s));
				} else if (isSword) {
					if (register) {
						registerSword(origin, parts[0], item);
					} else {
						removeSword(origin, parts[0], item);
					}
				} else if (register) {
					registerWeapon(origin, parts[0], item);
				} else {
					removeWeapon(origin, parts[0], item);
				}
			}
		}
	}

	/**
	 * Registers an item as a sword: it may be used to activate sword-specific skills, cut grass, etc.
	 * Item will be removed from the forbidden sword list if present.
	 * @param origin String containing information about origins of registration, e.g. "IMC"
	 * @param modid String modid of mod which caused this method to be called
	 * @param item Item to add to the sword registry
	 * @return true if item was successfully added
	 */
	public boolean registerSword(String origin, String modid, Item item) {
		boolean added = false;
		if (allowed_weapons.contains(item)) {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] CONFLICT: %s cannot be registered as a sword - it is already registered as a non-sword weapon", origin, modid, item.getRegistryName().toString()));
		} else if (allowed_swords.add(item)) {
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] Registered %s as a sword", origin, modid, item.getRegistryName().toString()));
			added = true;
		} else {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] %s has already been registered as a sword", origin, modid, item.getRegistryName().toString()));
		}
		forbidden_swords.remove(item);
		return added;
	}

	/**
	 * Registers an item as a generic melee weapon: it may be used to activate all skills that do not specifically require a sword.
	 * Item will be removed from the forbidden weapons list if present.
	 * @param origin String containing information about origins of registration, e.g. "IMC"
	 * @param modid String modid of mod which caused this method to be called
	 * @param item Item to add to the weapon registry
	 * @return true if item was successfully added
	 */
	public boolean registerWeapon(String origin, String modid, Item item) {
		boolean added = false;
		if (allowed_swords.contains(item)) {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] CONFLICT: %s cannot be registered as a weapon - it is already registered as a sword", origin, modid, item.getRegistryName().toString()));
		} else if (allowed_weapons.add(item)) {
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] Registered %s as a non-sword weapon", origin, modid, item.getRegistryName().toString()));
			added = true;
		} else {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] %s has already been registered as a weapon", origin, modid, item.getRegistryName().toString()));
		}
		forbidden_weapons.remove(item);
		return added;
	}

	/**
	 * Removes the item from the SWORD registry if present and adds it to the forbidden swords list
	 * @return true if item was both present and removed from the SWORD list
	 */
	public boolean removeSword(String origin, String modid, Item item) {
		boolean added = false; // prevent too much log spam
		if (forbidden_swords.add(item)) {
			added = true;
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] %s added to FORBIDDEN swords list", origin, modid, item.getRegistryName().toString()));
		}
		if (allowed_swords.remove(item)) {
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] Removed %s from list of registered SWORDS", origin, modid, item.getRegistryName().toString()));
			return true;
		} else if (!added) {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] Could not remove %s - it was not registered as a sword", modid, item.getRegistryName().toString()));
		}
		return added;
	}

	/**
	 * Removes the item from the WEAPON registry if present and adds it to the forbidden weapons list
	 * @return true if item was both present and removed from the WEAPON list
	 */
	public boolean removeWeapon(String origin, String modid, Item item) {
		boolean added = false; // prevent too much log spam
		if (forbidden_weapons.add(item)) {
			added = true;
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] %s added to FORBIDDEN weapons list", origin, modid, item.getRegistryName().toString()));
		}
		if (allowed_weapons.remove(item)) {
			SwordSkillsApi.LOGGER.info(String.format("[WeaponRegistry] [%s] [%s] Removed %s from list of registered WEAPONS", origin, modid, item.getRegistryName().toString()));
			return true;
		} else if (!added) {
			SwordSkillsApi.LOGGER.warn(String.format("[WeaponRegistry] [%s] [%s] Could not remove %s - it was not registered as a non-sword weapon", origin, modid, item.getRegistryName().toString()));
		}
		return added;
	}

	/**
	 * Parses a String into an array containing the mod_id and item_name, or NULL if format was invalid
	 * @param itemid Expected format is 'modid:registered_item_name'
	 */
	public static String[] parseString(String itemid) {
		String[] parts = itemid.split(":");
		if (parts.length == 2) {
			return parts;
		}
		SwordSkillsApi.LOGGER.error(String.format("[WeaponRegistry] String must be in the format 'modid:registered_item_name', received: %s", itemid));
		return null;
	}
}
