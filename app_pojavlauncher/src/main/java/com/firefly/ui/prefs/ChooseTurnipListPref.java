package com.firefly.ui.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import com.firefly.utils.TurnipUtils;

import java.util.Arrays;
import java.util.List;

public class ChooseTurnipListPref extends ListPreference {
    private List<String> defaultLibs;
    private OnPreferenceChangeListener preferenceChangeListener;
    private View.OnClickListener confirmButtonListener;

    public ChooseTurnipListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadDefaultLibs(context);
    }

    private void loadDefaultLibs(Context context) {
        defaultLibs = Arrays.asList(context.getResources().getStringArray(R.array.turnip_values));
    }

    @Override
    protected void onClick() {
        showDialog();
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getDialogTitle());

        CharSequence[] entriesCharSequence = getEntries();
        String[] entries = new String[entriesCharSequence.length];
        for (int i = 0; i < entriesCharSequence.length; i++) {
            entries[i] = entriesCharSequence[i].toString();
        }

        builder.setItems(entries, (dialog, which) -> {
            String newValue = getEntryValues()[which].toString();
            if (preferenceChangeListener != null) {
                if (preferenceChangeListener.onPreferenceChange(this, newValue)) {
                    setValue(newValue);
                }
            } else {
                setValue(newValue);
            }
            dialog.dismiss();
        });

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        
        Button createButton = new Button(getContext());
        createButton.setText(R.string.pgw_settings_custom_turnip_creat);
        createButton.setOnClickListener(view -> {
            if (confirmButtonListener != null) {
                confirmButtonListener.onClick(view);
            }
        });
        layout.addView(createButton);
        builder.setView(layout);

        AlertDialog dialog = builder.create();
        dialog.show();

        ListView listView = dialog.getListView();
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

    public void setConfirmButton(String buttonText, View.OnClickListener listener) {
        this.confirmButtonListener = listener;
    }

    private void showDeleteConfirmationDialog(String version) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.pgw_settings_ctu_delete_title)
                .setMessage(getContext().getString(R.string.pgw_settings_ctu_delete_message, version))
                .setPositiveButton(R.string.alertdialog_done, (dialog, which) -> {
                    boolean success = TurnipUtils.INSTANCE.deleteTurnipDriver(version);
                    if (success) {
                        Toast.makeText(getContext(), R.string.preference_rendererexp_mesa_deleted, Toast.LENGTH_SHORT).show();
                        setEntriesAndValues();
                    } else {
                        Toast.makeText(getContext(), R.string.preference_rendererexp_mesa_delete_fail, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.alertdialog_cancel, null)
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