package pro.sketchware.activities.editor.view;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import a.a.a.Lx;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML = "xml";
    public static final String SCHEME_JAVA = "java";

    private ActivityCodeViewerBinding binding;
    private SharedPreferences editorPrefs;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        editorPrefs = getSharedPreferences("dce", MODE_PRIVATE);
        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        var code = getIntent().getStringExtra("code");
        var scheme = getIntent().getStringExtra("scheme");
        var scId = getIntent().getStringExtra("sc_id");

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setSubtitle(scId);

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(Math.max(10, Math.min(24, editorPrefs.getInt("font_size", 14))));
        binding.editor.setText(Lx.j(code == null ? "" : code, false));
        binding.editor.setEditable(false);
        binding.editor.setWordwrap(editorPrefs.getBoolean("word_wrap", false));
        loadColorScheme(scheme);

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "Toggle word wrap")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 2, Menu.NONE, "Increase text size")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 3, Menu.NONE, "Decrease text size")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            boolean enabled = !editorPrefs.getBoolean("word_wrap", false);
            editorPrefs.edit().putBoolean("word_wrap", enabled).apply();
            binding.editor.setWordwrap(enabled);
            return true;
        }
        if (item.getItemId() == 2 || item.getItemId() == 3) {
            int size = editorPrefs.getInt("font_size", 14) + (item.getItemId() == 2 ? 2 : -2);
            size = Math.max(10, Math.min(24, size));
            editorPrefs.edit().putInt("font_size", size).apply();
            binding.editor.setTextSize(size);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadColorScheme(String scheme) {
        if (scheme.equals(SCHEME_XML)) {
            EditorUtils.loadXmlConfig(binding.editor);
        } else {
            EditorUtils.loadJavaConfig(binding.editor);
        }
    }
}