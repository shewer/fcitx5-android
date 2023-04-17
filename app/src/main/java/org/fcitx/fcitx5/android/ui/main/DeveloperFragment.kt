package org.fcitx.fcitx5.android.ui.main

import android.os.Bundle
import android.os.Debug
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.iso8601UTCDateTime
import org.fcitx.fcitx5.android.utils.toast
import org.fcitx.fcitx5.android.utils.withTempDir
import java.io.File

class DeveloperFragment : PaddingPreferenceFragment() {

    private lateinit var hprofFile: File
    private lateinit var launcher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext()
        launcher = registerForActivityResult(CreateDocument("application/octet-stream")) { uri ->
            lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                uri?.runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { o ->
                        hprofFile.inputStream().use { i -> i.copyTo(o) }
                    }
                }?.toast(ctx)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(R.string.real_time_logs) {
                AppUtil.launchLog(context)
            }
            addPreference(SwitchPreference(context).apply {
                key = AppPrefs.getInstance().internal.verboseLog.key
                setTitle(R.string.verbose_log)
                setSummary(R.string.verbose_log_summary)
                setDefaultValue(false)
                isIconSpaceReserved = false
                isSingleLineTitle = false
            })
            addPreference(R.string.delete_and_sync_data) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.delete_and_sync_data)
                    .setMessage(R.string.delete_and_sync_data_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            DataManager.deleteAndSync()
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    getString(R.string.synced),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            addPreference(R.string.clear_clb_db) {
                lifecycleScope.launch {
                    ClipboardManager.nukeTable()
                    Toast.makeText(context, getString(R.string.done), Toast.LENGTH_SHORT).show()
                }
            }
            addPreference(R.string.capture_heap_dump) {
                withTempDir {
                    val fileName = "${context.packageName}_${iso8601UTCDateTime()}.hprof"
                    hprofFile = it.resolve(fileName)
                    System.gc()
                    Debug.dumpHprofData(hprofFile.absolutePath)
                    launcher.launch(fileName)
                }
            }
        }
    }

}