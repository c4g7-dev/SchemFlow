package com.c4g7.schemflow.we;

import com.c4g7.schemflow.SchemFlowPlugin;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import org.bukkit.Location;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorldEditUtils {
    public static Path exportCuboid(Location a, Location b, Path outSchemFile) throws Exception {
        return exportCuboid(a, b, outSchemFile, null);
    }

    public static Path exportCuboid(Location a, Location b, Path outSchemFile, WeFlags flags) throws Exception {
        World weWorld = BukkitAdapter.adapt(a.getWorld());
        BlockVector3 min = BlockVector3.at(Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
        BlockVector3 max = BlockVector3.at(Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        CuboidRegion region = new CuboidRegion(weWorld, min, max);

        com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard clipboard = new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(region);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, MapEntities.exportTarget(clipboard), region.getMinimumPoint());
            boolean copyEnts = flags != null ? flags.entities : true;
            copy.setCopyingEntities(copyEnts);
            copy.setRemovingEntities(false);
            // ForwardExtentCopy leaves biomes out unless asked: stored by default (maps.copyBiomes), or with -b
            copy.setCopyingBiomes((flags != null && flags.biomes) || MapEntities.copyBiomes(SchemFlowPlugin.getInstance().getConfig()));
            Operations.complete(copy);
        }

        ClipboardFormat format = ClipboardFormats.findByFile(outSchemFile.toFile());
        if (format == null) {
            format = ClipboardFormats.findByAlias("sponge");
            if (format == null) {
                format = ClipboardFormats.findByAlias("schem");
            }
        }
        if (format == null) throw new IllegalStateException("Could not detect schematic format by file: " + outSchemFile);
        try (OutputStream os = Files.newOutputStream(outSchemFile);
             ClipboardWriter writer = format.getWriter(os)) {
            writer.write(clipboard);
        }
        return outSchemFile;
    }

    public static void paste(Location at, Path schemFile, boolean copyEntities) throws Exception {
        paste(at, schemFile, copyEntities, false, true);
    }

    public static boolean paste(Location at, Path schemFile, boolean copyEntities, boolean ignoreAir, boolean copyBiomes) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile.toFile());
        if (format == null) throw new IllegalStateException("Unknown schematic format: " + schemFile);
        try (var reader = format.getReader(Files.newInputStream(schemFile))) {
            var clipboard = reader.read();
            World weWorld = BukkitAdapter.adapt(at.getWorld());
            BlockVector3 to = BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            SchemFlowPlugin plugin = SchemFlowPlugin.getInstance();
            MapEntities.Paste riders = copyEntities && MapEntities.restorePassengers(plugin.getConfig())
                    ? MapEntities.plan(clipboard, to) : null;
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                editSession.setReorderMode(com.sk89q.worldedit.EditSession.ReorderMode.MULTI_STAGE);
                var op = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                        .createPaste(riders != null ? riders.wrap(editSession) : editSession)
                        .to(to)
                        .ignoreAirBlocks(ignoreAir)
                        .copyEntities(copyEntities)
                        .copyBiomes(copyBiomes && clipboard.hasBiomes()) // no stored biomes: keep the world's own
                        .build();
                Operations.complete(op);
            }
            // after close(): FAWE only writes the blocks when the session is flushed
            if (riders != null) riders.spawn(plugin, at.getWorld());
            PasteLighting.afterPaste(plugin, at.getWorld(), PasteLighting.pastedRegion(weWorld, clipboard, to));
            return true;
        }
    }

    /** Read a clipboard from a {@code .schem} file. Pure I/O — safe to call OFF the main thread. */
    public static Clipboard readClipboard(Path schemFile) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(schemFile.toFile());
        if (format == null) throw new IllegalStateException("Unknown schematic format: " + schemFile);
        try (var reader = format.getReader(Files.newInputStream(schemFile))) {
            return reader.read();
        }
    }

    /**
     * Paste a preloaded clipboard into a world. With FastAsyncWorldEdit this is safe (and strongly
     * preferred) to call OFF the main thread; with plain WorldEdit it must run on the main thread.
     *
     * <p>Biomes are written only when the clipboard has them, so an older schematic without biomes leaves
     * the target world's biomes as they are. Entities riding other entities (display-entity models) are
     * spawned with their passengers once the blocks are in (see {@link MapEntities}). The pasted chunks are
     * then relit per the {@code lighting.*} config (see {@link PasteLighting}). Called off the main thread,
     * this only returns once the riders are spawned and the relight is done.
     *
     * @param atOrigin true  &rarr; paste at the clipboard's stored origin (WorldEdit {@code //paste -o}),
     *                          so every block lands at its authored ABSOLUTE coordinate; use this for
     *                          maps exported with {@link #exportCuboidPreserveOrigin}.
     *                 false &rarr; paste so the schematic's minimum corner lands at world (0,0,0).
     */
    public static void pasteClipboard(org.bukkit.World bukkitWorld, Clipboard clipboard,
                                      boolean atOrigin, boolean ignoreAir,
                                      boolean copyEntities, boolean copyBiomes) throws Exception {
        World weWorld = BukkitAdapter.adapt(bukkitWorld);
        BlockVector3 to = atOrigin
                ? clipboard.getOrigin()
                : clipboard.getOrigin().subtract(clipboard.getRegion().getMinimumPoint());
        SchemFlowPlugin plugin = SchemFlowPlugin.getInstance();
        MapEntities.Paste riders = copyEntities && MapEntities.restorePassengers(plugin.getConfig())
                ? MapEntities.plan(clipboard, to) : null;
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            editSession.setReorderMode(EditSession.ReorderMode.MULTI_STAGE);
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(riders != null ? riders.wrap(editSession) : editSession)
                    .to(to)
                    .ignoreAirBlocks(ignoreAir)
                    .copyEntities(copyEntities)
                    .copyBiomes(copyBiomes && clipboard.hasBiomes()) // no stored biomes: keep the world's own
                    .build();
            Operations.complete(op);
        }
        // after close(): FAWE only writes the blocks when the session is flushed. Off the main thread both
        // steps wait, so the paste returns with the models standing and the map already lit.
        if (riders != null) riders.spawn(plugin, bukkitWorld);
        PasteLighting.afterPaste(plugin, bukkitWorld, PasteLighting.pastedRegion(weWorld, clipboard, to));
    }

    /**
     * Export a cuboid to a {@code .schem} PRESERVING the absolute world origin: the clipboard origin
     * is forced to (0,0,0), so the selection's absolute minimum corner is baked into the schematic
     * offset. A later {@link #pasteClipboard}{@code (..., atOrigin=true)} then lands every block at
     * its original absolute coordinate. Use this for map authoring (ADD #3). Stores biomes (unless
     * {@code maps.copyBiomes} is off) and keeps riding entities inside their vehicle only (see
     * {@link MapEntities}). Runs on the main thread.
     */
    public static Path exportCuboidPreserveOrigin(Location a, Location b, Path outSchemFile) throws Exception {
        World weWorld = BukkitAdapter.adapt(a.getWorld());
        BlockVector3 min = BlockVector3.at(Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
        BlockVector3 max = BlockVector3.at(Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.ZERO);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, MapEntities.exportTarget(clipboard), region.getMinimumPoint());
            copy.setCopyingEntities(true);
            copy.setRemovingEntities(false);
            // ForwardExtentCopy leaves biomes out unless asked: maps came back with the void world's biomes
            copy.setCopyingBiomes(MapEntities.copyBiomes(SchemFlowPlugin.getInstance().getConfig()));
            Operations.complete(copy);
        }
        ClipboardFormat format = ClipboardFormats.findByFile(outSchemFile.toFile());
        if (format == null) {
            format = ClipboardFormats.findByAlias("sponge");
            if (format == null) format = ClipboardFormats.findByAlias("schem");
        }
        if (format == null) throw new IllegalStateException("Could not detect schematic format by file: " + outSchemFile);
        try (OutputStream os = Files.newOutputStream(outSchemFile);
             ClipboardWriter writer = format.getWriter(os)) {
            writer.write(clipboard);
        }
        return outSchemFile;
    }
}
