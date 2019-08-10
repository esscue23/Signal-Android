package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.jobs.ContactSyncRequestJob;
import org.thoughtcrime.securesms.jobs.GroupSyncRequestJob;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.gcm.FcmUtil;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

/**
 * Created by Benni on 08.07.2016.
 */
public class LinkingService extends Service {

  private final Binder binder = new LinkingServiceBinder();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  public static final String LINKING_EVENT = "org.thoughtcrime.securesms.LINKING_EVENT";
  public static final String LINKING_PUBKEY = "org.thoughtcrime.securesms.LINKING_PUBKEY";

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.d("LinkingService", "onStartCommand");
    executor.execute(new Runnable() {
      @Override
      public void run() {
        handleLinkIntent(intent);
      }
    });
    return START_NOT_STICKY;
  }

  private void handleLinkIntent(final Intent linkIntent) {
    try {
      /* create tsdevice link */
      String password = Util.getSecret(18);
      IdentityKeyPair temporaryIdentity = KeyHelper.generateIdentityKeyPair();
      SignalServiceAccountManager accountManager = new SignalServiceAccountManager(new SignalServiceNetworkAccess(this).getConfiguration(this), null, password, BuildConfig.USER_AGENT, new UptimeSleepTimer());

      String uuid = accountManager.getNewDeviceUuid(); /* timeouts sometimes */
      URI tsdevicelink = new URI("tsdevice:/?uuid=" + URLEncoder.encode(uuid, "utf-8") + "&pub_key=" + URLEncoder.encode(Base64.encodeBytesWithoutPadding(temporaryIdentity.getPublicKey().serialize()), "utf-8"));
      Log.d("LinkingService", tsdevicelink.toString());
      Intent intent = new Intent(LINKING_PUBKEY);
      intent.putExtra(LINKING_PUBKEY, tsdevicelink.toString());
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

      /* finish link */
      String temporarySignalingKey = Util.getSecret(52);
      int registrationId = KeyHelper.generateRegistrationId(false);
      String deviceName = "androidtest-" + UUID.randomUUID();
      SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.finishNewDeviceRegistration(temporaryIdentity, temporarySignalingKey, false, true, registrationId, deviceName);
      Optional<String> fcmToken = FcmUtil.getToken();
      accountManager.setGcmId(fcmToken);

      GroupSyncRequestJob groupSyncRequestJob = new GroupSyncRequestJob();
      ContactSyncRequestJob contactSyncRequestJob = new ContactSyncRequestJob();

      /* save identity and deviceid */
      TextSecurePreferences.setDeviceId(this, ret.getDeviceId());
      final String username = ret.getNumber();
      TextSecurePreferences.setLocalNumber(this, ret.getNumber());
      final IdentityKeyPair retIdentity = ret.getIdentity();
      IdentityKeyUtil.setIdentityKeys(this, retIdentity);

      /* create PreKeys */
      List<PreKeyRecord> oneTimePreKeys = PreKeyUtil.generatePreKeys(this);
      SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(this, retIdentity, true);
      accountManager.setPreKeys(retIdentity.getPublicKey(), signedPreKey, oneTimePreKeys);

      /* save own public key to DB */
      Recipient self = Recipient.from(this, Address.fromExternal(this, username), false);
      DatabaseFactory.getIdentityDatabase(this).saveIdentity(self.getAddress(), ret.getIdentity().getPublicKey(), IdentityDatabase.VerifiedStatus.VERIFIED, true, System.currentTimeMillis(), true);

      TextSecurePreferences.setVerifying(this, false);
      TextSecurePreferences.setPushRegistered(this, true);
      TextSecurePreferences.setPushServerPassword(this, password);
      TextSecurePreferences.setSignedPreKeyRegistered(this, true);
      TextSecurePreferences.setPromptedPushRegistration(this, true);
      TextSecurePreferences.setWebsocketRegistered(this, true);
      TextSecurePreferences.setFcmToken(this, fcmToken.get());
      TextSecurePreferences.setFcmDisabled(this, false);
      TextSecurePreferences.setMultiDevice(this, true);
      TextSecurePreferences.setLocalRegistrationId(this, registrationId);
      TextSecurePreferences.setUnauthorizedReceived(this, false);
      TextSecurePreferences.setProfileKey(this, Base64.encodeBytes(ret.getProfileKey()));
      TextSecurePreferences.setUnidentifiedAccessCertificate(this, accountManager.getSenderCertificate());
      TextSecurePreferences.setSignalingKey(this, temporarySignalingKey);
      DirectoryRefreshListener.schedule(this);
      RotateSignedPreKeyListener.schedule(this);

      /* send sync groups */
      ApplicationContext.getInstance(this).getJobManager().add(groupSyncRequestJob);
      ApplicationContext.getInstance(this).getJobManager().add(contactSyncRequestJob);

      intent = new Intent(LINKING_EVENT);
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    } catch (Exception e) {
      Log.w("LinkingService", e);
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
    shutdown();
  }

  public void shutdown() {
  }

  public class LinkingServiceBinder extends Binder {
    public LinkingService getService() {
      return LinkingService.this;
    }
  }
}
