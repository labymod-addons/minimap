package net.labymod.addons.minimap.map.v2;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.labymod.addons.minimap.MinimapContext;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.map.MinimapBounds;
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
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.client.gui.screen.state.states.GuiTextureSet;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.logging.Logging;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.joml.Vector3f;

public final class MinimapRenderer {

  private static final Logging LOGGER = Logging.getLogger();
  private final MinimapBounds minimapBounds = new MinimapBounds();
  private final MinimapConfigProvider configProvider;
  private final SectionTextureRepository sectionTextureRepository;
  private final ChunkDataStorage storage;
  private final MinimapUniformBlocks uniformBlocks;

  private DaylightPeriod currentPeriod = DaylightPeriod.DAYTIME;

  private boolean lastUnderground = false;
  private int lastMidChunkX;
  private int lastMidChunkZ;
  private int lastPlayerY;
  private int lastZoom;
  private boolean changed = true;

  public MinimapRenderer(
      MinimapConfigProvider configProvider,
      MinimapContext minimapContext
  ) {
    this.configProvider = configProvider;
    this.sectionTextureRepository = minimapContext.sectionTextureRepository();
    this.storage = minimapContext.storage();
    this.uniformBlocks = minimapContext.uniformBlocks();
  }

  public void tick() {
    this.refreshMinimap();
  }

  public void setZoomSupplier(IntSupplier zoomSupplier) {
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
    minimap.sunPosition().set(new Vector3f(0.9F, -1.0F, 1F));
    float timeOfDay = this.getTimeOfDay();
    float normalizedDayTime = (float) (1.0F - (Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F + 0.2F));
    normalizedDayTime = MathHelper.clamp(normalizedDayTime, 0.0F, 1.0F);
    normalizedDayTime = 1.0F - normalizedDayTime;

    minimap.pixelSize().set(new Vector3f(1.0F / width, 1.0F / height, 0F));
    minimap.dayTime().set(normalizedDayTime);

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
        canvas.submitState(
            (pose, scissorArea) ->
                new MinimapGuiBlitRenderState(
                    MinimapRenderStates.MINIMAP,
                    pose,
                    GuiTextureSet.builder()
                        .setTexture(0, colorTexture.deviceTextureView())
                        .setTexture(1, heightmapTexture.deviceTextureView())
                        .setTexture(2, lightmapTexture.deviceTextureView())
                        .build(),
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
              TextFlags.SHADOW
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

    if (Laby.labyAPI().minecraft().isKeyPressed(Key.O)) {
      this.storage.resetCompilations();
    }

    long dayTime = this.getDayTime();
    this.setDaylightPeriod(DaylightPeriod.findByTime(dayTime));

    ClientWorld level = Laby.labyAPI().minecraft().clientWorld();
    int minBuildHeight = level.getMinBuildHeight();
    int maxBuildHeight = level.getMaxBuildHeight();

    Position position = player.position();
    int midX = MathHelper.floor(position.getX());
    int midZ = MathHelper.floor(position.getZ());

    int zoom = this.configProvider.hudWidgetConfig().zoom().get() * 10;
    int minX = midX - zoom;
    int minZ = midZ - zoom;
    int maxX = midX + zoom;
    int maxZ = midZ + zoom;

    int midChunkX = midX >> 4;
    int midChunkZ = midZ >> 4;

    boolean underground = false;
    this.storage.setPlayerPosition(player.position(), false);

    int minChunkX = minX >> 4;
    int minChunkZ = minZ >> 4;

    int maxChunkX = maxX >> 4;
    int maxChunkZ = maxZ >> 4;

    ColorFormat format = ColorFormat.ARGB32;
    if (this.changed || this.storage.shouldProcess()) {
      this.changed = false;

      this.forEach(
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

            for (int pixelX = 0; pixelX < SectionTexture.CHUNK_SIZE_X; pixelX++) {
              for (int pixelZ = 0; pixelZ < SectionTexture.CHUNK_SIZE_Z; pixelZ++) {
                int destX = basePixelX + pixelX;
                int destZ = basePixelZ + pixelZ;

                int tileColor = chunk.getColor(pixelX, pixelZ);
                int height = chunk.getHeight(pixelX, pixelZ);

                float normalized =
                    (height - minBuildHeight) * (1.0F - 0.0F) / (maxBuildHeight - minBuildHeight)
                        + 0.0F;

                int heightmapColor = format.pack(normalized, normalized, normalized, 1.0F);
                colorTexture.image().setARGB(destX, destZ, tileColor);
                colorTexture.image().setARGB(destX, destZ, tileColor);
                colorTexture.image().setARGB(destX, destZ, tileColor);
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
    }

    int py = MathHelper.floor(position.getY());
    boolean changeLevel = py < this.lastPlayerY - 5 || py > this.lastPlayerY + 5;
    if ((this.lastMidChunkX != midChunkX
        && this.lastMidChunkZ != midChunkZ)
        || this.lastUnderground != underground
        || this.lastZoom != zoom
    || changeLevel) {
      this.lastMidChunkX = midChunkX;
      this.lastMidChunkZ = midChunkZ;

      this.lastZoom = zoom;
      this.changed = true;
    }
  }

  private void forEach(int minX, int minZ, int maxX, int maxZ, ChunkConsumer consumer) {
    for (int chunkX = minX; chunkX <= maxX; chunkX++) {
      for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
        ChunkData chunk = this.storage.getChunk(chunkX, chunkZ);
        if (chunk == null || this.storage.isCompiled(chunk)) {
          continue;
        }

        consumer.accept(chunkX, chunkZ, chunk);
      }
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
