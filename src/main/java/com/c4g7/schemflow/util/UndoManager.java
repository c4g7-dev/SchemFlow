package com.c4g7.schemflow.util;

import org.bukkit.Location;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class UndoManager {
    public static class Action {
        public enum Type { PASTE, DELETE }
        public final Type type;
        public final UUID player;
        public final String group;
        public final String name;
        public final Location at;
        public final Path schemFile;
        public Action(Type type, UUID player, String group, String name, Location at, Path schemFile) {
            this.type = type; this.player = player; this.group = group; this.name = name; this.at = at; this.schemFile = schemFile;
        }
    }

    private final Deque<Action> undo = new ArrayDeque<>();
    private final Deque<Action> redo = new ArrayDeque<>();

    public synchronized void record(Action a) {
        undo.push(a);
        redo.clear();
    }

    public synchronized Action popUndo() { return undo.isEmpty() ? null : undo.pop(); }
    public synchronized void pushRedo(Action a) { redo.push(a); }
    public synchronized Action popRedo() { return redo.isEmpty() ? null : redo.pop(); }
}