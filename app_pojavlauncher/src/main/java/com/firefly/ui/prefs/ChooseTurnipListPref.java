package com.firefly.ui.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import com.firefly.ui.dialog.CustomDialog;
import com.firefly.utils.TurnipUtils;

import java.util.Arrays;
import java.util.List;

public class ChooseTurnipListPref extends ListPreference {
    private List<String> defaultLibs;
    private OnPreferenceChangeListener preferenceChangeListener;
    private View.OnClickListener importButtonListener;

    public ChooseTurnipListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadDefaultLibs(context);
    }

    private void loadDefaultLibs(Context context) {
        defaultLibs = Arrays.asList(context.getResources().getStringArray(R.array.turnip_values));
    }

    @Override
    protected void onClick() {
        String initialValue = getValue();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getDialogTitle());

        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 20, 50, 20);

        ListView listView = new ListView(getContext());
        CharSequence[] entriesCharSequence = getEntries();
        String[] entries = new String[entriesCharSequence.length];
        for (int i = 0; i < entriesCharSequence.length; i++) {
            entries[i] = entriesCharSequence[i].toString();
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, entries);
        listView.setAdapter(adapter);

        mainLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.CENTER);

        Button importButton = new Button(getContext());
        importButton.setText(R.string.pgw_settings_custom_turnip_creat);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        buttonLayout.addView(importButton, buttonParams);

        mainLayout.addView(buttonLayout);

        builder.setView(mainLayout);

        AlertDialog dialog = builder.create();
        dialog.show();

        importButton.setOnClickListener(v -> {
            if (importButtonListener != null) {
                importButtonListener.onClick(v);
            }
            dialog.dismiss();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String newValue = getEntryValues()[position].toString();
            if (!newValue.equals(initialValue)) {
                if (getOnPreferenceChangeListener() != null) {
                    if (getOnPreferenceChangeListener().onPreferenceChange(this, newValue)) {
                        setValue(newValue);
                    }
                } else {
                    setValue(newValue);
                }
            }
            dialog.dismiss();
        });

        listView.setOnItemLongClickListener((adapterView, view, position, id) -> {
            String selectedVersion = getEntryValues()[position].toString();
            if (defaultLibs.contains(selectedVersion)) {
                Toast.makeText(getContext(), R.string.preference_rendererexp_mesa_delete_defaultlib, Toast.LENGTH_SHORT).show();
            } else {
                showDeleteConfirmationDialog(selectedVersion);
            }
            dialog.dismiss();
            return true;
        });
    }

    @Override
    public void setOnPreferenceChangeListener(OnPreferenceChangeListener listener) {
        this.preferenceChangeListener = listener;
        super.setOnPreferenceChangeListener(listener);
    }

    public void setImportButton(String buttonText, View.OnClickListener listener) {
        this.importButtonListener = listener;
    }

    private void showDeleteConfirmationDialog(String version) {
        new CustomDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.pgw_settings_ctu_delete_title))
                .setMessage(getContext().getString(R.string.pgw_settings_ctu_delete_message, version))
                .setConfirmListener(android.R.string.ok, customView -> {
                    boolean success = TurnipUtils.INSTANCE.deleteTurnipDriver(version);
                    if (success) {
                        Toast.makeText(getContext(), R.string.preference_rendererexp_mesa_deleted, Toast.LENGTH_SHORT).show();
                        setEntriesAndValues();
                    } else {
                        Toast.makeText(getContext(), R.string.preference_rendererexp_mesa_delete_fail, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                })
                .setCancelListener(android.R.string.cancel, customView -> true)
                .build()
                .show();
    }

    private void setEntriesAndValues() {
        Tools.IListAndArry array = Tools.getCompatibleCTurnipDriver(getContext());
        setEntries(array.getArray());
        setEntryValues(array.getList().toArray(new String[0]));
        String currentValue = getValue();
        if (!array.getList().contains(currentValue)) {
            setValueIndex(0);
        }
    }
}