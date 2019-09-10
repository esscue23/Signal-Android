package org.thoughtcrime.securesms.jobs;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsInputStream;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.PKIXRevocationChecker;
import java.util.LinkedList;
import java.util.List;

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

    DeviceContactsListInputStream din = new DeviceContactsListInputStream(c_in);
    List<DeviceContact> contacts = null;
    try {
      contacts = din.readList();
    } catch (IOException e) {
      Log.w(TAG, e);
      return;
    }

    for (DeviceContact contact : contacts) {
      if (contact.getNumber().equals(self.getAddress().toPhoneString())) {
        try {
          self.setProfileKey(Base64.decode(TextSecurePreferences.getProfileKey(context)));
        } catch (IOException e) {
          Log.w(TAG, e);
        }
        ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileJob(self));
      } else {
        Address add = Address.fromExternal(context, contact.getNumber());
        Recipient r = Recipient.from(context, add, false);
        //r.setRegistered(RecipientDatabase.RegisteredState.UNKNOWN);
        if (contact.getProfileKey().isPresent()) {
          r.setProfileKey(contact.getProfileKey().get());
        }
        if (contact.getColor().isPresent()) {
          try {
            r.setColor(MaterialColor.fromSerialized(contact.getColor().get()));
          } catch (MaterialColor.UnknownColorException e) {
            Log.w(TAG, e);
          }
        }

        if (contact.getName().isPresent()) {
          r.setName(contact.getName().get());
        }

        IdentityDatabase.VerifiedStatus dbstatus = null;
        IdentityKey identity = null;
        if (contact.getVerified().isPresent()) {
          VerifiedMessage.VerifiedState state = contact.getVerified().get().getVerified();
          identity = contact.getVerified().get().getIdentityKey();
          if (state == VerifiedMessage.VerifiedState.DEFAULT) {
            dbstatus = IdentityDatabase.VerifiedStatus.DEFAULT;
          } else if (state == VerifiedMessage.VerifiedState.UNVERIFIED) {
            dbstatus = IdentityDatabase.VerifiedStatus.UNVERIFIED;
          } else if (state == VerifiedMessage.VerifiedState.UNVERIFIED) {
            dbstatus = IdentityDatabase.VerifiedStatus.VERIFIED;
          }
          
          DatabaseFactory.getIdentityDatabase(context).saveIdentity(r.getAddress(), identity, dbstatus, true, System.currentTimeMillis(), true);
        }

        DirectoryHelper.refreshDirectoryFor(context, r);
        ApplicationContext.getInstance(context).getJobManager().add(new RetrieveProfileJob(r));
        DatabaseFactory.getThreadDatabase(context).getThreadIdFor(r);
      }
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

  public class DeviceContactsListInputStream extends DeviceContactsInputStream {
    private final String TAG = DeviceContactsListInputStream.class.getSimpleName();

    public DeviceContactsListInputStream(InputStream in) {
      super(in);
    }

    public List<DeviceContact> readList() throws IOException {
      List<DeviceContact> result = new LinkedList<>();
      DeviceContact current = read();
      while (current != null) {
        if (current.getAvatar().isPresent()) {
          // Immediately read the avatar in order to move the input stream to the correct position
          // for reading next contact details
          InputStream a_in = current.getAvatar().get().getInputStream();
          while (a_in.read() != -1) {}
        }
        result.add(current);
        current = read();
      }
      return result;
    }
  }
}
