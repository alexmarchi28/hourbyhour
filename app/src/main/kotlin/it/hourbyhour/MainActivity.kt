package it.hourbyhour

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView

/** Asks for calendar access, offers to add the widget and shows a preview of it. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var grantButton: Button
    private lateinit var preview: WidgetPreview
    private var deniedForever = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        grantButton = findViewById(R.id.grant)
        preview = WidgetPreview(this, findViewById(R.id.preview))

        grantButton.setOnClickListener {
            if (deniedForever) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
            }
        }

        val widgets = AppWidgetManager.getInstance(this)
        findViewById<Button>(R.id.add_widget).apply {
            visibility = if (widgets.isRequestPinAppWidgetSupported) View.VISIBLE else View.GONE
            setOnClickListener { widgets.requestPinAppWidget(ComponentName(context, HourByHourWidget::class.java), null, null) }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQUEST_CALENDAR) return
        // After repeated denials Android stops showing the dialog: from then on, send the user to the settings.
        deniedForever = grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
        refresh()
    }

    private fun refresh() {
        val granted = CalendarSource.hasPermission(this)
        status.setText(if (granted) R.string.status_ready else R.string.status_needs_permission)
        grantButton.visibility = if (granted) View.GONE else View.VISIBLE
        grantButton.setText(if (deniedForever) R.string.open_settings else R.string.grant_access)
        // Calendar access may have changed since the widgets were last drawn.
        val context = applicationContext
        preview.reload { WidgetUpdater.updateAll(context) }
    }

    private companion object {
        const val REQUEST_CALENDAR = 1
    }
}
