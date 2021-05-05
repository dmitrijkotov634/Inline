package com.dm.inline.platform;

import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ClipData;

public class Clipboard extends LuaTable {

    public Context context;
    public ClipboardManager clipboard;

    public Clipboard(Context context, ClipboardManager manager) {
        this.context = context;
        this.clipboard = manager;

        set("sethtml", new setHtml());
        set("set", new setClip());
        set("get", new getClip());
        set("has", new hasClip());
        set("clear", new clearClip());
    }

    public class setHtml extends TwoArgFunction {
        public LuaValue call(LuaValue text, LuaValue html) {
            clipboard.setPrimaryClip(ClipData.newHtmlText(null, text.tojstring(), html.tojstring()));
            return NIL;
        }
    }

    public class setClip extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text.tojstring()));
            return NIL;
        }
    }

    public class getClip extends ZeroArgFunction {
        public LuaValue call() {
            ClipData clip = clipboard.getPrimaryClip();
            return valueOf(clip.getItemAt(0).coerceToHtmlText(context));
        }
    }

    public class hasClip extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(clipboard.hasPrimaryClip());
        }
    }

    public class clearClip extends ZeroArgFunction {
        public LuaValue call() {
            clipboard.clearPrimaryClip();
            return NIL;
        }
    }
}
