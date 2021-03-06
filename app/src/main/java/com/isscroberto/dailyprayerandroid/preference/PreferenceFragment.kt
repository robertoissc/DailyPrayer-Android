package com.isscroberto.dailyprayerandroid.preference

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.google.firebase.analytics.FirebaseAnalytics
import com.isscroberto.dailyprayerandroid.BuildConfig
import com.isscroberto.dailyprayerandroid.R
import com.isscroberto.dailyprayerandroid.alarm.AlarmReceiver
import com.isscroberto.dailyprayerandroid.analytics.AnalyticsHelper
import com.isscroberto.dailyprayerandroid.analytics.EventType
import java.util.*

class PreferenceFragment : PreferenceFragmentCompat(), BillingProcessor.IBillingHandler, com.wdullaer.materialdatetimepicker.time.TimePickerDialog.OnTimeSetListener {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var mBillingProcessor: BillingProcessor? = null

    override fun onCreatePreferences(p0: Bundle?, p1: String?) {
        addPreferencesFromResource(R.xml.preferences)

        // Verify if ads are enabled.
        val adsEnabled = requireActivity().getSharedPreferences("com.isscroberto.dailyprayerandroid", Context.MODE_PRIVATE).getBoolean("AdsEnabled", true)
        if (adsEnabled) {
            // Initialize the billing processor.
            mBillingProcessor = BillingProcessor(activity, getString(R.string.billing_license_key), this)
            // Add click listener to preference.
            val preferenceAds = findPreference<Preference>("preference_ads")
            preferenceAds?.setOnPreferenceClickListener {
                if (BuildConfig.DEBUG) {
                    mBillingProcessor!!.purchase(activity, "android.test.purchased")
                } else {
                    mBillingProcessor!!.purchase(activity, "com.isscroberto.dailyprayerandroid.removeads")
                }
                true
            }
        } else {
            val preferenceAds = findPreference<Preference>(getString(R.string.preference_ads))
            val preferenceCategory = findPreference<PreferenceCategory>(getString(R.string.preference_category_general))
            preferenceCategory?.removePreference(preferenceAds)
        }

        // Add click listener to preferences.
        val preferencePrivacy = findPreference<Preference>(getString(R.string.preference_privacy))
        preferencePrivacy?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://isscroberto.com/daily-bible-privacy-policy/")))
            true
        }
        val preferenceMoreApps = findPreference<Preference>(getString(R.string.preference_apps))
        preferenceMoreApps?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:isscroberto")))
            true
        }
        val preferenceReminder = findPreference<Preference>(getString(R.string.preference_reminder)) as CheckBoxPreference
        preferenceReminder.setOnPreferenceClickListener {
            if(preferenceReminder.isChecked) {
                val tdp = com.wdullaer.materialdatetimepicker.time.TimePickerDialog.newInstance(this@PreferenceFragment, true)
                tdp.show(parentFragmentManager, "Timepickerdialog")
                tdp.setOnCancelListener {
                    preferenceReminder.isChecked = false
                }
            } else {
                cancelReminder()
            }
            true
        }

        setPreferenceReminderSummary()

    }

    override fun onDestroy() {
        if (mBillingProcessor != null) {
            mBillingProcessor!!.release()
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!mBillingProcessor!!.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onProductPurchased(productId: String, details: TransactionDetails?) {
        // Product was purchased succesfully.
        disableAds()
    }

    override fun onPurchaseHistoryRestored() {}

    override fun onBillingError(errorCode: Int, error: Throwable?) {}

    override fun onBillingInitialized() {
        // Verify if user already removed ads.
        val purchased = if(BuildConfig.DEBUG) mBillingProcessor!!.isPurchased("android.test.purchased") else mBillingProcessor!!.isPurchased("com.isscroberto.dailyprayerandroid.removeads")

        if (purchased) {
            disableAds()
            Toast.makeText(activity, "Ads Removed!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTimeSet(view: com.wdullaer.materialdatetimepicker.time.TimePickerDialog?, hourOfDay: Int, minute: Int, second: Int) {
       setReminder(hourOfDay, minute)
    }

    private fun setPreferenceReminderSummary() {
        val preferenceReminder = findPreference<CheckBoxPreference>(getString(R.string.preference_reminder))
        if(preferenceReminder?.isChecked == true) {
            val time = requireActivity().getSharedPreferences("com.isscroberto.dailyprayerandroid", Context.MODE_PRIVATE).getString("ReminderTime", "")
            preferenceReminder.summary = "Your daily prayer reminder is set at $time"
        } else {
            preferenceReminder?.summary = getString(R.string.settings_prayer_reminder_summary)
        }
    }

    private fun setReminder(hourOfDay: Int, minute: Int) {
        // Create alarm components.
        val alarmManager = requireActivity().getSystemService(Activity.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(requireContext().applicationContext, AlarmReceiver::class.java).apply {
            action = requireContext().getString(R.string.action_prayer_reminder)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        // Set clanedar with time for alarm.
        val calendar: Calendar = Calendar.getInstance(Locale.getDefault()).apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
        }

        // Set repeating alarm.
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)

        // Update summary.
        val time = String.format("%02d:%02d", hourOfDay, minute)
        val editor = requireActivity().getSharedPreferences("com.isscroberto.dailyprayerandroid", Context.MODE_PRIVATE).edit()
        editor.putString("ReminderTime", time)
        editor.apply()
        setPreferenceReminderSummary()

        // Log event.
        firebaseAnalytics = FirebaseAnalytics.getInstance(requireContext())
        val bundle = Bundle()
        bundle.putString("time", time)
        AnalyticsHelper.LogEvent(firebaseAnalytics, EventType.Reminder, bundle);
    }

    private fun cancelReminder() {
        // Create alarm components.
        val alarmManager = requireActivity().getSystemService(Activity.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(requireContext().applicationContext, AlarmReceiver::class.java).apply {
            action = requireContext().getString(R.string.action_prayer_reminder)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0)

        // Cancel any previous alarms.
        alarmManager.cancel(pendingIntent)

        // Update summary.
        val editor = requireActivity().getSharedPreferences("com.isscroberto.dailyprayerandroid", Context.MODE_PRIVATE).edit()
        editor.remove("ReminderTime")
        editor.apply()
        setPreferenceReminderSummary()
    }

    private fun disableAds() {
        val editor = requireActivity().getSharedPreferences("com.isscroberto.dailyprayerandroid", Context.MODE_PRIVATE).edit()
        editor.putBoolean("AdsEnabled", false)
        editor.apply()

        val preferenceAds = findPreference<Preference>("preference_ads")
        val preferenceCategory = findPreference<PreferenceCategory>("preference_general")
        preferenceCategory?.removePreference(preferenceAds)
    }

}