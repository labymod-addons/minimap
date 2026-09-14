package net.labymod.addons.minimap.worldmap;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Arrays;
import java.util.Iterator;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.gui.state.MinimapGuiBlitRenderState;
import net.labymod.addons.minimap.laby3d.MinimapRenderStates;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.map.v2.texture.SectionTextureRepository;
import net.labymod.addons.minimap.world.MapLod;
import net.labymod.addons.minimap.world.MapRegion;
import net.labymod.addons.minimap.world.MapRegionStore;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.laby3d.pipeline.material.GuiMaterial;
import net.labymod.api.util.math.MathHelper;
import net.labymod.laby3d.api.textures.DeviceTextureView;
import net.labymod.laby3d.api.textures.SamplerDescription;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;
import org.jetbrains.annotations.Nullable;

/**
 * Draws saved regions at three levels of detail: shaded block textures when zoomed in, one overview
 * per region further out and merged overviews of {@value #GROUP_REGIONS}&times;
 * {@value #GROUP_REGIONS} regions when zoomed out all the way. Builds textures for at most 4 ms
 * per frame and frees the least recently drawn ones.
 */
final class WorldMapRenderer {

  private static final int SECTION_BLOCKS =
      SectionTextureRepository.SECTION_SIZE * ChunkData.CHUNK_SIZE;
  private static final int SECTIONS_PER_REGION = MapRegion.CHUNKS / SectionTextureRepository.SECTION_SIZE;
  private static final int GROUP_REGIONS = 4;
  private static final int GROUP_BLOCKS = GROUP_REGIONS * MapRegion.BLOCKS;
  private static final int MAX_DETAIL_TILES = 128;
  private static final int MAX_REGION_TILES = 256;
  private static final int MAX_GROUP_TILES = 64;
  private static final float MIN_DETAIL_SCALE = 0.75F;
  private static final long BUILD_BUDGET_NANOS = 4_000_000L;
  private static final int HEIGHT_OFFSET = 32768;
  private static final SamplerDescription SAMPLER = SamplerDescription.builder()
      .setFilter(Filter.NEAREST)
      .build();

  private final MinimapRenderer minimapRenderer;
  private final MinimapUniformBlocks uniformBlocks;
  private final Long2ObjectLinkedOpenHashMap<DetailTile> detailTiles = new Long2ObjectLinkedOpenHashMap<>();
  private final Long2ObjectLinkedOpenHashMap<LodTile> regionTiles = new Long2ObjectLinkedOpenHashMap<>();
  private final Long2ObjectLinkedOpenHashMap<LodTile> groupTiles = new Long2ObjectLinkedOpenHashMap<>();
  private final int[] groupPixels = new int[MapLod.SIZE * MapLod.SIZE];
  @Nullable
  private DynamicTexture fadeTexture;
  @Nullable
  private MapRegionStore store;
  private long deadline;
  private int frame;
  private int nextTextureId;

  WorldMapRenderer(MinimapRenderer minimapRenderer, MinimapUniformBlocks uniformBlocks) {
    this.minimapRenderer = minimapRenderer;
    this.uniformBlocks = uniformBlocks;
  }

  void render(
      ScreenContext context,
      MapRegionStore store,
      WorldMapCamera camera,
      float width, float height
  ) {
    if (this.store != store) {
      this.dispose();
      this.store = store;
    }

    this.frame++;
    this.deadline = System.nanoTime() + BUILD_BUDGET_NANOS;

    int minBlockX = MathHelper.floor(camera.screenToWorldX(0.0F, width));
    int minBlockZ = MathHelper.floor(camera.screenToWorldZ(0.0F, height));
    int maxBlockX = MathHelper.floor(camera.screenToWorldX(width, width));
    int maxBlockZ = MathHelper.floor(camera.screenToWorldZ(height, height));

    int minRegionX = minBlockX >> MapRegion.BLOCK_SHIFT;
    int minRegionZ = minBlockZ >> MapRegion.BLOCK_SHIFT;
    int maxRegionX = maxBlockX >> MapRegion.BLOCK_SHIFT;
    int maxRegionZ = maxBlockZ >> MapRegion.BLOCK_SHIFT;
    int regionCount = (maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1);

    int minSectionX = Math.floorDiv(minBlockX, SECTION_BLOCKS);
    int minSectionZ = Math.floorDiv(minBlockZ, SECTION_BLOCKS);
    int maxSectionX = Math.floorDiv(maxBlockX, SECTION_BLOCKS);
    int maxSectionZ = Math.floorDiv(maxBlockZ, SECTION_BLOCKS);
    int sectionCount = (maxSectionX - minSectionX + 1) * (maxSectionZ - minSectionZ + 1);

    if (camera.scale() >= MIN_DETAIL_SCALE && sectionCount <= MAX_DETAIL_TILES) {
      // Overviews fill in while the detailed textures are still being built
      this.renderRegionTiles(context, store, camera, width, height, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
      this.renderDetailTiles(context, store, camera, width, height, minSectionX, minSectionZ, maxSectionX, maxSectionZ);
    } else if (regionCount <= MAX_REGION_TILES) {
      this.renderRegionTiles(context, store, camera, width, height, minRegionX, minRegionZ, maxRegionX, maxRegionZ);
    } else {
      this.renderGroupTiles(
          context, store, camera, width, height,
          Math.floorDiv(minRegionX, GROUP_REGIONS), Math.floorDiv(minRegionZ, GROUP_REGIONS),
          Math.floorDiv(maxRegionX, GROUP_REGIONS), Math.floorDiv(maxRegionZ, GROUP_REGIONS)
      );
    }

    this.trim(this.detailTiles, MAX_DETAIL_TILES);
    this.trim(this.regionTiles, MAX_REGION_TILES);
    this.trim(this.groupTiles, MAX_GROUP_TILES);
  }

  void dispose() {
    disposeTiles(this.detailTiles);
    disposeTiles(this.regionTiles);
    disposeTiles(this.groupTiles);
    if (this.fadeTexture != null) {
      Tile.dispose(this.fadeTexture);
      this.fadeTexture = null;
    }

    this.store = null;
  }

  private void renderDetailTiles(
      ScreenContext context,
      MapRegionStore store,
      WorldMapCamera camera,
      float width, float height,
      int minSectionX, int minSectionZ,
      int maxSectionX, int maxSectionZ
  ) {
    if (!this.minimapRenderer.applyUniforms()) {
      return;
    }

    DeviceTextureView fade = this.fadeTexture().deviceTextureView();
    ScreenCanvas canvas = context.canvas();
    float size = SECTION_BLOCKS * camera.scale();
    for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
      for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
        MapRegion region = store.getRegion(
            Math.floorDiv(sectionX, SECTIONS_PER_REGION),
            Math.floorDiv(sectionZ, SECTIONS_PER_REGION)
        );
        if (region == null) {
          continue;
        }

        DetailTile tile = this.detailTile(sectionX, sectionZ, region);
        if (tile == null || tile.empty) {
          continue;
        }

        float x = camera.worldToScreenX((double) sectionX * SECTION_BLOCKS, width);
        float y = camera.worldToScreenY((double) sectionZ * SECTION_BLOCKS, height);
        DeviceTextureView color = tile.color.deviceTextureView();
        DeviceTextureView heightmap = tile.heightmap.deviceTextureView();
        DeviceTextureView lightmap = tile.lightmap.deviceTextureView();
        // Saved tiles don't crossfade, so the previous textures are the current ones
        canvas.submitState(
            (pose, scissorArea) -> new MinimapGuiBlitRenderState(
                GuiMaterial.builder(MinimapRenderStates.MINIMAP)
                    .setTexture(0, color)
                    .setTexture(1, heightmap)
                    .setTexture(2, lightmap)
                    .setTexture(3, fade)
                    .setTexture(4, color)
                    .setTexture(5, heightmap)
                    .setTexture(6, lightmap)
                    .build(),
                pose,
                x, y, size, size,
                0.0F, 0.0F, 1.0F, 1.0F,
                -1,
                scissorArea,
                this.uniformBlocks
            )
        );
      }
    }
  }

  @Nullable
  private DetailTile detailTile(int sectionX, int sectionZ, MapRegion region) {
    long key = MapRegion.key(sectionX, sectionZ);
    DetailTile tile = this.detailTiles.getAndMoveToLast(key);
    boolean outdated = tile == null || tile.revision != region.revision();
    if (outdated && System.nanoTime() < this.deadline) {
      if (tile == null) {
        tile = new DetailTile(this.nextTextureId++);
        this.detailTiles.putAndMoveToLast(key, tile);
      }

      tile.fill(
          region,
          Math.floorMod(sectionX, SECTIONS_PER_REGION) * SECTION_BLOCKS,
          Math.floorMod(sectionZ, SECTIONS_PER_REGION) * SECTION_BLOCKS
      );
    }

    if (tile != null) {
      tile.frame = this.frame;
    }

    return tile;
  }

  private void renderRegionTiles(
      ScreenContext context,
      MapRegionStore store,
      WorldMapCamera camera,
      float width, float height,
      int minRegionX, int minRegionZ,
      int maxRegionX, int maxRegionZ
  ) {
    ScreenCanvas canvas = context.canvas();
    float size = MapRegion.BLOCKS * camera.scale();
    for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
      for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
        int[] lod = store.getLod(regionX, regionZ);
        if (lod == null) {
          continue;
        }

        long key = MapRegion.key(regionX, regionZ);
        LodTile tile = this.regionTiles.getAndMoveToLast(key);
        if ((tile == null || tile.source != lod) && System.nanoTime() < this.deadline) {
          if (tile == null) {
            tile = new LodTile(this.nextTextureId++);
            this.regionTiles.putAndMoveToLast(key, tile);
          }

          tile.fill(lod);
          tile.source = lod;
        }

        if (tile == null) {
          continue;
        }

        this.drawLod(
            canvas, tile,
            camera.worldToScreenX((double) regionX * MapRegion.BLOCKS, width),
            camera.worldToScreenY((double) regionZ * MapRegion.BLOCKS, height),
            size
        );
      }
    }
  }

  private void renderGroupTiles(
      ScreenContext context,
      MapRegionStore store,
      WorldMapCamera camera,
      float width, float height,
      int minGroupX, int minGroupZ,
      int maxGroupX, int maxGroupZ
  ) {
    ScreenCanvas canvas = context.canvas();
    float size = GROUP_BLOCKS * camera.scale();
    int regions = GROUP_REGIONS * GROUP_REGIONS;
    int cell = MapLod.SIZE / GROUP_REGIONS;
    for (int groupX = minGroupX; groupX <= maxGroupX; groupX++) {
      for (int groupZ = minGroupZ; groupZ <= maxGroupZ; groupZ++) {
        long signature = 1L;
        boolean found = false;
        for (int index = 0; index < regions; index++) {
          int[] lod = store.getLod(
              groupX * GROUP_REGIONS + index % GROUP_REGIONS,
              groupZ * GROUP_REGIONS + index / GROUP_REGIONS
          );
          signature = signature * 31L + (lod == null ? 0 : System.identityHashCode(lod));
          found |= lod != null;
        }

        if (!found) {
          continue;
        }

        long key = MapRegion.key(groupX, groupZ);
        LodTile tile = this.groupTiles.getAndMoveToLast(key);
        if ((tile == null || tile.signature != signature) && System.nanoTime() < this.deadline) {
          Arrays.fill(this.groupPixels, 0);
          for (int index = 0; index < regions; index++) {
            int[] lod = store.getLod(
                groupX * GROUP_REGIONS + index % GROUP_REGIONS,
                groupZ * GROUP_REGIONS + index / GROUP_REGIONS
            );
            if (lod != null) {
              MapLod.downsample(
                  lod, this.groupPixels,
                  index % GROUP_REGIONS * cell, index / GROUP_REGIONS * cell,
                  GROUP_REGIONS
              );
            }
          }

          if (tile == null) {
            tile = new LodTile(this.nextTextureId++);
            this.groupTiles.putAndMoveToLast(key, tile);
          }

          tile.fill(this.groupPixels);
          tile.signature = signature;
        }

        if (tile == null) {
          continue;
        }

        this.drawLod(
            canvas, tile,
            camera.worldToScreenX((double) groupX * GROUP_BLOCKS, width),
            camera.worldToScreenY((double) groupZ * GROUP_BLOCKS, height),
            size
        );
      }
    }
  }

  private void drawLod(ScreenCanvas canvas, LodTile tile, float x, float y, float size) {
    tile.frame = this.frame;
    canvas.submitGuiBlit(
        GuiMaterial.builder(MinimapRenderStates.GUI_TEXTURED)
            .setTexture(0, tile.texture.deviceTextureView())
            .build(),
        x, y, size, size,
        0.0F, 0.0F, 1.0F, 1.0F,
        -1
    );
  }

  private DynamicTexture fadeTexture() {
    if (this.fadeTexture == null) {
      this.fadeTexture = new DynamicTexture(
          Util.newDefaultNamespace("texture/worldmap/fade"),
          1, 1,
          SAMPLER
      );
      this.fadeTexture.getImage().setARGB(0, 0, -1);
      this.fadeTexture.upload();
    }

    return this.fadeTexture;
  }

  private void trim(Long2ObjectLinkedOpenHashMap<? extends Tile> tiles, int maxTiles) {
    int excess = tiles.size() - maxTiles;
    Iterator<? extends Tile> iterator = tiles.values().iterator();
    while (excess > 0 && iterator.hasNext()) {
      Tile tile = iterator.next();
      if (tile.frame == this.frame) {
        continue;
      }

      iterator.remove();
      tile.dispose();
      excess--;
    }
  }

  private static void disposeTiles(Long2ObjectLinkedOpenHashMap<? extends Tile> tiles) {
    for (Tile tile : tiles.values()) {
      tile.dispose();
    }

    tiles.clear();
  }

  private abstract static class Tile {

    protected int frame;

    abstract void dispose();

    static DynamicTexture createTexture(int id, String suffix, int size) {
      return new DynamicTexture(
          Util.newDefaultNamespace("texture/worldmap/tile_" + id + "_" + suffix),
          size, size,
          SAMPLER
      );
    }

    static void dispose(DynamicTexture texture) {
      texture.release();
      texture.close();
    }
  }

  private static final class DetailTile extends Tile {

    private final DynamicTexture color;
    private final DynamicTexture heightmap;
    private final DynamicTexture lightmap;
    private long revision = -1L;
    private boolean empty = true;

    private DetailTile(int id) {
      this.color = createTexture(id, "color", SECTION_BLOCKS);
      this.heightmap = createTexture(id, "heightmap", SECTION_BLOCKS);
      this.lightmap = createTexture(id, "lightmap", SECTION_BLOCKS);
    }

    private void fill(MapRegion region, int baseX, int baseZ) {
      GameImage colorImage = this.color.getImage();
      GameImage heightmapImage = this.heightmap.getImage();
      GameImage lightmapImage = this.lightmap.getImage();
      boolean empty = true;
      for (int z = 0; z < SECTION_BLOCKS; z++) {
        int localZ = baseZ + z;
        for (int x = 0; x < SECTION_BLOCKS; x++) {
          int localX = baseX + x;
          if (!region.hasColumn(localX, localZ)) {
            colorImage.setARGB(x, z, 0);
            heightmapImage.setARGB(x, z, 0);
            lightmapImage.setARGB(x, z, 0);
            continue;
          }

          empty = false;
          colorImage.setARGB(x, z, region.color(localX, localZ));

          // Same 16 bit encoding as the minimap, offset so negative heights stay positive
          int height = Math.max(0, Math.min(region.height(localX, localZ) + HEIGHT_OFFSET, 0xFFFF));
          heightmapImage.setARGB(x, z, 0xFF000000 | (height & 0xFF) << 16 | (height >> 8) << 8);
          lightmapImage.setARGB(
              x, z,
              MinimapRenderer.lightmapColor(region.blockLightLevel(localX, localZ))
          );
        }
      }

      this.empty = empty;
      this.revision = region.revision();
      if (!empty) {
        this.color.upload();
        this.heightmap.upload();
        this.lightmap.upload();
      }
    }

    @Override
    void dispose() {
      dispose(this.color);
      dispose(this.heightmap);
      dispose(this.lightmap);
    }
  }

  private static final class LodTile extends Tile {

    private final DynamicTexture texture;
    private int @Nullable [] source;
    private long signature;

    private LodTile(int id) {
      this.texture = createTexture(id, "lod", MapLod.SIZE);
    }

    private void fill(int[] pixels) {
      GameImage image = this.texture.getImage();
      for (int y = 0; y < MapLod.SIZE; y++) {
        int row = y * MapLod.SIZE;
        for (int x = 0; x < MapLod.SIZE; x++) {
          image.setARGB(x, y, pixels[row + x]);
        }
      }

      this.texture.upload();
    }

    @Override
    void dispose() {
      dispose(this.texture);
    }
  }
}
