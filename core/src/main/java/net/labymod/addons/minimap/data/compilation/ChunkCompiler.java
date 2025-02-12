package net.labymod.addons.minimap.data.compilation;

import net.labymod.addons.minimap.data.ChunkData;

public interface ChunkCompiler<T extends ChunkData> {

  boolean isCompatible(ChunkData data);

  void compile(T t);

}
