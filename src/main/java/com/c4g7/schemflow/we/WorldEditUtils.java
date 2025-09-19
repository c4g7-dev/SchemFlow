package com.c4g7.schemflow.we;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
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
}
