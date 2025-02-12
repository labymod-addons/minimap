package net.labymod.addons.minimap.data.compilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.ChunkData;

public class CompilationService {

  private final List<ChunkCompiler<?>> compilers;
  private final Map<Long, ChunkData> compiled = new ConcurrentHashMap<>();

  public CompilationService() {
    this.compilers = new ArrayList<>();
    this.registerCompiler(new GameChunkCompiler());
    this.registerCompiler(new LocalChunkCompiler());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void compile(ChunkData data) {
    long chunkId = this.getChunkId(data);
    if (this.compiled.containsKey(chunkId)) {
      return;
    }

    for (ChunkCompiler compiler : this.compilers) {
      if (compiler.isCompatible(data)) {
        compiler.compile(data);
        this.compiled.put(chunkId, data);
      }
    }
  }

  private void registerCompiler(ChunkCompiler<?> compiler) {
    this.compilers.add(compiler);
  }

  public void resetCompilation(ChunkData data) {
    this.compiled.remove(this.getChunkId(data));
  }

  private long getChunkId(ChunkData data) {
    return Util.getChunkId(data.getX(), data.getZ());
  }
}
