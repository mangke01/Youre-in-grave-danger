package com.b1n_ry.yigd.core;

import com.b1n_ry.yigd.Yigd;
import com.b1n_ry.yigd.api.YigdApi;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static net.minecraft.entity.damage.DamageSource.GENERIC;

public class DeadPlayerData {
    public DefaultedList<ItemStack> inventory;
    public Map<String, Object> modInventories;
    public BlockPos gravePos;
    public GameProfile graveOwner;
    public int xp;
    public Identifier worldId;
    public String dimensionName;
    public DamageSource deathSource;
    public long deathTime;

    public UUID id;
    public byte availability; // 0 = retrieved, 1 = available, -1 = broken/missing
    public GameProfile claimedBy;

    public static DeadPlayerData create(DefaultedList<ItemStack> inventory, Map<String, Object> modInventories, BlockPos gravePos, GameProfile graveOwner , int xp, World world, DamageSource deathSource, UUID id) {
        Identifier dimId = world.getRegistryManager().get(Registry.DIMENSION_TYPE_KEY).getId(world.getDimension());
        String dimName = dimId != null ? dimId.toString() : "void";
        return new DeadPlayerData(inventory, modInventories, gravePos, graveOwner, xp, world.getRegistryKey().getValue(), dimName, deathSource, world.getTimeOfDay(), (byte) 1, null, id);
    }
    public DeadPlayerData(DefaultedList<ItemStack> inventory, Map<String, Object> modInventories, BlockPos gravePos, GameProfile graveOwner, int xp, Identifier worldId, String dimensionName, DamageSource deathSource, long deathTime, byte availability, GameProfile claimedBy, UUID id) {
        this.inventory = inventory;
        this.modInventories = modInventories;
        this.gravePos = gravePos;
        this.graveOwner = graveOwner;
        this.xp = xp;
        this.worldId = worldId;
        this.deathSource = deathSource;
        this.deathTime = deathTime;
        this.dimensionName = dimensionName;
        this.availability = availability;
        this.claimedBy = claimedBy;
        this.id = id;
    }

    // Convert the DeadPlayerData object to NBT
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        NbtCompound invNbt = Inventories.writeNbt(new NbtCompound(), this.inventory);
        NbtCompound modNbt = new NbtCompound();
        for (YigdApi yigdApi : Yigd.apiMods) {
            String modName = yigdApi.getModName();
            if (!modInventories.containsKey(modName)) continue;

            modNbt.put(modName, yigdApi.writeNbt(modInventories.get(modName)));
        }
        NbtCompound posNbt = NbtHelper.fromBlockPos(this.gravePos);
        NbtCompound graveOwnerNbt = NbtHelper.writeGameProfile(new NbtCompound(), this.graveOwner);

        String worldId = this.worldId.toString();

        NbtCompound dsNbt = new NbtCompound();
        dsNbt.putString("name", this.deathSource.name);
        if (this.deathSource.getSource() != null) {
            dsNbt.putUuid("sourceUuid", deathSource.getSource().getUuid());
        }
        if (deathSource.getAttacker() != null) {
            dsNbt.putUuid("attackerUuid", deathSource.getAttacker().getUuid());
        }

        nbt.put("inventory", invNbt);
        nbt.putInt("inventorySize", this.inventory.size());
        nbt.put("modInventory", modNbt);
        nbt.put("gravePos", posNbt);
        nbt.put("owner", graveOwnerNbt);
        nbt.putInt("xp", this.xp);
        nbt.putString("world", worldId);
        nbt.putString("dimension", this.dimensionName);
        nbt.put("causeOfDeath", dsNbt);
        nbt.putLong("deathTime", this.deathTime);
        nbt.putByte("availability", this.availability);
        if (this.claimedBy != null) nbt.put("claimedBy", NbtHelper.writeGameProfile(new NbtCompound(), this.claimedBy));
        nbt.putUuid("id", this.id);

        return nbt;
    }

    // Create a new DeadPlayerData instance from NBT
    public static DeadPlayerData fromNbt(NbtCompound nbt) {
        return fromNbt(nbt, null);
    }
    public static DeadPlayerData fromNbt(NbtCompound nbt, @Nullable ServerWorld world) {
        NbtElement itemNbt = nbt.get("inventory");
        int invSize = nbt.getInt("inventorySize");
        DefaultedList<ItemStack> items = DefaultedList.ofSize(invSize, ItemStack.EMPTY);
        if (itemNbt instanceof NbtCompound itemNbtCompound) {
            Inventories.readNbt(itemNbtCompound, items);
        }
        NbtElement modNbt = nbt.get("modInventory");
        Map<String, Object> modInventories = new HashMap<>();
        if (modNbt instanceof NbtCompound modNbtCompound) {
            for (YigdApi yigdApi : Yigd.apiMods) {
                String modName = yigdApi.getModName();
                NbtElement e = modNbtCompound.get(modName);
                if (!(e instanceof NbtCompound c)) continue;

                modInventories.put(modName, yigdApi.readNbt(c));
            }
        }
        BlockPos pos;
        NbtElement blockPosNbt = nbt.get("gravePos");
        if (blockPosNbt instanceof NbtCompound c) {
            pos = NbtHelper.toBlockPos(c);
        } else {
            pos = null;
        }

        NbtElement ownerElement = nbt.get("owner");
        GameProfile graveOwner;
        if (ownerElement instanceof NbtCompound ownerNbt) {
            graveOwner = NbtHelper.toGameProfile(ownerNbt);
        } else {
            graveOwner = null;
        }

        int xp = nbt.getInt("xp");
        String worldId = nbt.getString("world");
        Identifier worldIdentifier = new Identifier(worldId);
        String dimName = nbt.getString("dimension");
        long deathTime = nbt.contains("deathTime") ? nbt.getLong("deathTime") : 0;
        byte availability = nbt.contains("availability") ? nbt.getByte("availability") : 1;
        GameProfile claimedBy = nbt.get("claimedBy") instanceof NbtCompound claimedByNbt ? NbtHelper.toGameProfile(claimedByNbt) : null;
        UUID id = nbt.contains("id") ? nbt.getUuid("id") : UUID.randomUUID();

        NbtElement damageSourceNbt = nbt.get("causeOfDeath");
        DamageSource deathSource;
        if (damageSourceNbt instanceof NbtCompound dsNbt) {
            String sourceName = dsNbt.getString("name");
            Entity source, attacker;
            if (world != null) {
                source = world.getEntity(dsNbt.getUuid("sourceUuid"));
                attacker = world.getEntity(dsNbt.getUuid("attackerUuid"));
            } else {
                source = null;
                attacker = null;
            }
            if (source != null) {
                if (source.equals(attacker)) { // Entity attacker and source
                    deathSource = new EntityDamageSource(sourceName, source);
                } else { // Projectile source and entity attacker
                    deathSource = new ProjectileDamageSource(sourceName, source, attacker);
                }
            } else {
                switch (sourceName) {
                    case "inFire" -> deathSource = DamageSource.IN_FIRE;
                    case "lightningBolt" -> deathSource = DamageSource.LIGHTNING_BOLT;
                    case "onFire" -> deathSource = DamageSource.ON_FIRE;
                    case "lava" -> deathSource = DamageSource.LAVA;
                    case "hotFloor" -> deathSource = DamageSource.HOT_FLOOR;
                    case "inWall" -> deathSource = DamageSource.IN_WALL;
                    case "cramming" -> deathSource = DamageSource.CRAMMING;
                    case "drown" -> deathSource = DamageSource.DROWN;
                    case "starve" -> deathSource = DamageSource.STARVE;
                    case "cactus" -> deathSource = DamageSource.CACTUS;
                    case "fall" -> deathSource = DamageSource.FALL;
                    case "flyIntoWall" -> deathSource = DamageSource.FLY_INTO_WALL;
                    case "outOfWorld" -> deathSource = DamageSource.OUT_OF_WORLD;
                    case "magic" -> deathSource = DamageSource.MAGIC;
                    case "wither" -> deathSource = DamageSource.WITHER;
                    case "anvil" -> deathSource = DamageSource.ANVIL;
                    case "fallingBlock" -> deathSource = DamageSource.FALLING_BLOCK;
                    case "dragonBreath" -> deathSource = DamageSource.DRAGON_BREATH;
                    case "dryout" -> deathSource = DamageSource.DRYOUT;
                    case "sweetBerryBush" -> deathSource = DamageSource.SWEET_BERRY_BUSH;
                    case "freeze" -> deathSource = DamageSource.FREEZE;
                    case "fallingStalactite" -> deathSource = DamageSource.FALLING_STALACTITE;
                    case "stalagmite" -> deathSource = DamageSource.STALAGMITE;
                    default -> deathSource = GENERIC;
                }
            }
        } else {
            deathSource = null;
        }

        return new DeadPlayerData(items, modInventories, pos, graveOwner, xp, worldIdentifier, dimName, deathSource, deathTime, availability, claimedBy, id);
    }

    public static class Soulbound {
        private static final Map<UUID, DefaultedList<ItemStack>> soulboundInventories = new HashMap<>();
        private static final Map<UUID, Map<String, Object>> moddedSoulbound = new HashMap<>();

        public static DefaultedList<ItemStack> getSoulboundInventory(UUID userId) {
            return soulboundInventories.get(userId);
        }
        public static Map<String, Object> getModdedSoulbound(UUID userId) {
            return moddedSoulbound.get(userId);
        }
        public static void setSoulboundInventories(UUID userId, DefaultedList<ItemStack> soulboundItems) {
            dropSoulbound(userId);
            soulboundInventories.put(userId, soulboundItems);
            DeathInfoManager.INSTANCE.markDirty();
        }
        public static void addModdedSoulbound(UUID userId, Object modInventory, String modName) {
            if (!moddedSoulbound.containsKey(userId)) {
                moddedSoulbound.put(userId, new HashMap<>());
            }
            moddedSoulbound.get(userId).put(modName, modInventory);
            DeathInfoManager.INSTANCE.markDirty();
        }

        public static void dropSoulbound(UUID userId) {
            soulboundInventories.remove(userId);
            DeathInfoManager.INSTANCE.markDirty();
        }
        public static void dropModdedSoulbound(UUID userId) {
            moddedSoulbound.remove(userId);
            DeathInfoManager.INSTANCE.markDirty();
        }

        public static NbtCompound getNbt() {
            NbtCompound nbt = new NbtCompound();

            NbtList vanillaList = new NbtList();
            soulboundInventories.forEach((uuid, itemStacks) -> {
                NbtCompound playerNbt = Inventories.writeNbt(new NbtCompound(), itemStacks);
                playerNbt.putInt("inventorySize", itemStacks.size());
                playerNbt.putUuid("user", uuid);

                vanillaList.add(playerNbt);
            });
            NbtList modList = new NbtList();
            moddedSoulbound.forEach((uuid, objects) -> {
                NbtCompound playerNbt = new NbtCompound();
                NbtCompound inventories = new NbtCompound();
                for (YigdApi yigdApi : Yigd.apiMods) {
                    String modName = yigdApi.getModName();
                    if (!objects.containsKey(modName)) continue;

                    inventories.put(modName, yigdApi.writeNbt(objects.get(modName)));
                }
                playerNbt.put("Inventories", inventories);
                playerNbt.putUuid("user", uuid);

                modList.add(playerNbt);
            });

            nbt.put("vanilla", vanillaList);
            nbt.put("mods", modList);

            return nbt;
        }

        public static void fromNbt(NbtCompound nbt) {
            soulboundInventories.clear();
            moddedSoulbound.clear();

            NbtList vanillaList = nbt.getList("vanilla", NbtElement.COMPOUND_TYPE);
            NbtList modList = nbt.getList("mods", NbtElement.COMPOUND_TYPE);

            for (NbtElement e : vanillaList) {
                if (!(e instanceof NbtCompound cVanilla)) continue;
                int itemSize = cVanilla.getInt("inventorySize");
                DefaultedList<ItemStack> items = DefaultedList.ofSize(itemSize, ItemStack.EMPTY);
                UUID userId = cVanilla.getUuid("user");
                Inventories.readNbt(cVanilla, items);

                soulboundInventories.put(userId, items);
            }
            for (NbtElement e : modList) {
                if (!(e instanceof NbtCompound cMods)) continue;

                NbtCompound modsNbt = cMods.getCompound("Inventories");
                UUID userId = cMods.getUuid("user");

                for (YigdApi yigdApi : Yigd.apiMods) {
                    String modName = yigdApi.getModName();
                    NbtCompound modNbt = modsNbt.getCompound(modName);
                    Object modInventory = yigdApi.readNbt(modNbt);

                    addModdedSoulbound(userId, modInventory, modName);
                }
            }
        }
    }
}
