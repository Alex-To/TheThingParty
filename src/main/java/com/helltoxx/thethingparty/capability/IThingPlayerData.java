package com.helltoxx.thethingparty.capability;

import net.minecraft.nbt.CompoundTag;

public interface IThingPlayerData {
    enum Role { HUMAN, THING }

    Role getRole();
    void setRole(Role role);

    boolean isMonsterForm();
    void setMonsterForm(boolean isMonsterForm);

    int getBiomass();
    void setBiomass(int biomass);
    void addBiomass(int amount);

    int getWeaponLockTicks();
    void setWeaponLockTicks(int ticks);
    void decreaseWeaponLockTicks();

    void copyFrom(IThingPlayerData source);
    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}