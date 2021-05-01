package com.dm.inline.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;
import android.widget.AdapterView;
import android.view.View;
import android.widget.Adapter;
import java.util.ArrayList;
import com.dm.inline.R;
import com.dm.inline.InlineService;

public class ExplorerActivity extends Activity {

    public InlineService service;
    public ListView explorer;
    public ExploreAdapter adapter;

    public int lastPos = 0;

    public ArrayList<LuaValue> path = new ArrayList<LuaValue>();
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ExplorerActivity);

        explorer = findViewById(R.id.explorer);
        service = InlineService.getSharedInstance();

        explorer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Varargs item = adapter.getItem(position);

                    if (item.arg(2).istable()) {
                        path.add(item.arg1());

                        lastPos = position;
                        show(find(service.env, path));
                        updateTitle(path);
                    }
                }
            });

        if (service != null)
            show(service.env);
    }

    public void onBackPressed() {
        if (path.isEmpty()) {
            super.onBackPressed();
        } else {
            path.remove(path.size() - 1);
            show(find(service.env, path));
            updateTitle(path);

            explorer.setSelection(lastPos);
        }
    }

    public LuaValue find(LuaValue value, ArrayList<LuaValue> path) {
        for (LuaValue item : path) {
            if (value.istable())
                value = value.get(item);
        }
        return value;
    }

    public void updateTitle(ArrayList<LuaValue> path) {
        StringBuilder title = new StringBuilder();
        title.append("/");
        for (LuaValue item : path) {
            title.append(item.tojstring());
            title.append("/");
        }

        getWindow().setTitle(title);
    }

    public void show(LuaValue value) {
        ArrayList<Varargs> data = new ArrayList<Varargs>();
        LuaValue k = LuaValue.NIL;
        while (true) {
            Varargs n = value.next(k);
            if ((k = n.arg1()).isnil())
                break;

            data.add(n);
        }

        adapter = new ExploreAdapter(getApplicationContext(), data);
        explorer.setAdapter(adapter);
    }
}
