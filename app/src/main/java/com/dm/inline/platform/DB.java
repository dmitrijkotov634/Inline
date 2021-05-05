package com.dm.inline.platform;

import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class DB extends LuaTable {

    private SharedPreferences db;

    public DB(SharedPreferences db) {
        this.db = db;

        LuaValue put = new putKey();
        this.set("put", put);
        this.set("contains", new contains());
        this.set("getstring", new getString());
        this.set("getint", new getInt());
        this.set("getfloat", new getFloat());
        this.set("getlong", new getLong());
        this.set("getboolean", new getBoolean());
        this.set("getstringset", new getStringSet());

        LuaTable m = new LuaTable();
        m.set(LuaValue.INDEX, new getStringDefault());
        m.set(LuaValue.NEWINDEX, put);

        this.setmetatable(m);
    }

    public class getStringDefault extends TwoArgFunction {
        public LuaValue call(LuaValue table, LuaValue key) {
            return valueOf(db.getString(key.tojstring(), ""));
        }
    }

    public class getString extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            return valueOf(db.getString(key.tojstring(), defaultVal.isstring() ? defaultVal.tojstring() : ""));
        }
    }

    public class getStringSet extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            HashSet<String> items = new HashSet<String>();

            if (defaultVal.istable()) {
                LuaValue k = NIL;
                while (true) {
                    Varargs n = defaultVal.next(k);
                    if ((k = n.arg1()).isnil() || !k.isint())
                        break;
                    LuaValue v = n.arg(2);

                    items.add(v.tojstring());
                }
            }

            LuaTable table = new LuaTable();
            Set<String> set = db.getStringSet(key.tojstring(), items);

            int index = 1;
            for (String item : set) {
                table.set(index++, valueOf(item));
            }

            return table;
        }
    }

    public class getInt extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            return valueOf(db.getInt(key.tojstring(), defaultVal.isnumber() ? defaultVal.toint() : 0));
        }
    }

    public class getFloat extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            return valueOf(db.getFloat(key.tojstring(), defaultVal.isnumber() ? defaultVal.tofloat() : 0));
        }
    }

    public class getLong extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            return valueOf(db.getLong(key.tojstring(), defaultVal.isnumber() ? defaultVal.tolong() : 0));
        }
    }

    public class getBoolean extends TwoArgFunction {
        public LuaValue call(LuaValue key, LuaValue defaultVal) {
            return valueOf(db.getBoolean(key.tojstring(), defaultVal.toboolean()));
        }
    }

    public class contains extends OneArgFunction {
        public LuaValue call(LuaValue key) {
            return valueOf(db.contains(key.tojstring()));
        }
    }

    public class putKey extends ThreeArgFunction {
        public LuaValue call(LuaValue table, LuaValue key, LuaValue value) {
            if (key.isstring())
                if (value.isnil())
                    db.edit().remove(key.tojstring()).apply();
                else
                if (value.isnumber()) {
                    if (value.isint())
                        db.edit().putInt(key.tojstring(), value.toint()).apply();
                    if (value.islong())
                        db.edit().putLong(key.tojstring(), value.tolong()).apply();
                    else
                        db.edit().putFloat(key.tojstring(), value.tofloat()).apply();

                } else if (value.istable()) {
                    HashSet<String> items = new HashSet<String>();

                    LuaValue k = NIL;
                    while (true) {
                        Varargs n = value.next(k);
                        if ((k = n.arg1()).isnil() || !k.isint())
                            break;
                        LuaValue v = n.arg(2);

                        items.add(v.tojstring());
                    }

                    db.edit().putStringSet(key.tojstring(), items).apply();

                } else if (value.isboolean())
                    db.edit().putBoolean(key.tojstring(), value.toboolean()).apply();
                else
                    db.edit().putString(key.tojstring(), value.tojstring()).apply();

            return NIL;
        }
    }
}
