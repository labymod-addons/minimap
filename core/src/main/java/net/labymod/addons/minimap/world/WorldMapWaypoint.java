package net.labymod.addons.minimap.world;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;

public record WorldMapWaypoint(
    String id,
    double x,
    double y,
    double z,
    Icon icon,
    int iconColor,
    Component title,
    boolean movable
) {

}
