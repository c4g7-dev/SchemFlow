package com.c4g7.schemflow.we;

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
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            boolean copyEnts = flags != null ? flags.entities : true;
            copy.setCopyingEntities(copyEnts);
            copy.setRemovingEntities(false);
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
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                editSession.setReorderMode(com.sk89q.worldedit.EditSession.ReorderMode.MULTI_STAGE);
                var op = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
                        .ignoreAirBlocks(ignoreAir)
                        .copyEntities(copyEntities)
                        .copyBiomes(copyBiomes)
                        .build();
                Operations.complete(op);
                return true;
            }
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
     * Paste a preloaded clipboard into a world. MUST run on the server main thread.
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
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            editSession.setReorderMode(EditSession.ReorderMode.MULTI_STAGE);
            Operation op = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(ignoreAir)
                    .copyEntities(copyEntities)
                    .copyBiomes(copyBiomes)
                    .build();
            Operations.complete(op);
        }
    }

    /**
     * Export a cuboid to a {@code .schem} PRESERVING the absolute world origin: the clipboard origin
     * is forced to (0,0,0), so the selection's absolute minimum corner is baked into the schematic
     * offset. A later {@link #pasteClipboard}{@code (..., atOrigin=true)} then lands every block at
     * its original absolute coordinate. Use this for map authoring (ADD #3). Runs on the main thread.
     */
    public static Path exportCuboidPreserveOrigin(Location a, Location b, Path outSchemFile) throws Exception {
        World weWorld = BukkitAdapter.adapt(a.getWorld());
        BlockVector3 min = BlockVector3.at(Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
        BlockVector3 max = BlockVector3.at(Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        CuboidRegion region = new CuboidRegion(weWorld, min, max);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.ZERO);
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
            ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(true);
            copy.setRemovingEntities(false);
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
