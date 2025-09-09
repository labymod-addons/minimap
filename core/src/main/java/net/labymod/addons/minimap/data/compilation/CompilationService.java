package net.labymod.addons.minimap.data.compilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

public class CompilationService {

  private final List<ChunkCompiler<?>> compilers;
  private final Map<Long, ChunkData> compiled = new ConcurrentHashMap<>();
  private int playerX;
  private int playerY;
  private int playerZ;
  private boolean underground;

  public CompilationService() {
    this.compilers = new ArrayList<>();
    this.registerCompiler(new GameChunkCompiler());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public boolean compile(ChunkData data) {
    long chunkId = this.getChunkId(data);
    if (this.compiled.containsKey(chunkId)) {
      return false;
    }

    boolean compiled = false;
    for (ChunkCompiler compiler : this.compilers) {
      if (compiler.isCompatible(data)) {
        compiler.setPlayerPosition(this.playerX, this.playerY, this.playerZ, this.underground);
        compiler.compile(data);
        this.compiled.put(chunkId, data);
        compiled = true;
      }
    }

    return compiled;
  }

  public void setPlayerPosition(Position position, boolean underground) {
     this.setPlayerPosition(
         MathHelper.floor(position.getX()),
         MathHelper.floor(position.getY()),
         MathHelper.floor(position.getZ()),
         underground
     );
  }

  public void setPlayerPosition(int x, int y, int z, boolean underground) {
    this.playerX = x;
    this.playerY = y;
    this.playerZ = z;
    this.underground = underground;
  }

  private void registerCompiler(ChunkCompiler<?> compiler) {
    this.compilers.add(compiler);
  }

  public void resetCompilation(ChunkData data) {
    this.compiled.remove(this.getChunkId(data));
  }

  public boolean isCompiled(ChunkData data) {
    return this.compiled.containsKey(this.getChunkId(data));
  }

  public void resetCompilations() {
    this.compiled.clear();
  }

  private long getChunkId(ChunkData data) {
    return Util.getChunkId(data.getX(), data.getZ());
  }
}
