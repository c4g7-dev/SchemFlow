package com.c4g7.schemflow.world;

/**
 * Lightweight, reflection-friendly value holder describing a schematic.
 *
 * <p>{@code width/height/length} are the schematic's block dimensions. {@code originX/Y/Z} hold the
 * authored ABSOLUTE minimum corner of the selection for schematics saved with absolute origin (see
 * {@link WorldProvisioner#saveSelectionAsMap}); for legacy schematics this is the relative offset
 * (typically 0,0,0). A caller pasting at a fixed point can map an authored coordinate {@code C} to
 * its pasted coordinate via {@code C - (originX, originY, originZ)}.
 */
public final class SchematicInfo {
    public final int width;
    public final int height;
    public final int length;
    public final int originX;
    public final int originY;
    public final int originZ;

    public SchematicInfo(int width, int height, int length, int originX, int originY, int originZ) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }
    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getOriginZ() { return originZ; }

    @Override
    public String toString() {
        return "SchematicInfo{" + width + "x" + height + "x" + length
                + ", origin=(" + originX + "," + originY + "," + originZ + ")}";
    }
}
