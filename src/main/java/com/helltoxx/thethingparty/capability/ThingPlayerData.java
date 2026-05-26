package com.helltoxx.thethingparty.capability;

import net.minecraft.nbt.CompoundTag;

public class ThingPlayerData implements IThingPlayerData {
    private Role role = Role.HUMAN;
    private boolean isMonsterForm = false;
    private int biomass = 0;
    private int weaponLockTicks = 0;
    private int transformTicks = 0;
    private int transformCooldownTicks = 0;

    @Override
    public Role getRole() { return role; }
    @Override
    public void setRole(Role role) { this.role = role; }

    @Override
    public boolean isMonsterForm() { return isMonsterForm; }
    @Override
    public void setMonsterForm(boolean isMonsterForm) { this.isMonsterForm = isMonsterForm; }

    @Override
    public int getBiomass() { return biomass; }
    @Override
    public void setBiomass(int biomass) { this.biomass = biomass; }
    @Override
    public void addBiomass(int amount) { this.biomass += amount; }

    @Override
    public int getWeaponLockTicks() { return weaponLockTicks; }
    @Override
    public void setWeaponLockTicks(int ticks) { this.weaponLockTicks = ticks; }
    @Override
    public void decreaseWeaponLockTicks() {
        if (this.weaponLockTicks > 0) this.weaponLockTicks--;
    }

    @Override
    public int getTransformTicks() { return transformTicks; }
    @Override
    public void setTransformTicks(int ticks) { this.transformTicks = ticks; }

    @Override
    public int getTransformCooldownTicks() { return transformCooldownTicks; }
    @Override
    public void setTransformCooldownTicks(int ticks) { this.transformCooldownTicks = ticks; }

    @Override
    public void copyFrom(IThingPlayerData source) {
        this.role = source.getRole();
        this.isMonsterForm = source.isMonsterForm();
        this.biomass = source.getBiomass();
        this.weaponLockTicks = source.getWeaponLockTicks();
        // transformTicks НЕ копируем - переходное состояние, при респавне должно быть 0.
    }

    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putString("Role", role.name());
        nbt.putBoolean("IsMonsterForm", isMonsterForm);
        nbt.putInt("Biomass", biomass);
        nbt.putInt("WeaponLockTicks", weaponLockTicks);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        if (nbt.contains("Role")) this.role = Role.valueOf(nbt.getString("Role"));
        this.isMonsterForm = nbt.getBoolean("IsMonsterForm");
        this.biomass = nbt.getInt("Biomass");
        this.weaponLockTicks = nbt.getInt("WeaponLockTicks");
    }
}