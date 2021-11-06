package com.b1n4ry.yigd.compat;

import com.b1n4ry.yigd.api.YigdApi;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.component.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CuriosCosmeticCompat implements YigdApi {
    @Override
    public List<ItemStack> getInventory(PlayerEntity player) {
        List<ItemStack> inventory = new ArrayList<>();

        CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
            handler.getCurios().forEach((id, stacksHandler) -> {
                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    ItemStack stack = stacksHandler.getCosmeticStacks().getStack(i);
                    inventory.add(stack);
                }
            });
        });

        return inventory;
    }

    @Override
    public void setInventory(List<ItemStack> inventory, PlayerEntity player) {
        int n = 0;

        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosHelper().getCuriosHandler(player);
        if (optional.isPresent()) {
            ICuriosItemHandler itemHandler = optional.get();

            for (ICurioStacksHandler stacksHandler : itemHandler.getCurios().values()) {
                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    stacksHandler.getCosmeticStacks().setStack(i, inventory.get(n));
                    n++;
                }
            }
        }
    }

    @Override
    public int getInventorySize(PlayerEntity player) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosHelper().getCuriosHandler(player);
        if (optional.isPresent()) {
            ICuriosItemHandler itemHandler = optional.get();

            return itemHandler.getSlots();
        }

        return 0;
    }

    @Override
    public void dropAll(PlayerEntity player) {
        CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(itemHandler -> {
            itemHandler.getCurios().forEach((id, stacksHandler) -> {
                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    stacksHandler.getCosmeticStacks().setStack(i, ItemStack.EMPTY);
                }
            });
        });
    }
}
