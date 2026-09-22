package net.labymod.addons.minimap.worldmap;

import java.util.UUID;

/**
 * Another player of the recorded world, seen by the client or reported by the server.
 */
record WorldMapPlayer(UUID uuid, String name, String dimension, double x, double z) {

}
