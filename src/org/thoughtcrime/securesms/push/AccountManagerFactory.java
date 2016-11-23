package org.thoughtcrime.securesms.push;

import android.content.Context;
import android.os.AsyncTask;
import org.thoughtcrime.securesms.logging.Log;

import com.google.android.gms.security.ProviderInstaller;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;

public class AccountManagerFactory {

  private static final String TAG = AccountManagerFactory.class.getSimpleName();

  public static SignalServiceAccountManager createManager(Context context) {

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(context),
                                           TextSecurePreferences.getLocalNumber(context),
                                           TextSecurePreferences.getPushServerPassword(context),
                                           TextSecurePreferences.getDeviceId(context),
                                           BuildConfig.USER_AGENT, new UptimeSleepTimer());
  }

  public static SignalServiceAccountManager createManager(final Context context, String number, String password, int deviceId) {
    if (new SignalServiceNetworkAccess(context).isCensored(number)) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          try {
            ProviderInstaller.installIfNeeded(context);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    return new SignalServiceAccountManager(new SignalServiceNetworkAccess(context).getConfiguration(number),
                                           number, password, deviceId, BuildConfig.USER_AGENT, new UptimeSleepTimer());
  }

}
