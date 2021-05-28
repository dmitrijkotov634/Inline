package com.dm.inline;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import java.util.ArrayList;
import android.transition.PathMotion;

public class InlineService extends AccessibilityService {
    static final String PATH = "/assets/modules/;/sdcard/inline/;/sdcard/.inline/";

    private static InlineService sharedInstance;

    public ClipboardManager clipboard;
    public Bundle arg;

    public Globals env;
    public SharedPreferences prefs;

    public HashMap<LuaValue, LuaValue> watchers = new HashMap<LuaValue, LuaValue>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

		AccessibilityServiceInfo info = new AccessibilityServiceInfo();

        info.flags = AccessibilityServiceInfo.DEFAULT |
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
		setServiceInfo(info);

        sharedInstance = this;

        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        prefs = getSharedPreferences("inline", MODE_PRIVATE);

        env = JsePlatform.standardGlobals();

        LuaValue loader = new loadModules();

        LuaTable inline = new LuaTable();
        inline.set("toast", new toast());
        inline.set("timer", new InlineTimer());
        inline.set("context", new InlineContext());
        inline.set("prefs", new InlinePrefs(prefs));
        inline.set("loadmodules", loader);

        LuaTable watchers = new LuaTable();
        watchers.set(LuaValue.INDEX, new getWatcher());
        watchers.set(LuaValue.NEWINDEX, new setWatcher());
        watchers.set(LuaValue.LEN, new lenWatcher());

        inline.set("watchers", new LuaTable().setmetatable(watchers));

        LuaTable clipboard = new LuaTable();
        clipboard.set("sethtml", new setHtml());
        clipboard.set("set", new setClip());
        clipboard.set("get", new getClip());
        clipboard.set("has", new hasClip());
        clipboard.set("clear", new clearClip());

        inline.set("clipboard", clipboard);

        LuaTable fmt = new LuaTable();
        fmt.set("fromhtml", new fromHtml());
        fmt.set("fromclipboard", new FromClipboardFlag());

        inline.set("fmt", fmt);

        env.set("inline", inline);
        env.set("cake", LuaValue.FALSE);

        loader.call();
    }

	public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();

        InlineContext context = new InlineContext(node);
        for (LuaValue watcher : watchers.values()) {
            try {
                watcher.call(context);
            } catch (Exception e) {
                logError(e);
            }
        }

        if (Build.VERSION.SDK_INT >= 26 ? !node.isShowingHintText() : true) {

            String text = node.getText() == null ? "" : node.getText().toString();
            Matcher m = Pattern.compile("\\{.+\\}\\$", Pattern.DOTALL).matcher(text);

            expressions:
            while (m.find()) {
                String[] args = m.group(0).substring(1, m.group(0).length() - 2).split(" ", 2);

                if (args.length > 0) {
                    LuaValue value = env;

                    for (String path : args[0].split("\\.")) {
                        if ((value = value.get(path)).isnil())
                            break;
                    }

                    try {
                        find:
                        while (true) {
                            switch (value.type()) {
                                case LuaValue.TFUNCTION:
                                    value = args.length == 1 ? value.call(context) : value.call(context, LuaValue.valueOf(args[1]));
                                    break;

                                case LuaValue.TNUMBER:
                                case LuaValue.TSTRING:
                                case LuaValue.TBOOLEAN:
                                    text = text.replace(m.group(0), value.tojstring());
                                    break find;

                                case 10:
                                    arg = new Bundle();
                                    arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, m.start());
                                    arg.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, m.end());
                                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arg);
                                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE);

                                default:
                                    continue expressions;
                            }
                        }

                        arg = new Bundle();
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

    public void onInterrupt() {}

    public boolean onUnbind(Intent intent) {
        sharedInstance = null;
        return super.onUnbind(intent);
    }

    public static InlineService getSharedInstance() {
        return sharedInstance;
    }

    public void logError(Exception e) {
        Toast toast = Toast.makeText(this, e.toString(), 1);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        e.printStackTrace();
    }

    public class InlineContext extends LuaTable {
        public AccessibilityNodeInfo node;

        public InlineContext(AccessibilityNodeInfo node) {
            this();
            this.node = node;
        }

        public InlineContext() {
            set("settext", new setText());
            set("gettext", new getText());
            set("setselection", new setSelection());
            set("getselection", new getSelection());
            set("cut", new cut());
            set("copy", new copy());
            set("paste", new paste());
            set("isfocused", new isFocused());
            set("ismultiline", new isMultiLine());
            set("isshowinghinttext", new isShowingHintText());
            set("refresh", new refresh());
            set("getpackage", new getPackage());
        }

        public class setText extends TwoArgFunction {
            public LuaValue call(LuaValue self, LuaValue text) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.isnil() ? "" : text.checkjstring());
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                return NIL;
            }
        }

        public class getText extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                CharSequence text = ((InlineContext)self).node.getText();
                return valueOf(text == null ? "" : text.toString());
            }
        }

        public class setSelection extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue start, LuaValue end) {
                Bundle args = new Bundle();
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start.checkint() - 1);
                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end.isnil() ? start.checkint() - 1 : end.checkint() - 1);
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args);
                return NIL;
            }
        }

        public class getSelection extends VarArgFunction {
            public Varargs invoke(Varargs v) {
                return varargsOf(valueOf(((InlineContext)v.arg1()).node.getTextSelectionStart() + 1), 
                                 valueOf(((InlineContext)v.arg1()).node.getTextSelectionEnd() + 1));
            }
        }

        public class cut extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_CUT);
                return NIL;
            }
        }

        public class copy extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_COPY);
                return NIL;
            }
        }

        public class paste extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                return NIL;
            }
        }

        public class isMultiLine extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.isMultiLine());
            }
        }

        public class isShowingHintText extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(Build.VERSION.SDK_INT >= 26 ? ((InlineContext)self).node.isShowingHintText() : false);
            }
        }

        public class isFocused extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.isFocused());
            }
        }

        public class refresh extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineContext)self).node.refresh();
                return NIL;
            }
        }

        public class getPackage extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                return valueOf(((InlineContext)self).node.getPackageName().toString());
            }
        }
    }

    public class InlinePrefs extends LuaTable {
        public SharedPreferences prefs;

        public InlinePrefs(String name) {
            this();
            this.prefs = getSharedPreferences(name, MODE_PRIVATE);
        }

        public InlinePrefs(SharedPreferences sprefs) {
            this();
            this.prefs = sprefs;
        }

        public InlinePrefs() {
            set("getstring", new getString());
            set("getstringset", new getStringSet());
            set("getint", new getInt());
            set("getfloat", new getFloat());
            set("getlong", new getLong());
            set("getboolean", new getBoolean());
            set("contains", new contains());

            LuaTable m = new LuaTable();
            m.set(CALL, new call());
            m.set(INDEX, new getStringDefault());
            m.set(NEWINDEX, new putKey());

            this.setmetatable(m);
        }

        public class call extends TwoArgFunction {
            public LuaValue call(LuaValue self, LuaValue name) {
                return (LuaValue) new InlinePrefs(name.checkjstring());
            }
        }

        public class getStringDefault extends TwoArgFunction {
            public LuaValue call(LuaValue self, LuaValue key) {
                return valueOf(((InlinePrefs)self).prefs.getString(key.checkjstring(), ""));
            }
        }

        public class getString extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                return valueOf(((InlinePrefs)self).prefs.getString(key.checkjstring(), defaultVal.isnil() ? "" : defaultVal.checkjstring()));
            }
        }

        public class getStringSet extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                HashSet<String> items = new HashSet<String>();

                if (!defaultVal.isnil() && defaultVal.checktable() != null) {
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
                Set<String> set = ((InlinePrefs)self).prefs.getStringSet(key.checkjstring(), items);

                int index = 1;
                for (String item : set) {
                    table.set(index++, valueOf(item));
                }

                return table;
            }
        }

        public class getInt extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                return valueOf(((InlinePrefs)self).prefs.getInt(key.checkjstring(), defaultVal.isnil() ? 0 : defaultVal.checknumber().toint()));
            }
        }

        public class getFloat extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                return valueOf(((InlinePrefs)self).prefs.getFloat(key.checkjstring(), defaultVal.isnil() ? 0 : defaultVal.checknumber().tofloat()));
            }
        }

        public class getLong extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                return valueOf(((InlinePrefs)self).prefs.getLong(key.checkjstring(), defaultVal.isnil() ? 0 : defaultVal.checknumber().tolong()));
            }
        }

        public class getBoolean extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue defaultVal) {
                return valueOf(((InlinePrefs)self).prefs.getBoolean(key.checkjstring(), defaultVal.toboolean()));
            }
        }

        public class contains extends TwoArgFunction {
            public LuaValue call(LuaValue self, LuaValue key) {
                return valueOf(((InlinePrefs)self).prefs.contains(key.checkjstring()));
            }
        }

        public class putKey extends ThreeArgFunction {
            public LuaValue call(LuaValue self, LuaValue key, LuaValue value) {
                String k = key.checkjstring();

                SharedPreferences.Editor editor = ((InlinePrefs)self).prefs.edit();

                switch (value.type()) {
                    case TNIL:
                        editor.remove(k).apply();
                        break;

                    case TNUMBER:
                        if (value.isint())
                            editor.putInt(k, value.toint());
                        else if (value.islong())
                            editor.putLong(k, value.tolong());
                        else
                            editor.putFloat(k, value.tofloat());
                        break;
                    case TBOOLEAN:
                        editor.putBoolean(k, value.toboolean());
                        break;
                    case TTABLE:
                        HashSet<String> items = new HashSet<String>();

                        LuaValue q = NIL;
                        while (true) {
                            Varargs n = value.next(q);
                            if ((q = n.arg1()).isnil() || !q.isint())
                                break;
                            LuaValue v = n.arg(2);
                            items.add(v.tojstring());
                        }

                        editor.putStringSet(k, items);
                        break;

                    case TSTRING:
                    default:
                        editor.putString(k, value.tojstring());
                }

                editor.apply();
                return NIL;
            }
        }
    }

    public class InlineTimer extends LuaTable {
        public Timer timer;

        public InlineTimer() {
            this.timer = new Timer();

            set("schedule", new schedule());
            set("cancel", new cancel());

            LuaTable m = new LuaTable();
            m.set(CALL, new call());
            this.setmetatable(m);
        }

        public class call extends ZeroArgFunction {
            public LuaValue call() {
                return (LuaValue) new InlineTimer();
            }
        }

        public class cancel extends OneArgFunction {
            public LuaValue call(LuaValue self) {
                ((InlineTimer)self).timer.cancel();
                return NIL;
            }
        }

        public class schedule extends VarArgFunction {
            public Varargs invoke(final Varargs v) {
                TimerTask task = new TimerTask() {
                    public void run() {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (v.arg(2).call().toboolean())
                                        cancel();
                                }});
                    }
                };

                if (v.arg(4).isnil())
                    ((InlineTimer)v.arg1()).timer.schedule(task, v.arg(3).checkint());
                else
                    ((InlineTimer)v.arg1()).timer.scheduleAtFixedRate(task, v.arg(3).checkint(), v.arg(4).checkint());

                return NIL;
            }
        }
    }

    public class toast extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            Toast.makeText(getApplicationContext(), text.tojstring(), 1).show();
            return NIL;
        }
    }

    public class loadModules extends ZeroArgFunction {
        private AssetManager assets;
        
        public void loadFile(String path, File file) throws IOException {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            reader.mark(1);
            int ch = reader.read();
            if (ch != 65279)
                reader.reset();

            prepare(path, env.load(reader, path).call());

        }

        public void loadAsset(String path) throws IOException {
            InputStream stream = assets.open(path);
            byte[] buffer = new byte[stream.available()];
            stream.read(buffer);

            prepare(path, env.load(new String(buffer), path).call());
        }
        
        public void prepare(String path, LuaValue value) {
            if (value.isfunction())
                watchers.put(valueOf(path), value);
        }
        
        public LuaValue call() {
            watchers.clear();

            try {
                for (String path : prefs.getString("path", PATH).split(";"))
                    if (path.startsWith("/assets/")) {
                        assets = getResources().getAssets();
                        
                        String assetPath = path.substring(8);
                        String[] files = assets.list(assetPath.substring(0, assetPath.length() - 1));

                        if (files.length > 0)
                            for (String file : files)
                                loadAsset(assetPath + file);
                        else
                            loadAsset(assetPath);
                    } else {
                        File module = new File(path);

                        if (module.isDirectory()) {
                            for (File file : module.listFiles())
                                if (file.isFile())
                                    loadFile(path + file.getName(), file);
                        } else if (module.isFile()) {
                            loadFile(path, module);
                        }
                    }
            } catch (Exception e) {
                logError(e);
            }

            return TRUE;
        }
    }


    public class getWatcher extends TwoArgFunction {
        public LuaValue call(LuaValue table, LuaValue name) {
            return watchers.containsKey(name) ? watchers.get(name) : NIL;
        }
    }

    public class lenWatcher extends ZeroArgFunction {
        public LuaValue call() {
            return valueOf(watchers.size());
        }
    }

    public class setWatcher extends ThreeArgFunction {
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


    public class setHtml extends TwoArgFunction {
        public LuaValue call(LuaValue text, LuaValue html) {
            clipboard.setPrimaryClip(ClipData.newHtmlText(null, text.checkjstring(), html.checkjstring()));
            return NIL;
        }
    }

    public class setClip extends OneArgFunction {
        public LuaValue call(LuaValue text) {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text.checkjstring()));
            return NIL;
        }
    }

    public class getClip extends ZeroArgFunction {
        public LuaValue call() {
            ClipData clip = clipboard.getPrimaryClip();
            return valueOf(clip.getItemAt(0).coerceToHtmlText(getApplicationContext()));
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

    public class fromHtml extends OneArgFunction {
        public LuaValue call(LuaValue html) {
            clipboard.setPrimaryClip(ClipData.newHtmlText(null, "", html.checkjstring()));
            return new FromClipboardFlag();
        }
    }

    public class FromClipboardFlag extends LuaValue {
        public int type() {
            return 10;
        }

        public String typename() {
            return "formatflag";
        }
    }
}
        
