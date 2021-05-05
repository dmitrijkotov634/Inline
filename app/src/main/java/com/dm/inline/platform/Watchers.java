package com.dm.inline.platform;

import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.util.HashMap;

public class Watchers extends LuaTable {
    private HashMap<LuaValue, LuaValue> watchers;

    public Watchers(HashMap<LuaValue, LuaValue> watchers) {
        this.watchers = watchers;

        LuaTable m = new LuaTable();
        m.set(INDEX, new get());
        m.set(NEWINDEX, new set());
        m.set(LEN, new len());

        this.setmetatable(m);
    }

    public class get extends TwoArgFunction {
        public LuaValue call(LuaValue table, LuaValue name) {
            return watchers.containsKey(name) ? watchers.get(name) : NIL;
        }
    }

    public class len extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(watchers.size());
        }
    }

    public class set extends ThreeArgFunction {
        public LuaValue call(LuaValue table, LuaValue name, LuaValue function) {
            if (function.isnil()) {
                if (watchers.containsKey(name))
                    watchers.remove(name);
            } else {
                watchers.put(name, function);
            }

            return NIL;
        }
    }
}
