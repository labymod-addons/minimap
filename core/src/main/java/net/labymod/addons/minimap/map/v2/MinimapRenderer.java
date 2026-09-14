package net.labymod.addons.minimap.map.v2;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.labymod.addons.minimap.MinimapContext;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.minimap.gui.state.MinimapGuiBlitRenderState;
import net.labymod.addons.minimap.laby3d.MinimapRenderStates;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.laby3d.shader.MinimapUniformBlock;
import net.labymod.addons.minimap.map.v2.texture.CompositeSectionTexture;
import net.labymod.addons.minimap.map.v2.texture.SectionTexture;
import net.labymod.addons.minimap.map.v2.texture.SectionTexture.Variant;
import net.labymod.addons.minimap.map.v2.texture.SectionTextureRepository;
import net.labymod.addons.minimap.util.MinimapDebugFlags;
import net.labymod.addons.minimap.util.PlayerUtil;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.states.GuiTextureSet;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.laby3d.pipeline.material.GuiMaterial;
import net.labymod.api.util.Lazy;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.logging.Logging;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.joml.Vector3f;

public final class MinimapRenderer {

  private static final Logging LOGGER = Logging.getLogger();
  private static final long BUILD_BUDGET_NANOS = 3_000_000L;
  private static final int UNDERGROUND_SURFACE_THRESHOLD = 4;
  private static final int UNDERGROUND_SWITCH_TICKS = 10;
  private final MinimapBounds minimapBounds = new MinimapBounds();
  private final MinimapConfigProvider configProvider;
  private final SectionTextureRepository sectionTextureRepository;
  private final ChunkDataStorage storage;
  private final MinimapUniformBlocks uniformBlocks;
  private final Lazy<Icon> dummyMinimap;

  private DaylightPeriod currentPeriod = DaylightPeriod.DAYTIME;

  private boolean lastUnderground = false;
  private int undergroundSwitchTicks;
  private int lastMidChunkX;
  private int lastMidChunkZ;
  private int lastPlayerY;
  private int lastZoom;
  private int minimumBuildRadius;
  private int lastBuildRadius;
  private boolean changed = true;
  private int[] chunkOrder = new int[0];
  private int chunkOrderRadius = -1;

  public MinimapRenderer(
      MinimapConfigProvider configProvider,
      MinimapContext minimapContext
  ) {
    this.configProvider = configProvider;
    this.sectionTextureRepository = minimapContext.sectionTextureRepository();
    this.storage = minimapContext.storage();
    this.uniformBlocks = minimapContext.uniformBlocks();
    this.dummyMinimap = Lazy.of(
        () -> Icon.texture(Util.newDefaultNamespace("themes/vanilla/textures/dummy_minimap.png"))
    );
  }

  public void tick() {
    this.refreshMinimap();
  }

  public void setZoomSupplier(IntSupplier zoomSupplier) {
  }

  /**
   * Builds chunks at least this many blocks around the player, even if the zoom shows less.
   */
  public void setMinimumBuildRadius(int minimumBuildRadius) {
    this.minimumBuildRadius = minimumBuildRadius;
  }

  public MinimapBounds minimapBounds() {
    return this.minimapBounds;
  }

  public void renderMinimap(BooleanSupplier allowed, Runnable renderer) {
    if (!allowed.getAsBoolean()) {
      return;
    }

    renderer.run();
  }

  public void renderDummyMinimap(
      ScreenContext context,
      float x, float y,
      float width, float height
  ) {
    context.canvas().submitIcon(this.dummyMinimap.get(), x, y, width, height);
  }

  public void render(ScreenContext context, float x, float y, float width, float height) {
    ScreenCanvas canvas = context.canvas();

    // Bounds are in BLOCK coordinates
    int x1 = this.minimapBounds.getX1();
    int z1 = this.minimapBounds.getZ1();
    int x2 = this.minimapBounds.getX2();
    int z2 = this.minimapBounds.getZ2();

    // Skip only when bounds are invalid (note the correct inequality)
    if (x2 <= x1 || z2 <= z1) {
      return;
    }

    // Pixels per BLOCK
    float pxPerBlockX = width / (float) (x2 - x1);
    float pxPerBlockZ = height / (float) (z2 - z1);

    MinimapUniformBlock minimap = this.uniformBlocks.minimap();
    if (minimap == null) {
      return;
    }

    float timeOfDay = this.getTimeOfDay();
    float normalizedDayTime = (float) (1.0F - (Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F
        + 0.2F));
    normalizedDayTime = MathHelper.clamp(normalizedDayTime, 0.0F, 1.0F);
    normalizedDayTime = 1.0F - normalizedDayTime;

    // The shader samples neighbouring block heights one section texel apart
    minimap.pixelSize().set(new Vector3f(
        1.0F / (SectionTextureRepository.SECTION_SIZE * SectionTexture.CHUNK_SIZE_X),
        1.0F / (SectionTextureRepository.SECTION_SIZE * SectionTexture.CHUNK_SIZE_Z),
        0F
    ));
    minimap.dayTime().set(this.lastUnderground ? 1.0F : normalizedDayTime);

    // Convert view bounds to CHUNK coordinates (inclusive range)
    int minChunkX = Math.floorDiv(x1, SectionTexture.CHUNK_SIZE_X);
    int minChunkZ = Math.floorDiv(z1, SectionTexture.CHUNK_SIZE_Z);
    int maxChunkX = Math.floorDiv(x2 - 1, SectionTexture.CHUNK_SIZE_X);
    int maxChunkZ = Math.floorDiv(z2 - 1, SectionTexture.CHUNK_SIZE_Z);

    // Convert CHUNK bounds to SECTION coordinates (inclusive range)
    int minSecX = Math.floorDiv(minChunkX, SectionTextureRepository.SECTION_SIZE);
    int minSecZ = Math.floorDiv(minChunkZ, SectionTextureRepository.SECTION_SIZE);
    int maxSecX = Math.floorDiv(maxChunkX, SectionTextureRepository.SECTION_SIZE);
    int maxSecZ = Math.floorDiv(maxChunkZ, SectionTextureRepository.SECTION_SIZE);

    long now = System.nanoTime();

    // Iterate only visible sections and render the intersecting part
    for (int secX = minSecX; secX <= maxSecX; secX++) {
      for (int secZ = minSecZ; secZ <= maxSecZ; secZ++) {
        CompositeSectionTexture composite = this.sectionTextureRepository.getSectionTexture(
            secX,
            secZ
        );
        if (composite == null) {
          continue; // Skip non-existing sections; don't create new ones here
        }

        // Section rect in BLOCK coordinates
        int secMinBlockX =
            (secX * SectionTextureRepository.SECTION_SIZE) << 4; // * SECTION_SIZE * 16
        int secMinBlockZ = (secZ * SectionTextureRepository.SECTION_SIZE) << 4;
        int secBlocksX = SectionTextureRepository.SECTION_SIZE * 16;
        int secBlocksZ = SectionTextureRepository.SECTION_SIZE * 16;
        int secMaxBlockX = secMinBlockX + secBlocksX;
        int secMaxBlockZ = secMinBlockZ + secBlocksZ;

        // Intersection with view bounds
        int visMinBlockX = Math.max(secMinBlockX, x1);
        int visMinBlockZ = Math.max(secMinBlockZ, z1);
        int visMaxBlockX = Math.min(secMaxBlockX, x2);
        int visMaxBlockZ = Math.min(secMaxBlockZ, z2);

        if (visMaxBlockX <= visMinBlockX || visMaxBlockZ <= visMinBlockZ) {
          continue;
        }

        // UVs relative to this section texture [0..1]
        float u0 = (visMinBlockX - secMinBlockX) / (float) secBlocksX;
        float v0 = (visMinBlockZ - secMinBlockZ) / (float) secBlocksZ;
        float u1 = (visMaxBlockX - secMinBlockX) / (float) secBlocksX;
        float v1 = (visMaxBlockZ - secMinBlockZ) / (float) secBlocksZ;

        // Destination rectangle in pixels
        float dstX = x + (visMinBlockX - x1) * pxPerBlockX;
        float dstY = y + (visMinBlockZ - z1) * pxPerBlockZ;
        float dstW = (visMaxBlockX - visMinBlockX) * pxPerBlockX;
        float dstH = (visMaxBlockZ - visMinBlockZ) * pxPerBlockZ;

        SectionTexture colorTexture = composite.getTexture(SectionTexture.Variant.COLOR);
        SectionTexture heightmapTexture = composite.getTexture(SectionTexture.Variant.HEIGHTMAP);
        SectionTexture lightmapTexture = composite.getTexture(SectionTexture.Variant.LIGHTMAP);
        SectionTexture previousColorTexture = composite.getPreviousTexture(Variant.COLOR);
        SectionTexture previousHeightmapTexture = composite.getPreviousTexture(Variant.HEIGHTMAP);
        SectionTexture previousLightmapTexture = composite.getPreviousTexture(Variant.LIGHTMAP);
        composite.updateFade(now);
        canvas.submitState(
            (pose, scissorArea) ->
                new MinimapGuiBlitRenderState(
                    GuiMaterial.builder(MinimapRenderStates.MINIMAP)
                        .setTexture(0, colorTexture.deviceTextureView())
                        .setTexture(1, heightmapTexture.deviceTextureView())
                        .setTexture(2, lightmapTexture.deviceTextureView())
                        .setTexture(3, composite.fadeTextureView())
                        .setTexture(4, previousColorTexture.deviceTextureView())
                        .setTexture(5, previousHeightmapTexture.deviceTextureView())
                        .setTexture(6, previousLightmapTexture.deviceTextureView())
                        .build(),
                    pose,
                    dstX, dstY, dstW, dstH,
                    u0, v0, u1, v1,
                    -1,
                    scissorArea,
                    this.uniformBlocks
                )
        );

        if (MinimapDebugFlags.DEBUG_SECTIONS) {
          canvas.submitRelativeOutlineRect(
              dstX,
              dstY,
              dstW,
              dstH,
              0.5F, 0xFFFFBF00,
              0xFFFFBF00
          );

          canvas.submitText(
              "Section: " + composite.x() + "," + composite.z(),
              dstX + 2.0F,
              dstY + 2.0F,
              -1,
              0.75F,
              TextRenderingOptions.SHADOW
          );
        }
      }
    }
  }

  private void refreshMinimap() {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    if (MinimapDebugFlags.DEBUG_RESET_KEY && Laby.labyAPI().minecraft().isKeyPressed(Key.O)) {
      this.storage.resetCompilations();
    }

    long dayTime = this.getDayTime();
    this.setDaylightPeriod(DaylightPeriod.findByTime(dayTime));

    ClientWorld level = Laby.labyAPI().minecraft().clientWorld();
    int minBuildHeight = level.getMinBuildHeight();

    Position position = player.position();
    int midX = MathHelper.floor(position.getX());
    int midZ = MathHelper.floor(position.getZ());

    int zoom = this.configProvider.hudWidgetConfig().zoom().get() * 10;
    int minX = midX - zoom;
    int minZ = midZ - zoom;
    int maxX = midX + zoom;
    int maxZ = midZ + zoom;
    int buildRadius = Math.max(zoom, this.minimumBuildRadius);

    int midChunkX = midX >> 4;
    int midChunkZ = midZ >> 4;

    boolean underground = false;

    if (this.configProvider.hudWidgetConfig().caveMode().get()) {
      underground = this.lastUnderground;
      if (PlayerUtil.isPlayerUnderground(level, player, UNDERGROUND_SURFACE_THRESHOLD) == underground) {
        this.undergroundSwitchTicks = 0;
      } else if (++this.undergroundSwitchTicks >= UNDERGROUND_SWITCH_TICKS) {
        this.undergroundSwitchTicks = 0;
        underground = !underground;
      }
    }

    this.storage.setPlayerPosition(player.position(), underground);

    int minChunkX = (midX - buildRadius) >> 4;
    int minChunkZ = (midZ - buildRadius) >> 4;

    int maxChunkX = (midX + buildRadius) >> 4;
    int maxChunkZ = (midZ + buildRadius) >> 4;

    ColorFormat format = ColorFormat.ARGB32;
    if (this.changed || this.storage.shouldProcess()) {
      boolean completed = this.forEach(
          midChunkX, midChunkZ,
          minChunkX, minChunkZ,
          maxChunkX, maxChunkZ,
          (chunkX, chunkZ, chunk) -> {
            this.storage.compile(chunk);

            CompositeSectionTexture texture = this.sectionTextureRepository.getOrCreateSectionTexture(
                chunkX, chunkZ);
            SectionTexture colorTexture = texture.getTexture(Variant.COLOR);
            SectionTexture heightmapTexture = texture.getTexture(Variant.HEIGHTMAP);
            SectionTexture lightmapTexture = texture.getTexture(Variant.LIGHTMAP);

            int localChunkX = Math.floorMod(chunkX, SectionTextureRepository.SECTION_SIZE);
            int localChunkZ = Math.floorMod(chunkZ, SectionTextureRepository.SECTION_SIZE);

            int basePixelX = localChunkX * SectionTexture.CHUNK_SIZE_X;
            int basePixelZ = localChunkZ * SectionTexture.CHUNK_SIZE_Z;

            texture.beginChunkFade(localChunkX, localChunkZ);

            for (int pixelX = 0; pixelX < SectionTexture.CHUNK_SIZE_X; pixelX++) {
              for (int pixelZ = 0; pixelZ < SectionTexture.CHUNK_SIZE_Z; pixelZ++) {
                int destX = basePixelX + pixelX;
                int destZ = basePixelZ + pixelZ;

                int tileColor = chunk.getColor(pixelX, pixelZ);
                int height = chunk.getHeight(pixelX, pixelZ);

                // 16 bit block height. Red holds the low byte, green the high byte.
                int relativeHeight = Math.max(0, Math.min(height - minBuildHeight, 0xFFFF));
                int heightmapColor = 0xFF000000
                    | (relativeHeight & 0xFF) << 16
                    | (relativeHeight >> 8) << 8;
                colorTexture.image().setARGB(destX, destZ, tileColor);
                heightmapTexture.image().setARGB(destX, destZ, heightmapColor);

                int blockLightLevel = chunk.getBlockLightLevel(pixelX, pixelZ);

                int normalizedLightLevel = this.normalize(blockLightLevel);

                boolean noBlockLighting = normalizedLightLevel == 150;

                lightmapTexture.image().setARGB(
                    destX, destZ,
                    format.pack(
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : normalizedLightLevel,
                        noBlockLighting ? 0 : 255
                    )
                );
              }
            }

            texture.updateTexture();
          });

      this.minimapBounds.update(minX, minZ, maxX, maxZ, 0);
      this.storage.processed();
      this.changed = !completed;
    }

    int py = MathHelper.floor(position.getY());
    boolean changeLevel = py < this.lastPlayerY - 5 || py > this.lastPlayerY + 5;
    if ((this.lastMidChunkX != midChunkX
        && this.lastMidChunkZ != midChunkZ)
        || this.lastUnderground != underground
        || this.lastZoom != zoom
        || this.lastBuildRadius != buildRadius
        || changeLevel) {
      this.lastMidChunkX = midChunkX;
      this.lastMidChunkZ = midChunkZ;

      if (this.lastUnderground != underground) {
        this.storage.resetCompilations();
        this.beginTransition();
      }

      if (underground) {
        if (this.lastPlayerY != py) {
          this.storage.resetCompilations();
          this.beginTransition();
        }
      }


      this.lastUnderground = underground;
      this.lastPlayerY = py;


      this.lastZoom = zoom;
      this.lastBuildRadius = buildRadius;
      this.changed = true;
    }
  }

  /**
   * Passes uncompiled chunks to the consumer, nearest to the player first, until
   * {@link #BUILD_BUDGET_NANOS} is used up. Nearest first makes the map grow as a circle.
   *
   * @return {@code false} if the budget ran out before every chunk in bounds was visited
   */
  private boolean forEach(
      int midX, int midZ,
      int minX, int minZ,
      int maxX, int maxZ,
      ChunkConsumer consumer
  ) {
    long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
    int radius = Math.max(
        Math.max(midX - minX, maxX - midX),
        Math.max(midZ - minZ, maxZ - midZ)
    );
    this.updateChunkOrder(radius);
    radius = this.chunkOrderRadius;

    int size = radius * 2 + 1;
    for (int cell : this.chunkOrder) {
      int chunkX = midX + cell / size - radius;
      int chunkZ = midZ + cell % size - radius;
      if (chunkX < minX || chunkX > maxX || chunkZ < minZ || chunkZ > maxZ) {
        continue;
      }

      ChunkData chunk = this.storage.getChunk(chunkX, chunkZ);
      if (chunk == null || this.storage.isCompiled(chunk)) {
        continue;
      }

      consumer.accept(chunkX, chunkZ, chunk);
      if (System.nanoTime() >= deadline) {
        return false;
      }
    }

    return true;
  }

  private void updateChunkOrder(int radius) {
    if (radius <= this.chunkOrderRadius) {
      return;
    }

    int size = radius * 2 + 1;
    long[] keys = new long[size * size];
    for (int cell = 0; cell < keys.length; cell++) {
      long dx = cell / size - radius;
      long dz = cell % size - radius;
      keys[cell] = (dx * dx + dz * dz) << 32 | cell;
    }
    Arrays.sort(keys);

    this.chunkOrder = new int[keys.length];
    for (int i = 0; i < keys.length; i++) {
      this.chunkOrder[i] = (int) keys[i];
    }
    this.chunkOrderRadius = radius;
  }

  private void beginTransition() {
    for (CompositeSectionTexture texture : this.sectionTextureRepository.textures()) {
      texture.beginTransition();
    }
  }

  private int normalize(int value) {
    return this.normalize(value, 0, 15, 10, 255);
  }

  private int normalize(int value, int oldMin, int oldMax, int newMin, int newMax) {
    return (value - oldMin) * (newMax - newMin) / (oldMax - oldMin) + newMin;
  }

  private float getTimeOfDay() {
    long dayTime = this.getDayTime();
    double timeFraction = this.frac(dayTime / 24000.0 - 0.25D);
    double timeOfDay = 0.5D - Math.cos(timeFraction * Math.PI) / 2.0D;
    return (float) (timeFraction * 2.0D + timeOfDay) / 3.0F;
  }

  private double frac(double value) {
    return value - Math.floor(value);
  }

  private void setDaylightPeriod(DaylightPeriod period) {
    if (this.currentPeriod == period) {
      return;
    }

    System.out.println("Changed period from " + this.currentPeriod + " to " + period);
    this.currentPeriod = period;
  }

  private long getDayTime() {
    return Laby.references().clientWorld().getDayTime() % 24000L;
  }

  @FunctionalInterface
  public interface ChunkConsumer {

    void accept(int chunkX, int chunkZ, ChunkData chunk);

  }
}
