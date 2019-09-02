package org.thoughtcrime.securesms.jobs;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.PKIXRevocationChecker;

import javax.inject.Inject;

public class UpdateContactJob extends BaseJob implements InjectableType {

  public static final String KEY = "UpdateContactJob";

  private static final String TAG = UpdateContactJob.class.getSimpleName();

  private SignalServiceAttachmentPointer aPointer = null;

  private static final String POINTER_ID = "pointer_id";
  private static final String POINTER_CONTENT_TYPE = "pointer_content_type";
  private static final String POINTER_KEY = "pointer_key";
  private static final String POINTER_SIZE = "pointer_size";
  private static final String POINTER_DIGEST = "pointer_digest";

  @Inject SignalServiceMessageReceiver receiver;

  private byte[] groupId;

  public UpdateContactJob(SignalServiceAttachmentPointer aPointer) {
    this(new Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         aPointer);
  }

  private UpdateContactJob(@NonNull Parameters parameters, SignalServiceAttachmentPointer aPointer) {
    super(parameters);
    this.aPointer = aPointer;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
            .putLong(POINTER_ID, aPointer.getId())
            .putString(POINTER_CONTENT_TYPE, aPointer.getContentType())
            .putString(POINTER_KEY, Base64.encodeBytes(aPointer.getKey()))
            .putInt(POINTER_SIZE, aPointer.getSize().get())
            .putString(POINTER_DIGEST, Base64.encodeBytes(aPointer.getDigest().get()))
            .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    Recipient self = Recipient.from(context, Address.fromExternal(context, TextSecurePreferences.getLocalNumber(context)), false);

    File attachment = null;
    InputStream c_in = null;
    try {
      attachment = File.createTempFile("contact", "tmp", context.getCacheDir());
      attachment.deleteOnExit();

      c_in = receiver.retrieveAttachment(aPointer, attachment, Integer.MAX_VALUE);
    } catch (IOException e) {
      Log.w(TAG, e);
      return;
    } catch (InvalidMessageException e) {
      Log.w(TAG, e);
      return;
    }

    DeviceContactsInputStream din = new DeviceContactsInputStream(c_in);
    DeviceContact contact = null;
    try {
      contact = din.read();
    } catch (IOException e) {
      Log.w(TAG, e);
      return;
    }
    if (contact.getNumber().equals(self.getAddress().toPhoneString())) {
      try {
        self.setProfileKey(Base64.decode(TextSecurePreferences.getProfileKey(context)));
      } catch (IOException e) {
        Log.w(TAG, e);
      }
      ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileJob(self));
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  public static final class Factory implements Job.Factory<UpdateContactJob> {
    @Override
    public @NonNull
    UpdateContactJob create(@NonNull Parameters parameters, @NonNull Data data) {
      SignalServiceAttachmentPointer p = null;
      try {
        p = new SignalServiceAttachmentPointer(data.getLong(POINTER_ID),
                data.getString(POINTER_CONTENT_TYPE),
                Base64.decode(data.getString(POINTER_KEY)),
                Optional.of(data.getInt(POINTER_SIZE)),
                Optional.absent(),
                0, 0,
                Optional.of(Base64.decode(data.getString(POINTER_DIGEST))),
                Optional.absent(),
                false,
                Optional.absent());
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return new UpdateContactJob(parameters, p);
    }
  }
}
