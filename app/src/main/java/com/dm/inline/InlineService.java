package com.dm.inline;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.animation.Keyframe;
import android.text.Selection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.LuaTable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import com.dm.inline.ArgumentTokenizer;

public class InlineService extends AccessibilityService {

    public AccessibilityNodeInfo node;

    public Globals env;
    public SharedPreferences db;

    Set<String> defaultPath = new HashSet<String>();
    public HashMap<LuaValue, LuaValue> watchers;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

		AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        info.flags = AccessibilityServiceInfo.DEFAULT |
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
		setServiceInfo(info);

        db = getSharedPreferences("db", MODE_PRIVATE);

        defaultPath.add("/assets/modules/");
        defaultPath.add("/sdcard/inline/");
        defaultPath.add("/sdcard/.inline/");

        env = JsePlatform.standardGlobals();

        LuaValue loader = new loadModules();

        LuaTable inline = new LuaTable();
        inline.set("setselection", new setSelection());
        inline.set("getselection", new getSelection());
        inline.set("copy", new copy());
        inline.set("cut", new cut());
        inline.set("paste", new paste());
        inline.set("ismultiline", new isMultiLine());
        inline.set("getpackage", new getPackage());
        inline.set("settext", new setText());
        inline.set("gettext", new getText());
        inline.set("toast", new toast());
        inline.set("loadmodules", loader);

        LuaTable mwatchers = new LuaTable();
        mwatchers.set(LuaValue.INDEX, new getWatcher());
        mwatchers.set(LuaValue.NEWINDEX, new putWatcher());
        mwatchers.set(LuaValue.LEN, new sizeWatcher());

        inline.set("watchers", (new LuaTable()).setmetatable(mwatchers));

        LuaTable mdb = new LuaTable();
        mdb.set(LuaValue.INDEX, new getStringDefault());
        mdb.set(LuaValue.NEWINDEX, new putKey());

        LuaTable db = new LuaTable();
        db.set("contains", new contains());
        db.set("getstring", new getString());
        db.set("getint", new getInt());
        db.set("getfloat", new getFloat());
        db.set("getlong", new getLong());
        db.set("getboolean", new getBoolean());
        db.set("getstringset", new getStringSet());

        inline.set("db", db.setmetatable(mdb));

        env.set("inline", inline);
        env.set("cake", LuaValue.FALSE);

        loader.call();
    }

	public void onAccessibilityEvent(AccessibilityEvent event) {
        node = event.getSource();

        if (node != null && event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED && node.isEditable()
            && node.getText() != null && (Build.VERSION.SDK_INT >= 26 ? !node.isShowingHintText() : true)) {

            String text = node.getText().toString();

            for (LuaValue watcher : watchers.values()) {
                try {
                    watcher.call(text);
                } catch (Exception e) {
                    logError(e);
                }
            }

            Matcher m = Pattern.compile("\\{.+\\}\\$", Pattern.DOTALL).matcher(text);

            while (m.find()) {
                String[] args = m.group(0).substring(1, m.group(0).length() - 2).split(" ", 2);

                if (args.length > 0) {
                    LuaValue value = env;

                    for (String path : args[0].split("\\.")) {
                        if ((value = value.get(path)).isnil())
                            break;
                    }

                    try {
                        switch (value.type()) {
                            case LuaValue.TFUNCTION:
                                LuaValue result = args.length == 1 ? value.call() : value.call(args[1]);

                                if (result.isnil())
                                    continue;
                                else
                                    text = text.replace(m.group(0), result.tojstring());
                                break;

                            case LuaValue.TNUMBER:
                            case LuaValue.TSTRING:
                            case LuaValue.TBOOLEAN:
                                text = text.replace(m.group(0), value.tojstring());
                                break;

                            default:
                                continue;
                        }

                        Bundle arg = new Bundle();
                        arg.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arg);

                        arg = new Bundle();
                        arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, node.getTextSelectionStart());
                        arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, node.getTextSelectionEnd());
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arg);

                    } catch (Exception e) {
                        logError(e);
                    }
                }
            }
        }
    }

    public void logError(Exception e) {
        Toast toast = Toast.makeText(getApplicationContext(), e.toString(), 0);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        e.printStackTrace();
    }

    @Override
    public void onInterrupt() {}

    public class setSelection extends TwoArgFunction {
        public LuaValue call(LuaValue start, LuaValue end) {
            Bundle args = new Bundle();
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start.toint() - 1);
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end.isnil() ? start.toint() - 1 : end.toint() - 1);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);

            return NIL;
        }
    }

    public class getSelection extends VarArgFunction {
        public Varargs invoke(Varargs v) {
            return varargsOf(valueOf(node.getTextSelectionStart() + 1), 
                             valueOf(node.getTextSelectionEnd() + 1));
        }
    }

    public class cut extends ZeroArgFunction {
        public LuaValue call() {
            node.performAction(AccessibilityNodeInfo.ACTION_CUT);
            return NIL;
        }
    }

    public class copy extends ZeroArgFunction {
        public LuaValue call() {
            node.performAction(AccessibilityNodeInfo.ACTION_COPY);
            return NIL;
        }
    }

    public class paste extends ZeroArgFunction {
        public LuaValue call() {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            return NIL;
        }
    }

    public class getPackage extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(node.getPackageName().toString());
        }
    }

    public class isMultiLine extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(node.isMultiLine());
        }
    }

    public class setText extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.isnil() ? "" : text.tojstring());
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

            return NIL;
        }
    }

    public class toast extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            Toast.makeText(getApplicationContext(), text.tojstring(), 0).show();
            return NIL;
        }
    }

    public class getText extends ZeroArgFunction {
        public LuaValue call() {
            CharSequence text = node.getText();
            return valueOf(text == null ? "" : text.toString());
        }
    }

    public class loadModules extends ZeroArgFunction {
        public void loadFile(String path, File file) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));

                reader.mark(1);
                int ch = reader.read();
                if (ch != 65279)
                    reader.reset();

                String rpath = path + file.getName();
                prepare(env.load(reader, rpath).call(), rpath);
            } catch (Exception e) {
                logError(e);
            }
        }

        public void loadAsset(String path, AssetManager assets) {
            try {
                InputStream stream = assets.open(path);
                byte[] buffer = new byte[stream.available()];
                stream.read(buffer);

                String rpath = "/assets/" + path;
                prepare(env.load(new String(buffer), rpath).call(), rpath);
            } catch (Exception e) {
                logError(e);
            }
        }

        public void prepare(LuaValue value, String path) {
            if (value.isfunction())
                watchers.put(valueOf(path), value);
        }

        public LuaValue call() {
            watchers = new HashMap<LuaValue, LuaValue>();

            for (String path : db.getStringSet("path", defaultPath))
                if (path.startsWith("/assets/")) {
                    AssetManager assets = getResources().getAssets();
                    String assetPath = path.substring(8);

                    String[] files = {};

                    try {
                        files = assets.list(assetPath.substring(0, assetPath.length() - 1));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (files.length > 0)
                        for (String file : files)
                            loadAsset(assetPath + file, assets);
                    else
                        loadAsset(assetPath, assets);
                } else {
                    try {
                        File module = new File(path);

                        if (module.isDirectory()) {
                            for (File file : module.listFiles())
                                if (file.isFile())
                                    loadFile(path, file);
                        } else if (module.isFile()) {
                            loadFile(path, module);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            return TRUE;
        }
    }

    // Watchers

    public class getWatcher extends TwoArgFunction {
        public LuaValue call(LuaValue table, LuaValue name) {
            return watchers.get(name);
        }
    }

    public class sizeWatcher extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(watchers.size());
        }
    }

    public class putWatcher extends ThreeArgFunction {
        public LuaValue call(LuaValue table, LuaValue name, LuaValue watcher) {
            if (watcher.isnil())
                watchers.remove(name);
            else                  
                watchers.put(name, watcher);
            return NIL;
        }
    }

    // DB

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
            Set<String> items = new HashSet<String>();

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
                    Set<String> items = new HashSet<String>();

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
